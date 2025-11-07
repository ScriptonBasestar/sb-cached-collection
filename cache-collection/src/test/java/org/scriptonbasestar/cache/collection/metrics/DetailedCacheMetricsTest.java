package org.scriptonbasestar.cache.collection.metrics;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * DetailedCacheMetrics 테스트
 *
 * @author archmagece
 * @since 2025-01
 */
public class DetailedCacheMetricsTest {

	private DetailedCacheMetrics metrics;

	@Before
	public void setUp() {
		metrics = new DetailedCacheMetrics();
	}

	@Test
	public void testBasicMetrics() {
		// Given
		metrics.recordHit();
		metrics.recordMiss();
		metrics.recordLoadSuccess(1000000L);  // 1ms

		// Then
		assertEquals(1, metrics.hitCount());
		assertEquals(1, metrics.missCount());
		assertEquals(2, metrics.requestCount());
		assertEquals(0.5, metrics.hitRate(), 0.001);
	}

	@Test
	public void testAccessTimeTracking() {
		// Given
		metrics.recordAccessTime(500000L);  // 0.5ms
		metrics.recordAccessTime(1500000L); // 1.5ms
		metrics.recordHit();
		metrics.recordHit();

		// Then
		assertEquals(1000000.0, metrics.averageAccessTime(), 0.1);  // (0.5 + 1.5) / 2 = 1.0ms
	}

	@Test
	public void testLoadTimeStats() {
		// Given
		metrics.recordLoadSuccess(1000000L);   // 1ms
		metrics.recordLoadSuccess(5000000L);   // 5ms
		metrics.recordLoadSuccess(3000000L);   // 3ms

		// Then
		assertEquals(5000000L, metrics.maxLoadTime());
		assertEquals(1000000L, metrics.minLoadTime());
		assertEquals(3000000.0, metrics.averageLoadPenalty(), 0.1);  // (1+5+3)/3 = 3ms
	}

	@Test
	public void testPutAndRemoveCount() {
		// Given
		metrics.recordPut();
		metrics.recordPut();
		metrics.recordRemove();

		// Then
		assertEquals(2, metrics.putCount());
		assertEquals(1, metrics.removeCount());
	}

	@Test
	public void testLastAccessTime() throws InterruptedException {
		// Given
		long before = System.currentTimeMillis();
		Thread.sleep(10);
		metrics.recordHit();
		long after = System.currentTimeMillis();

		// Then
		long lastAccess = metrics.lastAccessTime();
		assertTrue(lastAccess >= before);
		assertTrue(lastAccess <= after);
		assertNotNull(metrics.lastAccessInstant());
	}

	@Test
	public void testUptimeAndRequestRate() throws InterruptedException {
		// Given
		Thread.sleep(100);  // 100ms 대기
		metrics.recordHit();
		metrics.recordHit();
		metrics.recordMiss();

		// Then
		assertTrue(metrics.uptimeMillis() >= 100);
		assertTrue(metrics.requestsPerMinute() > 0);
		assertTrue(metrics.requestsPerHour() > 0);
	}

	@Test
	public void testReset() {
		// Given
		metrics.recordHit();
		metrics.recordMiss();
		metrics.recordLoadSuccess(1000000L);
		metrics.recordPut();
		metrics.recordRemove();

		// When
		metrics.reset();

		// Then
		assertEquals(0, metrics.hitCount());
		assertEquals(0, metrics.missCount());
		assertEquals(0, metrics.loadSuccessCount());
		assertEquals(0, metrics.putCount());
		assertEquals(0, metrics.removeCount());
		assertEquals(0, metrics.maxLoadTime());
		assertEquals(0, metrics.minLoadTime());
	}

	@Test
	public void testToString() {
		// Given
		metrics.recordHit();
		metrics.recordMiss();
		metrics.recordLoadSuccess(1000000L);

		// When
		String str = metrics.toString();

		// Then
		assertNotNull(str);
		assertTrue(str.contains("DetailedCacheMetrics"));
		assertTrue(str.contains("requests=2"));
		assertTrue(str.contains("hits=1"));
	}

	@Test
	public void testMaxLoadTimeThreadSafety() throws InterruptedException {
		// Given
		Thread t1 = new Thread(() -> metrics.recordLoadSuccess(1000000L));
		Thread t2 = new Thread(() -> metrics.recordLoadSuccess(5000000L));
		Thread t3 = new Thread(() -> metrics.recordLoadSuccess(3000000L));

		// When
		t1.start();
		t2.start();
		t3.start();
		t1.join();
		t2.join();
		t3.join();

		// Then
		assertEquals(5000000L, metrics.maxLoadTime());
		assertEquals(1000000L, metrics.minLoadTime());
	}

	@Test
	public void testNoLoadsScenario() {
		// When - 로드가 하나도 없을 때
		// Then
		assertEquals(0, metrics.maxLoadTime());
		assertEquals(0, metrics.minLoadTime());
		assertEquals(0.0, metrics.averageLoadPenalty(), 0.001);
	}

	@Test
	public void testLastAccessTimeUpdates() {
		// Given
		long time1 = metrics.lastAccessTime();
		assertEquals(0, time1);

		// When
		metrics.recordPut();
		long time2 = metrics.lastAccessTime();

		metrics.recordRemove();
		long time3 = metrics.lastAccessTime();

		// Then
		assertTrue(time2 > time1);
		assertTrue(time3 >= time2);
	}
}
