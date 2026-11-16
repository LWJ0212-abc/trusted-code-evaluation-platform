package lwj.code.server.submission.infrastructure.persistence.repository;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lwj.code.server.submission.application.AssignmentSubmissionCounterRepository;
import lwj.code.server.submission.domain.Assignment;
import lwj.code.server.submission.infrastructure.persistence.entity.AssignmentSubmissionCounterEntity;
import lwj.code.server.submission.infrastructure.persistence.mapper.AssignmentSubmissionCounterMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Slf4j
@Repository
@RequiredArgsConstructor
public class AssignmentSubmissionCounterRepositoryImpl implements AssignmentSubmissionCounterRepository {

    private final AssignmentSubmissionCounterMapper counterMapper;

    @Override
    @Transactional
    public long nextAttemptNo(
            Assignment assignment,
            String ownerId
    ) {
        if (assignment == null) {
            throw new IllegalArgumentException("assignment 不能为空");
        }

        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId 不能为空");
        }

        AssignmentSubmissionCounterEntity entity =
                findCounter(assignment.assignmentId(), ownerId);
        //* 初始化提交记录
        if (entity == null) {
            int inserted = counterMapper.insertIgnore(
                    assignment.assignmentId(),
                    ownerId,
                    assignment.maxAttempts(),
                    LocalDateTime.now()
            );
            if (inserted==1){
                return 1L;
            }
            //返回的是0的话，进行查询
            entity=findCounter(assignment.assignmentId(),ownerId);
            if (entity==null){
                throw new IllegalStateException("无法读取提交计数器");
            }
        }
        //* 检验是否能够再提交
        validateAttemptLimit(entity);

        //* 更新
        return incrementCounter(entity);
    }

    /**
     * 更新提交次数和记录实体
     * @param entity 实体
     * @return 下一次提价次数
     */
    private long incrementCounter(AssignmentSubmissionCounterEntity entity) {
        if (entity.getLastAttemptNo() == null) {
            throw new IllegalStateException("计数器提交次数为空");
        }

        if (entity.getVersion() == null) {
            throw new IllegalStateException("计数器版本号为空");
        }

        // 当前时间
        LocalDateTime now = LocalDateTime.now();
        entity.setUpdatedAt(now);

        // 计算下一次提交次数
        long nextAttemptNo = entity.getLastAttemptNo() + 1;
        entity.setLastAttemptNo(nextAttemptNo);
        //*更新
        int rows=counterMapper.update(
              entity,
                Wrappers.<AssignmentSubmissionCounterEntity>lambdaUpdate()
                        .eq(AssignmentSubmissionCounterEntity::getAssignmentId,entity.getAssignmentId())
                        .eq(AssignmentSubmissionCounterEntity::getStudentId,entity.getStudentId())
                        .eq(AssignmentSubmissionCounterEntity::getVersion,entity.getVersion())
        );
        if (rows!=1){
            throw new IllegalStateException(  "提交次数并发更新冲突，请重试");
        }
        return nextAttemptNo;
    }

    /**
     * 检验是否能够继续提交
     * @param entity 记录实体
     */
    private void validateAttemptLimit(AssignmentSubmissionCounterEntity entity) {
        if (entity.getMaxAttempts()==null||entity.getMaxAttempts()==0) return;
        if (entity.getLastAttemptNo()>=entity.getMaxAttempts()){
                throw new IllegalStateException("已经达到最大提交次数，不能再提交");
        }
    }

    /**
     * 根据作业id和学生id查询对应的提交记录
     * @param assignmentId 作业id
     * @param ownerId 学生id
     * @return 作业记录实体
     */
    private AssignmentSubmissionCounterEntity findCounter(String assignmentId, String ownerId) {
        return counterMapper.selectOne(
                Wrappers.<AssignmentSubmissionCounterEntity>lambdaQuery()
                        .eq(AssignmentSubmissionCounterEntity::getAssignmentId, assignmentId)
                        .eq(AssignmentSubmissionCounterEntity::getStudentId, ownerId)
        );
    }


}
