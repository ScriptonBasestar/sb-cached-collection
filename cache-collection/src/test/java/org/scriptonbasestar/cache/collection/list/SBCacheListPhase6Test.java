package org.scriptonbasestar.cache.collection.list;

import org.junit.Test;
import org.scriptonbasestar.cache.collection.metrics.CacheMetrics;
import org.scriptonbasestar.cache.core.loader.SBCacheListLoader;
import org.scriptonbasestar.cache.core.strategy.LoadStrategy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * SBCacheList Phase 6 기능 테스트
 *
 * @author archmagece
 * @since 2025-01
 */
public class SBCacheListPhase6Test {

	/**
	 * 기본 생성자 테스트 (하위 호환성)
	 */
	@Test
	public void testBasicConstructor() throws InterruptedException {
		AtomicInteger loadCount = new AtomicInteger(0);

		SBCacheListLoader<String> loader = new SBCacheListLoader<String>() {
			@Override
			public List<String> loadAll() {
				loadCount.incrementAndGet();
				return Arrays.asList("a", "b", "c");
			}

			@Override
			public String loadOne(int index) {
				return "item-" + index;
			}
		};

		try (SBCacheList<String> cacheList = new SBCacheList<>(loader, 1)) {  // 1초 TTL
			// 초기 로드 확인
			assertEquals(1, loadCount.get());
			assertEquals(3, cacheList.size());

			// getList() 호출 (캐시 히트)
			List<String> list1 = cacheList.getList();
			assertEquals(3, list1.size());
			assertEquals("a", list1.get(0));
			assertEquals(1, loadCount.get());  // 재로드 안 됨

			// 1.5초 대기
			Thread.sleep(1500);

			// getList() 호출 (캐시 미스, 비동기 재로드)
			List<String> list2 = cacheList.getList();
			assertEquals(3, list2.size());

			// 비동기 로드가 완료될 때까지 대기
			Thread.sleep(500);
			assertEquals(2, loadCount.get());
		}
	}

	/**
	 * Builder 패턴 기본 테스트
	 */
	@Test
	public void testBuilderPattern() {
		SBCacheListLoader<Integer> loader = new SBCacheListLoader<Integer>() {
			@Override
			public List<Integer> loadAll() {
				return Arrays.asList(1, 2, 3, 4, 5);
			}

			@Override
			public Integer loadOne(int index) {
				return index * 10;
			}
		};

		try (SBCacheList<Integer> cacheList = SBCacheList.<Integer>builder()
				.loader(loader)
				.timeoutSec(60)
				.loadStrategy(LoadStrategy.ALL)
				.build()) {

			assertEquals(5, cacheList.size());
			assertEquals(Integer.valueOf(1), cacheList.get(0));
		}
	}

	/**
	 * Metrics 기본 동작 테스트
	 */
	@Test
	public void testMetricsBasic() throws InterruptedException {
		AtomicInteger loadCount = new AtomicInteger(0);

		SBCacheListLoader<String> loader = new SBCacheListLoader<String>() {
			@Override
			public List<String> loadAll() {
				loadCount.incrementAndGet();
				return Arrays.asList("x", "y", "z");
			}

			@Override
			public String loadOne(int index) {
				return "item-" + index;
			}
		};

		try (SBCacheList<String> cacheList = SBCacheList.<String>builder()
				.loader(loader)
				.timeoutSec(1)  // 1초 TTL
				.enableMetrics(true)
				.loadStrategy(LoadStrategy.ALL)
				.build()) {

			CacheMetrics metrics = cacheList.metrics();
			assertNotNull("Metrics should be enabled", metrics);

			// 초기 상태
			assertEquals(0, metrics.hitCount());
			assertEquals(0, metrics.missCount());

			// 첫 번째 조회 (캐시 히트)
			cacheList.getList();
			assertEquals(1, metrics.hitCount());
			assertEquals(0, metrics.missCount());
			assertEquals(1.0, metrics.hitRate(), 0.01);

			// 두 번째 조회 (캐시 히트)
			cacheList.getList();
			assertEquals(2, metrics.hitCount());
			assertEquals(0, metrics.missCount());

			// 1.5초 대기 후 조회 (캐시 미스)
			Thread.sleep(1500);
			cacheList.getList();
			assertEquals(2, metrics.hitCount());
			assertEquals(1, metrics.missCount());
			assertEquals(0.666, metrics.hitRate(), 0.01);

			System.out.println("Metrics: " + metrics);
		}
	}

