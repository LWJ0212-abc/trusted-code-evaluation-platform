package lwj.code.server.outbox.application.exception;

/**
 * Outbox 持久化未能按预期完成时抛出的异常。
 * <p>
 * 数据库驱动或 MyBatis 已抛出的异常应保留原类型和 cause，不要统一包装成此异常，
 * 以便上层识别唯一约束冲突等具体情况。
 */
public class OutboxPersistenceException extends RuntimeException {

    public OutboxPersistenceException(String message) {
        super(message);
    }
}
