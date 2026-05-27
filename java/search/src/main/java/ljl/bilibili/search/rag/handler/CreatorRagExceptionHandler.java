package ljl.bilibili.search.rag.handler;

import ljl.bilibili.client.creator.CreatorException;
import ljl.bilibili.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ljl.bilibili.search.rag")
@Order(1)
@Slf4j
public class CreatorRagExceptionHandler {

    @ExceptionHandler(CreatorException.class)
    public Result<Void> handle(CreatorException e) {
        log.warn("RAG CreatorException code={}", e.getCode());
        return Result.bizError(e.getCode(), e.getUserMessage());
    }
}
