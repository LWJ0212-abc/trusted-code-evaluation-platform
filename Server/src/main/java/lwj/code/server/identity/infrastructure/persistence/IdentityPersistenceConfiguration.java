package lwj.code.server.identity.infrastructure.persistence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("lwj.code.server.identity.infrastructure.persistence.mapper")
public class IdentityPersistenceConfiguration {
}
