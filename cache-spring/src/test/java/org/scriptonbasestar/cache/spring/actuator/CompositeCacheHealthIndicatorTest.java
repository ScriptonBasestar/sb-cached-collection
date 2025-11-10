package org.scriptonbasestar.cache.spring.actuator;

import org.junit.Before;
import org.junit.Test;
import org.scriptonbasestar.cache.collection.metrics.CacheMetrics;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * CompositeCacheHealthIndicator 테스트
 *
 * @author archmagece
 * @since 2025-01
 */
public class CompositeCacheHealthIndicatorTest {

	private Map<String, CacheMetrics> metricsMap;
	private CompositeCacheHealthIndicator indicator;

	@Before
	public void setUp() {
		metricsMap = new HashMap<>();
		metricsMap.put("cache1", new CacheMetrics());
		metricsMap.put("cache2", new CacheMetrics());
		metricsMap.put("cache3", new CacheMetrics());

		indicator = new CompositeCacheHealthIndicator(metricsMap);
	}

	@Test
	public void testAllCachesHealthy() {
		// Given - 모든 캐시가 건강함
		for (CacheMetrics metrics : metricsMap.values()) {
			for (int i = 0; i < 80; i++) {
				metrics.recordHit();
			}
			for (int i = 0; i < 20; i++) {
				metrics.recordMiss();
				metrics.recordLoadSuccess(10_000_000L);
			}
		}

		// When
		Health health = indicator.health();

		// Then
		assertEquals(Status.UP, health.getStatus());
		assertEquals(3, health.getDetails().get("totalCaches"));
		assertEquals(3, health.getDetails().get("healthyCaches"));
		assertEquals(0, health.getDetails().get("unhealthyCaches"));
		assertEquals(300L, health.getDetails().get("totalRequests"));
	}

	@Test
	public void testSomeCachesUnhealthy() {
		// Given - cache1과 cache2는 건강, cache3는 불건강
		CacheMetrics metrics1 = metricsMap.get("cache1");
		for (int i = 0; i < 80; i++) metrics1.recordHit();
		for (int i = 0; i < 20; i++) {
			metrics1.recordMiss();
			metrics1.recordLoadSuccess(10_000_000L);
		}

		CacheMetrics metrics2 = metricsMap.get("cache2");
		for (int i = 0; i < 80; i++) metrics2.recordHit();
		for (int i = 0; i < 20; i++) {
			metrics2.recordMiss();
			metrics2.recordLoadSuccess(10_000_000L);
		}

		CacheMetrics metrics3 = metricsMap.get("cache3");
		for (int i = 0; i < 10; i++) {
			metrics3.recordMiss();
			metrics3.recordLoadSuccess(10_000_000L);
		}
		for (int i = 0; i < 40; i++) {
			metrics3.recordMiss();
			metrics3.recordLoadFailure();  // 높은 실패율
		}

		// When
		Health health = indicator.health();

		// Then
		assertEquals(Status.DOWN, health.getStatus());
		assertEquals(3, health.getDetails().get("totalCaches"));
		assertEquals(2, health.getDetails().get("healthyCaches"));
		assertEquals(1, health.getDetails().get("unhealthyCaches"));
	}

	@Test
	public void testOverallHitRate() {
		// Given
		CacheMetrics metrics1 = metricsMap.get("cache1");
		for (int i = 0; i < 90; i++) metrics1.recordHit();
		for (int i = 0; i < 10; i++) metrics1.recordMiss();

		CacheMetrics metrics2 = metricsMap.get("cache2");
		for (int i = 0; i < 80; i++) metrics2.recordHit();
		for (int i = 0; i < 20; i++) metrics2.recordMiss();

		CacheMetrics metrics3 = metricsMap.get("cache3");
		for (int i = 0; i < 70; i++) metrics3.recordHit();
		for (int i = 0; i < 30; i++) metrics3.recordMiss();

		// When
		Health health = indicator.health();

		// Then
		assertEquals(300L, health.getDetails().get("totalRequests"));
		String hitRate = (String) health.getDetails().get("overallHitRate");
		assertNotNull(hitRate);
		assertTrue(hitRate.contains("80.00%"));  // (90+80+70) / 300 = 80%
	}

