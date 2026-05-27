package ljl.bilibili.video.creator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "creator.asr")
public class CreatorAsrProperties {
    private int timeoutSeconds = 120;
    private int maxAudioSeconds = 300;
    private int pollIntervalMs = 2000;
}
