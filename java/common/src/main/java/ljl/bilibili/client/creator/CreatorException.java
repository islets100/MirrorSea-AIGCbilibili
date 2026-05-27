package ljl.bilibili.client.creator;

import lombok.Getter;

@Getter
public class CreatorException extends RuntimeException {
    private final int code;
    private final String userMessage;

    public CreatorException(CreatorErrorCode errorCode) {
        super(errorCode.getUserMessage());
        this.code = errorCode.getCode();
        this.userMessage = errorCode.getUserMessage();
    }

    public CreatorException(CreatorErrorCode errorCode, String detail) {
        super(detail);
        this.code = errorCode.getCode();
        this.userMessage = errorCode.getUserMessage();
    }

    public CreatorException(int code, String userMessage) {
        super(userMessage);
        this.code = code;
        this.userMessage = userMessage;
    }
}
