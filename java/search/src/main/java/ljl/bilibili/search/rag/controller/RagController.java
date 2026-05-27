package ljl.bilibili.search.rag.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import ljl.bilibili.client.creator.RagIngestResponse;
import ljl.bilibili.client.creator.RagRetrieveRequest;
import ljl.bilibili.client.creator.RagRetrieveResponse;
import ljl.bilibili.search.rag.service.HotCaseIngestService;
import ljl.bilibili.search.rag.service.KnowledgeIngestService;
import ljl.bilibili.search.rag.service.RagRetrieveService;
import ljl.bilibili.util.Result;
import lombok.extern.slf4j.Slf4j;
import ljl.bilibili.client.creator.CreatorErrorCode;
import ljl.bilibili.client.creator.CreatorException;
import ljl.bilibili.search.rag.config.CreatorRagProperties;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/search/rag")
@Api(tags = "创作辅助RAG检索")
@Slf4j
public class RagController {

    @Resource
    private RagRetrieveService ragRetrieveService;

    @Resource
    private KnowledgeIngestService knowledgeIngestService;

    @Resource
    private HotCaseIngestService hotCaseIngestService;

    @Resource
    private CreatorRagProperties creatorRagProperties;

    @PostMapping("/retrieve")
    @ApiOperation("双源向量召回")
    public Result<RagRetrieveResponse> retrieve(@RequestBody RagRetrieveRequest request) {
        return Result.data(ragRetrieveService.retrieve(request));
    }

    @PostMapping("/ingest/knowledge")
    @ApiOperation("触发知识库 ingest")
    public Result<RagIngestResponse> ingestKnowledge(
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {
        assertIngestAdmin(adminToken);
        int chunks = knowledgeIngestService.ingest();
        return Result.data(new RagIngestResponse().setIngestedChunks(chunks));
    }

    @PostMapping("/ingest/hotcases")
    @ApiOperation("触发爆款案例 ingest")
    public Result<RagIngestResponse> ingestHotCases(
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {
        assertIngestAdmin(adminToken);
        int cases = hotCaseIngestService.ingest();
        return Result.data(new RagIngestResponse().setIngestedCases(cases));
    }

    private void assertIngestAdmin(String adminToken) {
        String configured = creatorRagProperties.getAdminToken();
        if (configured == null || configured.isEmpty()) {
            return;
        }
        if (adminToken == null || !configured.equals(adminToken)) {
            throw new CreatorException(CreatorErrorCode.FORBIDDEN, "ingest 需要管理员令牌");
        }
    }
}
