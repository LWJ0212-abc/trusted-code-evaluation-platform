package lwj.code.server.submission.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * submission 表的持久化实体。
 *
 * <p>记录一次正式代码提交及其源代码对象快照，不保存源代码文件内容。</p>
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("submission")
public class SubmissionEntity {

    /** 提交唯一标识，由服务端生成。 */
    @TableId(value = "submission_id", type = IdType.INPUT)
    private String submissionId;

    /** 提交者用户 ID。 */
    @TableField("owner_id")
    private String ownerId;

    /** 所属作业 ID。 */
    @TableField("assignment_id")
    private String assignmentId;

    /** 提交时使用的内容版本。 */
    @TableField("content_version")
    private String contentVersion;

    /** 关联的源代码上传对象 ID。 */
    @TableField("source_upload_id")
    private String sourceUploadId;

    /** 用户在该作业中的提交序号。 */
    @TableField("attempt_no")
    private Long attemptNo;

    /** 客户端幂等请求标识。 */
    @TableField("idempotency_key")
    private String idempotencyKey;

    /** 提交源代码的 SHA-256 摘要快照。 */
    @TableField("source_sha256")
    private String sourceSha256;

    /** 提交源代码大小，单位为字节。 */
    @TableField("source_size_bytes")
    private Long sourceSizeBytes;

    /** 提交处理状态。 */
    @TableField("status")
    private String status;

    /** 用户提交时间。 */
    @TableField("submitted_at")
    private LocalDateTime submittedAt;

    /** 记录创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 记录最后更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 乐观锁版本号。 */
    @Version
    @TableField("version")
    private Long version;
}
