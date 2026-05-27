package ljl.bilibili.search.rag.handler;

import com.xxl.job.core.handler.annotation.XxlJob;
import ljl.bilibili.search.rag.service.HotCaseIngestService;
import ljl.bilibili.search.rag.service.KnowledgeIngestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class RagIngestJobHandler {

    @Resource
    private KnowledgeIngestService knowledgeIngestService;

    @Resource
    private HotCaseIngestService hotCaseIngestService;

    @XxlJob("ragKnowledgeIngestHandler")
    public void ingestKnowledge() {
        log.info("XXL-Job ragKnowledgeIngestHandler start");
        knowledgeIngestService.ingest();
    }

    @XxlJob("ragHotCaseIngestHandler")
    public void ingestHotCases() {
        log.info("XXL-Job ragHotCaseIngestHandler start");
        hotCaseIngestService.ingest();
    }
}
