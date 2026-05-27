package ljl.bilibili.chat.creator.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import ljl.bilibili.chat.creator.config.XunfeiSparkProperties;
import ljl.bilibili.chat.creator.event.CreatorSuggestEvent;
import ljl.bilibili.chat.creator.service.SparkCredentialPool.SparkSlot;
import ljl.bilibili.client.creator.*;
import ljl.bilibili.client.creator.xunfei.XunfeiAuthUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 星火 WebSocket 真流式生成，输出 JSON：titles[3] + intros[2]。
 */
@Component
@Slf4j
public class CreatorSparkStreamHandler {

    private static final Gson GSON = new Gson();

    @Resource
    private XunfeiSparkProperties sparkProperties;

    @Resource
    private SparkCredentialPool sparkCredentialPool;

    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    public CreatorSuggestResult generate(String userId, String taskId, String prompt, RagRetrieveResponse ragResponse)
            throws InterruptedException {
        if (!sparkCredentialPool.hasCredentials()) {
            throw new CreatorException(CreatorErrorCode.LLM_FAILED, "星火凭证未配置");
        }
        SparkSlot slot = sparkCredentialPool.acquire();
        try {
            String authUrl;
            try {
                authUrl = XunfeiAuthUtil.buildWsAuthUrl(
                        sparkProperties.getHostUrl(), slot.getApiKey(), slot.getApiSecret());
            } catch (Exception e) {
                throw new CreatorException(CreatorErrorCode.LLM_FAILED, e.getMessage());
            }
            String wsUrl = authUrl.replace("http://", "ws://").replace("https://", "wss://");
            CountDownLatch done = new CountDownLatch(1);
            StringBuilder full = new StringBuilder();
            AtomicReference<String> field = new AtomicReference<>("title");
            AtomicReference<Exception> error = new AtomicReference<>();

            OkHttpClient client = new OkHttpClient.Builder()
                    .readTimeout(120, TimeUnit.SECONDS)
                    .build();
            Request request = new Request.Builder().url(wsUrl).build();
            WebSocketListener listener = new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    sendRequest(webSocket, slot.getAppId(), prompt);
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    try {
                        SparkMessage msg = GSON.fromJson(text, SparkMessage.class);
                        if (msg.header != null && msg.header.code != 0) {
                            error.set(new CreatorException(CreatorErrorCode.LLM_FAILED,
                                    "星火错误码:" + msg.header.code));
                            done.countDown();
                            webSocket.close(1000, "");
                            return;
                        }
                        if (msg.payload != null && msg.payload.choices != null && msg.payload.choices.text != null) {
                            for (SparkText t : msg.payload.choices.text) {
                                if (t.content != null && !t.content.isEmpty()) {
                                    full.append(t.content);
                                    if (full.indexOf("\"intros\"") >= 0) {
                                        field.set("intro");
                                    }
                                    pushToken(userId, taskId, field.get(), t.content);
                                }
                            }
                        }
                        if (msg.header != null && msg.header.status == 2) {
                            done.countDown();
                            webSocket.close(1000, "");
                        }
                    } catch (Exception e) {
                        error.set(e);
                        done.countDown();
                    }
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    error.set(new CreatorException(CreatorErrorCode.LLM_FAILED, t.getMessage()));
                    done.countDown();
                }
            };
            client.newWebSocket(request, listener);
            if (!done.await(90, TimeUnit.SECONDS)) {
                throw new CreatorException(CreatorErrorCode.LLM_FAILED, "星火响应超时");
            }
            if (error.get() != null) {
                if (error.get() instanceof CreatorException) {
                    throw (CreatorException) error.get();
                }
                throw new CreatorException(CreatorErrorCode.LLM_FAILED, error.get().getMessage());
            }
            return parseAndValidate(full.toString(), ragResponse);
        } finally {
            sparkCredentialPool.release(slot);
        }
    }

    private void sendRequest(WebSocket webSocket, String appId, String prompt) {
        JSONObject requestJson = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("app_id", appId);
        header.put("uid", UUID.randomUUID().toString().substring(0, 10));
        JSONObject parameter = new JSONObject();
        JSONObject chat = new JSONObject();
        chat.put("domain", sparkProperties.getDomain());
        chat.put("temperature", 0.5);
        chat.put("max_tokens", 4096);
        parameter.put("chat", chat);
        JSONObject payload = new JSONObject();
        JSONObject message = new JSONObject();
        JSONArray text = new JSONArray();
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", prompt);
        text.add(user);
        message.put("text", text);
        payload.put("message", message);
        requestJson.put("header", header);
        requestJson.put("parameter", parameter);
        requestJson.put("payload", payload);
        webSocket.send(requestJson.toJSONString());
    }

    private CreatorSuggestResult parseAndValidate(String raw, RagRetrieveResponse ragResponse) {
        String json = extractJsonObject(raw);
        JSONObject obj = JSON.parseObject(json);
        if (obj == null) {
            throw new CreatorException(CreatorErrorCode.LLM_FAILED, "模型输出非 JSON");
        }
        JSONArray titlesArr = obj.getJSONArray("titles");
        JSONArray introsArr = obj.getJSONArray("intros");
        if (titlesArr == null || titlesArr.size() != 3 || introsArr == null || introsArr.size() != 2) {
            throw new CreatorException(CreatorErrorCode.LLM_FAILED, "模型输出结构不符合约定");
        }
        List<String> titles = titlesArr.toJavaList(String.class);
        List<String> intros = introsArr.toJavaList(String.class);
        CreatorSuggestResult result = new CreatorSuggestResult();
        result.setTitles(titles);
        result.setIntros(intros);
        result.setReferences(buildReferences(ragResponse));
        return result;
    }

    private static String extractJsonObject(String raw) {
        if (raw == null) {
            return "{}";
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw.trim();
    }

    private List<CreatorReferenceItem> buildReferences(RagRetrieveResponse ragResponse) {
        List<CreatorReferenceItem> refs = new ArrayList<>();
        if (ragResponse == null) {
            return refs;
        }
        if (ragResponse.getHotCases() != null) {
            for (HotCaseItem item : ragResponse.getHotCases()) {
                refs.add(new CreatorReferenceItem()
                        .setSourceType("HOT_CASE")
                        .setTitle(item.getTitle())
                        .setIntro(item.getIntro())
                        .setPlayCount(item.getPlayCount())
                        .setScore(item.getScore()));
            }
        }
        if (ragResponse.getKnowledgeChunks() != null) {
            for (KnowledgeChunkItem chunk : ragResponse.getKnowledgeChunks()) {
                refs.add(new CreatorReferenceItem()
                        .setSourceType("KNOWLEDGE")
                        .setTitle(chunk.getSourceFile())
                        .setContent(chunk.getContent())
                        .setScore(chunk.getScore()));
            }
        }
        return refs;
    }

    private void pushToken(String userId, String taskId, String field, String content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "creator_suggest");
        payload.put("taskId", taskId);
        payload.put("status", 1);
        payload.put("field", field);
        payload.put("content", content);
        applicationEventPublisher.publishEvent(
                new CreatorSuggestEvent(this, userId, taskId, JSON.toJSONString(payload)));
    }

    private static class SparkMessage {
        SparkHeader header;
        SparkPayload payload;
    }

    private static class SparkHeader {
        int code;
        int status;
    }

    private static class SparkPayload {
        SparkChoices choices;
    }

    private static class SparkChoices {
        List<SparkText> text;
    }

    private static class SparkText {
        String content;
    }
}
