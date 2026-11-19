package lwj.code.server.evaluation.infrastructure;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * {@code evaluation.requested} v1 事件的 payload。
 *
 * <p>只包含读取已持久化评测任务所需的稳定标识和不可变内容版本引用，
 * 不包含源代码内容、对象存储凭证或临时签名 URL。</p>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EvaluationRequestedPayload(
        /** 已受理提交的唯一标识。 */
        String submissionId,

        /** 已在 Server 数据库中创建的评测任务标识。 */
        String evaluationTaskId,

        /** 本次评测必须使用的不可变内容版本 UUID。 */
        String contentVersion,

        /** 可选批次标识；普通单次提交必须序列化为 JSON null。 */
        String batchId
) {
}
