package lwj.code.server.submission.infrastructure.objectstore;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;


@Configuration
@Slf4j
@EnableConfigurationProperties(ObjectStoreProperties.class)
public class RustfsConfig {

    /**
     * 返回s3客户端
     */

    @Bean
    S3Client rustfsS3Client(ObjectStoreProperties properties){
        log.info("加载RustFS配置");
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        properties.accessKey(),
                                        properties.secretKey()
                                )
                        )
                )
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build()
                )
                .endpointOverride(URI.create(properties.endpoint()))
                .build();

    }
}
