package org.scriptonbasestar.cache.collection.metrics;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * CacheHealthCheck 테스트
 *
 * @author archmagece
 * @since 2025-01
 */
public class CacheHealthCheckTest {

	private CacheMetrics metrics;
	private CacheHealthCheck healthCheck;

	@Before
	public void setUp() {
		metrics = new CacheMetrics();
		healthCheck = new CacheHealthCheck(metrics);
	}

	@Test
	public void testHealthyCache() {
		// Given - 건강한 캐시 (히트율 70%)
		for (int i = 0; i < 70; i++) {
			metrics.recordHit();
		}
		for (int i = 0; i < 30; i++) {
			metrics.recordMiss();
			metrics.recordLoadSuccess(10_000_000L);  // 10ms
		}

		// When
		CacheHealthCheck.HealthStatus status = healthCheck.check();

		// Then
		assertTrue(status.isHealthy());
		assertEquals(0, status.errors().length);
	}

	@Test
	public void testLowHitRate() {
		// Given - 낮은 히트율 (30%)
		for (int i = 0; i < 30; i++) {
			metrics.recordHit();
		}
		for (int i = 0; i < 70; i++) {
			metrics.recordMiss();
			metrics.recordLoadSuccess(10_000_000L);
		}

		// When
		CacheHealthCheck.HealthStatus status = healthCheck.check();

		// Then
		assertTrue(status.isHealthy());  // 경고만 있고 에러는 없음
		assertTrue(status.warnings().length > 0);
		assertTrue(status.warnings()[0].contains("Low hit rate"));
	}

	@Test
	public void testHighLoadFailureRate() {
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
		CacheHealthCheck.HealthStatus status = healthCheck.check();

		// Then
		assertFalse(status.isHealthy());
		assertTrue(status.errors().length > 0);
		assertTrue(status.errors()[0].contains("High load failure rate"));
	}

	@Test
	public void testHighAverageLoadTime() {
		// Given - 높은 평균 로드 시간 (150ms)
		for (int i = 0; i < 50; i++) {
			metrics.recordHit();
		}
		for (int i = 0; i < 50; i++) {
			metrics.recordMiss();
			metrics.recordLoadSuccess(150_000_000L);  // 150ms
		}

		// When
		CacheHealthCheck.HealthStatus status = healthCheck.check();

		// Then
		assertTrue(status.isHealthy());  // 경고만 있음
		assertTrue(status.warnings().length > 0);
		assertTrue(status.warnings()[0].contains("High average load time"));
	}

	@Test
	public void testLowRequestCount() {
		// Given - 낮은 요청 수 (5개)
		for (int i = 0; i < 5; i++) {
			metrics.recordHit();
		}

		// When
		CacheHealthCheck.HealthStatus status = healthCheck.check();

		// Then
		assertTrue(status.isHealthy());
		assertTrue(status.info().length > 0);
		assertTrue(status.info()[0].contains("Low request count"));
	}

	@Test
	public void testStrictThresholds() {
		// Given
		CacheHealthCheck strictCheck = new CacheHealthCheck(
			metrics,
			CacheHealthCheck.HealthThresholds.STRICT
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
		CacheHealthCheck.HealthStatus status = strictCheck.check();

		// Then
		assertTrue(status.isHealthy());  // 에러는 아니지만
		assertTrue(status.warnings().length > 0);  // 경고는 발생
	}

	@Test
	public void testRelaxedThresholds() {
		// Given
		CacheHealthCheck relaxedCheck = new CacheHealthCheck(
			metrics,
			CacheHealthCheck.HealthThresholds.RELAXED
		);

		// 히트율 40%
		for (int i = 0; i < 40; i++) {
			metrics.recordHit();
		}
		for (int i = 0; i < 60; i++) {
			metrics.recordMiss();
			metrics.recordLoadSuccess(200_000_000L);  // 200ms
		}

		// When
		CacheHealthCheck.HealthStatus status = relaxedCheck.check();

		// Then
		assertTrue(status.isHealthy());
		assertEquals(0, status.warnings().length);  // RELAXED 기준에는 문제 없음
	}

	@Test
	public void testIsHealthyShortcut() {
		// Given - 건강한 캐시
		for (int i = 0; i < 80; i++) {
			metrics.recordHit();
		}
		for (int i = 0; i < 20; i++) {
			metrics.recordMiss();
			metrics.recordLoadSuccess(10_000_000L);
		}

		// When & Then
		assertTrue(healthCheck.isHealthy());
	}

	@Test
	public void testHealthStatusToString() {
		// Given
		for (int i = 0; i < 30; i++) {
			metrics.recordHit();
		}
		for (int i = 0; i < 70; i++) {
			metrics.recordMiss();
			metrics.recordLoadSuccess(10_000_000L);
		}

		// When
		CacheHealthCheck.HealthStatus status = healthCheck.check();
		String str = status.toString();

		// Then
		assertNotNull(str);
		assertTrue(str.contains("HealthStatus"));
		assertTrue(str.contains("warnings="));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullMetrics() {
		new CacheHealthCheck(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullThresholds() {
		new CacheHealthCheck(metrics, null);
	}

	@Test
	public void testMultipleIssues() {
		// Given - 여러 문제가 동시에 발생
		// 낮은 히트율 (20%)
		for (int i = 0; i < 20; i++) {
			metrics.recordHit();
		}
		// 높은 로드 실패율과 긴 로드 시간
		for (int i = 0; i < 60; i++) {
			metrics.recordMiss();
			metrics.recordLoadSuccess(200_000_000L);  // 200ms
		}
		for (int i = 0; i < 20; i++) {
			metrics.recordMiss();
			metrics.recordLoadFailure();
		}

		// When
		CacheHealthCheck.HealthStatus status = healthCheck.check();

		// Then
		assertFalse(status.isHealthy());
		assertTrue(status.errors().length > 0);    // 로드 실패율 에러
		assertTrue(status.warnings().length > 0);  // 히트율, 로드 시간 경고
	}
}
