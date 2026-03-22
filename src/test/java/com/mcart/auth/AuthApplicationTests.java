package com.mcart.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.Mockito.mock;

@SpringBootTest
@ActiveProfiles("test")
@Import(AuthApplicationTests.RedisStub.class)
class AuthApplicationTests {

	@TestConfiguration
	static class RedisStub {

		@Bean
		StringRedisTemplate stringRedisTemplate() {
			return mock(StringRedisTemplate.class);
		}
	}

	@Test
	void contextLoads() {
	}
}
