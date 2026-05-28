package ljl.bilibili.search.rag.support;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.List;

/**
 * 将 TextSegment 批量写入 EmbeddingStore
 */
public final class RagSegmentIngestor {

    private RagSegmentIngestor() {
    }

    public static void ingest(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> store,
                             List<TextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return;
        }
        Response<List<Embedding>> embeddings = embeddingModel.embedAll(segments);
        store.addAll(embeddings.content(), segments);
    }
}
