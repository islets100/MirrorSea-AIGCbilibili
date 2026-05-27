package ljl.bilibili.client.creator;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class CreatorAdoptRequest {
    @NotNull(message = "userId 不能为空")
    private Integer userId;
    @Size(max = 256)
    private String adoptedTitle;
    @Size(max = 1000)
    private String adoptedIntro;
}
