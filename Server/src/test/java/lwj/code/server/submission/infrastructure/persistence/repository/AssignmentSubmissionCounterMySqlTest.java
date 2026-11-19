package lwj.code.server.submission.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.mysql.cj.jdbc.MysqlDataSource;
import lwj.code.server.course.domain.Assignment;
import lwj.code.server.course.domain.AssignmentStatus;
import lwj.code.server.submission.application.AssignmentSubmissionCounterRepository;
import lwj.code.server.submission.infrastructure.persistence.mapper.AssignmentSubmissionCounterMapper;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(AssignmentSubmissionCounterMySqlTest.TestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class AssignmentSubmissionCounterMySqlTest {

    private static final String ASSIGNMENT_ID = "assignment-concurrent-init";
    private static final String OWNER_ID = "student-concurrent-init";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("submission_counter_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("test.mysql.jdbc-url", MYSQL::getJdbcUrl);
        registry.add("test.mysql.username", MYSQL::getUsername);
        registry.add("test.mysql.password", MYSQL::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AssignmentSubmissionCounterRepository counterRepository;

    @Autowired
    private EmptyInitialReadBarrier initialReadBarrier;

    @BeforeEach
    void createCounterTable() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS assignment_submission_counter");
        jdbcTemplate.execute("""
                CREATE TABLE assignment_submission_counter (
                    assignment_id CHAR(36) NOT NULL,
                    student_id CHAR(36) NOT NULL,
                    last_attempt_no BIGINT UNSIGNED NOT NULL DEFAULT 0,
                    accepted_count INT UNSIGNED NOT NULL DEFAULT 0,
                    max_attempts INT UNSIGNED NULL,
                    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                        ON UPDATE CURRENT_TIMESTAMP(6),
                    PRIMARY KEY (assignment_id, student_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        initialReadBarrier.resetForTwoReaders();
    }

    @Test
    void concurrentFirstRequestsAllocateDistinctAttemptNumbers() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Assignment assignment = assignment(5);
            Future<Long> first = executor.submit(() -> counterRepository.nextAttemptNo(assignment, OWNER_ID));
            Future<Long> second = executor.submit(() -> counterRepository.nextAttemptNo(assignment, OWNER_ID));

            assertTrue(initialReadBarrier.awaitBothEmptyReads(10, TimeUnit.SECONDS),
                    "两个事务都应在初始化前查到计数器不存在");
            initialReadBarrier.releaseReaders();

            List<Long> allocated = new ArrayList<>(List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            ));
            allocated.sort(Long::compareTo);

            assertEquals(List.of(1L, 2L), allocated);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM assignment_submission_counter WHERE assignment_id = ? AND student_id = ?",
                    Integer.class, ASSIGNMENT_ID, OWNER_ID));
            assertEquals(2L, jdbcTemplate.queryForObject(
                    "SELECT last_attempt_no FROM assignment_submission_counter WHERE assignment_id = ? AND student_id = ?",
                    Long.class, ASSIGNMENT_ID, OWNER_ID));
        } finally {
            initialReadBarrier.releaseReaders();
            executor.shutdownNow();
        }
    }

    private static Assignment assignment(int maxAttempts) {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);
        return new Assignment(
                ASSIGNMENT_ID,
                "course-1",
                "Concurrency test",
                "",
                "content-v1",
                "teacher-1",
                AssignmentStatus.PUBLISHED,
                null,
                null,
                maxAttempts,
                now,
                now
        );
    }

    @Configuration
    @EnableTransactionManagement
    @org.mybatis.spring.annotation.MapperScan(
            basePackageClasses = AssignmentSubmissionCounterMapper.class,
            sqlSessionFactoryRef = "sqlSessionFactory")
    static class TestConfiguration {

        @Bean
        DataSource dataSource(
                @org.springframework.beans.factory.annotation.Value("${test.mysql.jdbc-url}") String jdbcUrl,
                @org.springframework.beans.factory.annotation.Value("${test.mysql.username}") String username,
                @org.springframework.beans.factory.annotation.Value("${test.mysql.password}") String password) {
            MysqlDataSource dataSource = new MysqlDataSource();
            dataSource.setURL(jdbcUrl);
            dataSource.setUser(username);
            dataSource.setPassword(password);
            return dataSource;
        }

        @Bean
        org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory(
                DataSource dataSource,
                EmptyInitialReadBarrier barrier) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addInterceptor(barrier);
            org.mybatis.spring.SqlSessionFactoryBean factory = new org.mybatis.spring.SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        AssignmentSubmissionCounterRepository counterRepository(
                AssignmentSubmissionCounterMapper mapper,
                Clock clock) {
            return new AssignmentSubmissionCounterRepositoryImpl(mapper, clock);
        }

        @Bean
        EmptyInitialReadBarrier emptyInitialReadBarrier() {
            return new EmptyInitialReadBarrier();
        }
    }

    @Intercepts(@Signature(
            type = Executor.class,
            method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}
    ))
    static class EmptyInitialReadBarrier implements Interceptor {

        private volatile CountDownLatch emptyReads = new CountDownLatch(2);
        private volatile CountDownLatch release = new CountDownLatch(1);

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            Object parameter = invocation.getArgs()[1];
            BoundSql boundSql = statement.getBoundSql(parameter);
            String sql = boundSql.getSql().toLowerCase();
            boolean initialCounterRead = statement.getId().endsWith(".selectList")
                    && sql.contains("assignment_submission_counter")
                    && !sql.contains("for update");

            Object result = invocation.proceed();
            if (initialCounterRead && result instanceof List<?> rows && rows.isEmpty()) {
                emptyReads.countDown();
                if (!release.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("等待并发计数器查询超时");
                }
            }
            return result;
        }

        @Override
        public Object plugin(Object target) {
            return Plugin.wrap(target, this);
        }

        boolean awaitBothEmptyReads(long timeout, TimeUnit unit) throws InterruptedException {
            return emptyReads.await(timeout, unit);
        }

        void releaseReaders() {
            release.countDown();
        }

        void resetForTwoReaders() {
            emptyReads = new CountDownLatch(2);
            release = new CountDownLatch(1);
        }
    }
}
