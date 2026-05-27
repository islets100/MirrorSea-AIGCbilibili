package ljl.bilibili.client.creator;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class RagRetrieveResponse {
    private List<HotCaseItem> hotCases = new ArrayList<>();
    private List<KnowledgeChunkItem> knowledgeChunks = new ArrayList<>();
}
