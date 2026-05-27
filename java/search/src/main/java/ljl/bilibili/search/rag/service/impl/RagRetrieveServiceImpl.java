package ljl.bilibili.search.rag.service.impl;

import com.alibaba.fastjson.JSON;
import ljl.bilibili.client.creator.HotCaseItem;
import ljl.bilibili.client.creator.KnowledgeChunkItem;
import ljl.bilibili.client.creator.RagRetrieveRequest;
import ljl.bilibili.client.creator.RagRetrieveResponse;
import ljl.bilibili.search.rag.config.CreatorRagProperties;
import ljl.bilibili.search.rag.constant.RagConstant;
import ljl.bilibili.search.rag.service.EmbeddingService;
import ljl.bilibili.search.rag.service.RagRetrieveService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScriptScoreQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class RagRetrieveServiceImpl implements RagRetrieveService {

    @Resource
    private RestHighLevelClient restHighLevelClient;

    @Resource
    private EmbeddingService embeddingService;

    @Resource
    private CreatorRagProperties creatorRagProperties;

    @Override
    public RagRetrieveResponse retrieve(RagRetrieveRequest request) {
        RagRetrieveResponse response = new RagRetrieveResponse();
        if (request.getQueryText() == null || request.getQueryText().trim().isEmpty()) {
            return response;
        }
        float[] queryVector = embeddingService.embed(request.getQueryText());
        double minScore = request.getMinScore() == null ? 0.5 : request.getMinScore();
        int topKCase = request.getTopKCase() == null ? 5 : request.getTopKCase();
        int topKKnowledge = request.getTopKKnowledge() == null ? 3 : request.getTopKKnowledge();

        response.setHotCases(searchHotCases(queryVector, topKCase, minScore));
        response.setKnowledgeChunks(searchKnowledge(queryVector, topKKnowledge, minScore));
        return response;
    }

    private List<HotCaseItem> searchHotCases(float[] queryVector, int topK, double minScore) {
        List<HotCaseItem> items = vectorSearch(RagConstant.VIDEO_CASE_INDEX, queryVector, topK, minScore, hit -> {
            Map<String, Object> source = hit.getSourceAsMap();
            HotCaseItem item = new HotCaseItem();
            Object videoId = source.get("video_id");
            if (videoId instanceof Number) {
                item.setVideoId(((Number) videoId).intValue());
            }
            item.setTitle(stringVal(source.get("title")));
            item.setIntro(stringVal(source.get("intro")));
            Object playCount = source.get("play_count");
            if (playCount instanceof Number) {
                item.setPlayCount(((Number) playCount).intValue());
            }
            Object likeCount = source.get("like_count");
            if (likeCount instanceof Number) {
                item.setLikeCount(((Number) likeCount).intValue());
            }
            item.setScore(normalizeScore(hit.getScore()));
            return item;
        });
        return items;
    }

    private List<KnowledgeChunkItem> searchKnowledge(float[] queryVector, int topK, double minScore) {
        return vectorSearch(RagConstant.KNOWLEDGE_INDEX, queryVector, topK, minScore, hit -> {
            Map<String, Object> source = hit.getSourceAsMap();
            KnowledgeChunkItem item = new KnowledgeChunkItem();
            item.setChunkId(stringVal(source.get("chunk_id")));
            item.setSourceFile(stringVal(source.get("source_file")));
            item.setCategory(stringVal(source.get("category")));
            item.setContent(stringVal(source.get("content")));
            item.setScore(normalizeScore(hit.getScore()));
            return item;
        });
    }

    private <T> List<T> vectorSearch(String index, float[] queryVector, int topK, double minScore,
                                       java.util.function.Function<SearchHit, T> mapper) {
        try {
            Script script = new Script(
                    ScriptType.INLINE,
                    "painless",
                    "cosineSimilarity(params.query_vector, 'content_vector') + 1.0",
                    Collections.singletonMap("query_vector", queryVector)
            );
            ScriptScoreQueryBuilder scriptScoreQuery = QueryBuilders.scriptScoreQuery(
                    QueryBuilders.matchAllQuery(), script);

            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                    .query(scriptScoreQuery)
                    .size(Math.max(topK, creatorRagProperties.getRetrieveCandidateSize()))
                    .fetchSource(true);

            SearchRequest searchRequest = new SearchRequest(index);
            searchRequest.source(sourceBuilder);
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            List<T> results = new ArrayList<>();
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                double score = normalizeScore(hit.getScore());
                if (score < minScore) {
                    continue;
                }
                results.add(mapper.apply(hit));
                if (results.size() >= topK) {
                    break;
                }
            }
            return results;
        } catch (Exception e) {
            log.error("Vector search failed on index {}", index, e);
            return Collections.emptyList();
        }
    }

    private static double normalizeScore(float esScore) {
        // script_score: cosineSimilarity + 1.0, range [0, 2]
        return Math.max(0, Math.min(1, (esScore - 1.0) / 1.0 + 0.5));
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
