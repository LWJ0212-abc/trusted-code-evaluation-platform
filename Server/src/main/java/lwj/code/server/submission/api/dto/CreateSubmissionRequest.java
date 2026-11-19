package lwj.code.server.submission.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSubmissionRequest(

        @NotBlank(message = "作业ID不能为空")
        String assignmentId,

        @NotBlank(message = "内容版本号不能为空")
        String contentVersion,

        @NotBlank(message = "上传文件ID不能为空")
        String uploadId,

        @NotBlank(message = "文件的SHA256校验值不能为空")
        @Size(min = 64, max = 64, message = "SHA256长度必须为64位")
        String sha256

) {}