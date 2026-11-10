package lwj.code.server.identity.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lwj.code.server.identity.domain.PlatformRole;

import java.time.LocalDateTime;

/**
 * platform_user_role 表的持久化实体。
 *
 * 表示平台用户拥有的平台级角色，不包括课程角色。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("platform_user_role")
public class PlatformUserRoleEntity {

    /**
     * 被授予角色的平台内部用户 ID。
     */
    @TableField("user_id")
    private String userId;

    /**
     * 平台级角色，例如 PLATFORM_ADMIN、AUDITOR。
     */
    @TableField("role")
    private PlatformRole role;

    /**
     * 角色授予时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
