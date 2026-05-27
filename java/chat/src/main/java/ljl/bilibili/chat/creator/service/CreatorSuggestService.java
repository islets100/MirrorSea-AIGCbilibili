package ljl.bilibili.chat.creator.service;

import ljl.bilibili.client.creator.*;
import ljl.bilibili.util.Result;

public interface CreatorSuggestService {
    Result<CreatorSuggestSubmitResponse> submit(CreatorSuggestRequest request);

    Result<CreatorSuggestTaskResponse> getTask(String taskId, Integer userId);

    Result<RagRetrieveResponse> getReferences(String taskId, Integer userId);

    Result<Boolean> adopt(String taskId, CreatorAdoptRequest request);
}
