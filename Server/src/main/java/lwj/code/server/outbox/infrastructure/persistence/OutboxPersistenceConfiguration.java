package lwj.code.server.outbox.infrastructure.persistence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("lwj.code.server.outbox.infrastructure.persistence.mapper")
public class OutboxPersistenceConfiguration {
}
