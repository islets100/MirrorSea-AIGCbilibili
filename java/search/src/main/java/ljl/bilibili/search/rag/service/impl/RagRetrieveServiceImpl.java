package ljl.bilibili.search.rag.service.impl;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import ljl.bilibili.client.creator.HotCaseItem;
import ljl.bilibili.client.creator.KnowledgeChunkItem;
import ljl.bilibili.client.creator.RagRetrieveRequest;
import ljl.bilibili.client.creator.RagRetrieveResponse;
import ljl.bilibili.search.rag.config.CreatorRagProperties;
import ljl.bilibili.search.rag.service.RagRetrieveService;
import ljl.bilibili.search.rag.support.RagMatchMapper;
import ljl.bilibili.search.rag.support.RagScoreNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
@Slf4j
public class RagRetrieveServiceImpl implements RagRetrieveService {

    @Resource
    private EmbeddingModel creatorEmbeddingModel;

    @Resource
    @Qualifier("knowledgeEmbeddingStore")
    private EmbeddingStore<TextSegment> knowledgeEmbeddingStore;

    @Resource
    @Qualifier("videoCaseEmbeddingStore")
    private EmbeddingStore<TextSegment> videoCaseEmbeddingStore;

    @Resource
    private CreatorRagProperties creatorRagProperties;

    @Override
    public RagRetrieveResponse retrieve(RagRetrieveRequest request) {
        RagRetrieveResponse response = new RagRetrieveResponse();
        if (request.getQueryText() == null || request.getQueryText().trim().isEmpty()) {
            return response;
        }
        Embedding queryEmbedding = creatorEmbeddingModel.embed(request.getQueryText()).content();
        double minScore = request.getMinScore() == null ? 0.5 : request.getMinScore();
        int topKCase = request.getTopKCase() == null ? 5 : request.getTopKCase();
        int topKKnowledge = request.getTopKKnowledge() == null ? 3 : request.getTopKKnowledge();
        int candidateSize = Math.max(
                creatorRagProperties.getRetrieveCandidateSize(),
                Math.max(topKCase, topKKnowledge));

        response.setHotCases(search(videoCaseEmbeddingStore, queryEmbedding, topKCase, minScore,
                candidateSize, RagMatchMapper::toHotCaseItem));
        response.setKnowledgeChunks(search(knowledgeEmbeddingStore, queryEmbedding, topKKnowledge, minScore,
                candidateSize, RagMatchMapper::toKnowledgeChunkItem));
        return response;
    }

    private <T> List<T> search(
            EmbeddingStore<TextSegment> store,
            Embedding queryEmbedding,
            int topK,
            double minScore,
            int maxResults,
            Function<EmbeddingMatch<TextSegment>, T> mapper) {
        List<T> items = new ArrayList<>();
        EmbeddingSearchResult<TextSegment> result = store.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(maxResults)
                        .build());
        if (result == null || result.matches() == null) {
            return items;
        }
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            if (RagScoreNormalizer.normalize(match.score()) < minScore) {
                continue;
            }
            items.add(mapper.apply(match));
            if (items.size() >= topK) {
                break;
            }
        }
        return items;
    }
}
