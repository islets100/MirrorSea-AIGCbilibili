package ljl.bilibili.client.creator;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CreatorSuggestTaskResponse {
    private String taskId;
    private String status;
    private Integer progress;
    private Boolean partial;
    /** 40002 ASR 降级 / 40003 RAG 无召回 */
    private Integer warningCode;
    private CreatorSuggestResult result;
}
