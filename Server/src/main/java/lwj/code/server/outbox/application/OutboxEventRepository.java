package lwj.code.server.outbox.application;

import lwj.code.server.outbox.application.exception.OutboxPersistenceException;
import lwj.code.server.outbox.domain.OutboxEvent;

/**
 * Outbox 事件仓储接口。
 * <p>
 * 定义应用层对 Outbox 事件的持久化操作，不负责向 RabbitMQ 发布消息。
 */
public interface OutboxEventRepository {

    /**
     * 新增一条 Outbox 事件记录。
     * <p>
     * 调用方应在创建业务数据的同一数据库事务中调用此方法，确保业务记录与待发布事件
     * 一起提交或回滚。
     *
     * @param event 待保存的 Outbox 事件，不能为 {@code null}
     * @return 已保存的 Outbox 事件
     * @throws IllegalArgumentException 当事件为空或必填字段不合法时抛出
     * @throws OutboxPersistenceException
     *         当数据库未能恰好插入一条记录时抛出
     * @throws org.springframework.dao.DataAccessException 当数据库访问失败时由持久层抛出
     */
    OutboxEvent save(OutboxEvent event);
}
