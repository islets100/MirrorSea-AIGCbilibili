package ljl.bilibili.search.rag.support;

/**
 * 将 LangChain4j 相似度分数归一化到 [0,1]，与历史 minScore 阈值语义对齐
 */
public final class RagScoreNormalizer {

    private RagScoreNormalizer() {
    }

    public static double normalize(Double score) {
        if (score == null) {
            return 0;
        }
        // cosine 相似度通常在 [0,1]；若超出则截断
        return Math.max(0, Math.min(1, score));
    }
}
