package ljl.bilibili.video.creator.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import ljl.bilibili.client.creator.CreatorErrorCode;
import ljl.bilibili.client.creator.CreatorException;
import ljl.bilibili.client.creator.xunfei.XunfeiAuthUtil;
import ljl.bilibili.video.creator.config.XunfeiAsrProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 讯飞录音文件转写（LFASR）客户端。凭证未配置时抛出 50002。
 */
@Component
@Slf4j
public class XunfeiAsrClient {

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    @Resource
    private XunfeiAsrProperties properties;

    public void ensureConfigured() {
        if (!XunfeiAuthUtil.isConfigured(properties.getAppId(), properties.getApiKey(), properties.getApiSecret())) {
            throw new CreatorException(CreatorErrorCode.ASR_UNAVAILABLE);
        }
    }

    /**
     * 提交转写并轮询至完成，返回全文。失败抛 CreatorException。
     */
    public String transcribe(String videoObjectName) {
        ensureConfigured();
        try {
            String audioUrl = properties.getAudioBaseUrl() + videoObjectName;
            String orderId = uploadByUrl(audioUrl);
            return pollResult(orderId);
        } catch (CreatorException e) {
            throw e;
        } catch (Exception e) {
            log.error("Xunfei ASR failed", e);
            throw new CreatorException(CreatorErrorCode.ASR_UNAVAILABLE, e.getMessage());
        }
    }

    private String uploadByUrl(String audioUrl) throws Exception {
        long ts = System.currentTimeMillis() / 1000;
        String signature = sign(properties.getAppId(), properties.getApiSecret(), ts);
        HttpUrl url = HttpUrl.parse(properties.getLfasrHost() + "/upload").newBuilder()
                .addQueryParameter("appId", properties.getAppId())
                .addQueryParameter("ts", String.valueOf(ts))
                .addQueryParameter("signa", signature)
                .addQueryParameter("fileName", "video.mp4")
                .addQueryParameter("fileSize", "1")
                .addQueryParameter("duration", "300")
                .addQueryParameter("audioMode", "urlLink")
                .addQueryParameter("audioUrl", audioUrl)
                .build();
        Request request = new Request.Builder().url(url).post(RequestBody.create(null, new byte[0])).build();
        try (Response response = HTTP.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            JSONObject json = JSON.parseObject(body);
            if (json != null && "000000".equals(json.getString("code"))) {
                return json.getJSONObject("content").getString("orderId");
            }
            log.warn("LFASR upload response: {}", body);
            throw new CreatorException(CreatorErrorCode.ASR_UNAVAILABLE, "ASR upload failed");
        }
    }

    private String pollResult(String orderId) throws Exception {
        for (int i = 0; i < 60; i++) {
            long ts = System.currentTimeMillis() / 1000;
            String signature = sign(properties.getAppId(), properties.getApiSecret(), ts);
            HttpUrl url = HttpUrl.parse(properties.getLfasrHost() + "/getResult").newBuilder()
                    .addQueryParameter("appId", properties.getAppId())
                    .addQueryParameter("ts", String.valueOf(ts))
                    .addQueryParameter("signa", signature)
                    .addQueryParameter("orderId", orderId)
                    .addQueryParameter("resultType", "transfer")
                    .build();
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = HTTP.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                JSONObject json = JSON.parseObject(body);
                if (json == null) {
                    Thread.sleep(2000);
                    continue;
                }
                if ("000000".equals(json.getString("code"))) {
                    JSONObject content = json.getJSONObject("content");
                    if (content != null && content.getIntValue("status") == 4) {
                        return content.getJSONObject("orderResult").getString("lattice");
                    }
                }
            }
            Thread.sleep(2000);
        }
        throw new CreatorException(CreatorErrorCode.ASR_UNAVAILABLE, "ASR poll timeout");
    }

    private static String sign(String appId, String secret, long ts) throws Exception {
        String base = appId + ts;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(base.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }
}
