package ljl.bilibili.client.creator;

import lombok.Data;

@Data
public class RagRetrieveRequest {
    private String queryText;
    private Integer topKCase = 5;
    private Integer topKKnowledge = 3;
    private Double minScore = 0.5;
}
