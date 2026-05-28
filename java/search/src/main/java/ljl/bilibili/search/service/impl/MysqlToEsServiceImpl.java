package ljl.bilibili.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.bulk.UpdateAction;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import ljl.bilibili.entity.user_center.user_info.User;
import ljl.bilibili.entity.user_center.user_relationships.IdCount;
import ljl.bilibili.entity.video.video_production.upload.Video;
import ljl.bilibili.entity.video.video_production.upload.VideoData;
import ljl.bilibili.mapper.user_center.user_info.UserMapper;
import ljl.bilibili.mapper.user_center.user_relationships.FollowMapper;
import ljl.bilibili.mapper.video.video_production.upload.VideoDataMapper;
import ljl.bilibili.mapper.video.video_production.upload.VideoMapper;
import ljl.bilibili.search.constant.Constant;
import ljl.bilibili.search.entity.UserEntity;
import ljl.bilibili.search.service.MysqlToEsService;
import ljl.bilibili.search.vo.response.VideoKeywordSearchResponse;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL 同步数据到 ES（Elasticsearch 8.x）
 */
@Service
public class MysqlToEsServiceImpl implements MysqlToEsService {

    @Resource
    private VideoMapper videoMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private FollowMapper followMapper;

    @Resource
    private VideoDataMapper videoDataMapper;

    @Resource
    private ElasticsearchClient client;

    @Override
    public Boolean userMysqlToEs() throws IOException {
        MPJLambdaWrapper<User> wrapper = new MPJLambdaWrapper<>();
        wrapper.select(User::getCover, User::getNickname, User::getId, User::getIntro);
        List<UserEntity> userList = userMapper.selectJoinList(UserEntity.class, wrapper);
        for (UserEntity user : userList) {
            Map<String, Object> map = objectMapper.convertValue(user, Map.class);
            Integer id = (Integer) map.get(Constant.INDEX_ID);
            client.index(i -> i.index(Constant.USER_INDEX_NAME).id(String.valueOf(id)).document(map));
        }
        return true;
    }

    @Override
    public Boolean videoMysqlToEs() throws IOException {
        MPJLambdaWrapper<Video> wrapper = new MPJLambdaWrapper<>();
        wrapper.leftJoin(User.class, User::getId, Video::getUserId);
        wrapper.leftJoin(VideoData.class, VideoData::getVideoId, Video::getId);
        wrapper.select(Video::getCover, Video::getIntro, Video::getCreateTime, Video::getLength, Video::getUrl);
        wrapper.select(VideoData::getDanmakuCount, VideoData::getPlayCount);
        wrapper.selectAs(Video::getName, VideoKeywordSearchResponse::getVideoName);
        wrapper.selectAs(User::getNickname, VideoKeywordSearchResponse::getAuthorName);
        wrapper.selectAs(Video::getId, VideoKeywordSearchResponse::getVideoId);
        wrapper.selectAs(User::getId, VideoKeywordSearchResponse::getAuthorId);
        List<VideoKeywordSearchResponse> list = videoMapper.selectJoinList(VideoKeywordSearchResponse.class, wrapper);
        for (VideoKeywordSearchResponse response : list) {
            Map<String, Object> map = objectMapper.convertValue(response, Map.class);
            String id = String.valueOf(map.get(Constant.VIDEO_INDEX_ID));
            client.index(i -> i.index(Constant.VIDEO_INDEX_NAME).id(id).document(map));
        }
        updateUserData();
        return true;
    }

    @Override
    public Boolean updateVideoData() throws IOException {
        List<VideoData> videoDataList = videoDataMapper.selectList(null);
        List<BulkOperation> operations = new ArrayList<>();
        for (VideoData videoData : videoDataList) {
            Map<String, Object> map = objectMapper.convertValue(videoData, Map.class);
            map.remove(Constant.INDEX_ID);
            map.remove(Constant.VIDEO_INDEX_REMOVE_COMMENT_COUNT);
            map.remove(Constant.VIDEO_INDEX_REMOVE_LIKE_COUNT);
            int intId = (Integer) map.get(Constant.VIDEO_INDEX_ID);
            String id = String.valueOf(intId);
            Map<String, Object> doc = new HashMap<>(map);
            operations.add(BulkOperation.of(op -> op.update(UpdateOperation.of(u -> u
                    .index(Constant.VIDEO_INDEX_NAME)
                    .id(id)
                    .action(UpdateAction.of(a -> a.doc(doc)))))));
        }
        if (!operations.isEmpty()) {
            client.bulk(BulkRequest.of(b -> b.operations(operations)));
        }
        return true;
    }

    @Override
    public Boolean updateUserData() throws IOException {
        List<User> userList = userMapper.selectList(null);
        List<Integer> ids = new ArrayList<>();
        for (User user : userList) {
            ids.add(user.getId());
        }
        Map<Integer, Integer> fansCountMap = new HashMap<>();
        Map<Integer, Integer> videoCountMap = new HashMap<>();
        for (IdCount idCount : followMapper.getIdolCount(ids)) {
            fansCountMap.put(idCount.getId(), idCount.getCount());
        }
        for (IdCount idCount : followMapper.getVideoCount(ids)) {
            videoCountMap.put(idCount.getId(), idCount.getCount());
        }
        List<BulkOperation> operations = new ArrayList<>();
        for (Integer id : ids) {
            Map<String, Object> map = new HashMap<>();
            map.put(Constant.INDEX_ID, id.toString());
            map.put(Constant.USER_INDEX_PUT_FANS_COUNT, fansCountMap.getOrDefault(id, 0));
            map.put(Constant.USER_INDEX_PUT_VIDEO_COUNT, videoCountMap.getOrDefault(id, 0));
            operations.add(BulkOperation.of(op -> op.update(UpdateOperation.of(u -> u
                    .index(Constant.USER_INDEX_NAME)
                    .id(id.toString())
                    .action(UpdateAction.of(a -> a.doc(map)))))));
        }
        if (!operations.isEmpty()) {
            client.bulk(BulkRequest.of(b -> b.operations(operations)));
        }
        return true;
    }
}
