package ljl.bilibili.chat.creator.service;

import ljl.bilibili.client.creator.CreatorSuggestResult;
import ljl.bilibili.client.creator.RagRetrieveResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class CreatorLLMService {

    @Resource
    private CreatorSparkStreamHandler creatorSparkStreamHandler;

    public CreatorSuggestResult generate(String userId, String taskId, String prompt, RagRetrieveResponse ragResponse) {
        try {
            return creatorSparkStreamHandler.generate(userId, taskId, prompt, ragResponse);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ljl.bilibili.client.creator.CreatorException(
                    ljl.bilibili.client.creator.CreatorErrorCode.LLM_FAILED);
        }
    }
}
