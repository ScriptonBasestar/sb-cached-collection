package org.scriptonbasestar.cache.collection.map;

import org.junit.Test;
import org.scriptonbasestar.cache.collection.metrics.CacheMetrics;
import org.scriptonbasestar.cache.core.loader.SBCacheMapLoader;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Phase 6 기능 테스트: Metrics, maxSize, 항목별 TTL, 워밍업
 *
 * @author archmagece
 * @since 2025-01
 */
public class SBCacheMapPhase6Test {

	/**
	 * CacheMetrics 기본 동작 테스트
	 */
	@Test
	public void testMetricsBasic() {
		AtomicInteger loadCount = new AtomicInteger(0);

		SBCacheMapLoader<Integer, String> loader = new SBCacheMapLoader<Integer, String>() {
			@Override
			public String loadOne(Integer key) throws SBCacheLoadFailException {
				loadCount.incrementAndGet();
				return "value-" + key;
			}
		};

		try (SBCacheMap<Integer, String> cache = SBCacheMap.<Integer, String>builder()
				.loader(loader)
				.timeoutSec(60)
				.enableMetrics(true)
				.build()) {

			CacheMetrics metrics = cache.metrics();
			assertNotNull("Metrics should be enabled", metrics);

			// 초기 상태
			assertEquals(0, metrics.hitCount());
			assertEquals(0, metrics.missCount());
			assertEquals(0.0, metrics.hitRate(), 0.01);

			// 첫 번째 조회 (미스)
			String val1 = cache.get(1);
			assertEquals("value-1", val1);
			assertEquals(0, metrics.hitCount());
			assertEquals(1, metrics.missCount());
			assertEquals(1, metrics.loadSuccessCount());

			// 두 번째 조회 (히트)
			String val2 = cache.get(1);
			assertEquals("value-1", val2);
			assertEquals(1, metrics.hitCount());
			assertEquals(1, metrics.missCount());
			assertEquals(0.5, metrics.hitRate(), 0.01);

			// 세 번째 조회 (히트)
			cache.get(1);
			assertEquals(2, metrics.hitCount());
			assertEquals(1, metrics.missCount());
			assertEquals(0.666, metrics.hitRate(), 0.01);

			// 다른 키 조회 (미스)
			cache.get(2);
			assertEquals(2, metrics.hitCount());
			assertEquals(2, metrics.missCount());
			assertEquals(0.5, metrics.hitRate(), 0.01);

			System.out.println("Metrics: " + metrics);
		}
	}

	/**
	 * maxSize와 LRU 제거 테스트
	 */
	@Test
	public void testMaxSizeAndLRU() throws InterruptedException {
		SBCacheMapLoader<Integer, String> loader = new SBCacheMapLoader<Integer, String>() {
			@Override
			public String loadOne(Integer key) throws SBCacheLoadFailException {
				return "value-" + key;
			}
		};

		try (SBCacheMap<Integer, String> cache = SBCacheMap.<Integer, String>builder()
				.loader(loader)
				.timeoutSec(60)
				.maxSize(3)  // 최대 3개만 유지
				.enableMetrics(true)
				.build()) {

			// 3개 추가
			cache.put(1, "v1");
			cache.put(2, "v2");
			cache.put(3, "v3");
			assertEquals(3, cache.size());

			// 4번째 추가 시 가장 오래된 항목 제거
			cache.put(4, "v4");
			assertEquals(3, cache.size());

			// 1번이 제거되었는지 확인 (재로드 필요)
			String reloaded = cache.get(1);
			assertEquals("value-1", reloaded);  // 재로드됨

			// 메트릭 확인
			CacheMetrics metrics = cache.metrics();
			assertTrue("Eviction should have occurred", metrics.evictionCount() > 0);

			System.out.println("Eviction count: " + metrics.evictionCount());
		}
	}

	/**
	 * 항목별 TTL 설정 테스트
	 */
	@Test
	public void testPerItemTTL() throws InterruptedException {
		AtomicInteger loadCount = new AtomicInteger(0);

		SBCacheMapLoader<String, String> loader = new SBCacheMapLoader<String, String>() {
			@Override
			public String loadOne(String key) throws SBCacheLoadFailException {
				loadCount.incrementAndGet();
				return "loaded-" + key + "-" + loadCount.get();
			}
		};

		try (SBCacheMap<String, String> cache = new SBCacheMap<>(loader, 60)) {

			// 일반 항목: 60초 TTL
			cache.put("long", "long-value");

			// 짧은 TTL: 1초
			cache.put("short", "short-value", 1);

			// 즉시 조회 (둘 다 캐시됨)
			assertEquals("long-value", cache.get("long"));
			assertEquals("short-value", cache.get("short"));
			assertEquals(0, loadCount.get());  // 로드 없음

			// 1.5초 대기
			Thread.sleep(1500);

			// 짧은 TTL 항목은 만료되어 재로드
			String shortReloaded = cache.get("short");
			assertEquals("loaded-short-1", shortReloaded);
			assertEquals(1, loadCount.get());

			// 긴 TTL 항목은 여전히 캐시됨
			assertEquals("long-value", cache.get("long"));
			assertEquals(1, loadCount.get());  // 추가 로드 없음
		}
	}

