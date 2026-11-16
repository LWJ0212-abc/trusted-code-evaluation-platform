package lwj.code.server.submission.application;

import lwj.code.server.submission.domain.Assignment;

public interface AssignmentSubmissionCounterRepository {

    /**
     * 返回下一次提交的编号
     *
     * @param assignment   作业实体
     * @param studentId    学生id
     * @return
     */
    public long nextAttemptNo(
            Assignment assignment,
            String studentId
    );
}
