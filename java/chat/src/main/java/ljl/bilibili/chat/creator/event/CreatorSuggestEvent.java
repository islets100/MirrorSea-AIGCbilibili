package ljl.bilibili.chat.creator.event;

import org.springframework.context.ApplicationEvent;

public class CreatorSuggestEvent extends ApplicationEvent {
    private final String userId;
    private final String taskId;
    private final String payloadJson;

    public CreatorSuggestEvent(Object source, String userId, String taskId, String payloadJson) {
        super(source);
        this.userId = userId;
        this.taskId = taskId;
        this.payloadJson = payloadJson;
    }

    public String getUserId() {
        return userId;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }
}
