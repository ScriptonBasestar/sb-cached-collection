package org.scriptonbasestar.cache.collection.map;

import org.junit.Test;
import org.scriptonbasestar.cache.collection.strategy.LoadStrategy;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Phase 8: LoadStrategy.ASYNC 기능 테스트
 *
 * <p>ASYNC 전략은 캐시 미스 시 만료된 데이터를 즉시 반환하고 백그라운드에서 새 데이터를 로드합니다.</p>
 *
 * @author archmagece
 * @since 2025-01
 */
public class SBCacheMapAsyncTest {

	/**
	 * ASYNC 전략 기본 동작 테스트
	 * - 첫 로드는 SYNC처럼 동작 (데이터가 없으므로)
	 * - 만료 후 재조회 시 stale 데이터를 즉시 반환하고 백그라운드 갱신
	 */
	@Test
	public void testAsyncBasicBehavior() throws Exception {
		AtomicInteger loadCount = new AtomicInteger(0);
		AtomicInteger currentValue = new AtomicInteger(100);

		SBCacheMapLoader<String, Integer> loader = new SBCacheMapLoader<String, Integer>() {
			@Override
			public Integer loadOne(String key) throws SBCacheLoadFailException {
				loadCount.incrementAndGet();
				return currentValue.get();
			}
		};

		try (SBCacheMap<String, Integer> cache = SBCacheMap.<String, Integer>builder()
				.loader(loader)
				.timeoutSec(1) // 1초 TTL (테스트용)
				.loadStrategy(LoadStrategy.ASYNC)
				.build()) {

			// 1. 첫 로드: 데이터가 없으므로 SYNC처럼 동작 (블로킹)
			Integer value1 = cache.get("key1");
			assertEquals(Integer.valueOf(100), value1);
			assertEquals(1, loadCount.get());

			// 2. TTL 이내 재조회: 캐시 히트
			Integer value2 = cache.get("key1");
			assertEquals(Integer.valueOf(100), value2);
			assertEquals(1, loadCount.get()); // 로드 안 됨

			// 3. TTL 경과 대기
			Thread.sleep(1500);

			// 4. 데이터 변경 (새 로드 시 200 반환됨)
			currentValue.set(200);

			// 5. ASYNC 동작: stale 데이터(100) 즉시 반환 + 백그라운드 갱신
			Integer staleValue = cache.get("key1");
			assertEquals("Should return stale data immediately", Integer.valueOf(100), staleValue);

			// 6. 백그라운드 로드 대기
			Thread.sleep(500);

			// 7. 갱신된 데이터 확인
			Integer freshValue = cache.get("key1");
			assertEquals("Should return fresh data after background refresh", Integer.valueOf(200), freshValue);
			assertEquals(2, loadCount.get()); // 백그라운드 로드 완료
		}
	}

	/**
	 * SYNC 전략과 ASYNC 전략 비교 테스트
	 * - SYNC: 만료 시 블로킹하여 새 데이터 로드
	 * - ASYNC: 만료 시 stale 데이터 즉시 반환
	 */
	@Test
	public void testSyncVsAsync() throws Exception {
		AtomicInteger syncLoadCount = new AtomicInteger(0);
		AtomicInteger asyncLoadCount = new AtomicInteger(0);

		// 로드 시 약간의 지연 추가 (실제 데이터 로드 시뮬레이션)
		SBCacheMapLoader<String, String> slowLoader = new SBCacheMapLoader<String, String>() {
			@Override
			public String loadOne(String key) throws SBCacheLoadFailException {
				try {
					Thread.sleep(300); // 300ms 지연
				} catch (InterruptedException e) {
					throw new SBCacheLoadFailException(e);
				}
				return "loaded-" + key;
			}
		};

		// SYNC 전략 캐시
		try (SBCacheMap<String, String> syncCache = SBCacheMap.<String, String>builder()
				.loader(key -> {
					syncLoadCount.incrementAndGet();
					return slowLoader.loadOne(key);
				})
				.timeoutSec(1)
				.loadStrategy(LoadStrategy.SYNC)
				.build()) {

			// ASYNC 전략 캐시
			try (SBCacheMap<String, String> asyncCache = SBCacheMap.<String, String>builder()
					.loader(key -> {
						asyncLoadCount.incrementAndGet();
						return slowLoader.loadOne(key);
					})
					.timeoutSec(1)
					.loadStrategy(LoadStrategy.ASYNC)
					.build()) {

				// 초기 로드 (둘 다 SYNC처럼 동작)
				String syncValue1 = syncCache.get("key1");
				String asyncValue1 = asyncCache.get("key1");
				assertEquals("loaded-key1", syncValue1);
				assertEquals("loaded-key1", asyncValue1);

				// TTL 경과 대기
				Thread.sleep(1500);

				// SYNC: 블로킹 (300ms 대기)
				long syncStart = System.currentTimeMillis();
				String syncValue2 = syncCache.get("key1");
				long syncTime = System.currentTimeMillis() - syncStart;
				assertEquals("loaded-key1", syncValue2);
				assertTrue("SYNC should take ~300ms", syncTime >= 250);

				// ASYNC: 즉시 반환 (stale 데이터)
				long asyncStart = System.currentTimeMillis();
				String asyncValue2 = asyncCache.get("key1");
				long asyncTime = System.currentTimeMillis() - asyncStart;
				assertEquals("loaded-key1", asyncValue2);
				assertTrue("ASYNC should return immediately (<50ms)", asyncTime < 50);

				// 백그라운드 로드 완료 대기
				Thread.sleep(500);
				assertEquals(2, asyncLoadCount.get()); // 백그라운드 로드 완료
			}
		}
	}

