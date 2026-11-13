package lwj.code.server.submission.infrastructure.objectstore;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="tcep.object-store")
public record ObjectStoreProperties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String sourceBucket,
        String resultBucket,
        String stagingPrefix,
        String sourcePrefix,
        String resultPrefix
) {
}
