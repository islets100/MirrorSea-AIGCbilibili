package ljl.bilibili.client.creator;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RagIngestResponse {
    private Integer ingestedChunks;
    private Integer ingestedCases;
}