	/**
	 * Forced Timeout 테스트
	 */
	@Test
	public void testForcedTimeout() throws InterruptedException {
		AtomicInteger loadCount = new AtomicInteger(0);

		SBCacheListLoader<String> loader = new SBCacheListLoader<String>() {
			@Override
			public List<String> loadAll() {
				loadCount.incrementAndGet();
				return Arrays.asList("data-" + loadCount.get());
			}

			@Override
			public String loadOne(int index) {
				return "item";
			}
		};

		try (SBCacheList<String> cacheList = SBCacheList.<String>builder()
				.loader(loader)
				.timeoutSec(60)  // 접근 기반 TTL: 60초
				.forcedTimeoutSec(2)  // 절대 만료: 2초
				.loadStrategy(LoadStrategy.ALL)
				.build()) {

			// 초기 로드
			assertEquals(1, loadCount.get());

			// 즉시 조회 (캐시 히트)
			cacheList.getList();
			assertEquals(1, loadCount.get());

			// 1초 후 조회 (아직 2초 안 지남)
			Thread.sleep(1000);
			cacheList.getList();
			assertEquals(1, loadCount.get());

			// 2.5초 후 조회 (forced timeout 도달)
			Thread.sleep(1500);
			cacheList.getList();

			// 비동기 로드 대기
			Thread.sleep(500);
			assertTrue("Forced timeout should trigger reload", loadCount.get() >= 2);
		}
	}

	/**
	 * MaxSize 경고 테스트
	 */
	@Test
	public void testMaxSizeWarning() {
		SBCacheListLoader<Integer> loader = new SBCacheListLoader<Integer>() {
			@Override
			public List<Integer> loadAll() {
				List<Integer> largeList = new ArrayList<>();
				for (int i = 0; i < 1000; i++) {
					largeList.add(i);
				}
				return largeList;
			}

			@Override
			public Integer loadOne(int index) {
				return index;
			}
		};

		try (SBCacheList<Integer> cacheList = SBCacheList.<Integer>builder()
				.loader(loader)
				.timeoutSec(60)
				.maxSize(500)  // 최대 500개 (실제 1000개 로드됨)
				.build()) {

			// 1000개가 로드되지만 경고만 출력됨 (List는 제거하지 않음)
			assertEquals(1000, cacheList.size());

			// 로그에 경고 메시지가 출력되었을 것
			System.out.println("MaxSize exceeded warning test completed");
		}
	}

