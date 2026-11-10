package lwj.code.server.identity.domain;

/**
 * 平台角色
 */
public enum PlatformRole {
    PLATFORM_ADMIN,//平台管理员，属于平台级角色，不是课程角色。
    AUDITOR//审计员，属于平台级角色，用于查看审计信息。
}
