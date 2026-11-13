package lwj.code.server.submission.infrastructure.objectstore;



import java.io.InputStream;

/**
 * 对象存储端口。
 * 仅定义业务层所需的存储能力，隔离底层具体存储实现（如 OSS、S3、MinIO 等）。
 */
public interface ObjectStore {

    /**
     * 获取对象的元数据信息（不下载文件内容）。
     *
     * @param bucket  存储桶名称
     * @param key     对象存储键（路径/文件名）
     * @param version 对象版本号（若未开启版本控制或获取最新版本可传 null）
     * @return 对象元数据（包含大小、内容类型、ETag 等信息）
     */
    ObjectMetadata head(String bucket, String key, String version);

    /**
     * 读取对象的文件内容流。
     * 注意：调用方需负责在使用完毕后关闭返回的输入流，以释放网络连接资源。
     *
     * @param bucket  存储桶名称
     * @param key     对象存储键（路径/文件名）
     * @param version 对象版本号（若未开启版本控制或读取最新版本可传 null）
     * @return 对象内容的输入流
     */
    InputStream read(String bucket, String key, String version);

    /**
     * 删除指定的对象或特定版本。
     *
     * @param bucket  存储桶名称
     * @param key     对象存储键（路径/文件名）
     * @param version 对象版本号（若不指定版本则执行常规删除或添加删除标记）
     */
    void delete(String bucket, String key, String version);
}