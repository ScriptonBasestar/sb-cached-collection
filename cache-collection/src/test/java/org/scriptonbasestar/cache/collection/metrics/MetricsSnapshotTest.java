package org.scriptonbasestar.cache.collection.metrics;

import org.junit.Before;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.*;

/**
 * MetricsSnapshot 테스트
 *
 * @author archmagece
 * @since 2025-01
 */
public class MetricsSnapshotTest {

	private CacheMetrics metrics;

	@Before
	public void setUp() {
		metrics = new CacheMetrics();
	}

	@Test
	public void testBasicSnapshot() {
		// Given
		metrics.recordHit();
		metrics.recordHit();
		metrics.recordMiss();
		metrics.recordLoadSuccess(1000000L);

		// When
		MetricsSnapshot snapshot = new MetricsSnapshot(metrics);

		// Then
		assertEquals(2, snapshot.hitCount());
		assertEquals(1, snapshot.missCount());
		assertEquals(3, snapshot.requestCount());
		assertEquals(1, snapshot.loadSuccessCount());
		assertTrue(snapshot.timestamp() > 0);
	}

	@Test
	public void testHitAndMissRate() {
		// Given
		for (int i = 0; i < 70; i++) {
			metrics.recordHit();
		}
		for (int i = 0; i < 30; i++) {
			metrics.recordMiss();
		}

		// When
		MetricsSnapshot snapshot = new MetricsSnapshot(metrics);

		// Then
		assertEquals(0.7, snapshot.hitRate(), 0.001);
		assertEquals(0.3, snapshot.missRate(), 0.001);
	}

	@Test
	public void testTimestamp() {
		// Given
		long before = System.currentTimeMillis();
		MetricsSnapshot snapshot = new MetricsSnapshot(metrics);
		long after = System.currentTimeMillis();

		// Then
		assertTrue(snapshot.timestamp() >= before);
		assertTrue(snapshot.timestamp() <= after);
		assertNotNull(snapshot.instant());
		assertTrue(snapshot.instant().isBefore(Instant.now().plusSeconds(1)));
	}

	@Test
	public void testEvictionAndLoadFailure() {
		// Given
		metrics.recordEviction(5);
		metrics.recordLoadFailure();
		metrics.recordLoadFailure();

		// When
		MetricsSnapshot snapshot = new MetricsSnapshot(metrics);

		// Then
		assertEquals(5, snapshot.evictionCount());
		assertEquals(2, snapshot.loadFailureCount());
	}

	@Test
	public void testAverageLoadPenalty() {
		// Given
		metrics.recordLoadSuccess(1000000L);  // 1ms
		metrics.recordLoadSuccess(3000000L);  // 3ms
		metrics.recordLoadSuccess(2000000L);  // 2ms

		// When
		MetricsSnapshot snapshot = new MetricsSnapshot(metrics);

		// Then
		assertEquals(2000000.0, snapshot.averageLoadPenalty(), 0.1);  // (1+3+2)/3 = 2ms
	}

	@Test
	public void testSnapshotDiff() throws InterruptedException {
		// Given - 첫 번째 스냅샷
		metrics.recordHit();
		metrics.recordHit();
		metrics.recordMiss();
		MetricsSnapshot snapshot1 = new MetricsSnapshot(metrics);

		Thread.sleep(10);

		// 추가 활동
		metrics.recordHit();
		metrics.recordMiss();
		metrics.recordMiss();
		MetricsSnapshot snapshot2 = new MetricsSnapshot(metrics);

		// When
		MetricsSnapshot diff = snapshot2.diff(snapshot1);

		// Then
		assertEquals(1, diff.hitCount());  // 3 - 2 = 1
		assertEquals(2, diff.missCount());  // 3 - 1 = 2
		assertEquals(3, diff.requestCount());
		assertEquals(1.0 / 3.0, diff.hitRate(), 0.001);
	}