	/**
	 * 백그라운드 로드 실패 시 처리 테스트
	 * - ASYNC는 stale 데이터를 반환하므로 백그라운드 실패 시에도 응답 가능
	 * - 실패는 로그에만 기록되고 캐시는 계속 동작
	 */
	@Test
	public void testAsyncBackgroundFailure() throws Exception {
		AtomicInteger loadCount = new AtomicInteger(0);

		SBCacheMapLoader<String, String> loader = new SBCacheMapLoader<String, String>() {
			@Override
			public String loadOne(String key) throws SBCacheLoadFailException {
				int count = loadCount.incrementAndGet();
				if (count == 1) {
					return "initial-value";
				} else {
					// 두 번째 로드(백그라운드)는 실패
					throw new SBCacheLoadFailException("Simulated load failure");
				}
			}
		};

		try (SBCacheMap<String, String> cache = SBCacheMap.<String, String>builder()
				.loader(loader)
				.timeoutSec(1)
				.loadStrategy(LoadStrategy.ASYNC)
				.build()) {

			// 1. 초기 로드 성공
			String value1 = cache.get("key1");
			assertEquals("initial-value", value1);
			assertEquals(1, loadCount.get());

			// 2. TTL 경과
			Thread.sleep(1500);

			// 3. ASYNC: stale 데이터 반환 (백그라운드 로드는 실패할 것)
			String staleValue = cache.get("key1");
			assertEquals("Should return stale data even if background load will fail",
					"initial-value", staleValue);

			// 4. 백그라운드 로드 실패 대기
			Thread.sleep(500);
			assertEquals(2, loadCount.get()); // 백그라운드 로드 시도됨 (실패)

			// 5. 다시 조회: 여전히 stale 데이터 반환
			String stillStale = cache.get("key1");
			assertEquals("Should still return stale data after background failure",
					"initial-value", stillStale);
		}
	}

	/**
	 * 동시 요청 처리 테스트
	 * - 여러 스레드가 동시에 만료된 키를 조회할 때
	 * - 모두 stale 데이터를 즉시 받고 백그라운드 로드는 한 번만 실행
	 */
	@Test
	public void testAsyncConcurrentAccess() throws Exception {
		AtomicInteger loadCount = new AtomicInteger(0);

		SBCacheMapLoader<String, String> loader = new SBCacheMapLoader<String, String>() {
			@Override
			public String loadOne(String key) throws SBCacheLoadFailException {
				loadCount.incrementAndGet();
				try {
					Thread.sleep(200); // 로드 시뮬레이션
				} catch (InterruptedException e) {
					throw new SBCacheLoadFailException(e);
				}
				return "value-" + loadCount.get();
			}
		};

		try (SBCacheMap<String, String> cache = SBCacheMap.<String, String>builder()
				.loader(loader)
				.timeoutSec(1)
				.loadStrategy(LoadStrategy.ASYNC)
				.build()) {

			// 1. 초기 로드
			String value1 = cache.get("key1");
			assertEquals("value-1", value1);

			// 2. TTL 경과
			Thread.sleep(1500);

			// 3. 10개 스레드가 동시에 조회
			int threadCount = 10;
			CountDownLatch startLatch = new CountDownLatch(1);
			CountDownLatch doneLatch = new CountDownLatch(threadCount);
			AtomicInteger staleCount = new AtomicInteger(0);

			for (int i = 0; i < threadCount; i++) {
				new Thread(() -> {
					try {
						startLatch.await(); // 모든 스레드가 동시에 시작
						String value = cache.get("key1");
						if ("value-1".equals(value)) {
							staleCount.incrementAndGet();
						}
					} catch (InterruptedException e) {
						// ignore
					} finally {
						doneLatch.countDown();
					}
				}).start();
			}

			// 동시 시작
			startLatch.countDown();
			assertTrue("All threads should complete", doneLatch.await(5, TimeUnit.SECONDS));

			// 모든 스레드가 stale 데이터를 받음
			assertEquals("All threads should get stale data", threadCount, staleCount.get());

			// 백그라운드 로드 완료 대기
			Thread.sleep(500);

			// 백그라운드 로드는 한 번만 실행됨 (경쟁 조건으로 인해 1~3회 정도 가능)
			assertTrue("Background load should be minimal", loadCount.get() <= 3);

			// 최신 데이터 확인
			String freshValue = cache.get("key1");
			assertTrue("Should get fresh data", freshValue.startsWith("value-"));
		}
	}

