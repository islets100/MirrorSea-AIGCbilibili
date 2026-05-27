package ljl.bilibili.chat.creator.handler;

import feign.FeignException;
import ljl.bilibili.client.creator.CreatorErrorCode;
import ljl.bilibili.client.creator.CreatorException;
import ljl.bilibili.client.creator.CreatorFeignHelper;
import ljl.bilibili.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ljl.bilibili.chat.creator")
@Order(1)
@Slf4j
public class CreatorExceptionHandler {

    @ExceptionHandler(CreatorException.class)
    public Result<Void> handleCreator(CreatorException e) {
        log.warn("CreatorException code={} msg={}", e.getCode(), e.getMessage());
        return Result.bizError(e.getCode(), e.getUserMessage());
    }

    @ExceptionHandler(FeignException.class)
    public Result<Void> handleFeign(FeignException e) {
        log.error("Feign call failed", e);
        String body = e.contentUTF8();
        if (body != null && !body.isEmpty()) {
            CreatorException ex = CreatorFeignHelper.fromFeignBody(body);
            return Result.bizError(ex.getCode(), ex.getUserMessage());
        }
        return Result.bizError(CreatorErrorCode.LLM_FAILED);
    }
}
