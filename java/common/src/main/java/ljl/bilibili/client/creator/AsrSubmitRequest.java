package ljl.bilibili.client.creator;

import lombok.Data;

@Data
public class AsrSubmitRequest {
    private String resumableIdentifier;
    private String videoUrl;
}
