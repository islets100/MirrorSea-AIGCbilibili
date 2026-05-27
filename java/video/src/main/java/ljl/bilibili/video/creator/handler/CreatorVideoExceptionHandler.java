package ljl.bilibili.video.creator.handler;

import ljl.bilibili.client.creator.CreatorException;
import ljl.bilibili.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ljl.bilibili.video.creator")
@Order(1)
@Slf4j
public class CreatorVideoExceptionHandler {

    @ExceptionHandler(CreatorException.class)
    public Result<Void> handle(CreatorException e) {
        log.warn("Video CreatorException code={}", e.getCode());
        return Result.bizError(e.getCode(), e.getUserMessage());
    }
}
