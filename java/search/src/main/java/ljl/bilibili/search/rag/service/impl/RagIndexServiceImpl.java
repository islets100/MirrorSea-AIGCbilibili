package ljl.bilibili.search.rag.service.impl;

import ljl.bilibili.search.rag.config.CreatorRagProperties;
import ljl.bilibili.search.rag.constant.RagConstant;
import ljl.bilibili.search.rag.service.RagIndexService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;

@Service
@Slf4j
public class RagIndexServiceImpl implements RagIndexService {

    @Resource
    private RestHighLevelClient restHighLevelClient;

    @Resource
    private CreatorRagProperties creatorRagProperties;

    @PostConstruct
    public void init() {
        ensureIndexes();
    }

    @Override
    public void ensureIndexes() {
        try {
            createIndexIfAbsent(RagConstant.KNOWLEDGE_INDEX, knowledgeMapping());
            createIndexIfAbsent(RagConstant.VIDEO_CASE_INDEX, videoCaseMapping());
        } catch (IOException e) {
            log.error("Failed to ensure RAG indexes", e);
        }
    }

    private void createIndexIfAbsent(String indexName, String mapping) throws IOException {
        GetIndexRequest getIndexRequest = new GetIndexRequest(indexName);
        boolean exists = restHighLevelClient.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
        if (exists) {
            log.info("RAG index already exists: {}", indexName);
            return;
        }
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        request.mapping(mapping, XContentType.JSON);
        restHighLevelClient.indices().create(request, RequestOptions.DEFAULT);
        log.info("Created RAG index: {}", indexName);
    }

    private String knowledgeMapping() {
        int dims = creatorRagProperties.getEmbeddingDims();
        return "{"
                + "\"properties\":{"
                + "\"chunk_id\":{\"type\":\"keyword\"},"
                + "\"source_file\":{\"type\":\"keyword\"},"
                + "\"category\":{\"type\":\"keyword\"},"
                + "\"content\":{\"type\":\"text\"},"
                + "\"updated_at\":{\"type\":\"date\"},"
                + "\"content_vector\":{\"type\":\"dense_vector\",\"dims\":" + dims + "}"
                + "}}";
    }

    private String videoCaseMapping() {
        int dims = creatorRagProperties.getEmbeddingDims();
        return "{"
                + "\"properties\":{"
                + "\"video_id\":{\"type\":\"integer\"},"
                + "\"title\":{\"type\":\"text\"},"
                + "\"intro\":{\"type\":\"text\"},"
                + "\"play_count\":{\"type\":\"integer\"},"
                + "\"like_count\":{\"type\":\"integer\"},"
                + "\"content\":{\"type\":\"text\"},"
                + "\"content_vector\":{\"type\":\"dense_vector\",\"dims\":" + dims + "}"
                + "}}";
    }
}
