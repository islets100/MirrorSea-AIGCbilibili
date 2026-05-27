package ljl.bilibili.chat.creator.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("creator_suggest_log")
public class CreatorSuggestLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private String taskId;

    @TableField("user_id")
    private Integer userId;

    @TableField("resumable_identifier")
    private String resumableIdentifier;

    @TableField("input_snapshot")
    private String inputSnapshot;

    @TableField("output_snapshot")
    private String outputSnapshot;

    @TableField("adopted_title")
    private String adoptedTitle;

    @TableField("adopted_intro")
    private String adoptedIntro;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
