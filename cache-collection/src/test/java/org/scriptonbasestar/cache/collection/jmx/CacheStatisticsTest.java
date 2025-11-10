package org.scriptonbasestar.cache.collection.jmx;

import org.junit.Before;
import org.junit.Test;
import org.scriptonbasestar.cache.collection.metrics.CacheMetrics;

import static org.junit.Assert.*;

/**
 * CacheStatistics 테스트
 *
 * @author archmagece
 * @since 2025-01
 */
public class CacheStatisticsTest {

	private CacheMetrics metrics;
	private CacheStatistics statistics;

	@Before
	public void setUp() {
		metrics = new CacheMetrics();
		statistics = new CacheStatistics(metrics, "test-cache");
	}

	@Test
	public void testGetCacheName() {
		assertEquals("test-cache", statistics.getCacheName());
	}

	@Test
	public void testBasicCounts() {
		// Given
		metrics.recordHit();
		metrics.recordHit();
		metrics.recordMiss();

		// Then
		assertEquals(3, statistics.getRequestCount());
		assertEquals(2, statistics.getHitCount());
		assertEquals(1, statistics.getMissCount());
	}

	@Test
	public void testHitRatePercent() {
		// Given
		metrics.recordHit();
		metrics.recordHit();
		metrics.recordHit();
		metrics.recordMiss();

		// Then
		assertEquals(75.0, statistics.getHitRatePercent(), 0.1);
		assertEquals(25.0, statistics.getMissRatePercent(), 0.1);
	}

	@Test
	public void testLoadMetrics() {
		// Given
		metrics.recordLoadSuccess(1_000_000L);  // 1ms
		metrics.recordLoadSuccess(3_000_000L);  // 3ms
		metrics.recordLoadSuccess(5_000_000L);  // 5ms

		// Then
		assertEquals(3, statistics.getLoadSuccessCount());
		assertEquals(3.0, statistics.getAverageLoadTimeMillis(), 0.1);
		assertEquals(9.0, statistics.getTotalLoadTimeMillis(), 0.1);  // (1+3+5) = 9ms
	}

	@Test
	public void testLoadFailures() {
		// Given
		metrics.recordLoadFailure();
		metrics.recordLoadFailure();

		// Then
		assertEquals(2, statistics.getLoadFailureCount());
	}

	@Test
	public void testEvictionCount() {
		// Given
		metrics.recordEviction(5);
		metrics.recordEviction(3);

		// Then
		assertEquals(8, statistics.getEvictionCount());
	}

	@Test
	public void testSizeTracking() {
		// Given
		statistics.setMaxSize(100);
		statistics.updateCurrentSize(75);

		// Then
		assertEquals(75, statistics.getCurrentSize());
		assertEquals(100, statistics.getMaxSize());
		assertEquals(75.0, statistics.getFillPercent(), 0.1);
	}

	@Test
	public void testSizeNotAvailable() {
		// When - 기본값은 -1
		// Then
		assertEquals(-1, statistics.getCurrentSize());
		assertEquals(-1, statistics.getMaxSize());
		assertEquals(-1.0, statistics.getFillPercent(), 0.1);
	}

	@Test
	public void testResetStatistics() {
		// Given
		metrics.recordHit();
		metrics.recordMiss();
		metrics.recordLoadSuccess(1000000L);

		// When
		statistics.resetStatistics();

		// Then
		assertEquals(0, statistics.getRequestCount());
		assertEquals(0, statistics.getHitCount());
		assertEquals(0, statistics.getMissCount());
		assertEquals(0, statistics.getLoadSuccessCount());
	}

	@Test
	public void testStatisticsSummary() {
		// Given
		metrics.recordHit();
		metrics.recordHit();
		metrics.recordMiss();
		metrics.recordLoadSuccess(2_000_000L);
		statistics.setMaxSize(100);
		statistics.updateCurrentSize(50);

		// When
		String summary = statistics.getStatisticsSummary();

		// Then
		assertNotNull(summary);
		assertTrue(summary.contains("test-cache"));
		assertTrue(summary.contains("Requests: 3"));
		assertTrue(summary.contains("Hits: 2"));
		assertTrue(summary.contains("Misses: 1"));
		assertTrue(summary.contains("50 / 100"));
		assertTrue(summary.contains("50.0%"));
	}

	@Test
	public void testToString() {
		// Given
		metrics.recordHit();
		metrics.recordHit();
		metrics.recordMiss();

		// When
		String str = statistics.toString();

		// Then
		assertNotNull(str);
		assertTrue(str.contains("CacheStatistics"));
		assertTrue(str.contains("test-cache"));
		assertTrue(str.contains("requests=3"));
		assertTrue(str.contains("hits=2"));
	}

	@Test
	public void testGetMetrics() {
		assertSame(metrics, statistics.getMetrics());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullMetrics() {
		new CacheStatistics(null, "test");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullCacheName() {
		new CacheStatistics(metrics, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testEmptyCacheName() {
		new CacheStatistics(metrics, "");
	}

	@Test
	public void testSizeUpdateAfterMetricsChange() {
		// Given
		statistics.setMaxSize(100);

		// When
		for (int i = 0; i < 10; i++) {
			statistics.updateCurrentSize(i * 10);
		}

		// Then
		assertEquals(90, statistics.getCurrentSize());
		assertEquals(90.0, statistics.getFillPercent(), 0.1);
	}

	@Test
	public void testZeroMaxSize() {
		// Given
		statistics.setMaxSize(0);
		statistics.updateCurrentSize(50);

		// Then
		assertEquals(-1.0, statistics.getFillPercent(), 0.1);
	}
}
