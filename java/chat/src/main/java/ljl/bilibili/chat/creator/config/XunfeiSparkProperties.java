package ljl.bilibili.chat.creator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "creator.xunfei.spark")
public class XunfeiSparkProperties {
    private String hostUrl = "wss://spark-api.xf-yun.com/v3.5/chat";
    private String domain = "generalv3.5";
    private List<SparkCredential> credentials = new ArrayList<>();

    @Data
    public static class SparkCredential {
        private String appId = "xxx";
        private String apiKey = "xxx";
        private String apiSecret = "xxx";
    }
}
