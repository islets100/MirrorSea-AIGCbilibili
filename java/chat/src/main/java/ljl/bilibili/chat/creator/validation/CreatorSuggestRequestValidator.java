package ljl.bilibili.chat.creator.validation;

import ljl.bilibili.client.creator.CreatorErrorCode;
import ljl.bilibili.client.creator.CreatorSuggestRequest;
import ljl.bilibili.util.Result;

public final class CreatorSuggestRequestValidator {
    private CreatorSuggestRequestValidator() {
    }

    public static Result<Void> validateSubmit(CreatorSuggestRequest request) {
        if (request == null) {
            return Result.bizError(CreatorErrorCode.VIDEO_NOT_READY.getCode(), "请求体不能为空");
        }
        if (request.getUserId() == null) {
            return Result.bizError(CreatorErrorCode.VIDEO_NOT_READY.getCode(), "userId 不能为空");
        }
        boolean hasId = request.getResumableIdentifier() != null && !request.getResumableIdentifier().trim().isEmpty();
        boolean hasUrl = request.getVideoUrl() != null && !request.getVideoUrl().trim().isEmpty();
        if (!hasId && !hasUrl) {
            return Result.bizError(CreatorErrorCode.VIDEO_NOT_READY);
        }
        if (request.getDraftIntro() != null && request.getDraftIntro().length() > 1000) {
            return Result.bizError(CreatorErrorCode.VIDEO_NOT_READY.getCode(), "简介不能超过1000字");
        }
        return null;
    }
}
