package lwj.code.server.identity.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * external_identity 表的持久化实体。
 *
 * 只负责数据库字段映射，不包含身份查询和业务规则。
 */

@Getter
@Setter
@NoArgsConstructor
@TableName("external_identity")
public class ExternalIdentityEntity {
    /**
     * OIDC 签发者，对应令牌中的 iss。
     */
    @TableField("issuer")
    private String issuer;

    /**
     * 用户在该签发者中的唯一标识，对应令牌中的 sub。
     */
    @TableField("subject")
    private String subject;

    /**
     * 平台内部用户 ID。
     */
    @TableField("user_id")
    private String userId;

    /**
     * 外部身份绑定记录的创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
