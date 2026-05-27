package ljl.bilibili.client.search;

import ljl.bilibili.client.creator.RagIngestResponse;
import ljl.bilibili.client.creator.RagRetrieveRequest;
import ljl.bilibili.client.creator.RagRetrieveResponse;
import ljl.bilibili.client.pojo.RecommendVideo;
import ljl.bilibili.util.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
@Component
@FeignClient(name = "search",url = "http://localhost:8201")
public interface SearchClient {
    @GetMapping("/search/likelyVideoRecommend/{videoId}")
    List<RecommendVideo> getRecommendVideo(@PathVariable String videoId);

    @PostMapping("/search/rag/retrieve")
    Result<RagRetrieveResponse> ragRetrieve(@RequestBody RagRetrieveRequest request);

    @PostMapping("/search/rag/ingest/knowledge")
    Result<RagIngestResponse> ingestKnowledge();

    @PostMapping("/search/rag/ingest/hotcases")
    Result<RagIngestResponse> ingestHotCases();
}
