package lwj.code.server.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lwj.code.server.identity.application.IdentityDirectory;
import lwj.code.server.identity.domain.*;
import lwj.code.server.identity.infrastructure.persistence.entity.CourseMembershipEntity;
import lwj.code.server.identity.infrastructure.persistence.entity.ExternalIdentityEntity;
import lwj.code.server.identity.infrastructure.persistence.entity.PlatformUserRoleEntity;
import lwj.code.server.identity.infrastructure.persistence.mapper.CourseMembershipMapper;
import lwj.code.server.identity.infrastructure.persistence.mapper.ExternalIdentityMapper;
import lwj.code.server.identity.infrastructure.persistence.mapper.PlatformUserRoleMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Repository
public class DatabaseIdentityDirectory implements IdentityDirectory {

    private final CourseMembershipMapper courseMembershipMapper;
    private final ExternalIdentityMapper externalIdentityMapper;
    private final PlatformUserRoleMapper platformUserRoleMapper;

    //*三次查询可能读取到不同时间点的数据。身份绑定、课程角色或平台角色并发变化时，可能组装出不一致的授权快照。
    @Transactional(readOnly = true)  //!锁定mvcc快照
    @Override
    public Optional<User> find(ExternalIdentity identity) {
        //identity已经检验
        //*1. 查询外部身份
        ExternalIdentityEntity boundIdentity = externalIdentityMapper.selectOne(
                Wrappers.<ExternalIdentityEntity>lambdaQuery()
                        .eq(ExternalIdentityEntity::getIssuer, identity.issuer())
                        .eq(ExternalIdentityEntity::getSubject, identity.subject())
        );
        //!查不到用户说明该用户尚未绑定
        if (boundIdentity == null) return Optional.empty();
        //*2.查询课程成员关系，并转化为领域record
        List<CourseMembership> courseMemberships = courseMembershipMapper.selectList(
                        Wrappers.<CourseMembershipEntity>lambdaQuery()
                                .eq(CourseMembershipEntity::getUserId, boundIdentity.getUserId())
                                .orderByAsc(CourseMembershipEntity::getCreatedAt)
                ).stream()
                .map(entity ->
                        new CourseMembership(
                                UUID.fromString(entity.getCourseId()),
                                entity.getRole()
                        )).toList();
        //*3.查询平台角色
        Set<PlatformRole> platformUserRoles = platformUserRoleMapper.selectList(
                        Wrappers.<PlatformUserRoleEntity>lambdaQuery()
                                .select(PlatformUserRoleEntity::getRole)
                                .eq(PlatformUserRoleEntity::getUserId, boundIdentity.getUserId())
                ).stream().map(PlatformUserRoleEntity::getRole).
                collect(Collectors.toSet());
        //*4.转化为领域User
        User user = new User(
                UUID.fromString(boundIdentity.getUserId()),
                courseMemberships,
                platformUserRoles
        );
        return Optional.of(user);
    }
}
