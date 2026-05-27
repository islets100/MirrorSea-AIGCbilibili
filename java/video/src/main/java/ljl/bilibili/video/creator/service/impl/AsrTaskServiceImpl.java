package ljl.bilibili.video.creator.service.impl;

import ljl.bilibili.client.creator.AsrSubmitRequest;
import ljl.bilibili.client.creator.AsrTaskResponse;
import ljl.bilibili.client.creator.CreatorErrorCode;
import ljl.bilibili.client.creator.CreatorException;
import ljl.bilibili.client.creator.VideoContextResponse;
import ljl.bilibili.video.creator.client.XunfeiAsrClient;
import ljl.bilibili.video.creator.config.CreatorAsrProperties;
import ljl.bilibili.video.creator.service.AsrTaskService;
import ljl.bilibili.video.creator.service.CreatorContextService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AsrTaskServiceImpl implements AsrTaskService {

    private static final String ASR_KEY_PREFIX = "creator:asr:";
    private static final Pattern ONEBEST = Pattern.compile("\"onebest\"\\s*:\\s*\"([^\"]*)\"");

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CreatorContextService creatorContextService;

    @Resource
    private CreatorAsrProperties creatorAsrProperties;

    @Resource
    private XunfeiAsrClient xunfeiAsrClient;

    @Override
    public AsrTaskResponse submit(AsrSubmitRequest request) {
        xunfeiAsrClient.ensureConfigured();
        VideoContextResponse context = creatorContextService.getContext(
                request.getResumableIdentifier(), request.getVideoUrl());
        if (!Boolean.TRUE.equals(context.getMerged()) && context.getVideoUrl() == null) {
            throw new CreatorException(CreatorErrorCode.VIDEO_NOT_READY);
        }
        String asrTaskId = "asr_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "_" + UUID.randomUUID().toString().substring(0, 6);
        stringRedisTemplate.opsForValue().set(
                ASR_KEY_PREFIX + asrTaskId,
                "RUNNING",
                creatorAsrProperties.getTimeoutSeconds(),
                TimeUnit.SECONDS
        );
        runAsrAsync(asrTaskId, context);
        return new AsrTaskResponse().setAsrTaskId(asrTaskId).setStatus("RUNNING");
    }

    @Override
    public AsrTaskResponse getTask(String asrTaskId) {
        String status = stringRedisTemplate.opsForValue().get(ASR_KEY_PREFIX + asrTaskId);
        String text = stringRedisTemplate.opsForValue().get(ASR_KEY_PREFIX + asrTaskId + ":text");
        AsrTaskResponse response = new AsrTaskResponse();
        response.setAsrTaskId(asrTaskId);
        if (status == null) {
            response.setStatus("FAILED");
            return response;
        }
        response.setStatus(status);
        response.setText(text);
        if ("COMPLETED".equals(status)) {
            response.setConfidence(0.92);
            response.setDurationSeconds(creatorAsrProperties.getMaxAudioSeconds());
        }
        return response;
    }

    @Async
    public void runAsrAsync(String asrTaskId, VideoContextResponse context) {
        try {
            String objectName = context.getVideoUrl();
            if (objectName == null || objectName.isEmpty()) {
                objectName = context.getResumableIdentifier();
            }
            String raw = xunfeiAsrClient.transcribe(objectName);
            String text = extractTextFromLattice(raw);
            stringRedisTemplate.opsForValue().set(
                    ASR_KEY_PREFIX + asrTaskId + ":text",
                    text,
                    creatorAsrProperties.getTimeoutSeconds(),
                    TimeUnit.SECONDS
            );
            stringRedisTemplate.opsForValue().set(
                    ASR_KEY_PREFIX + asrTaskId,
                    "COMPLETED",
                    creatorAsrProperties.getTimeoutSeconds(),
                    TimeUnit.SECONDS
            );
            log.info("ASR task completed: {}", asrTaskId);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("ASR task failed: {}", asrTaskId, e);
            stringRedisTemplate.opsForValue().set(ASR_KEY_PREFIX + asrTaskId, "FAILED");
        }
    }

    private static String extractTextFromLattice(String lattice) {
        if (lattice == null || lattice.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Matcher m = ONEBEST.matcher(lattice);
        while (m.find()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(m.group(1));
        }
        if (sb.length() > 0) {
            return sb.toString();
        }
        return lattice.length() > 2000 ? lattice.substring(0, 2000) : lattice;
    }
}
