package ljl.bilibili.chat.creator.constant;

public final class CreatorTaskStatus {
    private CreatorTaskStatus() {
    }

    public static final String PENDING = "PENDING";
    public static final String EXTRACTING = "EXTRACTING";
    public static final String ASR_RUNNING = "ASR_RUNNING";
    public static final String ASR_DONE = "ASR_DONE";
    public static final String RETRIEVING = "RETRIEVING";
    public static final String GENERATING = "GENERATING";
    public static final String COMPLETED = "COMPLETED";
    public static final String PARTIAL = "PARTIAL";
    public static final String FAILED = "FAILED";
}
