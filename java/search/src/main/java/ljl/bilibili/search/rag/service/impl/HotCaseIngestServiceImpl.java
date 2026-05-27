package ljl.bilibili.search.rag.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ljl.bilibili.entity.video.video_production.upload.Video;
import ljl.bilibili.entity.video.video_production.upload.VideoData;
import ljl.bilibili.mapper.video.video_production.upload.VideoDataMapper;
import ljl.bilibili.mapper.video.video_production.upload.VideoMapper;
import ljl.bilibili.search.rag.config.CreatorRagProperties;
import ljl.bilibili.search.rag.constant.RagConstant;
import ljl.bilibili.search.rag.service.EmbeddingService;
import ljl.bilibili.search.rag.service.HotCaseIngestService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HotCaseIngestServiceImpl implements HotCaseIngestService {

    @Resource
    private VideoMapper videoMapper;

    @Resource
    private VideoDataMapper videoDataMapper;

    @Resource
    private RestHighLevelClient restHighLevelClient;

    @Resource
    private EmbeddingService embeddingService;

    @Resource
    private CreatorRagProperties creatorRagProperties;

    @Override
    public int ingest() {
        int threshold = creatorRagProperties.getHotCasePlayThreshold();
        List<VideoData> hotData = videoDataMapper.selectList(new LambdaQueryWrapper<VideoData>()
                .ge(VideoData::getPlayCount, threshold)
                .orderByDesc(VideoData::getPlayCount)
                .last("LIMIT 500"));
        if (hotData.isEmpty()) {
            log.warn("No hot video cases found with play_count >= {}", threshold);
            return ingestFallbackFromAllVideos();
        }
        Map<Integer, VideoData> dataMap = hotData.stream()
                .collect(Collectors.toMap(VideoData::getVideoId, v -> v, (a, b) -> a));
        List<Video> videos = videoMapper.selectBatchIds(dataMap.keySet());
        int count = 0;
        for (Video video : videos) {
            VideoData data = dataMap.get(video.getId());
            if (data == null) {
                continue;
            }
            count += indexVideoCase(video, data);
        }
        log.info("Hot case ingest completed, cases={}", count);
        return count;
    }

    private int ingestFallbackFromAllVideos() {
        List<Video> videos = videoMapper.selectList(new LambdaQueryWrapper<Video>()
                .isNotNull(Video::getName)
                .last("LIMIT 200"));
        int count = 0;
        for (Video video : videos) {
            VideoData data = videoDataMapper.selectOne(new LambdaQueryWrapper<VideoData>()
                    .eq(VideoData::getVideoId, video.getId())
                    .last("LIMIT 1"));
            if (data == null) {
                data = new VideoData().setVideoId(video.getId()).setPlayCount(0).setLikeCount(0);
            }
            count += indexVideoCase(video, data);
        }
        return count;
    }

    private int indexVideoCase(Video video, VideoData data) {
        try {
            String content = buildContent(video);
            if (content.trim().isEmpty()) {
                return 0;
            }
            Map<String, Object> doc = new HashMap<>();
            doc.put("video_id", video.getId());
            doc.put("title", video.getName());
            doc.put("intro", video.getIntro() == null ? "" : video.getIntro());
            doc.put("play_count", data.getPlayCount() == null ? 0 : data.getPlayCount());
            doc.put("like_count", data.getLikeCount() == null ? 0 : data.getLikeCount());
            doc.put("content", content);
            doc.put("content_vector", embeddingService.embed(content));

            IndexRequest indexRequest = new IndexRequest(RagConstant.VIDEO_CASE_INDEX)
                    .id(String.valueOf(video.getId()))
                    .source(JSON.toJSONString(doc), XContentType.JSON);
            restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
            return 1;
        } catch (Exception e) {
            log.error("Failed to index hot case videoId={}", video.getId(), e);
            return 0;
        }
    }

    private static String buildContent(Video video) {
        StringBuilder sb = new StringBuilder();
        if (video.getName() != null) {
            sb.append(video.getName()).append(" ");
        }
        if (video.getIntro() != null) {
            sb.append(video.getIntro());
        }
        return sb.toString().trim();
    }
}
