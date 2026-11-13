package lwj.code.server.submission.infrastructure.objectstore;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import java.time.Instant;



@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ObjectMetadata(
        String bucket,
        String key,
        String version,
        long size,
        String sha256,
        String contentType,
        Instant lastModified
) {
}
