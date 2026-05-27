package ljl.bilibili.video.creator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "creator.xunfei.asr")
public class XunfeiAsrProperties {
    private String appId = "xxx";
    private String apiKey = "xxx";
    private String apiSecret = "xxx";
    /** 讯飞录音文件转写 open host */
    private String lfasrHost = "https://raasr.xfyun.cn/v2/api";
    /** MinIO 对外访问前缀，供讯飞拉取音频；内网可配 nginx 反代地址 */
    private String audioBaseUrl = "http://localhost:9000/video/";
}
