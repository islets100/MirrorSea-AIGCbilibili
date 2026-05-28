package ljl.bilibili.search.rag.constant;

public final class RagConstant {
    private RagConstant() {
    }

    public static final String KNOWLEDGE_INDEX = "creator_knowledge";
    public static final String VIDEO_CASE_INDEX = "creator_video_case";
    /** BGE-small-zh-q 向量维度，与 creator.rag.embedding-dims 一致 */
    public static final int EMBEDDING_DIMS = 512;
}
