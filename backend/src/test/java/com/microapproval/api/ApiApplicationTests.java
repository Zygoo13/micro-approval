package com.microapproval.api;

import com.microapproval.api.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApiApplicationTests extends AbstractMySqlIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void freshContainerIsMigratedThroughFlywayV14() {
		Integer version = jdbcTemplate.queryForObject(
				"SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1",
				Integer.class
		);

		assertThat(version).isEqualTo(14);
	}

}
