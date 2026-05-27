package ljl.bilibili.client.creator;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CreatorSuggestSubmitResponse {
    private String taskId;
    private String status;
    private Integer estimatedSeconds;
}
