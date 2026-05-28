package ljl.bilibili.search.rag.support;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import ljl.bilibili.client.creator.HotCaseItem;
import ljl.bilibili.client.creator.KnowledgeChunkItem;

/**
 * LangChain4j EmbeddingMatch → Feign DTO
 */
public final class RagMatchMapper {

    private RagMatchMapper() {
    }

    public static HotCaseItem toHotCaseItem(EmbeddingMatch<TextSegment> match) {
        HotCaseItem item = new HotCaseItem();
        item.setScore(RagScoreNormalizer.normalize(match.score()));
        TextSegment segment = match.embedded();
        if (segment == null) {
            return item;
        }
        Metadata metadata = segment.metadata();
        if (metadata != null) {
            item.setVideoId(metadata.getInteger("video_id"));
            item.setTitle(metadata.getString("title"));
            item.setIntro(metadata.getString("intro"));
            item.setPlayCount(metadata.getInteger("play_count"));
            item.setLikeCount(metadata.getInteger("like_count"));
        }
        if (item.getTitle() == null || item.getTitle().isEmpty()) {
            item.setTitle(segment.text());
        }
        return item;
    }

    public static KnowledgeChunkItem toKnowledgeChunkItem(EmbeddingMatch<TextSegment> match) {
        KnowledgeChunkItem item = new KnowledgeChunkItem();
        item.setScore(RagScoreNormalizer.normalize(match.score()));
        TextSegment segment = match.embedded();
        if (segment == null) {
            return item;
        }
        Metadata metadata = segment.metadata();
        if (metadata != null) {
            item.setChunkId(metadata.getString("chunk_id"));
            item.setSourceFile(metadata.getString("source_file"));
            item.setCategory(metadata.getString("category"));
        }
        item.setContent(segment.text());
        return item;
    }
}
