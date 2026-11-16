package lwj.code.server.submission.infrastructure.persistence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("lwj.code.server.submission.infrastructure.persistence.mapper")
public class SubmissionPersistenceConfiguration {
}
