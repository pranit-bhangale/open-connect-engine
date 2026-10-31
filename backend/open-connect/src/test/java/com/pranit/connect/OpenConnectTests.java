package com.pranit.connect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TESTS", matches = "true")
class OpenConnectTests {

	@Test
	void contextLoads() {
	}

}
