package lwj.code.server.submission.application;


import lombok.RequiredArgsConstructor;
import lwj.code.server.submission.domain.SourceObject;
import lwj.code.server.submission.domain.SourceObjectValidationStatus;
import lwj.code.server.submission.infrastructure.objectstore.ObjectMetadata;
import lwj.code.server.submission.infrastructure.objectstore.ObjectStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;


@RequiredArgsConstructor
@Service
public class SourceObjectValidator {

    //*查询数据库实体
    private final SourceObjectRepository objectRepository;
    //*从rustfs里读取元信息
    private final ObjectStore objectStore;

    /**
     * 校验上传对象是否可以用于当前提交。
     *
     * @param uploadId       上传对象 ID
     * @param ownerId        当前登录用户 ID
     * @param assignmentId   作业 ID
     * @param contentVersion 内容版本
     * @param expectedSha256 客户端提交的 SHA-256
     * @return 已校验的源对象
     */
    public SourceObject validate(
            String uploadId,
            String ownerId,
            String assignmentId,
            String contentVersion,
            String expectedSha256
    ) {
        //*1.根据上传Id查询SourceObject，并检验是否为空
        SourceObject object = objectRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("上传对象不存在"));
        //*2. 校验 ownerId
        validateOwnership(object, ownerId, assignmentId);
        //*3. 校验 assignmentId，校验 contentVersion
        validateContentVersion(object, contentVersion);
        //*4. 校验 validationStatus
        validateStatus(object);
        //*5. 校验 expiresAt
        validateExpiration(object);

        ObjectMetadata metadata = objectStore.head(object.objectBucket(), object.objectKey(), object.objectVersion());


        //6.检验上传文件大小
        validateSize(object, metadata);
        //7.校验hash是否相等
        validateSha256(object, expectedSha256);

        return object;
    }

    private void validateSha256(SourceObject object, String expectedSha256) {
        if (expectedSha256 == null
                || !expectedSha256.equalsIgnoreCase(object.sha256())) {
            throw new IllegalArgumentException("请求摘要与数据库摘要不匹配");
        }

        String actualSha256 = calculateSha256(object.objectBucket(),
                object.objectKey(),
                object.objectVersion());

        if (!actualSha256.equalsIgnoreCase(object.sha256())) {
            throw new IllegalArgumentException("对象实际摘要不匹配");
        }

    }

    private String calculateSha256(String bucket, String key, String versionId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int readBytes;
            try (InputStream in = objectStore.read(bucket, key, versionId)) {
                while ((readBytes=in.read(buffer))!=-1){
                    digest.update(buffer,0,readBytes);
                }
                return HexFormat.of().formatHex(digest.digest());
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", e);
        } catch (IOException e) {
            throw new IllegalStateException("读取对象内容失败", e);
        }
    }

    private void validateSize(SourceObject object, ObjectMetadata metadata) {
        if (object.sizeBytes() != metadata.size()) {
            throw new IllegalArgumentException("对象大小不匹配");
        }
    }

    private void validateStatus(SourceObject object) {
        if (!SourceObjectValidationStatus.VALIDATED.name().equals(object.validationStatus())) {
            throw new IllegalArgumentException("上传对象尚未通过校验");
        }
    }

    private void validateExpiration(SourceObject object) {
        if (object.expiresAt() != null &&
                object.expiresAt().isBefore(LocalDateTime.now())
        ) {
            throw new IllegalArgumentException("上传对象已过期");
        }
    }

    private void validateContentVersion(SourceObject object, String contentVersion) {
        if (!object.contentVersion().equals(contentVersion)) {
            throw new IllegalArgumentException("内容版本不匹配");
        }
    }

    private void validateOwnership(SourceObject object, String ownerId, String assignmentId) {
        if (!object.ownerId().equals(ownerId)) {
            throw new IllegalArgumentException("无权访问该上传对象");
        }
        if (!object.assignmentId().equals(assignmentId)) {
            throw new IllegalArgumentException("上传对象不属于当前作业");
        }
    }
}
