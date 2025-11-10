package org.scriptonbasestar.cache.collection.map;

import org.junit.Assert;
import org.junit.Test;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;
import org.scriptonbasestar.cache.core.loader.SBCacheMapLoader;
import org.scriptonbasestar.cache.core.strategy.ReferenceType;

import java.util.HashMap;
import java.util.Map;

/**
 * SBCacheMap의 ReferenceType (STRONG, SOFT, WEAK) 테스트
 *
 * @author archmagece
 * @since 2025-01 (Phase 10-C Part 2)
 */
public class SBCacheMapReferenceTypeTest {

	private static class TestLoader implements SBCacheMapLoader<Integer, String> {
		private final Map<Integer, String> data = new HashMap<>();

		public TestLoader() {
			for (int i = 1; i <= 10; i++) {
				data.put(i, "Value-" + i);
			}
		}

		@Override
		public String loadOne(Integer key) throws SBCacheLoadFailException {
			String value = data.get(key);
			if (value == null) {
				throw new SBCacheLoadFailException("Key not found: " + key);
			}
			return value;
		}

		@Override
		public Map<Integer, String> loadAll() throws SBCacheLoadFailException {
			return new HashMap<>(data);
		}
	}

	/**
	 * STRONG 참조 타입 기본 동작 테스트
	 * STRONG 참조는 GC가 절대 회수하지 않음
	 */
	@Test
	public void testReferenceType_STRONG() throws SBCacheLoadFailException, InterruptedException {
		TestLoader loader = new TestLoader();
		SBCacheMap<Integer, String> cache = SBCacheMap.<Integer, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.referenceType(ReferenceType.STRONG)
			.build();

		// 데이터 로드
		String value1 = cache.get(1);
		Assert.assertEquals("Value-1", value1);

		// GC 유도 (STRONG은 영향 없음)
		System.gc();
		Thread.sleep(100);

		// STRONG 참조는 GC 후에도 유지되어야 함
		String value1After = cache.get(1);
		Assert.assertEquals("Value-1", value1After);

		cache.close();
	}

	/**
	 * SOFT 참조 타입 기본 동작 테스트
	 * SOFT 참조는 메모리 부족 시에만 GC가 회수
	 */
	@Test
	public void testReferenceType_SOFT() throws SBCacheLoadFailException, InterruptedException {
		TestLoader loader = new TestLoader();
		SBCacheMap<Integer, String> cache = SBCacheMap.<Integer, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.referenceType(ReferenceType.SOFT)
			.build();

		// 데이터 로드
		String value1 = cache.get(1);
		Assert.assertEquals("Value-1", value1);

		// 일반적인 GC로는 SOFT 참조가 회수되지 않음 (메모리 압박이 없으므로)
		System.gc();
		Thread.sleep(100);

		// SOFT 참조는 일반 GC 후에도 유지될 수 있음
		String value1After = cache.get(1);
		Assert.assertEquals("Value-1", value1After);

		cache.close();
	}

	/**
	 * WEAK 참조 타입 기본 동작 테스트
	 * WEAK 참조는 다음 GC 사이클에서 회수됨
	 */
	@Test
	public void testReferenceType_WEAK() throws SBCacheLoadFailException, InterruptedException {
		TestLoader loader = new TestLoader();
		SBCacheMap<Integer, String> cache = SBCacheMap.<Integer, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.referenceType(ReferenceType.WEAK)
			.build();

		// 데이터 로드
		String value1 = cache.get(1);
		Assert.assertEquals("Value-1", value1);

		// WEAK 참조는 다음 GC에서 회수될 수 있음
		// (단, 로컬 변수 value1이 여전히 참조하고 있으므로 회수되지 않을 수 있음)
		System.gc();
		Thread.sleep(100);

		// 재로드 후 확인 (loader가 있으므로 GC 후에도 재로드됨)
		String value1After = cache.get(1);
		Assert.assertEquals("Value-1", value1After);

		cache.close();
	}

	/**
	 * Builder를 통한 기본 참조 타입 테스트 (STRONG이 기본값)
	 */
	@Test
	public void testReferenceType_DefaultIsSTRONG() throws SBCacheLoadFailException, InterruptedException {
		TestLoader loader = new TestLoader();
		SBCacheMap<Integer, String> cache = SBCacheMap.<Integer, String>builder()
			.loader(loader)
			.timeoutSec(60)
			// referenceType 지정 안 함 (기본값 STRONG)
			.build();

		// 데이터 로드
		String value1 = cache.get(1);
		Assert.assertEquals("Value-1", value1);

		// GC 유도
		System.gc();
		Thread.sleep(100);

		// 기본값 STRONG이므로 GC 후에도 유지되어야 함
		String value1After = cache.get(1);
		Assert.assertEquals("Value-1", value1After);

		cache.close();
	}

	/**
	 * 여러 참조 타입 캐시 동시 사용 테스트
	 */
	@Test
	public void testReferenceType_MultipleTypesCoexist() throws SBCacheLoadFailException, InterruptedException {
		TestLoader loader = new TestLoader();

		SBCacheMap<Integer, String> strongCache = SBCacheMap.<Integer, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.referenceType(ReferenceType.STRONG)
			.build();

		SBCacheMap<Integer, String> softCache = SBCacheMap.<Integer, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.referenceType(ReferenceType.SOFT)
			.build();

		SBCacheMap<Integer, String> weakCache = SBCacheMap.<Integer, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.referenceType(ReferenceType.WEAK)
			.build();

		// 모든 캐시에 데이터 로드
		Assert.assertEquals("Value-1", strongCache.get(1));
		Assert.assertEquals("Value-1", softCache.get(1));
		Assert.assertEquals("Value-1", weakCache.get(1));

		// GC 유도
		System.gc();
		Thread.sleep(100);

		// 모든 캐시에서 데이터 확인 (loader가 있으므로 재로드 가능)
		Assert.assertEquals("Value-1", strongCache.get(1));
		Assert.assertEquals("Value-1", softCache.get(1));
		Assert.assertEquals("Value-1", weakCache.get(1));

		strongCache.close();
		softCache.close();
		weakCache.close();
	}

	/**
	 * ReferenceType과 maxSize가 함께 동작하는지 테스트
	 */
	@Test
	public void testReferenceType_WithMaxSize() throws SBCacheLoadFailException {
		TestLoader loader = new TestLoader();
		SBCacheMap<Integer, String> cache = SBCacheMap.<Integer, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.maxSize(3)
			.referenceType(ReferenceType.STRONG)
			.build();

		// 3개 항목 추가
		cache.get(1);
		cache.get(2);
		cache.get(3);

		Assert.assertEquals(3, cache.size());

		// 4번째 항목 추가 시 LRU에 의해 하나가 제거됨
		cache.get(4);

		Assert.assertEquals(3, cache.size());

		cache.close();
	}

	/**
	 * ReferenceType null 체크 (기본값 STRONG으로 설정되어야 함)
	 */
	@Test
	public void testReferenceType_NullDefaultsToSTRONG() throws SBCacheLoadFailException, InterruptedException {
		TestLoader loader = new TestLoader();
		SBCacheMap<Integer, String> cache = SBCacheMap.<Integer, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.referenceType(null)  // null 전달
			.build();

		// null이면 STRONG으로 동작해야 함
		String value1 = cache.get(1);
		Assert.assertEquals("Value-1", value1);

		System.gc();
		Thread.sleep(100);

		String value1After = cache.get(1);
		Assert.assertEquals("Value-1", value1After);

		cache.close();
	}
}
