package org.scriptonbasestar.cache.metrics.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;
import org.scriptonbasestar.cache.collection.metrics.CacheMetrics;

import static org.junit.Assert.*;

/**
 * MicrometerMetricsAdapter 테스트
 *
 * @author archmagece
 * @since 2025-01
 */
public class MicrometerMetricsAdapterTest {

	private CacheMetrics cacheMetrics;
	private MeterRegistry meterRegistry;
	private MicrometerMetricsAdapter adapter;

	@Before
	public void setUp() {
		cacheMetrics = new CacheMetrics();
		meterRegistry = new SimpleMeterRegistry();
		adapter = new MicrometerMetricsAdapter(cacheMetrics, meterRegistry, "test-cache");
	}

	@Test
	public void testRecordHit() {
		// When
		adapter.recordHit();
		adapter.recordHit();

		// Then
		assertEquals(2, cacheMetrics.hitCount());
		assertEquals(2.0, meterRegistry.counter("cache.hits", "cache", "test-cache").count(), 0.001);
	}

	@Test
	public void testRecordMiss() {
		// When
		adapter.recordMiss();
		adapter.recordMiss();
		adapter.recordMiss();

		// Then
		assertEquals(3, cacheMetrics.missCount());
		assertEquals(3.0, meterRegistry.counter("cache.misses", "cache", "test-cache").count(), 0.001);
	}

	@Test
	public void testRecordLoadSuccess() {
		// When
		adapter.recordLoadSuccess(1000000L);  // 1ms
		adapter.recordLoadSuccess(2000000L);  // 2ms

		// Then
		assertEquals(2, cacheMetrics.loadSuccessCount());
		assertEquals(2.0,
			meterRegistry.counter("cache.loads", "cache", "test-cache", "result", "success").count(),
			0.001
		);
		assertTrue(meterRegistry.timer("cache.load.duration", "cache", "test-cache").count() == 2);
	}

	@Test
	public void testRecordLoadFailure() {
		// When
		adapter.recordLoadFailure();

		// Then
		assertEquals(1, cacheMetrics.loadFailureCount());
		assertEquals(1.0,
			meterRegistry.counter("cache.loads", "cache", "test-cache", "result", "failure").count(),
			0.001
		);
	}

	@Test
	public void testRecordEviction() {
		// When
		adapter.recordEviction(5);
		adapter.recordEviction(3);

		// Then
		assertEquals(8, cacheMetrics.evictionCount());
		assertEquals(8.0,
			meterRegistry.counter("cache.evictions", "cache", "test-cache").count(),
			0.001
		);
	}

	@Test
	public void testRecordSize() {
		// When
		adapter.recordSize(100);
		adapter.recordSize(200);
		adapter.recordSize(150);

		// Then
		assertEquals(3,
			meterRegistry.summary("cache.size", "cache", "test-cache").count()
		);
	}

	@Test
	public void testHitRateGauge() {
		// When
		adapter.recordHit();
		adapter.recordHit();
		adapter.recordHit();
		adapter.recordMiss();

		// Then
		// Gauge는 cacheMetrics를 직접 참조하므로 cacheMetrics에서 값을 가져옴
		assertEquals(0.75, cacheMetrics.hitRate(), 0.001);  // 3 hits / 4 requests = 75%
	}

	@Test
	public void testMissRateGauge() {
		// When
		adapter.recordHit();
		adapter.recordMiss();
		adapter.recordMiss();
		adapter.recordMiss();

		// Then
		assertEquals(0.75, cacheMetrics.missRate(), 0.001);  // 3 misses / 4 requests = 75%
	}

	@Test
	public void testRequestCountGauge() {
		// When
		adapter.recordHit();
		adapter.recordMiss();
		adapter.recordHit();

		// Then
		assertEquals(3, cacheMetrics.requestCount());
	}

	@Test
	public void testGetCacheName() {
		// Then
		assertEquals("test-cache", adapter.getCacheName());
	}

	@Test
	public void testGetCacheMetrics() {
		// Then
		assertSame(cacheMetrics, adapter.getCacheMetrics());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullCacheMetrics() {
		new MicrometerMetricsAdapter(null, meterRegistry, "test");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullMeterRegistry() {
		new MicrometerMetricsAdapter(cacheMetrics, null, "test");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullCacheName() {
		new MicrometerMetricsAdapter(cacheMetrics, meterRegistry, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testEmptyCacheName() {
		new MicrometerMetricsAdapter(cacheMetrics, meterRegistry, "");
	}

	@Test
	public void testMultipleCaches() {
		// Given
		MicrometerMetricsAdapter adapter1 = new MicrometerMetricsAdapter(
			new CacheMetrics(), meterRegistry, "cache1"
		);
		MicrometerMetricsAdapter adapter2 = new MicrometerMetricsAdapter(
			new CacheMetrics(), meterRegistry, "cache2"
		);

		// When
		adapter1.recordHit();
		adapter1.recordHit();
		adapter2.recordHit();

		// Then
		assertEquals(2.0, meterRegistry.counter("cache.hits", "cache", "cache1").count(), 0.001);
		assertEquals(1.0, meterRegistry.counter("cache.hits", "cache", "cache2").count(), 0.001);
	}
}
