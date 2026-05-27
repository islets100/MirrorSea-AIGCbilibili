package ljl.bilibili.search.rag.service.impl;

import ljl.bilibili.search.rag.config.CreatorRagProperties;
import ljl.bilibili.search.rag.service.EmbeddingService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * 本地确定性 Embedding：无需外部 API 即可跑通 RAG 链路。
 * 生产环境可替换为讯飞 Embedding 实现。
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "creator.rag.embedding-provider", havingValue = "local", matchIfMissing = true)
public class LocalEmbeddingService implements EmbeddingService {

    private final int dims;

    public LocalEmbeddingService(CreatorRagProperties properties) {
        this.dims = properties.getEmbeddingDims();
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[dims];
        }
        float[] vector = new float[dims];
        String normalized = text.toLowerCase(Locale.ROOT).trim();
        byte[] seed = sha256(normalized);
        for (int i = 0; i < dims; i++) {
            int idx = i % seed.length;
            vector[i] = ((seed[idx] & 0xFF) / 255.0f) * 2 - 1;
        }
        normalize(vector);
        return vector;
    }

    @Override
    public int dimensions() {
        return dims;
    }

    private static byte[] sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void normalize(float[] vector) {
        double sum = 0;
        for (float v : vector) {
            sum += v * v;
        }
        if (sum == 0) {
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }
}
