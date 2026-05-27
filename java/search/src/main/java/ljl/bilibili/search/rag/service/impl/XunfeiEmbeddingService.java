package ljl.bilibili.search.rag.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import ljl.bilibili.client.creator.CreatorErrorCode;
import ljl.bilibili.client.creator.CreatorException;
import ljl.bilibili.client.creator.xunfei.XunfeiAuthUtil;
import ljl.bilibili.search.rag.config.CreatorRagProperties;
import ljl.bilibili.search.rag.config.XunfeiEmbeddingProperties;
import ljl.bilibili.search.rag.service.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
@Primary
@ConditionalOnProperty(name = "creator.rag.embedding-provider", havingValue = "xunfei")
@Slf4j
public class XunfeiEmbeddingService implements EmbeddingService {

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    @Resource
    private XunfeiEmbeddingProperties embeddingProperties;

    @Resource
    private CreatorRagProperties creatorRagProperties;

    @Override
    public float[] embed(String text) {
        if (!XunfeiAuthUtil.isConfigured(
                embeddingProperties.getAppId(),
                embeddingProperties.getApiKey(),
                embeddingProperties.getApiSecret())) {
            throw new CreatorException(CreatorErrorCode.EMBEDDING_FAILED);
        }
        if (text == null || text.trim().isEmpty()) {
            return new float[creatorRagProperties.getEmbeddingDims()];
        }
        try {
            JSONObject body = new JSONObject();
            JSONObject header = new JSONObject();
            header.put("app_id", embeddingProperties.getAppId());
            header.put("status", 3);
            JSONObject parameter = new JSONObject();
            JSONObject emb = new JSONObject();
            emb.put("domain", "query");
            emb.put("feature", "l2_norm");
            parameter.put("emb", emb);
            JSONObject payload = new JSONObject();
            JSONObject message = new JSONObject();
            JSONArray texts = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("text", text);
            texts.add(item);
            message.put("text", texts);
            payload.put("message", message);
            body.put("header", header);
            body.put("parameter", parameter);
            body.put("payload", payload);

            Request request = new Request.Builder()
                    .url(embeddingProperties.getHostUrl())
                    .addHeader("Authorization", buildRestAuth())
                    .post(RequestBody.create(
                            MediaType.parse("application/json; charset=utf-8"),
                            body.toJSONString()))
                    .build();
            try (Response response = HTTP.newCall(request).execute()) {
                String resp = response.body() != null ? response.body().string() : "";
                return parseVector(resp);
            }
        } catch (CreatorException e) {
            throw e;
        } catch (Exception e) {
            log.error("Xunfei embedding failed", e);
            throw new CreatorException(CreatorErrorCode.EMBEDDING_FAILED, e.getMessage());
        }
    }

    @Override
    public int dimensions() {
        return creatorRagProperties.getEmbeddingDims();
    }

    private String buildRestAuth() throws Exception {
        long ts = System.currentTimeMillis() / 1000;
        String base = embeddingProperties.getApiKey() + ts;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(embeddingProperties.getApiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sign = Base64.getEncoder().encodeToString(mac.doFinal(base.getBytes(StandardCharsets.UTF_8)));
        return String.format(
                "api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
                embeddingProperties.getApiKey(), sign);
    }

    private float[] parseVector(String resp) {
        JSONObject json = JSON.parseObject(resp);
        if (json == null) {
            throw new CreatorException(CreatorErrorCode.EMBEDDING_FAILED);
        }
        JSONObject header = json.getJSONObject("header");
        if (header != null && header.getIntValue("code") != 0) {
            throw new CreatorException(CreatorErrorCode.EMBEDDING_FAILED, header.getString("message"));
        }
        JSONArray vector = null;
        JSONObject payload = json.getJSONObject("payload");
        if (payload != null) {
            JSONObject emb = payload.getJSONObject("emb");
            if (emb != null) {
                vector = emb.getJSONArray("vector");
            }
        }
        if (vector == null || vector.isEmpty()) {
            throw new CreatorException(CreatorErrorCode.EMBEDDING_FAILED);
        }
        int dims = creatorRagProperties.getEmbeddingDims();
        float[] result = new float[dims];
        for (int i = 0; i < Math.min(dims, vector.size()); i++) {
            result[i] = vector.getFloatValue(i);
        }
        return result;
    }
}
