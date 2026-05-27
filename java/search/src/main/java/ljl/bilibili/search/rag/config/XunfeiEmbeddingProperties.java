package ljl.bilibili.search.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "creator.xunfei.embedding")
public class XunfeiEmbeddingProperties {
    private String appId = "xxx";
    private String apiKey = "xxx";
    private String apiSecret = "xxx";
    private String hostUrl = "https://cn-huabei-1.xf-yun.com/v1/api/embedding";
}
