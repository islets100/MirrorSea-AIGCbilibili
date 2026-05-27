package ljl.bilibili.video.creator.service.impl;

import ljl.bilibili.client.creator.VideoContextResponse;
import ljl.bilibili.video.creator.service.CreatorContextService;
import ljl.bilibili.video.pojo.UploadPart;
import org.springframework.stereotype.Service;

import static ljl.bilibili.video.constant.Constant.uploadPartMap;

@Service
public class CreatorContextServiceImpl implements CreatorContextService {

    @Override
    public VideoContextResponse getContext(String resumableIdentifier, String videoUrl) {
        VideoContextResponse response = new VideoContextResponse();
        UploadPart uploadPart = null;
        if (resumableIdentifier != null && !resumableIdentifier.isEmpty()) {
            uploadPart = uploadPartMap.get(resumableIdentifier);
            response.setResumableIdentifier(resumableIdentifier);
            response.setFileName(resumableIdentifier);
        }
        if (videoUrl != null && !videoUrl.isEmpty()) {
            response.setVideoUrl(videoUrl);
        } else if (uploadPart != null && uploadPart.getMergedVideoName() != null
                && !uploadPart.getMergedVideoName().isEmpty()) {
            response.setVideoUrl(uploadPart.getMergedVideoName());
        }
        if (uploadPart != null) {
            response.setCoverBase64(uploadPart.getCover());
            response.setMerged(Boolean.TRUE.equals(uploadPart.getMerged()));
        } else {
            response.setMerged(videoUrl != null && !videoUrl.isEmpty());
        }
        response.setDurationSeconds(0);
        return response;
    }
}
