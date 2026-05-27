package ljl.bilibili.chat.creator.service;

import ljl.bilibili.chat.creator.config.XunfeiSparkProperties;
import ljl.bilibili.client.creator.CreatorErrorCode;
import ljl.bilibili.client.creator.CreatorException;
import ljl.bilibili.client.creator.xunfei.XunfeiAuthUtil;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class SparkCredentialPool {

    private static final String LOCK_PREFIX = "creator:spark:lock:";

    @Resource
    private XunfeiSparkProperties sparkProperties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private List<SparkSlot> slots = new ArrayList<>();

    @PostConstruct
    public void init() {
        slots.clear();
        if (sparkProperties.getCredentials() != null) {
            int idx = 0;
            for (XunfeiSparkProperties.SparkCredential c : sparkProperties.getCredentials()) {
                if (XunfeiAuthUtil.isConfigured(c.getAppId(), c.getApiKey(), c.getApiSecret())) {
                    slots.add(new SparkSlot()
                            .setIndex(idx++)
                            .setAppId(c.getAppId())
                            .setApiKey(c.getApiKey())
                            .setApiSecret(c.getApiSecret()));
                }
            }
        }
        log.info("Spark credential pool loaded {} slot(s)", slots.size());
    }

    public boolean hasCredentials() {
        return !slots.isEmpty();
    }

    public SparkSlot acquire() throws InterruptedException {
        if (slots.isEmpty()) {
            throw new CreatorException(CreatorErrorCode.LLM_FAILED, "星火凭证未配置");
        }
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            for (SparkSlot slot : slots) {
                String key = LOCK_PREFIX + slot.getIndex();
                Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 120, TimeUnit.SECONDS);
                if (Boolean.TRUE.equals(ok)) {
                    slot.setHeldKey(key);
                    return slot;
                }
            }
            Thread.sleep(200);
        }
        throw new CreatorException(CreatorErrorCode.LLM_QPS_LIMIT);
    }

    public void release(SparkSlot slot) {
        if (slot != null && slot.getHeldKey() != null) {
            stringRedisTemplate.delete(slot.getHeldKey());
            slot.setHeldKey(null);
        }
    }

    @Data
    @Accessors(chain = true)
    public static class SparkSlot {
        private int index;
        private String appId;
        private String apiKey;
        private String apiSecret;
        private String heldKey;
    }
}
