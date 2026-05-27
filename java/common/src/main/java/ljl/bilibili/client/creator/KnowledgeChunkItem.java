package ljl.bilibili.client.creator;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class KnowledgeChunkItem {
    private String chunkId;
    private String sourceFile;
    private String category;
    private String content;
    private Double score;
}
