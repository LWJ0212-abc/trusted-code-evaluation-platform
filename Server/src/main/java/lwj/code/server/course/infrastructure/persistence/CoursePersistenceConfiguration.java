package lwj.code.server.course.infrastructure.persistence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("lwj.code.server.course.infrastructure.persistence.mapper")
public class CoursePersistenceConfiguration {
}