	@Test
	public void testEmptyCaches() {
		// Given - 빈 캐시 맵
		CompositeCacheHealthIndicator emptyIndicator =
			new CompositeCacheHealthIndicator(new HashMap<>());

		// When
		Health health = emptyIndicator.health();

		// Then
		assertEquals(Status.UP, health.getStatus());
		assertEquals(0, health.getDetails().get("totalCaches"));
		assertNotNull(health.getDetails().get("message"));
	}

	@Test
	public void testAddCache() {
		// Given
		CacheMetrics newMetrics = new CacheMetrics();
		for (int i = 0; i < 50; i++) newMetrics.recordHit();
		for (int i = 0; i < 50; i++) newMetrics.recordMiss();

		// When
		indicator.addCache("cache4", newMetrics);

		// Then
		assertEquals(4, indicator.getCacheCount());
		Health health = indicator.health();
		assertEquals(4, health.getDetails().get("totalCaches"));
	}

	@Test
	public void testRemoveCache() {
		// When
		indicator.removeCache("cache2");

		// Then
		assertEquals(2, indicator.getCacheCount());
		Health health = indicator.health();
		assertEquals(2, health.getDetails().get("totalCaches"));
	}

	@Test
	public void testIsAllHealthy() {
		// Given - 모든 캐시 건강
		for (CacheMetrics metrics : metricsMap.values()) {
			for (int i = 0; i < 80; i++) metrics.recordHit();
			for (int i = 0; i < 20; i++) {
				metrics.recordMiss();
				metrics.recordLoadSuccess(10_000_000L);
			}
		}

		// Then
		assertTrue(indicator.isAllHealthy());
	}

	@Test
	public void testIsAllHealthyWithOneUnhealthy() {
		// Given - 하나만 불건강
		CacheMetrics metrics1 = metricsMap.get("cache1");
		for (int i = 0; i < 80; i++) metrics1.recordHit();
		for (int i = 0; i < 20; i++) {
			metrics1.recordMiss();
			metrics1.recordLoadSuccess(10_000_000L);
		}

		CacheMetrics metrics2 = metricsMap.get("cache2");
		for (int i = 0; i < 10; i++) {
			metrics2.recordMiss();
			metrics2.recordLoadSuccess(10_000_000L);
		}
		for (int i = 0; i < 40; i++) {
			metrics2.recordMiss();
			metrics2.recordLoadFailure();
		}

		// Then
		assertFalse(indicator.isAllHealthy());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullMetricsMap() {
		new CompositeCacheHealthIndicator(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testAddCacheWithNullName() {
		indicator.addCache(null, new CacheMetrics());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testAddCacheWithEmptyName() {
		indicator.addCache("", new CacheMetrics());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testAddCacheWithNullMetrics() {
		indicator.addCache("cache4", null);
	}

	@Test
	public void testCacheDetailsInHealth() {
		// Given
		for (CacheMetrics metrics : metricsMap.values()) {
			for (int i = 0; i < 50; i++) metrics.recordHit();
			for (int i = 0; i < 50; i++) {
				metrics.recordMiss();
				metrics.recordLoadSuccess(10_000_000L);
			}
		}

		// When
		Health health = indicator.health();

		// Then
		@SuppressWarnings("unchecked")
		Map<String, Health> caches = (Map<String, Health>) health.getDetails().get("caches");
		assertNotNull(caches);
		assertEquals(3, caches.size());
		assertTrue(caches.containsKey("cache1"));
		assertTrue(caches.containsKey("cache2"));
		assertTrue(caches.containsKey("cache3"));
	}

	@Test
	public void testGetCacheCount() {
		assertEquals(3, indicator.getCacheCount());
	}

	@Test
	public void testZeroRequestsInAllCaches() {
		// When - 모든 캐시가 요청 없음
		Health health = indicator.health();

		// Then
		assertEquals(Status.UP, health.getStatus());
		assertEquals(0L, health.getDetails().get("totalRequests"));
		assertNull(health.getDetails().get("overallHitRate"));  // 요청이 없으면 히트율도 없음
	}
}
