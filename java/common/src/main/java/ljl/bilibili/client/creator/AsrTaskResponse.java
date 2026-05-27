package ljl.bilibili.client.creator;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AsrTaskResponse {
    private String asrTaskId;
    /** PENDING | RUNNING | COMPLETED | FAILED */
    private String status;
    private String text;
    private Double confidence;
    private Integer durationSeconds;
}
