package lwj.code.server.submission.infrastructure.objectstore;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;

@Component
@RequiredArgsConstructor
public class S3ObjectStoreAdapter implements ObjectStore {

    private final S3Client s3Client;
    private final ObjectStoreProperties properties;

    /**
     * 获取对象的元数据信息（不下载文件内容）。
     *
     * @param bucket  存储桶名称
     * @param key     对象存储键（路径/文件名）
     * @param version 对象版本号（若未开启版本控制或获取最新版本可传 null）
     * @return
     */
    @Override
    public ObjectMetadata head(String bucket, String key, String version) {
        //*1.检验三个参数
        validateLocation(bucket, key, version);
        HeadObjectRequest.Builder request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(key);
        //*进一步验证version是否为null：
        //*null 表示读取当前版本；非 null 时必须是非空字符串。之后在请求构造时，只有非空才设置
        if (version != null) request.versionId(version);
        //*获取返回结果

        try {
            HeadObjectResponse response = s3Client.headObject(request.build());

            return new ObjectMetadata(
                    bucket,
                    key,
                    response.versionId(),
                    response.contentLength(),
                    response.checksumSHA256(),
                    response.contentType(),
                    response.lastModified()
            );
        } catch (S3Exception | SdkClientException e) {
            throw new ObjectStoreException("读取对象元数据失败: " + bucket + "/" + key, e);
        }

    }

    /**
     * @param bucket  存储桶名称
     * @param key     对象存储键（路径/文件名）
     * @param version 对象版本号（若未开启版本控制或读取最新版本可传 null）
     * @return
     */
    @Override
    public InputStream read(String bucket, String key, String version) {
        validateLocation(bucket, key, version);
        GetObjectRequest.Builder getBuilder = GetObjectRequest.builder().bucket(bucket).key(key);
        if (version != null) getBuilder.versionId(version);
        //返回流
        try{
            return s3Client.getObject(getBuilder.build());
        }catch (S3Exception | SdkClientException e){
            throw new ObjectStoreException("读取对象的文件内容流失败: " + bucket + "/" + key, e);
        }

    }

    /**
     * @param bucket  存储桶名称
     * @param key     对象存储键（路径/文件名）
     * @param version 对象版本号（若不指定版本则执行常规删除或添加删除标记）
     */
    @Override
    public void delete(String bucket, String key, String version) {
        validateLocation(bucket, key, version);
        DeleteObjectRequest.Builder deleteBuilder = DeleteObjectRequest.builder().bucket(bucket).key(key);
        if (version != null) deleteBuilder.versionId(version);

        try {
            DeleteObjectResponse response = s3Client.deleteObject(deleteBuilder.build());
            if (!response.sdkHttpResponse().isSuccessful()) {
                throw new ObjectStoreException(
                        "删除对象失败，HTTP 状态码: "
                                + response.sdkHttpResponse().statusCode()
                );
            }
        } catch (S3Exception | SdkClientException e) {
            throw new ObjectStoreException(
                    "删除对象失败: " + bucket + "/" + key,
                    e
            );
        }
    }

    private void validateLocation(String bucket, String key, String version) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket 不能为空");
        }

        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key 不能为空");
        }

        if (!isAllowedBucket(bucket)) {
            throw new IllegalArgumentException("不允许访问该 bucket");
        }
        if (key.startsWith("/")
                || containsParentPathSegment(key)
                || key.contains("\\")
                || key.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("非法对象 key");
        }

        if (version != null && version.isBlank()) {
            throw new IllegalArgumentException("version 不能是空字符串");
        }
    }

    private boolean isAllowedBucket(String bucket) {
        return bucket.equals(properties.resultBucket()) ||
                bucket.equals(properties.sourceBucket());
    }

    private boolean containsParentPathSegment(String key) {
        for (String segment : key.split("/", -1)) {
            if (segment.equals("..")) {
                return true;
            }
        }
        return false;
    }
}
