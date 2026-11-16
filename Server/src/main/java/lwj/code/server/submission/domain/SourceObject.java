package lwj.code.server.submission.domain;


import java.time.LocalDateTime;

public record SourceObject(
        String uploadId,
        String ownerId,
        String assignmentId,
        String contentVersion,
        String objectBucket,
        String objectKey,
        String objectVersion,
        String sha256,
        long sizeBytes,
        String validationStatus,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime validatedAt
) {
}
