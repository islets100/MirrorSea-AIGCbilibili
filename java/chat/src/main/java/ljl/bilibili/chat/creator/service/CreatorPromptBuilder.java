package ljl.bilibili.chat.creator.service;

import ljl.bilibili.client.creator.CreatorSuggestRequest;
import ljl.bilibili.client.creator.HotCaseItem;
import ljl.bilibili.client.creator.KnowledgeChunkItem;
import ljl.bilibili.client.creator.RagRetrieveResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class CreatorPromptBuilder {

    public String build(String queryText, RagRetrieveResponse ragResponse, CreatorSuggestRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 MirrorSea 视频平台的爆款内容运营专家。\n");
        sb.append("请根据以下视频内容、平台爆款案例和运营知识，生成 3 个吸引人的视频标题（每个不超过80字）");
        sb.append("和 2 个视频简介（每个不超过200字）。\n");
        sb.append("输出必须是合法 JSON，格式：{\"titles\":[\"\",\"\",\"\"],\"intros\":[\"\",\"\"]}\n\n");

        sb.append("【视频内容】\n").append(queryText).append("\n\n");

        if (request.getDraftTitle() != null && !request.getDraftTitle().isEmpty()) {
            sb.append("【用户草稿标题】").append(request.getDraftTitle()).append("\n");
        }
        if (request.getDraftIntro() != null && !request.getDraftIntro().isEmpty()) {
            sb.append("【用户草稿简介】").append(request.getDraftIntro()).append("\n");
        }
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            sb.append("【标签】").append(String.join(", ", request.getTags())).append("\n");
        }

        List<HotCaseItem> hotCases = ragResponse.getHotCases();
        if (hotCases != null && !hotCases.isEmpty()) {
            sb.append("\n【平台相似爆款案例】\n");
            for (int i = 0; i < hotCases.size(); i++) {
                HotCaseItem item = hotCases.get(i);
                sb.append(i + 1).append(". 标题：").append(item.getTitle());
                if (item.getPlayCount() != null) {
                    sb.append("（播放量：").append(item.getPlayCount()).append("）");
                }
                sb.append("\n   简介：").append(item.getIntro() == null ? "" : item.getIntro()).append("\n");
            }
        }

        List<KnowledgeChunkItem> chunks = ragResponse.getKnowledgeChunks();
        if (chunks != null && !chunks.isEmpty()) {
            sb.append("\n【运营知识库参考】\n");
            sb.append(chunks.stream()
                    .map(KnowledgeChunkItem::getContent)
                    .collect(Collectors.joining("\n---\n")));
            sb.append("\n");
        }

        sb.append("\n请模仿爆款案例的网感与结构，结合视频内容生成建议。");
        return sb.toString();
    }
}
