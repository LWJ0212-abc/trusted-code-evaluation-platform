package lwj.code.server.course.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lwj.code.server.course.application.AssignmentRepository;
import lwj.code.server.course.domain.Assignment;
import lwj.code.server.course.infrastructure.persistence.entity.AssignmentEntity;
import lwj.code.server.course.infrastructure.persistence.mapper.AssignmentMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Slf4j
@RequiredArgsConstructor
public class AssignmentRepositoryImpl implements AssignmentRepository {

    private final AssignmentMapper assignmentMapper;

    /**
     * @param assignmentId 作业的唯一标识 ID
     * @return
     */
    @Override
    public Optional<Assignment> findById(String assignmentId) {
        //*1.检验assignmenntId是否合规
        if (assignmentId==null||assignmentId.isBlank()){
            throw new IllegalArgumentException("assignmentId 不能为空");
        }
        //*2.查询
        AssignmentEntity entity=assignmentMapper.selectById(assignmentId);
        return entity==null?
                Optional.empty()
                :Optional.of(toDomain(entity));
    }


    /**
     * 保存作业
     * @param assignment 待保存的作业领域对象
     * @return
     */
    @Override
    public Assignment save(Assignment assignment) {
        if (assignment==null){
            throw new IllegalArgumentException("作业不能为null");
        }

        int rows=assignmentMapper.insert(toEntity(assignment));
        if (rows!=1){
            throw new IllegalStateException("插入作业失败");
        }
        return assignment;
    }

    /**
     * @param assignment 包含最新数据的作业领域对象
     * @return
     */
    @Override
    public Assignment update(Assignment assignment) {
        if (assignment==null){
            throw new IllegalArgumentException("作业传递不能为空");
        }
        if (assignment.assignmentId()==null||assignment.assignmentId().isBlank()){
            throw new IllegalArgumentException("assignmentId 不能为空");
        }
        //* 更新实体
        int rows=assignmentMapper.update(
                toEntity(assignment),
                Wrappers.<AssignmentEntity>lambdaUpdate()
                        .eq(AssignmentEntity::getAssignmentId,assignment.assignmentId())
        );
        if (rows!=1){
            throw new IllegalStateException("更新作业失败，作业可能不存在");
        }
        return assignment;
    }

    /**
     * 将实体类转为domain
     */
    private Assignment toDomain(AssignmentEntity entity) {
        return Assignment.builder()
                .assignmentId(entity.getAssignmentId())
                .courseId(entity.getCourseId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .contentVersion(entity.getContentVersion())
                .createdBy(entity.getCreatedBy())
                .status(entity.getStatus())
                .opensAt(entity.getOpensAt())
                .dueAt(entity.getDueAt())
                .maxAttempts(entity.getMaxAttempts())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AssignmentEntity toEntity(Assignment assignment){
        return AssignmentEntity.builder()
                .assignmentId(assignment.assignmentId())
                .contentVersion(assignment.contentVersion())
                .title(assignment.title())
                .dueAt(assignment.dueAt())
                .createdAt(assignment.createdAt())
                .createdBy(assignment.createdBy())
                .opensAt(assignment.opensAt())
                .status(assignment.status())
                .maxAttempts(assignment.maxAttempts())
                .updatedAt(assignment.updatedAt())
                .description(assignment.description())
                .courseId(assignment.courseId())
                .build();
    }
}
