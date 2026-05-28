package ljl.bilibili.search.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import ljl.bilibili.entity.video.video_production.upload.Video;
import ljl.bilibili.entity.video.video_production.upload.VideoData;
import ljl.bilibili.mapper.video.video_production.upload.VideoDataMapper;
import ljl.bilibili.mapper.video.video_production.upload.VideoMapper;
import ljl.bilibili.search.rag.config.CreatorRagProperties;
import ljl.bilibili.search.rag.service.HotCaseIngestService;
import ljl.bilibili.search.rag.support.RagSegmentIngestor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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
    @Qualifier("videoCaseEmbeddingStore")
    private EmbeddingStore<TextSegment> videoCaseEmbeddingStore;

    @Resource
    private EmbeddingModel creatorEmbeddingModel;

    @Resource
    private CreatorRagProperties creatorRagProperties;

    @Override
    public int ingest() {
        videoCaseEmbeddingStore.removeAll();
        int threshold = creatorRagProperties.getHotCasePlayThreshold();
        List<VideoData> hotData = videoDataMapper.selectList(new LambdaQueryWrapper<VideoData>()
                .ge(VideoData::getPlayCount, threshold)
                .orderByDesc(VideoData::getPlayCount)
                .last("LIMIT 500"));
        int count;
        if (hotData.isEmpty()) {
            log.warn("No hot video cases found with play_count >= {}", threshold);
            count = ingestFallbackFromAllVideos();
        } else {
            Map<Integer, VideoData> dataMap = hotData.stream()
                    .collect(Collectors.toMap(VideoData::getVideoId, v -> v, (a, b) -> a));
            List<Video> videos = videoMapper.selectBatchIds(dataMap.keySet());
            List<TextSegment> segments = new ArrayList<>();
            for (Video video : videos) {
                VideoData data = dataMap.get(video.getId());
                if (data != null) {
                    TextSegment segment = toSegment(video, data);
                    if (segment != null) {
                        segments.add(segment);
                    }
                }
            }
            RagSegmentIngestor.ingest(creatorEmbeddingModel, videoCaseEmbeddingStore, segments);
            count = segments.size();
        }
        log.info("Hot case ingest completed (LangChain4j), cases={}", count);
        return count;
    }

    private int ingestFallbackFromAllVideos() {
        List<Video> videos = videoMapper.selectList(new LambdaQueryWrapper<Video>()
                .isNotNull(Video::getName)
                .last("LIMIT 200"));
        List<TextSegment> segments = new ArrayList<>();
        for (Video video : videos) {
            VideoData data = videoDataMapper.selectOne(new LambdaQueryWrapper<VideoData>()
                    .eq(VideoData::getVideoId, video.getId())
                    .last("LIMIT 1"));
            if (data == null) {
                data = new VideoData().setVideoId(video.getId()).setPlayCount(0).setLikeCount(0);
            }
            TextSegment segment = toSegment(video, data);
            if (segment != null) {
                segments.add(segment);
            }
        }
        RagSegmentIngestor.ingest(creatorEmbeddingModel, videoCaseEmbeddingStore, segments);
        return segments.size();
    }

    private static TextSegment toSegment(Video video, VideoData data) {
        String content = buildContent(video);
        if (content.isEmpty()) {
            return null;
        }
        Metadata metadata = new Metadata()
                .put("video_id", video.getId())
                .put("title", video.getName() == null ? "" : video.getName())
                .put("intro", video.getIntro() == null ? "" : video.getIntro())
                .put("play_count", data.getPlayCount() == null ? 0 : data.getPlayCount())
                .put("like_count", data.getLikeCount() == null ? 0 : data.getLikeCount());
        return TextSegment.from(content, metadata);
    }

    private static String buildContent(Video video) {
        StringBuilder sb = new StringBuilder();
        if (video.getName() != null) {
            sb.append(video.getName()).append(' ');
        }
        if (video.getIntro() != null) {
            sb.append(video.getIntro());
        }
        return sb.toString().trim();
    }
}
