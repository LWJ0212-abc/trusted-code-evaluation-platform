package lwj.code.server.course.application;

import lwj.code.server.course.domain.Assignment;
import java.util.Optional;

/**
 * 作业/任务仓储接口
 * 负责作业领域对象（Assignment）的持久化与数据访问操作。
 */
public interface AssignmentRepository {

    /**
     * 根据作业 ID 查询作业详情
     *
     * @param assignmentId 作业的唯一标识 ID
     * @return 包含作业对象的 Optional；如果不存在则返回 Optional.empty()
     */
    Optional<Assignment> findById(String assignmentId);

    /**
     * 保存新的作业记录
     * 通常用于新建作业（DRAFT 或 PUBLISHED）时的插入操作。
     *
     * @param assignment 待保存的作业领域对象
     * @return 保存后的作业对象（通常会包含数据库生成的主键或默认值）
     */
    Assignment save(Assignment assignment);

    /**
     * 更新已存在的作业记录
     * 通常用于修改作业内容、更新状态（如发布、关闭）等操作。
     *
     * @param assignment 包含最新数据的作业领域对象
     * @return 更新后的作业对象
     */
    Assignment update(Assignment assignment);
}