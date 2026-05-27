package ljl.bilibili.client.creator;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import ljl.bilibili.util.Result;

/**
 * 解析 Feign 响应体中的业务错误码。
 */
public final class CreatorFeignHelper {
    private CreatorFeignHelper() {
    }

    public static <T> T unwrapData(Result<T> result) {
        if (result == null) {
            throw new CreatorException(CreatorErrorCode.LLM_FAILED, "服务无响应");
        }
        if (result.getCode() == 200) {
            return result.getData();
        }
        mapBizCode(result.getCode(), result.getMsg());
        return null;
    }

    public static void mapBizCode(int code, String msg) {
        if (code == CreatorErrorCode.ASR_UNAVAILABLE.getCode()) {
            throw new CreatorException(CreatorErrorCode.ASR_UNAVAILABLE, msg);
        }
        if (code == CreatorErrorCode.EMBEDDING_FAILED.getCode()) {
            throw new CreatorException(CreatorErrorCode.EMBEDDING_FAILED, msg);
        }
        if (code == CreatorErrorCode.VIDEO_NOT_READY.getCode()) {
            throw new CreatorException(CreatorErrorCode.VIDEO_NOT_READY, msg);
        }
        throw new CreatorException(code, msg != null ? msg : "服务调用失败");
    }

    public static CreatorException fromFeignBody(String body) {
        try {
            JSONObject json = JSON.parseObject(body);
            int code = json.getIntValue("code");
            String msg = json.getString("msg");
            return new CreatorException(code, msg != null ? msg : "服务调用失败");
        } catch (Exception e) {
            return new CreatorException(CreatorErrorCode.LLM_FAILED, "服务调用失败");
        }
    }
}