	@Test
	public void testDiffWithNoChange() {
		// Given
		metrics.recordHit();
		MetricsSnapshot snapshot1 = new MetricsSnapshot(metrics);
		MetricsSnapshot snapshot2 = new MetricsSnapshot(metrics);

		// When
		MetricsSnapshot diff = snapshot2.diff(snapshot1);

		// Then
		assertEquals(0, diff.hitCount());
		assertEquals(0, diff.missCount());
		assertEquals(0, diff.requestCount());
	}

	@Test
	public void testToString() {
		// Given
		metrics.recordHit();
		metrics.recordMiss();
		metrics.recordLoadSuccess(1000000L);

		// When
		MetricsSnapshot snapshot = new MetricsSnapshot(metrics);
		String str = snapshot.toString();

		// Then
		assertNotNull(str);
		assertTrue(str.contains("MetricsSnapshot"));
		assertTrue(str.contains("requests=2"));
		assertTrue(str.contains("hits=1"));
	}

	@Test
	public void testToJson() {
		// Given
		metrics.recordHit();
		metrics.recordMiss();
		metrics.recordLoadSuccess(1000000L);

		// When
		MetricsSnapshot snapshot = new MetricsSnapshot(metrics);
		String json = snapshot.toJson();

		// Then
		assertNotNull(json);
		assertTrue(json.contains("\"hitCount\":1"));
		assertTrue(json.contains("\"missCount\":1"));
		assertTrue(json.contains("\"timestamp\":"));
		assertTrue(json.contains("\"hitRate\":"));
	}

	@Test
	public void testEmptyMetrics() {
		// Given - 아무 활동도 없는 메트릭
		// When
		MetricsSnapshot snapshot = new MetricsSnapshot(metrics);

		// Then
		assertEquals(0, snapshot.hitCount());
		assertEquals(0, snapshot.missCount());
		assertEquals(0, snapshot.requestCount());
		assertEquals(0.0, snapshot.hitRate(), 0.001);
		assertEquals(0.0, snapshot.missRate(), 0.001);
		assertEquals(0.0, snapshot.averageLoadPenalty(), 0.001);
	}

	@Test
	public void testSnapshotImmutability() {
		// Given
		metrics.recordHit();
		MetricsSnapshot snapshot = new MetricsSnapshot(metrics);

		// When - 스냅샷 생성 후 메트릭 변경
		metrics.recordHit();
		metrics.recordHit();

		// Then - 스냅샷은 변하지 않음
		assertEquals(1, snapshot.hitCount());
		assertEquals(3, metrics.hitCount());
	}

	@Test
	public void testMultipleSnapshots() {
		// Given
		metrics.recordHit();
		MetricsSnapshot snapshot1 = new MetricsSnapshot(metrics);

		metrics.recordHit();
		MetricsSnapshot snapshot2 = new MetricsSnapshot(metrics);

		metrics.recordHit();
		MetricsSnapshot snapshot3 = new MetricsSnapshot(metrics);

		// Then
		assertEquals(1, snapshot1.hitCount());
		assertEquals(2, snapshot2.hitCount());
		assertEquals(3, snapshot3.hitCount());

		// 타임스탬프는 증가해야 함
		assertTrue(snapshot2.timestamp() >= snapshot1.timestamp());
		assertTrue(snapshot3.timestamp() >= snapshot2.timestamp());
	}

	@Test
	public void testDiffChain() {
		// Given
		metrics.recordHit();
		MetricsSnapshot s1 = new MetricsSnapshot(metrics);

		metrics.recordHit();
		metrics.recordHit();
		MetricsSnapshot s2 = new MetricsSnapshot(metrics);

		metrics.recordHit();
		MetricsSnapshot s3 = new MetricsSnapshot(metrics);

		// When
		MetricsSnapshot diff1to2 = s2.diff(s1);
		MetricsSnapshot diff2to3 = s3.diff(s2);

		// Then
		assertEquals(2, diff1to2.hitCount());  // s1(1) -> s2(3): +2
		assertEquals(1, diff2to3.hitCount());  // s2(3) -> s3(4): +1
	}
}
