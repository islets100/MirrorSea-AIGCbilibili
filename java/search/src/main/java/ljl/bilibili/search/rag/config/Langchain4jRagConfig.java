package ljl.bilibili.search.rag.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhq.BgeSmallZhQuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import ljl.bilibili.search.config.properties.ElasticsearchClientProperties;
import ljl.bilibili.search.rag.constant.RagConstant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j RAG：BGE 中文 ONNX Embedding + Elasticsearch 8 向量库
 */
@Configuration
public class Langchain4jRagConfig {

    @Bean
    public EmbeddingModel creatorEmbeddingModel() {
        return new BgeSmallZhQuantizedEmbeddingModel();
    }

    @Bean(name = "knowledgeEmbeddingStore")
    public EmbeddingStore<TextSegment> knowledgeEmbeddingStore(
            ElasticsearchClientProperties esProperties,
            CreatorRagProperties ragProperties) {
        return buildStore(esProperties, ragProperties, RagConstant.KNOWLEDGE_INDEX);
    }

    @Bean(name = "videoCaseEmbeddingStore")
    public EmbeddingStore<TextSegment> videoCaseEmbeddingStore(
            ElasticsearchClientProperties esProperties,
            CreatorRagProperties ragProperties) {
        return buildStore(esProperties, ragProperties, RagConstant.VIDEO_CASE_INDEX);
    }

    private static ElasticsearchEmbeddingStore buildStore(
            ElasticsearchClientProperties esProperties,
            CreatorRagProperties ragProperties,
            String indexName) {
        return ElasticsearchEmbeddingStore.builder()
                .serverUrl(resolveServerUrl(esProperties))
                .indexName(indexName)
                .dimension(ragProperties.getEmbeddingDims())
                .build();
    }

    static String resolveServerUrl(ElasticsearchClientProperties properties) {
        if (properties.getHosts() == null || properties.getHosts().isEmpty()) {
            return "http://localhost:9200";
        }
        return properties.getHosts().get(0);
    }
}
