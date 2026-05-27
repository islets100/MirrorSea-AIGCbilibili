package ljl.bilibili.client.creator;

import lombok.Getter;

@Getter
public enum CreatorErrorCode {
    VIDEO_NOT_READY(40001, "请先完成视频上传"),
    ASR_DEGRADED(40002, "未能识别语音，已基于草稿生成"),
    RAG_EMPTY(40003, "未找到相似案例，已直接生成"),
    LLM_QPS_LIMIT(42901, "排队中，请稍候"),
    LLM_FAILED(50001, "生成失败，请重试"),
    ASR_UNAVAILABLE(50002, "语音识别暂不可用"),
    EMBEDDING_FAILED(50003, "检索服务异常"),
    TASK_NOT_FOUND(40401, "任务不存在"),
    FORBIDDEN(40301, "无权访问该任务");

    private final int code;
    private final String userMessage;

    CreatorErrorCode(int code, String userMessage) {
        this.code = code;
        this.userMessage = userMessage;
    }
}
