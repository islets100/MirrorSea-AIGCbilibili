package ljl.bilibili.search.handler;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.DeleteOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.bulk.UpdateAction;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.xxl.job.core.handler.annotation.XxlJob;
import ljl.bilibili.search.constant.Constant;
import ljl.bilibili.search.service.MysqlToEsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL 到 ES 数据同步的定时任务执行器（Elasticsearch 8.x）
 */
@Component
@Slf4j
public class MysqlToEsHandler {

    @Resource
    private RedisTemplate objectRedisTemplate;

    @Resource
    private ElasticsearchClient client;

    @Resource
    private MysqlToEsService mysqlToEsService;

    private static Boolean hasSynchronousVideo = false;
    private static Boolean hasSynchronousUser = false;

    private final BloomFilter<Integer> videoFilter = BloomFilter.create(
            Funnels.integerFunnel(), 1000, 0.01);
    private final BloomFilter<Integer> userFilter = BloomFilter.create(
            Funnels.integerFunnel(), 1000, 0.01);

    @XxlJob("mysqlToEs")
    public void mysqlToEsHandler() throws Exception {
        if (!hasSynchronousVideo) {
            mysqlToEsService.videoMysqlToEs();
            mysqlToEsService.updateVideoData();
            hasSynchronousVideo = true;
        }
        if (!hasSynchronousUser) {
            mysqlToEsService.userMysqlToEs();
            mysqlToEsService.updateUserData();
            hasSynchronousUser = true;
        }

        List<HashMap<String, Object>> videoAddList = objectRedisTemplate.opsForList()
                .range(Constant.VIDEO_ADD_KEY, 0, -1);
        List<HashMap<String, Object>> videoDeleteList = objectRedisTemplate.opsForList()
                .range(Constant.VIDEO_DELETE_KEY, 0, -1);
        List<HashMap<String, Object>> videoUpDateList = objectRedisTemplate.opsForList()
                .range(Constant.VIDEO_UPDATE_KEY, 0, -1);
        List<HashMap<String, Object>> userAddList = objectRedisTemplate.opsForList()
                .range(Constant.USER_ADD_KEY, 0, -1);
        List<HashMap<String, Object>> userUpDateList = objectRedisTemplate.opsForList()
                .range(Constant.USER_UPDATE_KEY, 0, -1);

        if (videoAddList != null && !videoAddList.isEmpty()) {
            mysqlAddToEs(Constant.OPERATION_ADD, videoAddList, Constant.VIDEO_INDEX_NAME);
        }
        if (videoDeleteList != null && !videoDeleteList.isEmpty()) {
            mysqlAddToEs(Constant.OPERATION_DELETE, videoDeleteList, Constant.VIDEO_INDEX_NAME);
        }
        if (userAddList != null && !userAddList.isEmpty()) {
            mysqlAddToEs(Constant.OPERATION_ADD, userAddList, Constant.USER_INDEX_NAME);
        }

        refreshBloomFilter(Constant.VIDEO_INDEX_NAME, videoFilter);
        refreshBloomFilter(Constant.USER_INDEX_NAME, userFilter);

        if (videoUpDateList != null && !videoUpDateList.isEmpty()) {
            mysqlAddToEs(Constant.OPERATION_UPDATE, videoUpDateList, Constant.VIDEO_INDEX_NAME);
        }
        if (userUpDateList != null && !userUpDateList.isEmpty()) {
            mysqlAddToEs(Constant.OPERATION_UPDATE, userUpDateList, Constant.USER_INDEX_NAME);
        }

        objectRedisTemplate.delete(Constant.VIDEO_ADD_KEY);
        objectRedisTemplate.delete(Constant.VIDEO_DELETE_KEY);
        objectRedisTemplate.delete(Constant.VIDEO_UPDATE_KEY);
        objectRedisTemplate.delete(Constant.USER_UPDATE_KEY);
        objectRedisTemplate.delete(Constant.USER_ADD_KEY);
    }

    private void refreshBloomFilter(String indexName, BloomFilter<Integer> filter) throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> response = client.search(
                s -> s.index(indexName).size(10_000).query(q -> q.matchAll(m -> m)), Map.class);
        if (response.hits() == null || response.hits().hits() == null) {
            return;
        }
        for (Hit<Map> hit : response.hits().hits()) {
            if (hit.id() != null) {
                filter.put(Integer.valueOf(hit.id()));
            }
        }
    }

    public Boolean mysqlAddToEs(String requestType, List<HashMap<String, Object>> list, String indexName)
            throws IOException {
        List<BulkOperation> operations = new ArrayList<>();
        if (Constant.OPERATION_ADD.equals(requestType)) {
            for (Map<String, Object> document : list) {
                int intId = Constant.VIDEO_INDEX_NAME.equals(indexName)
                        ? (Integer) document.get(Constant.VIDEO_INDEX_ID)
                        : (Integer) document.get(Constant.INDEX_ID);
                String id = String.valueOf(intId);
                operations.add(BulkOperation.of(op -> op.index(IndexOperation.of(i -> i
                        .index(indexName)
                        .id(id)
                        .document(document)))));
            }
        } else if (Constant.OPERATION_UPDATE.equals(requestType)) {
            BloomFilter<Integer> filter = Constant.VIDEO_INDEX_NAME.equals(indexName) ? videoFilter : userFilter;
            String idField = Constant.VIDEO_INDEX_NAME.equals(indexName)
                    ? Constant.VIDEO_INDEX_ID
                    : Constant.INDEX_ID;
            for (Map<String, Object> map : list) {
                int intId = (Integer) map.get(idField);
                if (filter.mightContain(intId)) {
                    operations.add(BulkOperation.of(op -> op.update(UpdateOperation.of(u -> u
                            .index(indexName)
                            .id(String.valueOf(intId))
                            .action(UpdateAction.of(a -> a.doc(map)))))));
                }
            }
        } else {
            for (Map<String, Object> document : list) {
                int intId = (Integer) document.get(Constant.VIDEO_INDEX_ID);
                String id = String.valueOf(intId);
                operations.add(BulkOperation.of(op -> op.delete(DeleteOperation.of(d -> d
                        .index(indexName)
                        .id(id)))));
            }
        }
        if (operations.isEmpty()) {
            return true;
        }
        bulkOperateUntilAllSuccess(operations, 10);
        return true;
    }

    private void bulkOperateUntilAllSuccess(List<BulkOperation> operations, int maxRetry) throws IOException {
        BulkResponse bulkResponse = client.bulk(BulkRequest.of(b -> b.operations(operations)));
        if (bulkResponse.errors() && maxRetry > 0) {
            List<BulkOperation> failed = new ArrayList<>();
            int i = 0;
            for (co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem item : bulkResponse.items()) {
                if (item.error() != null && i < operations.size()) {
                    failed.add(operations.get(i));
                }
                i++;
            }
            if (!failed.isEmpty()) {
                bulkOperateUntilAllSuccess(failed, maxRetry - 1);
            }
        }
    }
}
