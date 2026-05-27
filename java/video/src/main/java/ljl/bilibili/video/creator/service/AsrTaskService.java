package ljl.bilibili.video.creator.service;

import ljl.bilibili.client.creator.AsrSubmitRequest;
import ljl.bilibili.client.creator.AsrTaskResponse;

public interface AsrTaskService {
    AsrTaskResponse submit(AsrSubmitRequest request);

    AsrTaskResponse getTask(String asrTaskId);
}
