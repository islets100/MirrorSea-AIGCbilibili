package ljl.bilibili.chat.creator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "creator.suggest")
public class CreatorSuggestProperties {
    private int idempotentMinutes = 5;
    private int rateLimitPerMinute = 3;
    private int asrPollIntervalMs = 2000;
    private int asrTimeoutSeconds = 120;
}
