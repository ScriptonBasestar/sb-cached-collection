package org.scriptonbasestar.cache.spring.actuator;

import org.junit.Before;
import org.junit.Test;
import org.scriptonbasestar.cache.collection.metrics.CacheHealthCheck;
import org.scriptonbasestar.cache.collection.metrics.CacheMetrics;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.Assert.*;

/**
 * CacheHealthIndicator 테스트
 *
 * @author archmagece
 * @since 2025-01
 */
public class CacheHealthIndicatorTest {

	private CacheMetrics metrics;
	private CacheHealthIndicator indicator;

	@Before
	public void setUp() {
		metrics = new CacheMetrics();
		indicator = new CacheHealthIndicator("test-cache", metrics);
	}

	@Test
	public void testHealthyCache() {
		// Given - 건강한 캐시 (70% 히트율)
		for (int i = 0; i < 70; i++) {
			metrics.recordHit();
		}
		for (int i = 0; i < 30; i++) {
			metrics.recordMiss();
			metrics.recordLoadSuccess(10_000_000L);  // 10ms
		}

		// When
		Health health = indicator.health();

		// Then
		assertEquals(Status.UP, health.getStatus());
		assertEquals("test-cache", health.getDetails().get("cacheName"));
		assertEquals(100L, health.getDetails().get("requestCount"));
		assertEquals(70L, health.getDetails().get("hitCount"));
		assertEquals("70.00%", health.getDetails().get("hitRate"));
	}

	@Test
	public void testUnhealthyCache() {
		// Given - 낮은 히트율 (30%)
		for (int i = 0; i < 30; i++) {
			metrics.recordHit();
		}
		for (int i = 0; i < 70; i++) {
			metrics.recordMiss();
			metrics.recordLoadSuccess(10_000_000L);
		}

		// When
		Health health = indicator.health();

		// Then
		assertEquals(Status.UP, health.getStatus());  // 경고만 있고 에러는 없으므로 UP
		assertNotNull(health.getDetails().get("warnings"));
	}

	@Test
	public void testUnhealthyCacheWithFailures() {
		// Given - 높은 로드 실패율 (20%)
		for (int i = 0; i < 10; i++) {
			metrics.recordMiss();
			metrics.recordLoadSuccess(10_000_000L);
		}
		for (int i = 0; i < 40; i++) {
			metrics.recordMiss();
			metrics.recordLoadFailure();
		}

		// When
		Health health = indicator.health();

		// Then
		assertEquals(Status.DOWN, health.getStatus());
		assertNotNull(health.getDetails().get("errors"));
	}

	@Test
	public void testHealthDetails() {
		// Given
		metrics.recordHit();
		metrics.recordHit();
		metrics.recordMiss();
		metrics.recordLoadSuccess(5_000_000L);  // 5ms
		metrics.recordEviction(3);

		// When
		Health health = indicator.health();

		// Then
		assertEquals(3L, health.getDetails().get("requestCount"));
		assertEquals(2L, health.getDetails().get("hitCount"));
		assertEquals(1L, health.getDetails().get("missCount"));
		assertEquals(1L, health.getDetails().get("loadSuccessCount"));
		assertEquals(0L, health.getDetails().get("loadFailureCount"));
		assertEquals(3L, health.getDetails().get("evictionCount"));
		assertNotNull(health.getDetails().get("averageLoadTime"));
	}

	@Test
	public void testIsHealthy() {
		// Given - 건강한 캐시
		for (int i = 0; i < 80; i++) {
			metrics.recordHit();
		}
		for (int i = 0; i < 20; i++) {
			metrics.recordMiss();
			metrics.recordLoadSuccess(10_000_000L);
		}

		// Then
		assertTrue(indicator.isHealthy());
	}

	@Test
	public void testGetCacheName() {
		assertEquals("test-cache", indicator.getCacheName());
	}

	@Test
	public void testGetMetrics() {
		assertSame(metrics, indicator.getMetrics());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullCacheName() {
		new CacheHealthIndicator(null, metrics);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testEmptyCacheName() {
		new CacheHealthIndicator("", metrics);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullMetrics() {
		new CacheHealthIndicator("test", null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullThresholds() {
		new CacheHealthIndicator("test", metrics, null);
	}

	@Test
	public void testCustomThresholds() {
		// Given - STRICT 임계값
		CacheHealthIndicator strictIndicator = new CacheHealthIndicator(
			"test", metrics, CacheHealthCheck.HealthThresholds.STRICT
		);

		// 히트율 70% (STRICT는 80% 이상 요구)
		for (int i = 0; i < 70; i++) {
			metrics.recordHit();
		}
		for (int i = 0; i < 30; i++) {
			metrics.recordMiss();
			metrics.recordLoadSuccess(10_000_000L);
		}

		// When
		Health health = strictIndicator.health();

		// Then - 경고 발생
		assertEquals(Status.UP, health.getStatus());
		assertNotNull(health.getDetails().get("warnings"));
	}

	@Test
	public void testNoRequestsScenario() {
		// When - 요청이 하나도 없을 때
		Health health = indicator.health();

		// Then
		assertEquals(Status.UP, health.getStatus());
		assertEquals(0L, health.getDetails().get("requestCount"));
		assertNotNull(health.getDetails().get("info"));
	}

	@Test
	public void testInfoMessages() {
		// Given - 낮은 요청 수
		for (int i = 0; i < 5; i++) {
			metrics.recordHit();
		}

		// When
		Health health = indicator.health();

		// Then
		assertNotNull(health.getDetails().get("info"));
	}
}
