package lwj.code.server.submission.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * source_object 表的持久化实体。
 *
 * <p>记录上传对象的归属、对象存储位置、内容摘要和校验状态。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("source_object")
public class SourceObjectEntity {

    /** 上传对象唯一标识，也是表主键。 */
    @TableId(value = "upload_id", type = IdType.INPUT)
    private String uploadId;

    /** 对象所有者的用户 ID。 */
    @TableField("owner_id")
    private String ownerId;

    /** 对象所属作业的 ID。 */
    @TableField("assignment_id")
    private String assignmentId;

    /** 上传内容的版本标识。 */
    @TableField("content_version")
    private String contentVersion;

    /** 对象存储桶名称。 */
    @TableField("object_bucket")
    private String objectBucket;

    /** 对象存储键。 */
    @TableField("object_key")
    private String objectKey;

    /** 对象存储版本；未启用版本控制时可以为空。 */
    @TableField("object_version")
    private String objectVersion;

    /** 对象内容的 SHA-256 十六进制摘要。 */
    @TableField("sha256")
    private String sha256;

    /** 对象内容的字节数。 */
    @TableField("size_bytes")
    private Long sizeBytes;

    /** 对象校验状态。 */
    @TableField("validation_status")
    private String validationStatus;

    /** 暂存对象的过期时间；为空表示没有设置过期时间。 */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /** 记录创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 对象校验通过时间。 */
    @TableField("validated_at")
    private LocalDateTime validatedAt;
}