	/**
	 * Metrics와 ASYNC 전략 통합 테스트
	 * - 백그라운드 로드 성공/실패가 metrics에 정확히 기록되는지 확인
	 */
	@Test
	public void testAsyncWithMetrics() throws Exception {
		AtomicInteger loadCount = new AtomicInteger(0);

		SBCacheMapLoader<String, String> loader = new SBCacheMapLoader<String, String>() {
			@Override
			public String loadOne(String key) throws SBCacheLoadFailException {
				loadCount.incrementAndGet();
				return "value-" + key;
			}
		};

		try (SBCacheMap<String, String> cache = SBCacheMap.<String, String>builder()
				.loader(loader)
				.timeoutSec(1)
				.loadStrategy(LoadStrategy.ASYNC)
				.enableMetrics(true)
				.build()) {

			// 초기 로드
			cache.get("key1");
			assertEquals(1, cache.metrics().missCount());
			assertEquals(0, cache.metrics().hitCount());

			// TTL 이내 재조회: 히트
			cache.get("key1");
			assertEquals(1, cache.metrics().missCount());
			assertEquals(1, cache.metrics().hitCount());

			// TTL 경과
			Thread.sleep(1500);

			// ASYNC 조회: stale 반환 (miss로 카운트)
			cache.get("key1");
			assertEquals(2, cache.metrics().missCount());

			// 백그라운드 로드 완료 대기
			Thread.sleep(500);
			assertEquals(1, cache.metrics().loadSuccessCount());

			// 새 데이터 조회: 히트
			cache.get("key1");
			assertEquals(2, cache.metrics().hitCount());
		}
	}

	/**
	 * ASYNC 전략에서 첫 조회 시 데이터가 없으면 SYNC처럼 동작하는지 테스트
	 */
	@Test
	public void testAsyncFirstLoadIsBlocking() throws Exception {
		AtomicInteger loadCount = new AtomicInteger(0);

		SBCacheMapLoader<String, String> loader = new SBCacheMapLoader<String, String>() {
			@Override
			public String loadOne(String key) throws SBCacheLoadFailException {
				loadCount.incrementAndGet();
				try {
					Thread.sleep(300); // 로드 시간
				} catch (InterruptedException e) {
					throw new SBCacheLoadFailException(e);
				}
				return "value-" + key;
			}
		};

		try (SBCacheMap<String, String> cache = SBCacheMap.<String, String>builder()
				.loader(loader)
				.timeoutSec(60)
				.loadStrategy(LoadStrategy.ASYNC)
				.build()) {

			// 첫 로드는 블로킹되어야 함 (데이터가 없으므로)
			long start = System.currentTimeMillis();
			String value = cache.get("key1");
			long elapsed = System.currentTimeMillis() - start;

			assertEquals("value-key1", value);
			assertEquals(1, loadCount.get());
			assertTrue("First load should be blocking", elapsed >= 250);
		}
	}

	/**
	 * close() 시 asyncExecutor가 정상적으로 종료되는지 테스트
	 */
	@Test
	public void testAsyncExecutorShutdown() throws Exception {
		SBCacheMapLoader<String, String> loader = key -> "value-" + key;

		SBCacheMap<String, String> cache = SBCacheMap.<String, String>builder()
				.loader(loader)
				.timeoutSec(60)
				.loadStrategy(LoadStrategy.ASYNC)
				.build();

		// 정상 동작 확인
		String value = cache.get("key1");
		assertEquals("value-key1", value);

		// close 호출
		cache.close();

		// close 후에는 asyncExecutor가 shutdown되므로 추가 작업은 불가
		// (하지만 기존 데이터 조회는 가능)
		String cachedValue = cache.get("key1");
		assertEquals("value-key1", cachedValue);
	}
}
