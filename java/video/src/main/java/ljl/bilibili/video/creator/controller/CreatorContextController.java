package ljl.bilibili.video.creator.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import ljl.bilibili.client.creator.AsrSubmitRequest;
import ljl.bilibili.client.creator.AsrTaskResponse;
import ljl.bilibili.client.creator.VideoContextResponse;
import ljl.bilibili.util.Result;
import ljl.bilibili.video.creator.service.AsrTaskService;
import ljl.bilibili.video.creator.service.CreatorContextService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/createCenter/creator")
@Api(tags = "创作辅助-视频上下文与ASR")
@Slf4j
@CrossOrigin(value = "*")
public class CreatorContextController {

    @Resource
    private CreatorContextService creatorContextService;

    @Resource
    private AsrTaskService asrTaskService;

    @GetMapping("/context")
    @ApiOperation("获取上传上下文")
    public Result<VideoContextResponse> getContext(
            @RequestParam(value = "resumableIdentifier", required = false) String resumableIdentifier,
            @RequestParam(value = "videoUrl", required = false) String videoUrl) {
        return Result.data(creatorContextService.getContext(resumableIdentifier, videoUrl));
    }

    @PostMapping("/asr/submit")
    @ApiOperation("提交ASR转写任务")
    public Result<AsrTaskResponse> submitAsr(@RequestBody AsrSubmitRequest request) {
        return Result.data(asrTaskService.submit(request));
    }

    @GetMapping("/asr/{asrTaskId}")
    @ApiOperation("查询ASR任务状态")
    public Result<AsrTaskResponse> getAsrTask(@PathVariable String asrTaskId) {
        return Result.data(asrTaskService.getTask(asrTaskId));
    }
}
