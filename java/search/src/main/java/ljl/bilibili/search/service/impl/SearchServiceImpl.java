package ljl.bilibili.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.MoreLikeThisQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ljl.bilibili.client.pojo.RecommendVideo;
import ljl.bilibili.entity.user_center.user_relationships.Follow;
import ljl.bilibili.mapper.user_center.user_relationships.FollowMapper;
import ljl.bilibili.search.service.SearchService;
import ljl.bilibili.search.vo.request.EsIndexRequest;
import ljl.bilibili.search.vo.request.EsKeywordRequest;
import ljl.bilibili.search.vo.response.TotalCountSearchResponse;
import ljl.bilibili.search.vo.response.UserKeyWordSearchResponse;
import ljl.bilibili.search.vo.response.VideoKeywordSearchResponse;
import ljl.bilibili.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ljl.bilibili.search.constant.Constant.*;

/**
 * 搜索相关（Elasticsearch 8.x Java API Client）
 */
@Service
@Slf4j
public class SearchServiceImpl implements SearchService {

    @Resource
    private ElasticsearchClient client;

    private final int size = 20;

    @Resource
    private FollowMapper followMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public Result<TotalCountSearchResponse> totalKeywordSearch(String keyword) {
        try {
            long totalVideoCount = countByMultiMatch(VIDEO_INDEX_NAME, keyword,
                    MULTI_QUERY_VIDEO_NAME, MULTI_QUERY_AUTHOR_NAME, MULTI_QUERY_INTRO);
            long totalUserCount = countByMultiMatch(USER_INDEX_NAME, keyword,
                    MULTI_QUERY_NICKNAME, MULTI_QUERY_INTRO);
            long totalVideoPages = totalVideoCount / size + (totalVideoCount % size == 0 ? 0 : 1);
            long totalUserPages = totalUserCount / size + (totalUserCount % size == 0 ? 0 : 1);
            return Result.data(new TotalCountSearchResponse()
                    .setTotalVideoPage(totalVideoPages)
                    .setTotalVideoNum(totalVideoCount)
                    .setTotalUserNum(totalUserCount)
                    .setTotalUserPage(totalUserPages));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Result<Boolean> addKeywordSearchRecord(EsKeywordRequest esKeywordRequest) throws IOException {
        Map<String, Object> doc = objectMapper.convertValue(esKeywordRequest, Map.class);
        client.index(IndexRequest.of(i -> i.index(HISTORY_SEARCH_INDEX_NAME).document(doc)));
        return Result.success(true);
    }

    @Override
    public Result<List<VideoKeywordSearchResponse>> videoPageKeywordSearch(String keyword, int pageNumber, Integer type)
            throws JsonProcessingException {
        try {
            String sortField = resolveVideoSortField(type);
            SearchResponse<VideoKeywordSearchResponse> response = client.search(s -> s
                            .index(VIDEO_INDEX_NAME)
                            .from((pageNumber - 1) * size)
                            .size(size)
                            .minScore(1.0)
                            .query(multiMatchQuery(keyword, MULTI_QUERY_VIDEO_NAME, MULTI_QUERY_AUTHOR_NAME, MULTI_QUERY_INTRO))
                            .sort(so -> so.field(f -> f.field(sortField).order(SortOrder.Desc))),
                    VideoKeywordSearchResponse.class);
            return Result.data(hitsToList(response));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Result<List<UserKeyWordSearchResponse>> userPageKeywordSearch(String keyword, int pageNumber, Integer type,
            Integer userId) throws JsonProcessingException {
        try {
            SortOrder fansOrder = (type != null && type == 2) ? SortOrder.Asc : SortOrder.Desc;
            String sortField = (type != null && type == 0) ? ORDER_BY_SCORE : ORDER_BY_FANS_COUNT;
            SearchResponse<UserKeyWordSearchResponse> response = client.search(s -> s
                            .index(USER_INDEX_NAME)
                            .from((pageNumber - 1) * size)
                            .size(size)
                            .query(multiMatchQuery(keyword, MULTI_QUERY_NICKNAME, MULTI_QUERY_INTRO))
                            .sort(so -> so.field(f -> f.field(sortField).order(
                                    ORDER_BY_SCORE.equals(sortField) ? SortOrder.Desc : fansOrder))),
                    UserKeyWordSearchResponse.class);
            List<UserKeyWordSearchResponse> list = hitsToList(response);
            Set<Integer> followSet = new HashSet<>();
            LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Follow::getFansId, userId);
            for (Follow follow : followMapper.selectList(wrapper)) {
                followSet.add(follow.getIdolId());
            }
            for (UserKeyWordSearchResponse item : list) {
                item.setIsFollow(followSet.contains(item.getId()));
            }
            return Result.data(list);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Result<List<String>> likelyKeywordSearch(String searchWord) throws IOException {
        SearchResponse<EsKeywordRequest> response = client.search(s -> s
                        .index(HISTORY_SEARCH_INDEX_NAME)
                        .query(multiMatchQuery(searchWord, MULTI_QUERY_SEARCH_WORD)),
                EsKeywordRequest.class);
        List<String> list = new ArrayList<>();
        for (EsKeywordRequest item : hitsToList(response)) {
            list.add(item.getSearchWord());
        }
        return Result.data(list);
    }

    @Override
    public List<RecommendVideo> likelyVideoRecommend(String videoId) throws IOException {
        MoreLikeThisQuery mlt = MoreLikeThisQuery.of(m -> m
                .fields(MULTI_QUERY_VIDEO_NAME, MULTI_QUERY_AUTHOR_NAME, MULTI_QUERY_INTRO)
                .like(l -> l.document(d -> d.index(VIDEO_INDEX_NAME).id(videoId)))
                .minTermFreq(1)
                .maxQueryTerms(12));
        SearchResponse<RecommendVideo> response = client.search(s -> s
                        .index(VIDEO_INDEX_NAME)
                        .query(Query.of(q -> q.moreLikeThis(mlt)))
                        .sort(so -> so.score(sc -> sc.order(SortOrder.Desc))),
                RecommendVideo.class);
        List<RecommendVideo> list = new ArrayList<>();
        for (RecommendVideo item : hitsToList(response)) {
            if (item.getVideoId() != null && !item.getVideoId().equals(videoId)) {
                list.add(item);
            }
        }
        return list;
    }

    @Override
    public Boolean createIndex(EsIndexRequest esIndexRequest) {
        try {
            String index = esIndexRequest.getIndexName();
            Map<String, Property> properties = new HashMap<>();
            for (Map.Entry<String, String> entry : esIndexRequest.getProperties().entrySet()) {
                String fieldType = entry.getValue();
                properties.put(entry.getKey(), mapProperty(fieldType));
            }
            TypeMapping mapping = TypeMapping.of(m -> m.properties(properties));
            client.indices().create(CreateIndexRequest.of(c -> c.index(index).mappings(mapping)));
        } catch (Exception e) {
            log.error("createIndex failed", e);
        }
        return true;
    }

    @Override
    public Boolean deleteIndex(String indexName) throws IOException {
        boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
        if (exists) {
            client.indices().delete(DeleteIndexRequest.of(d -> d.index(indexName)));
            log.info("索引已删除: {}", indexName);
        } else {
            log.info("索引不存在: {}", indexName);
        }
        return true;
    }

    private long countByMultiMatch(String index, String keyword, String... fields) throws IOException {
        SearchResponse<Void> response = client.search(s -> s
                        .index(index)
                        .size(0)
                        .trackTotalHits(t -> t.enabled(true))
                        .minScore(1.0)
                        .query(multiMatchQuery(keyword, fields)),
                Void.class);
        TotalHits total = response.hits().total();
        if (total == null) {
            return 0;
        }
        if (total.relation() == TotalHitsRelation.Eq) {
            return total.value();
        }
        return total.value();
    }

    private static Query multiMatchQuery(String keyword, String... fields) {
        return Query.of(q -> q.multiMatch(m -> m
                .query(keyword)
                .fields(Arrays.asList(fields))
                .type(TextQueryType.MostFields)));
    }

    private static String resolveVideoSortField(Integer type) {
        if (type == null || type == 0) {
            return ORDER_BY_SCORE;
        }
        if (type == 1) {
            return ORDER_BY_PLAY_COUNT;
        }
        if (type == 2) {
            return ORDER_BY_CREATE_TIME;
        }
        return ORDER_BY_COLLECT_COUNT;
    }

    private static Property mapProperty(String type) {
        if ("keyword".equalsIgnoreCase(type)) {
            return Property.of(p -> p.keyword(k -> k));
        }
        if ("integer".equalsIgnoreCase(type) || "long".equalsIgnoreCase(type)) {
            return Property.of(p -> p.integer(i -> i));
        }
        if ("date".equalsIgnoreCase(type)) {
            return Property.of(p -> p.date(d -> d));
        }
        return Property.of(p -> p.text(t -> t));
    }

    private static <T> List<T> hitsToList(SearchResponse<T> response) {
        List<T> list = new ArrayList<>();
        if (response.hits() == null || response.hits().hits() == null) {
            return list;
        }
        for (Hit<T> hit : response.hits().hits()) {
            if (hit.source() != null) {
                list.add(hit.source());
            }
        }
        return list;
    }
}
