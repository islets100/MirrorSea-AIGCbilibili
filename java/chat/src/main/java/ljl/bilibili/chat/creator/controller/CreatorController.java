package ljl.bilibili.chat.creator.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import ljl.bilibili.client.creator.*;
import ljl.bilibili.chat.creator.service.CreatorSuggestService;
import ljl.bilibili.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

@RestController
@RequestMapping("/chat/creator")
@Api(tags = "创作辅助Agent")
@Slf4j
@Validated
public class CreatorController {

    @Resource
    private CreatorSuggestService creatorSuggestService;

    @PostMapping("/suggest")
    @ApiOperation("提交标题/简介生成任务")
    public Result<CreatorSuggestSubmitResponse> suggest(@Valid @RequestBody CreatorSuggestRequest request) {
        return creatorSuggestService.submit(request);
    }

    @GetMapping("/suggest/{taskId}")
    @ApiOperation("查询任务状态")
    public Result<CreatorSuggestTaskResponse> getTask(@PathVariable String taskId,
                                                      @RequestParam Integer userId) {
        return creatorSuggestService.getTask(taskId, userId);
    }

    @GetMapping("/suggest/{taskId}/references")
    @ApiOperation("获取RAG参考来源")
    public Result<RagRetrieveResponse> getReferences(@PathVariable String taskId,
                                                     @RequestParam Integer userId) {
        return creatorSuggestService.getReferences(taskId, userId);
    }

    @PostMapping("/suggest/{taskId}/adopt")
    @ApiOperation("采纳建议埋点")
    public Result<Boolean> adopt(@PathVariable String taskId, @Valid @RequestBody CreatorAdoptRequest request) {
        return creatorSuggestService.adopt(taskId, request);
    }
}