	/**
	 * LoadStrategy.ALL (비동기) 테스트
	 */
	@Test
	public void testLoadStrategyAll() throws InterruptedException {
		AtomicInteger loadCount = new AtomicInteger(0);

		SBCacheListLoader<String> loader = new SBCacheListLoader<String>() {
			@Override
			public List<String> loadAll() {
				int count = loadCount.incrementAndGet();
				try {
					Thread.sleep(100);  // 로드 시간 시뮬레이션
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return Arrays.asList("load-" + count);
			}

			@Override
			public String loadOne(int index) {
				return "single-" + index;
			}
		};

		try (SBCacheList<String> cacheList = SBCacheList.<String>builder()
				.loader(loader)
				.timeoutSec(1)
				.loadStrategy(LoadStrategy.ALL)
				.enableMetrics(true)
				.build()) {

			// 초기 로드
			assertEquals(1, loadCount.get());

			// 1.5초 대기 후 조회 (비동기 재로드 트리거)
			Thread.sleep(1500);
			List<String> list = cacheList.getList();

			// 비동기이므로 즉시 이전 데이터 반환
			assertNotNull(list);

			// 재로드 완료 대기
			Thread.sleep(500);
			assertEquals(2, loadCount.get());
		}
	}

	/**
	 * LoadStrategy.ONE (동기) 테스트
	 */
	@Test
	public void testLoadStrategyOne() throws InterruptedException {
		AtomicInteger loadAllCount = new AtomicInteger(0);
		AtomicInteger loadOneCount = new AtomicInteger(0);

		SBCacheListLoader<String> loader = new SBCacheListLoader<String>() {
			@Override
			public List<String> loadAll() {
				loadAllCount.incrementAndGet();
				return Arrays.asList("a", "b", "c");
			}

			@Override
			public String loadOne(int index) {
				loadOneCount.incrementAndGet();
				return "item-" + index;
			}
		};

		try (SBCacheList<String> cacheList = SBCacheList.<String>builder()
				.loader(loader)
				.timeoutSec(1)
				.loadStrategy(LoadStrategy.ONE)
				.build()) {

			// 초기 로드
			assertEquals(1, loadAllCount.get());
			assertEquals(0, loadOneCount.get());

			// 1.5초 대기 후 get(0) 호출 (LoadStrategy.ONE이므로 해당 인덱스만 갱신)
			Thread.sleep(1500);
			String value = cacheList.get(0);

			// loadOne이 호출되었는지 확인
			assertEquals("item-0", value);
			assertEquals(1, loadOneCount.get());
			assertEquals(1, loadAllCount.get());  // loadAll은 초기 한 번만
		}
	}

	/**
	 * Auto Cleanup 테스트
	 */
	@Test
	public void testAutoCleanup() throws InterruptedException {
		AtomicInteger loadCount = new AtomicInteger(0);

		SBCacheListLoader<String> loader = new SBCacheListLoader<String>() {
			@Override
			public List<String> loadAll() {
				loadCount.incrementAndGet();
				return Arrays.asList("data-" + loadCount.get());
			}

			@Override
			public String loadOne(int index) {
				return "item";
			}
		};

		try (SBCacheList<String> cacheList = SBCacheList.<String>builder()
				.loader(loader)
				.timeoutSec(2)  // 2초 TTL
				.enableAutoCleanup(true)
				.cleanupIntervalMinutes(1)  // 1분마다 (테스트용으로는 긴 시간)
				.loadStrategy(LoadStrategy.ALL)
				.build()) {

			// 초기 로드
			assertEquals(1, loadCount.get());

			// 2.5초 대기
			Thread.sleep(2500);

			// 자동 정리는 1분마다이므로 아직 실행 안 됨
			// 수동으로 getList() 호출하면 재로드
			cacheList.getList();

			Thread.sleep(500);
			assertTrue(loadCount.get() >= 2);
		}
	}

	/**
	 * Manual Refresh 테스트
	 */
	@Test
	public void testManualRefresh() throws InterruptedException {
		AtomicInteger loadCount = new AtomicInteger(0);

		SBCacheListLoader<String> loader = new SBCacheListLoader<String>() {
			@Override
			public List<String> loadAll() {
				int count = loadCount.incrementAndGet();
				return Arrays.asList("refresh-" + count);
			}

			@Override
			public String loadOne(int index) {
				return "item";
			}
		};

		try (SBCacheList<String> cacheList = SBCacheList.<String>builder()
				.loader(loader)
				.timeoutSec(60)  // 긴 TTL
				.loadStrategy(LoadStrategy.ALL)
				.build()) {

			// 초기 로드
			assertEquals(1, loadCount.get());
			List<String> list1 = cacheList.getList();
			assertEquals("refresh-1", list1.get(0));

			// 수동 갱신
			cacheList.refresh();

			// 재로드 대기
			Thread.sleep(500);
			assertEquals(2, loadCount.get());

			List<String> list2 = cacheList.getList();
			assertEquals("refresh-2", list2.get(0));
		}
	}

	/**
	 * Metrics 비활성화 테스트
	 */
	@Test
	public void testMetricsDisabled() {
		SBCacheListLoader<String> loader = new SBCacheListLoader<String>() {
			@Override
			public List<String> loadAll() {
				return Arrays.asList("a", "b", "c");
			}

			@Override
			public String loadOne(int index) {
				return "item";
			}
		};

		try (SBCacheList<String> cacheList = SBCacheList.<String>builder()
				.loader(loader)
				.timeoutSec(60)
				.enableMetrics(false)  // 비활성화
				.build()) {

			// Metrics가 null이어야 함
			assertNull("Metrics should be null when disabled", cacheList.metrics());

			// 정상 동작은 해야 함
			List<String> list = cacheList.getList();
			assertEquals(3, list.size());
		}
	}

	/**
	 * 모든 기능 통합 테스트
	 */
	@Test
	public void testAllFeaturesIntegrated() throws InterruptedException {
		AtomicInteger loadCount = new AtomicInteger(0);

		SBCacheListLoader<Integer> loader = new SBCacheListLoader<Integer>() {
			@Override
			public List<Integer> loadAll() {
				int count = loadCount.incrementAndGet();
				List<Integer> list = new ArrayList<>();
				for (int i = 0; i < 10; i++) {
					list.add(count * 100 + i);
				}
				try {
					Thread.sleep(50);  // 로드 시간 시뮬레이션
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return list;
			}

			@Override
			public Integer loadOne(int index) {
				return 999;
			}
		};

		try (SBCacheList<Integer> cacheList = SBCacheList.<Integer>builder()
				.loader(loader)
				.timeoutSec(2)
				.forcedTimeoutSec(5)
				.maxSize(100)
				.enableMetrics(true)
				.enableAutoCleanup(true)
				.cleanupIntervalMinutes(1)
				.loadStrategy(LoadStrategy.ALL)
				.build()) {

			// 초기 로드 확인
			assertEquals(1, loadCount.get());
			assertEquals(10, cacheList.size());

			CacheMetrics metrics = cacheList.metrics();
			assertNotNull(metrics);

			// 첫 조회 (히트)
			List<Integer> list1 = cacheList.getList();
			assertEquals(10, list1.size());
			assertEquals(Integer.valueOf(100), list1.get(0));
			assertEquals(1, metrics.hitCount());

			// 두 번째 조회 (히트)
			cacheList.getList();
			assertEquals(2, metrics.hitCount());

			// 2.5초 대기 후 조회 (미스, 재로드)
			Thread.sleep(2500);
			cacheList.getList();
			assertEquals(1, metrics.missCount());

			// 재로드 대기
			Thread.sleep(500);
			assertTrue(loadCount.get() >= 2);

			// 통계 출력
			System.out.println("=== Integrated Test Stats ===");
			System.out.println(metrics);
			System.out.println("Cache size: " + cacheList.size());
			System.out.println("Total loads: " + loadCount.get());
		}
	}

	/**
	 * AutoCloseable 테스트
	 */
	@Test
	public void testAutoCloseable() throws InterruptedException {
		SBCacheListLoader<String> loader = new SBCacheListLoader<String>() {
			@Override
			public List<String> loadAll() {
				return Arrays.asList("a", "b");
			}

			@Override
			public String loadOne(int index) {
				return "item";
			}
		};

		SBCacheList<String> cacheList = SBCacheList.<String>builder()
				.loader(loader)
				.timeoutSec(60)
				.build();

		// 사용
		assertEquals(2, cacheList.size());

		// 명시적 종료
		cacheList.close();

		// close()는 여러 번 호출해도 안전해야 함
		cacheList.close();

		System.out.println("AutoCloseable test completed successfully");
	}

	/**
	 * 불변 리스트 반환 테스트
	 */
	@Test(expected = UnsupportedOperationException.class)
	public void testUnmodifiableList() {
		SBCacheListLoader<String> loader = new SBCacheListLoader<String>() {
			@Override
			public List<String> loadAll() {
				return Arrays.asList("a", "b", "c");
			}

			@Override
			public String loadOne(int index) {
				return "item";
			}
		};

		try (SBCacheList<String> cacheList = new SBCacheList<>(loader, 60)) {
			List<String> list = cacheList.getList();

			// 수정 시도 시 UnsupportedOperationException 발생해야 함
			list.add("d");
		}
	}
}
