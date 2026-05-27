package ljl.bilibili.video.creator.service;

import ljl.bilibili.client.creator.AsrSubmitRequest;
import ljl.bilibili.client.creator.AsrTaskResponse;
import ljl.bilibili.client.creator.VideoContextResponse;

public interface CreatorContextService {
    VideoContextResponse getContext(String resumableIdentifier, String videoUrl);
}
