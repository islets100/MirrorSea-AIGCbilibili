package ljl.bilibili.search.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "creator.rag")
public class CreatorRagProperties {
    private String knowledgeDir = "../知识库";
    private int embeddingDims = 256;
    private int hotCasePlayThreshold = 10000;
    private int chunkSize = 500;
    private int chunkOverlap = 50;
    private int retrieveCandidateSize = 50;
    /** local | xunfei */
    private String embeddingProvider = "local";
    private String adminToken = "";
}
