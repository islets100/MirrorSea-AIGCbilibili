package ljl.bilibili.client.creator;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HotCaseItem {
    private Integer videoId;
    private String title;
    private String intro;
    private Integer playCount;
    private Integer likeCount;
    private Double score;
}