	/**
	 * 캐시 워밍업 테스트
	 */
	@Test
	public void testWarmUpAll() throws SBCacheLoadFailException {
		SBCacheMapLoader<Integer, String> loader = new SBCacheMapLoader<Integer, String>() {
			@Override
			public String loadOne(Integer key) throws SBCacheLoadFailException {
				return "value-" + key;
			}

			@Override
			public Map<Integer, String> loadAll() throws SBCacheLoadFailException {
				Map<Integer, String> all = new HashMap<>();
				all.put(1, "v1");
				all.put(2, "v2");
				all.put(3, "v3");
				return all;
			}
		};

		try (SBCacheMap<Integer, String> cache = SBCacheMap.<Integer, String>builder()
				.loader(loader)
				.timeoutSec(60)
				.enableMetrics(true)
				.build()) {

			CacheMetrics metrics = cache.metrics();

			// 워밍업 전: 캐시 비어있음
			assertEquals(0, cache.size());

			// 워밍업 실행
			cache.warmUp();

			// 워밍업 후: 3개 항목 로드됨
			assertEquals(3, cache.size());

			// 조회 시 모두 히트 (로드 불필요)
			assertEquals("v1", cache.get(1));
			assertEquals("v2", cache.get(2));
			assertEquals("v3", cache.get(3));

			// 모두 히트여야 함
			assertEquals(3, metrics.hitCount());
			assertEquals(0, metrics.missCount());
			assertEquals(1.0, metrics.hitRate(), 0.01);
		}
	}

	/**
	 * 선택적 워밍업 테스트
	 */
	@Test
	public void testWarmUpSelectedKeys() {
		AtomicInteger loadCount = new AtomicInteger(0);

		SBCacheMapLoader<Integer, String> loader = new SBCacheMapLoader<Integer, String>() {
			@Override
			public String loadOne(Integer key) throws SBCacheLoadFailException {
				loadCount.incrementAndGet();
				return "value-" + key;
			}
		};

		try (SBCacheMap<Integer, String> cache = new SBCacheMap<>(loader, 60)) {

			// 특정 키만 워밍업
			cache.warmUp(Arrays.asList(10, 20, 30));

			// 3번 로드되었는지 확인
			assertEquals(3, loadCount.get());
			assertEquals(3, cache.size());

			// 워밍업된 키는 히트
			assertEquals("value-10", cache.get(10));
			assertEquals(3, loadCount.get());  // 추가 로드 없음

			// 워밍업 안 된 키는 미스
			assertEquals("value-40", cache.get(40));
			assertEquals(4, loadCount.get());  // 로드됨
		}
	}

	/**
	 * 통계 리셋 테스트
	 */
	@Test
	public void testMetricsReset() {
		SBCacheMapLoader<Integer, String> loader = key -> "value-" + key;

		try (SBCacheMap<Integer, String> cache = SBCacheMap.<Integer, String>builder()
				.loader(loader)
				.timeoutSec(60)
				.enableMetrics(true)
				.build()) {

			CacheMetrics metrics = cache.metrics();

			// 몇 번 조회
			cache.get(1);  // miss
			cache.get(1);  // hit
			cache.get(2);  // miss

			assertEquals(1, metrics.hitCount());
			assertEquals(2, metrics.missCount());

			// 리셋
			metrics.reset();

			assertEquals(0, metrics.hitCount());
			assertEquals(0, metrics.missCount());
			assertEquals(0.0, metrics.hitRate(), 0.01);
		}
	}

	/**
	 * Metrics 비활성화 테스트
	 */
	@Test
	public void testMetricsDisabled() {
		SBCacheMapLoader<Integer, String> loader = key -> "value-" + key;

		try (SBCacheMap<Integer, String> cache = SBCacheMap.<Integer, String>builder()
				.loader(loader)
				.timeoutSec(60)
				.enableMetrics(false)  // 비활성화
				.build()) {

			// Metrics가 null이어야 함
			assertNull("Metrics should be null when disabled", cache.metrics());

			// 정상 동작은 해야 함
			assertEquals("value-1", cache.get(1));
			assertEquals("value-1", cache.get(1));
		}
	}

	/**
	 * 모든 기능 통합 테스트
	 */
	@Test
	public void testAllFeaturesIntegrated() throws InterruptedException {
		AtomicInteger loadCount = new AtomicInteger(0);

		SBCacheMapLoader<Integer, String> loader = new SBCacheMapLoader<Integer, String>() {
			@Override
			public String loadOne(Integer key) throws SBCacheLoadFailException {
				loadCount.incrementAndGet();
				try {
					Thread.sleep(10);  // 로드 시간 시뮬레이션
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return "value-" + key;
			}

			@Override
			public Map<Integer, String> loadAll() throws SBCacheLoadFailException {
				Map<Integer, String> all = new HashMap<>();
				all.put(1, "v1");
				all.put(2, "v2");
				return all;
			}
		};

		try (SBCacheMap<Integer, String> cache = SBCacheMap.<Integer, String>builder()
				.loader(loader)
				.timeoutSec(60)
				.forcedTimeoutSec(120)
				.maxSize(5)
				.enableMetrics(true)
				.enableAutoCleanup(true)
				.cleanupIntervalMinutes(1)
				.build()) {

			// 워밍업
			cache.warmUp();
			assertEquals(2, cache.size());

			// 항목별 TTL로 추가
			cache.put(10, "v10", 30);
			cache.put(20, "v20", 60);

			// 통계 확인
			CacheMetrics metrics = cache.metrics();
			assertNotNull(metrics);

			// 조회
			cache.get(1);  // hit
			cache.get(10); // hit
			cache.get(99); // miss (로드)

			// 통계 검증
			assertTrue(metrics.hitCount() > 0);
			assertTrue(metrics.missCount() > 0);
			assertTrue(metrics.averageLoadPenalty() > 0);

			System.out.println("=== Integrated Test Stats ===");
			System.out.println(metrics);
			System.out.println("Cache size: " + cache.size());
			System.out.println("Total loads: " + loadCount.get());
		}
	}
}
