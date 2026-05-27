package ljl.bilibili.client.creator;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CreatorReferenceItem {
    /** HOT_CASE | KNOWLEDGE */
    private String sourceType;
    private String title;
    private String intro;
    private String content;
    private Integer playCount;
    private Double score;
}
