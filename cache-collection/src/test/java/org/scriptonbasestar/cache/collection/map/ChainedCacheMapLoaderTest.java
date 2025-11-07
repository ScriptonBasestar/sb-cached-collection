package org.scriptonbasestar.cache.collection.map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * ChainedCacheMapLoader 테스트
 *
 * @author archmagece
 * @since 2025-01
 */
public class ChainedCacheMapLoaderTest {

	private AtomicInteger dbCallCount;
	private SBCacheMapLoader<Long, String> dbLoader;

	@Before
	public void setUp() {
		dbCallCount = new AtomicInteger(0);

		// DB 로더 시뮬레이션
		dbLoader = new SBCacheMapLoader<Long, String>() {
			@Override
			public String loadOne(Long key) throws SBCacheLoadFailException {
				dbCallCount.incrementAndGet();
				return "user" + key;
			}
		};
	}

	@After
	public void tearDown() {
		dbCallCount = null;
		dbLoader = null;
	}

	@Test
	public void test1LevelCache() throws Exception {
		// 1단 캐시 (메모리 → DB)
		try (SBCacheMap<Long, String> cache = new SBCacheMap<>(dbLoader, 60)) {

			// 첫 호출: DB 조회
			String user1 = cache.get(1L);
			assertEquals("user1", user1);
			assertEquals(1, dbCallCount.get());

			// 두 번째 호출: 캐시에서 반환
			String user1Cached = cache.get(1L);
			assertEquals("user1", user1Cached);
			assertEquals(1, dbCallCount.get()); // DB 호출 없음
		}
	}

	@Test
	public void test2LevelCache() throws Exception {
		// L2: 메모리 캐시 (1시간)
		try (SBCacheMap<Long, String> l2Cache = new SBCacheMap<>(dbLoader, 3600)) {

			// L1: 메모리 캐시 (1초) → L2로 체이닝
			ChainedCacheMapLoader<Long, String> chainedLoader = new ChainedCacheMapLoader<>(l2Cache);
			try (SBCacheMap<Long, String> l1Cache = new SBCacheMap<>(chainedLoader, 1)) {

				// 첫 호출: L1 미스 → L2 미스 → DB 조회
				String user1 = l1Cache.get(1L);
				assertEquals("user1", user1);
				assertEquals(1, dbCallCount.get());

				// 두 번째 호출 (즉시): L1 캐시에서 반환
				String user1L1 = l1Cache.get(1L);
				assertEquals("user1", user1L1);
				assertEquals(1, dbCallCount.get()); // DB 호출 없음

				// L1 만료 대기 (1초)
				Thread.sleep(1100);

				// 세 번째 호출: L1 만료 → L2에서 반환
				String user1L2 = l1Cache.get(1L);
				assertEquals("user1", user1L2);
				assertEquals(1, dbCallCount.get()); // 여전히 DB 호출 없음 (L2가 제공)

				// 다른 키 조회
				String user2 = l1Cache.get(2L);
				assertEquals("user2", user2);
				assertEquals(2, dbCallCount.get()); // 새 키는 DB 조회
			}
		}
	}

	@Test
	public void test3LevelCache() throws Exception {
		// L3: 메모리 캐시 (1시간)
		try (SBCacheMap<Long, String> l3Cache = new SBCacheMap<>(dbLoader, 3600)) {

			// L2: 메모리 캐시 (1분) → L3로 체이닝
			ChainedCacheMapLoader<Long, String> l2Loader = new ChainedCacheMapLoader<>(l3Cache);
			try (SBCacheMap<Long, String> l2Cache = new SBCacheMap<>(l2Loader, 60)) {

				// L1: 메모리 캐시 (1초) → L2로 체이닝
				ChainedCacheMapLoader<Long, String> l1Loader = new ChainedCacheMapLoader<>(l2Cache);
				try (SBCacheMap<Long, String> l1Cache = new SBCacheMap<>(l1Loader, 1)) {

					// 첫 호출: L1 미스 → L2 미스 → L3 미스 → DB 조회
					String user1 = l1Cache.get(1L);
					assertEquals("user1", user1);
					assertEquals(1, dbCallCount.get());

					// L1 만료 대기
					Thread.sleep(1100);

					// L1 만료 → L2에서 반환
					String user1Again = l1Cache.get(1L);
					assertEquals("user1", user1Again);
					assertEquals(1, dbCallCount.get()); // DB 호출 없음
				}
			}
		}
	}

	@Test(expected = SBCacheLoadFailException.class)
	public void testChainedLoaderPropagatesException() throws Exception {
		// 항상 예외를 던지는 로더
		SBCacheMapLoader<Long, String> failingLoader = new SBCacheMapLoader<Long, String>() {
			@Override
			public String loadOne(Long key) throws SBCacheLoadFailException {
				throw new SBCacheLoadFailException("Simulated failure");
			}
		};

		try (SBCacheMap<Long, String> l2Cache = new SBCacheMap<>(failingLoader, 60)) {
			ChainedCacheMapLoader<Long, String> chainedLoader = new ChainedCacheMapLoader<>(l2Cache);
			try (SBCacheMap<Long, String> l1Cache = new SBCacheMap<>(chainedLoader, 1)) {

				// 예외 발생 예상
				l1Cache.get(1L);
			}
		}
	}
}
