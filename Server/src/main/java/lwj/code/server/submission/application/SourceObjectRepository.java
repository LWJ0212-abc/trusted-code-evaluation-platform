package lwj.code.server.submission.application;

import lwj.code.server.submission.domain.SourceObject;

import java.util.Optional;

public interface SourceObjectRepository {

    /**
     * 根据id返回元数据信息
     * @param uploadId  上传的id
     * @return  元数据信息
     */
    Optional<SourceObject> findByUploadId(String uploadId);
}
