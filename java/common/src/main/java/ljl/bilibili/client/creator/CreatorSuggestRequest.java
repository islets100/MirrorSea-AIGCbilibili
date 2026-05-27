package ljl.bilibili.client.creator;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class CreatorSuggestRequest {
    private String resumableIdentifier;
    private String videoUrl;
    private String draftTitle;
    @Size(max = 1000, message = "简介不能超过1000字")
    private String draftIntro;
    private List<String> tags;
    @NotNull(message = "userId 不能为空")
    private Integer userId;
}
