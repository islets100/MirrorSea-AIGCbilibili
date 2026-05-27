package ljl.bilibili.chat.creator.service;

import com.alibaba.fastjson.JSON;
import feign.FeignException;
import ljl.bilibili.chat.creator.constant.CreatorTaskStatus;
import ljl.bilibili.chat.creator.entity.CreatorSuggestLog;
import ljl.bilibili.chat.creator.event.CreatorSuggestEvent;
import ljl.bilibili.chat.creator.mapper.CreatorSuggestLogMapper;
import ljl.bilibili.chat.creator.config.CreatorSuggestProperties;
import ljl.bilibili.chat.creator.validation.CreatorSuggestRequestValidator;
import ljl.bilibili.chat.handler.WebSocketHandler;
import ljl.bilibili.client.creator.*;
import ljl.bilibili.client.search.SearchClient;
import ljl.bilibili.client.video.VideoClient;
import ljl.bilibili.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CreatorSuggestServiceImpl implements CreatorSuggestService {

    private static final String TASK_KEY = "creator:task:";
    private static final String IDEMPOTENT_KEY = "creator:idempotent:";
    private static final String RATE_KEY = "creator:rate:";
    private static final String REF_KEY = "creator:ref:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CreatorSuggestProperties creatorSuggestProperties;

    @Resource
    private VideoClient videoClient;

    @Resource
    private SearchClient searchClient;

    @Resource
    private CreatorPromptBuilder creatorPromptBuilder;

    @Resource
    private CreatorLLMService creatorLLMService;

    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    @Resource
    private CreatorSuggestLogMapper creatorSuggestLogMapper;

    @Override
    public Result<CreatorSuggestSubmitResponse> submit(CreatorSuggestRequest request) {
        Result<Void> validation = CreatorSuggestRequestValidator.validateSubmit(request);
        if (validation != null) {
            return new Result<>(validation.getCode(), validation.getMsg(), null);
        }
        if (!checkRateLimit(request.getUserId())) {
            return Result.bizError(CreatorErrorCode.LLM_QPS_LIMIT);
        }
        String idempotentKey = IDEMPOTENT_KEY + request.getUserId() + ":"
                + (request.getResumableIdentifier() != null ? request.getResumableIdentifier() : request.getVideoUrl());
        String existingTaskId = stringRedisTemplate.opsForValue().get(idempotentKey);
        if (existingTaskId != null) {
            CreatorSuggestTaskResponse existing = getTaskInternal(existingTaskId);
            if (existing != null && !CreatorTaskStatus.FAILED.equals(existing.getStatus())) {
                return Result.data(new CreatorSuggestSubmitResponse()
                        .setTaskId(existingTaskId)
                        .setStatus(existing.getStatus())
                        .setEstimatedSeconds(15));
            }
        }
        String taskId = "cs_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "_" + UUID.randomUUID().toString().substring(0, 6);
        CreatorSuggestTaskResponse task = new CreatorSuggestTaskResponse()
                .setTaskId(taskId)
                .setStatus(CreatorTaskStatus.PENDING)
                .setProgress(0)
                .setPartial(false);
        saveTask(taskId, request.getUserId(), task);
        WebSocketHandler.CREATOR_TASK_USER_MAP.put(taskId, String.valueOf(request.getUserId()));
        stringRedisTemplate.opsForValue().set(idempotentKey, taskId,
                creatorSuggestProperties.getIdempotentMinutes(), TimeUnit.MINUTES);
        runPipelineAsync(taskId, request);
        return Result.data(new CreatorSuggestSubmitResponse()
                .setTaskId(taskId)
                .setStatus(CreatorTaskStatus.PENDING)
                .setEstimatedSeconds(15));
    }

    @Override
    public Result<CreatorSuggestTaskResponse> getTask(String taskId, Integer userId) {
        CreatorSuggestTaskResponse task = getTaskInternal(taskId);
        if (task == null) {
            return Result.bizError(CreatorErrorCode.TASK_NOT_FOUND);
        }
        if (!ownsTask(taskId, userId)) {
            return Result.bizError(CreatorErrorCode.FORBIDDEN);
        }
        return Result.data(task);
    }

    @Override
    public Result<RagRetrieveResponse> getReferences(String taskId, Integer userId) {
        if (!ownsTask(taskId, userId)) {
            return Result.bizError(CreatorErrorCode.FORBIDDEN);
        }
        String refJson = stringRedisTemplate.opsForValue().get(REF_KEY + taskId);
        if (refJson == null) {
            return Result.data(new RagRetrieveResponse());
        }
        return Result.data(JSON.parseObject(refJson, RagRetrieveResponse.class));
    }

    @Override
    public Result<Boolean> adopt(String taskId, CreatorAdoptRequest request) {
        if (!ownsTask(taskId, request.getUserId())) {
            return Result.bizError(CreatorErrorCode.FORBIDDEN);
        }
        CreatorSuggestTaskResponse task = getTaskInternal(taskId);
        CreatorSuggestLog logEntity = new CreatorSuggestLog()
                .setTaskId(taskId)
                .setUserId(request.getUserId())
                .setAdoptedTitle(request.getAdoptedTitle())
                .setAdoptedIntro(request.getAdoptedIntro())
                .setCreatedAt(LocalDateTime.now());
        if (task != null && task.getResult() != null) {
            logEntity.setOutputSnapshot(JSON.toJSONString(task.getResult()));
        }
        logEntity.setInputSnapshot(JSON.toJSONString(request));
        creatorSuggestLogMapper.insert(logEntity);
        log.info("Creator suggest adopted taskId={}, title={}", taskId, request.getAdoptedTitle());
        return Result.success(true);
    }

    @Async
    protected void runPipelineAsync(String taskId, CreatorSuggestRequest request) {
        Integer warningCode = null;
        try {
            updateStatus(taskId, request.getUserId(), CreatorTaskStatus.EXTRACTING, 10, false, null, null);
            pushProgress(request.getUserId(), taskId, CreatorTaskStatus.EXTRACTING, 10);

            Result<VideoContextResponse> contextResult = videoClient.getCreatorContext(
                    request.getResumableIdentifier(), request.getVideoUrl());
            VideoContextResponse context = contextResult != null ? contextResult.getData() : null;
            if (contextResult != null && contextResult.getCode() != 200) {
                failTask(request.getUserId(), taskId, contextResult.getCode(), contextResult.getMsg());
                return;
            }
            if (context == null || !Boolean.TRUE.equals(context.getMerged())) {
                failTask(request.getUserId(), taskId, CreatorErrorCode.VIDEO_NOT_READY.getCode(),
                        CreatorErrorCode.VIDEO_NOT_READY.getUserMessage());
                return;
            }

            updateStatus(taskId, request.getUserId(), CreatorTaskStatus.ASR_RUNNING, 25, false, null, null);
            pushProgress(request.getUserId(), taskId, CreatorTaskStatus.ASR_RUNNING, 25);

            AsrSubmitRequest asrSubmit = new AsrSubmitRequest();
            asrSubmit.setResumableIdentifier(request.getResumableIdentifier());
            asrSubmit.setVideoUrl(context.getVideoUrl());
            Result<AsrTaskResponse> asrSubmitResult = videoClient.submitAsr(asrSubmit);
            if (asrSubmitResult != null && asrSubmitResult.getCode() != 200) {
                if (asrSubmitResult.getCode() == CreatorErrorCode.ASR_UNAVAILABLE.getCode()) {
                    failTask(request.getUserId(), taskId, asrSubmitResult.getCode(), asrSubmitResult.getMsg());
                    return;
                }
            }
            AsrTaskResponse asrTask = asrSubmitResult != null ? asrSubmitResult.getData() : null;

            String asrText = pollAsr(asrTask);
            boolean partial = asrText == null || asrText.isEmpty();
            if (partial) {
                warningCode = CreatorErrorCode.ASR_DEGRADED.getCode();
                asrText = buildFallbackText(context, request);
            }

            updateStatus(taskId, request.getUserId(),
                    partial ? CreatorTaskStatus.PARTIAL : CreatorTaskStatus.ASR_DONE, 45, partial, null, warningCode);
            pushProgress(request.getUserId(), taskId, CreatorTaskStatus.ASR_DONE, 45);

            updateStatus(taskId, request.getUserId(), CreatorTaskStatus.RETRIEVING, 55, partial, null, warningCode);
            pushProgress(request.getUserId(), taskId, CreatorTaskStatus.RETRIEVING, 55);

            String queryText = buildQueryText(asrText, request);
            RagRetrieveRequest ragRequest = new RagRetrieveRequest();
            ragRequest.setQueryText(queryText);
            ragRequest.setTopKCase(5);
            ragRequest.setTopKKnowledge(3);
            ragRequest.setMinScore(0.5);
            RagRetrieveResponse ragResponse;
            try {
                Result<RagRetrieveResponse> ragResult = searchClient.ragRetrieve(ragRequest);
                if (ragResult != null && ragResult.getCode() != 200) {
                    failTask(request.getUserId(), taskId, ragResult.getCode(), ragResult.getMsg());
                    return;
                }
                ragResponse = ragResult != null ? ragResult.getData() : new RagRetrieveResponse();
            } catch (FeignException fe) {
                CreatorException ex = CreatorFeignHelper.fromFeignBody(fe.contentUTF8());
                failTask(request.getUserId(), taskId, ex.getCode(), ex.getUserMessage());
                return;
            }
            if (ragResponse == null) {
                ragResponse = new RagRetrieveResponse();
            }
            boolean ragEmpty = (ragResponse.getHotCases() == null || ragResponse.getHotCases().isEmpty())
                    && (ragResponse.getKnowledgeChunks() == null || ragResponse.getKnowledgeChunks().isEmpty());
            if (ragEmpty && warningCode == null) {
                warningCode = CreatorErrorCode.RAG_EMPTY.getCode();
            }
            stringRedisTemplate.opsForValue().set(REF_KEY + taskId, JSON.toJSONString(ragResponse), 24, TimeUnit.HOURS);

            updateStatus(taskId, request.getUserId(), CreatorTaskStatus.GENERATING, 70, partial, null, warningCode);
            pushProgress(request.getUserId(), taskId, CreatorTaskStatus.GENERATING, 70);

            String prompt = creatorPromptBuilder.build(queryText, ragResponse, request);
            CreatorSuggestResult result = creatorLLMService.generate(
                    request.getUserId().toString(), taskId, prompt, ragResponse);

            CreatorSuggestTaskResponse completed = new CreatorSuggestTaskResponse()
                    .setTaskId(taskId)
                    .setStatus(partial ? CreatorTaskStatus.PARTIAL : CreatorTaskStatus.COMPLETED)
                    .setProgress(100)
                    .setPartial(partial)
                    .setWarningCode(warningCode)
                    .setResult(result);
            saveTask(taskId, request.getUserId(), completed);
            pushComplete(request.getUserId(), taskId, completed);
        } catch (CreatorException e) {
            failTask(request.getUserId(), taskId, e.getCode(), e.getUserMessage());
        } catch (FeignException e) {
            CreatorException ex = CreatorFeignHelper.fromFeignBody(e.contentUTF8());
            failTask(request.getUserId(), taskId, ex.getCode(), ex.getUserMessage());
        } catch (Exception e) {
            log.error("Creator suggest pipeline failed taskId={}", taskId, e);
            failTask(request.getUserId(), taskId, CreatorErrorCode.LLM_FAILED.getCode(),
                    CreatorErrorCode.LLM_FAILED.getUserMessage());
        }
    }

    private String pollAsr(AsrTaskResponse asrTask) throws InterruptedException {
        if (asrTask == null || asrTask.getAsrTaskId() == null) {
            return null;
        }
        long deadline = System.currentTimeMillis() + creatorSuggestProperties.getAsrTimeoutSeconds() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Result<AsrTaskResponse> statusResult = videoClient.getAsrTask(asrTask.getAsrTaskId());
            if (statusResult != null && statusResult.getCode() != 200) {
                return null;
            }
            AsrTaskResponse status = statusResult != null ? statusResult.getData() : null;
            if (status != null && "COMPLETED".equals(status.getStatus())) {
                return status.getText();
            }
            if (status != null && "FAILED".equals(status.getStatus())) {
                return null;
            }
            Thread.sleep(creatorSuggestProperties.getAsrPollIntervalMs());
        }
        return null;
    }

    private void failTask(Integer userId, String taskId, int errorCode, String message) {
        updateStatus(taskId, userId, CreatorTaskStatus.FAILED, 0, false, null, null);
        pushError(userId, taskId, errorCode, message);
    }

    private static String buildFallbackText(VideoContextResponse context, CreatorSuggestRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getDraftTitle() != null) {
            sb.append(request.getDraftTitle()).append(" ");
        }
        if (request.getDraftIntro() != null) {
            sb.append(request.getDraftIntro()).append(" ");
        }
        if (context.getFileName() != null) {
            sb.append(context.getFileName());
        }
        return sb.toString().trim();
    }

    private static String buildQueryText(String asrText, CreatorSuggestRequest request) {
        StringBuilder sb = new StringBuilder();
        if (asrText != null) {
            sb.append(asrText).append(" ");
        }
        if (request.getDraftTitle() != null) {
            sb.append(request.getDraftTitle()).append(" ");
        }
        if (request.getDraftIntro() != null) {
            sb.append(request.getDraftIntro()).append(" ");
        }
        if (request.getTags() != null) {
            sb.append(String.join(" ", request.getTags()));
        }
        return sb.toString().trim();
    }

    private boolean checkRateLimit(Integer userId) {
        String key = RATE_KEY + userId;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, 1, TimeUnit.MINUTES);
        }
        return count == null || count <= creatorSuggestProperties.getRateLimitPerMinute();
    }

    private void saveTask(String taskId, Integer userId, CreatorSuggestTaskResponse task) {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("task", task);
        stringRedisTemplate.opsForValue().set(TASK_KEY + taskId, JSON.toJSONString(map), 24, TimeUnit.HOURS);
    }

    private CreatorSuggestTaskResponse getTaskInternal(String taskId) {
        String json = stringRedisTemplate.opsForValue().get(TASK_KEY + taskId);
        if (json == null) {
            return null;
        }
        Map map = JSON.parseObject(json, Map.class);
        Object taskObj = map.get("task");
        return JSON.parseObject(JSON.toJSONString(taskObj), CreatorSuggestTaskResponse.class);
    }

    private boolean ownsTask(String taskId, Integer userId) {
        String json = stringRedisTemplate.opsForValue().get(TASK_KEY + taskId);
        if (json == null) {
            return false;
        }
        Map map = JSON.parseObject(json, Map.class);
        Object uid = map.get("userId");
        return uid != null && userId.toString().equals(String.valueOf(uid));
    }

    private void updateStatus(String taskId, Integer userId, String status, int progress, boolean partial,
                              CreatorSuggestResult result, Integer warningCode) {
        CreatorSuggestTaskResponse task = getTaskInternal(taskId);
        if (task == null) {
            task = new CreatorSuggestTaskResponse().setTaskId(taskId);
        }
        task.setStatus(status).setProgress(progress).setPartial(partial).setWarningCode(warningCode);
        if (result != null) {
            task.setResult(result);
        }
        saveTask(taskId, userId, task);
    }

    private void pushProgress(Integer userId, String taskId, String phase, int progress) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "creator_suggest");
        payload.put("taskId", taskId);
        payload.put("status", 0);
        payload.put("phase", phase);
        payload.put("progress", progress);
        applicationEventPublisher.publishEvent(new CreatorSuggestEvent(
                this, String.valueOf(userId), taskId, JSON.toJSONString(payload)));
    }

    private void pushComplete(Integer userId, String taskId, CreatorSuggestTaskResponse completed) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "creator_suggest");
        payload.put("taskId", taskId);
        payload.put("status", 2);
        payload.put("result", completed.getResult());
        applicationEventPublisher.publishEvent(new CreatorSuggestEvent(
                this, String.valueOf(userId), taskId, JSON.toJSONString(payload)));
    }

    private void pushError(Integer userId, String taskId, int errorCode, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "creator_suggest");
        payload.put("taskId", taskId);
        payload.put("status", -1);
        payload.put("errorCode", errorCode);
        payload.put("message", message);
        applicationEventPublisher.publishEvent(new CreatorSuggestEvent(
                this, String.valueOf(userId), taskId, JSON.toJSONString(payload)));
    }
}
