package lwj.code.server.submission.domain;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 一次正式代码提交的领域对象。
 *
 * <p>该对象保存提交时的源对象快照，不包含源代码文件内容。</p>
 */
@Builder
public record Submission(
        /** 提交唯一标识。 */
        String submissionId,

        /** 提交者用户 ID。 */
        String ownerId,

        /** 所属作业 ID。 */
        String assignmentId,

        /** 提交时使用的内容版本。 */
        String contentVersion,

        /** 关联的源代码上传对象 ID。 */
        String sourceUploadId,

        /** 用户在该作业中的提交序号。 */
        long attemptNo,

        /** 客户端幂等请求标识。 */
        String idempotencyKey,

        /** 提交源代码的 SHA-256 摘要快照。 */
        String sourceSha256,

        /** 提交源代码大小，单位为字节。 */
        long sourceSizeBytes,

        /** 当前提交状态。 */
        SubmissionStatus status,

        /** 用户提交时间。 */
        LocalDateTime submittedAt,

        /** 记录创建时间。 */
        LocalDateTime createdAt,

        /** 记录最后更新时间。 */
        LocalDateTime updatedAt,
        /**乐观锁*/
        long version
) {

    /**
     * 创建一条刚通过源对象校验的提交记录。
     */
    public static Submission accepted(
            String submissionId,
            String ownerId,
            String assignmentId,
            String contentVersion,
            String sourceUploadId,
            long attemptNo,
            String idempotencyKey,
            String sourceSha256,
            long sourceSizeBytes,
            LocalDateTime now,
            long version
    ) {
        return new Submission(
                submissionId,
                ownerId,
                assignmentId,
                contentVersion,
                sourceUploadId,
                attemptNo,
                idempotencyKey,
                sourceSha256,
                sourceSizeBytes,
                SubmissionStatus.ACCEPTED,
                now,
                now,
                now,
                version
        );
    }

    /**
     * 返回状态更新后的新对象。
     */

    public Submission transitionTo(
            SubmissionStatus newStatus,
            LocalDateTime now
    ) {
        if (newStatus == null) {
            throw new IllegalArgumentException("新状态不能为空");
        }

        if (!isAllowedTransition(status, newStatus)) {
            throw new IllegalStateException(
                    "不允许的状态转换: "
                            + status + " -> " + newStatus
            );
        }

        return new Submission(
                submissionId,
                ownerId,
                assignmentId,
                contentVersion,
                sourceUploadId,
                attemptNo,
                idempotencyKey,
                sourceSha256,
                sourceSizeBytes,
                newStatus,
                submittedAt,
                createdAt,
                now,
                version
        );
    }

    private boolean isAllowedTransition(SubmissionStatus current,
                                        SubmissionStatus next) {
        return switch (current){
            case SubmissionStatus.ACCEPTED ->
                next==SubmissionStatus.QUEUED||
                        next==SubmissionStatus.REJECTED;
            case SubmissionStatus.QUEUED ->
                next==SubmissionStatus.REJECTED||
                        next==SubmissionStatus.RUNNING;
            case SubmissionStatus.RUNNING ->
                next==SubmissionStatus.FAILED||
                        next==SubmissionStatus.SUCCEEDED;
            case SubmissionStatus.SUCCEEDED,SubmissionStatus.FAILED,SubmissionStatus.REJECTED ->
                false;
        };
    }

    public boolean matchesRequest(
            String contentVersion,
            String sourceUploadId,
            String sha256
    ) {
        return Objects.equals(this.contentVersion.toLowerCase(), contentVersion.toLowerCase())
                && Objects.equals(this.sourceUploadId, sourceUploadId)
                && Objects.equals(this.sourceSha256, sha256);
    }
}
