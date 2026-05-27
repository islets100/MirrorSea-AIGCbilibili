package ljl.bilibili.client.creator;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class VideoContextResponse {
    private String resumableIdentifier;
    private String videoUrl;
    private String coverBase64;
    private String fileName;
    private Boolean merged;
    private Integer durationSeconds;
}
