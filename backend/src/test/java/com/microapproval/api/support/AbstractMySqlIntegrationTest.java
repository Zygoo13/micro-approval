package com.microapproval.api.support;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared MySQL infrastructure for Spring integration and concurrency tests.
 * The singleton is scoped to the test JVM; Testcontainers/Ryuk removes it when
 * the Maven test process exits. Pure unit tests do not load this class.
 */
@ActiveProfiles("test")
public abstract class AbstractMySqlIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.0");

    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE)
            .withDatabaseName("micro_approval_test")
            .withUsername("micro_test")
            .withPassword("micro_test_password")
            .withReuse(false);

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
    }
}
