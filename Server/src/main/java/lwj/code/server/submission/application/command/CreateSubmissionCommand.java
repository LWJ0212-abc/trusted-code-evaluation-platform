package lwj.code.server.submission.application.command;

public record CreateSubmissionCommand(
        /**
         * 当前登录用户 ID。
         * 由服务端从认证上下文获取，不允许客户端直接指定。
         */
        String ownerId,

        /** 所属作业 ID。 */
        String assignmentId,

        /** 提交使用的内容版本。 */
        String contentVersion,

        /** 源代码上传对象 ID。 */
        String uploadId,

        /** 客户端提交的源代码 SHA-256。 */
        String sha256,

        /** 幂等请求标识。 */
        String idempotencyKey
) {
}
