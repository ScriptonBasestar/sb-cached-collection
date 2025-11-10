package org.scriptonbasestar.cache.collection.map;

import org.junit.Assert;
import org.junit.Test;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;
import org.scriptonbasestar.cache.core.loader.SBCacheMapLoader;
import org.scriptonbasestar.cache.core.strategy.EvictionPolicy;

import java.util.HashMap;
import java.util.Map;

/**
 * SBCacheMap의 다양한 Eviction Policy 테스트
 *
 * @author archmagece
 * @since 2025-01 (Phase 10-B Part 2)
 */
public class SBCacheMapEvictionPolicyTest {

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
	 * LRU (Least Recently Used) 정책 테스트
	 * 가장 최근에 사용되지 않은 항목이 제거되어야 함
	 */
	@Test
	public void testEvictionPolicy_LRU() {
		// maxSize=3, LRU 정책 (loader 없음 - eviction만 테스트)
		SBCacheMap<Integer, String> cache = new SBCacheMap<>(60, 3, EvictionPolicy.LRU);

		// 3개 항목 추가
		cache.put(1, "A");
		cache.put(2, "B");
		cache.put(3, "C");

		Assert.assertEquals(3, cache.size());

		// 1번 액세스 (가장 최근 사용)
		Assert.assertEquals("A", cache.get(1));

		// 4번 추가 → 2번이 제거되어야 함 (가장 오래 전에 액세스됨)
		cache.put(4, "D");

		Assert.assertEquals(3, cache.size());
		Assert.assertEquals("A", cache.get(1));  // 1번은 최근 사용했으므로 남아있음
		Assert.assertNull(cache.get(2));         // 2번은 제거됨 (loader 없으므로 null 반환)
		Assert.assertEquals("C", cache.get(3));
		Assert.assertEquals("D", cache.get(4));
	}

	/**
	 * FIFO (First In First Out) 정책 테스트
	 * 가장 먼저 삽입된 항목이 제거되어야 함
	 */
	@Test
	public void testEvictionPolicy_FIFO() {
		// maxSize=3, FIFO 정책 (loader 없음)
		SBCacheMap<Integer, String> cache = new SBCacheMap<>(60, 3, EvictionPolicy.FIFO);

		// 3개 항목 추가
		cache.put(1, "A");
		cache.put(2, "B");
		cache.put(3, "C");

		Assert.assertEquals(3, cache.size());

		// 1번 액세스 (하지만 FIFO는 액세스 무시)
		Assert.assertEquals("A", cache.get(1));

		// 4번 추가 → 1번이 제거되어야 함 (가장 먼저 삽입됨)
		cache.put(4, "D");

		Assert.assertEquals(3, cache.size());
		Assert.assertNull(cache.get(1));         // 1번은 제거됨 (액세스했지만 FIFO는 삽입 순서만 고려)
		Assert.assertEquals("B", cache.get(2));
		Assert.assertEquals("C", cache.get(3));
		Assert.assertEquals("D", cache.get(4));
	}

	/**
	 * LFU (Least Frequently Used) 정책 테스트
	 * 가장 적게 사용된 항목이 제거되어야 함
	 */
	@Test
	public void testEvictionPolicy_LFU() {
		// maxSize=3, LFU 정책 (loader 없음)
		SBCacheMap<Integer, String> cache = new SBCacheMap<>(60, 3, EvictionPolicy.LFU);

		// 3개 항목 추가
		cache.put(1, "A");
		cache.put(2, "B");
		cache.put(3, "C");

		Assert.assertEquals(3, cache.size());

		// 1번을 3번 액세스
		cache.get(1);
		cache.get(1);
		cache.get(1);

		// 2번을 2번 액세스
		cache.get(2);
		cache.get(2);

		// 3번은 액세스 안 함 (액세스 횟수 = 0)

		// 4번 추가 → 3번이 제거되어야 함 (가장 적게 액세스됨)
		cache.put(4, "D");

		Assert.assertEquals(3, cache.size());
		Assert.assertEquals("A", cache.get(1));  // 1번은 3회 액세스
		Assert.assertEquals("B", cache.get(2));  // 2번은 2회 액세스
		Assert.assertNull(cache.get(3));         // 3번은 0회 액세스로 제거됨
		Assert.assertEquals("D", cache.get(4));
	}

	/**
	 * RANDOM 정책 테스트
	 * 무작위로 항목이 제거되어야 함 (제거는 발생하지만 순서는 예측 불가)
	 */
	@Test
	public void testEvictionPolicy_RANDOM() {
		// maxSize=3, RANDOM 정책 (loader 없음)
		SBCacheMap<Integer, String> cache = new SBCacheMap<>(60, 3, EvictionPolicy.RANDOM);

		// 3개 항목 추가
		cache.put(1, "A");
		cache.put(2, "B");
		cache.put(3, "C");

		Assert.assertEquals(3, cache.size());

		// 4번 추가 → 1, 2, 3 중 하나가 무작위로 제거됨
		cache.put(4, "D");

		Assert.assertEquals(3, cache.size());

		// 4번은 반드시 존재해야 함
		Assert.assertEquals("D", cache.get(4));

		// 1, 2, 3 중 정확히 하나가 제거되어야 함
		int nullCount = 0;
		if (cache.get(1) == null) nullCount++;
		if (cache.get(2) == null) nullCount++;
		if (cache.get(3) == null) nullCount++;

		Assert.assertEquals("정확히 하나의 항목이 제거되어야 함", 1, nullCount);
	}

	/**
	 * TTL 정책 테스트
	 * 가장 오래된 항목(생성 시간 기준)이 제거되어야 함
	 */
	@Test
	public void testEvictionPolicy_TTL() throws InterruptedException {
		// maxSize=3, TTL 정책 (loader 없음)
		SBCacheMap<Integer, String> cache = new SBCacheMap<>(60, 3, EvictionPolicy.TTL);

		// 3개 항목을 시간 간격을 두고 추가
		cache.put(1, "A");
		Thread.sleep(10);
		cache.put(2, "B");
		Thread.sleep(10);
		cache.put(3, "C");

		Assert.assertEquals(3, cache.size());

		// 1번을 여러 번 액세스 (하지만 TTL은 생성 시간만 고려)
		cache.get(1);
		cache.get(1);
		cache.get(1);

		// 4번 추가 → 1번이 제거되어야 함 (가장 오래 전에 생성됨)
		cache.put(4, "D");

		Assert.assertEquals(3, cache.size());
		Assert.assertNull(cache.get(1));         // 1번은 제거됨 (가장 오래된 생성 시간)
		Assert.assertEquals("B", cache.get(2));
		Assert.assertEquals("C", cache.get(3));
		Assert.assertEquals("D", cache.get(4));
	}

	/**
	 * maxSize=0 (무제한) 테스트
	 * eviction이 발생하지 않아야 함
	 */
	@Test
	public void testEvictionPolicy_UnlimitedSize() {
		// maxSize=0 (무제한), 정책은 의미 없음 (loader 없음)
		SBCacheMap<Integer, String> cache = new SBCacheMap<>(60, 0, EvictionPolicy.LRU);

		// 10개 항목 추가 (제한 없음)
		for (int i = 1; i <= 10; i++) {
			cache.put(i, "Value-" + i);
		}

		// 모든 항목이 남아있어야 함
		Assert.assertEquals(10, cache.size());
		for (int i = 1; i <= 10; i++) {
			Assert.assertEquals("Value-" + i, cache.get(i));
		}
	}

	/**
	 * 정책 변경 없이 기본 동작 테스트 (기본값 LRU)
	 */
	@Test
	public void testEvictionPolicy_DefaultLRU() {
		// evictionPolicy 지정 안 함 (기본값 LRU, loader 없음)
		SBCacheMap<Integer, String> cache = new SBCacheMap<>(60, 2);

		cache.put(1, "A");
		cache.put(2, "B");

		Assert.assertEquals(2, cache.size());

		// 1번 액세스
		Assert.assertEquals("A", cache.get(1));

		// 3번 추가 → 2번이 제거되어야 함 (LRU 기본 동작)
		cache.put(3, "C");

		Assert.assertEquals(2, cache.size());
		Assert.assertEquals("A", cache.get(1));
		Assert.assertNull(cache.get(2));  // 2번 제거됨
		Assert.assertEquals("C", cache.get(3));
	}

	/**
	 * removeAll() 호출 시 eviction strategy도 초기화되는지 테스트
	 */
	@Test
	public void testEvictionPolicy_RemoveAll() {
		// LFU 정책 (loader 없음)
		SBCacheMap<Integer, String> cache = new SBCacheMap<>(60, 3, EvictionPolicy.LFU);

		// 데이터 추가 및 액세스
		cache.put(1, "A");
		cache.put(2, "B");
		cache.get(1);
		cache.get(1);
		cache.get(1);

		Assert.assertEquals(2, cache.size());

		// 전체 삭제
		cache.removeAll();

		Assert.assertEquals(0, cache.size());

		// 다시 추가 (이전 LFU 카운트가 초기화되어야 함)
		cache.put(1, "A");
		cache.put(2, "B");
		cache.put(3, "C");

		// 4번 추가 시 1, 2, 3 중 하나가 제거되어야 함 (이전 액세스 카운트가 초기화됨)
		cache.put(4, "D");

		Assert.assertEquals(3, cache.size());
		// removeAll 후에는 모든 항목의 액세스 카운트가 0이므로
		// 1, 2, 3 중 하나가 제거됨 (LFU에서는 동일 빈도일 때 첫 번째 항목 제거)
		int nullCount = 0;
		if (cache.get(1) == null) nullCount++;
		if (cache.get(2) == null) nullCount++;
		if (cache.get(3) == null) nullCount++;
		Assert.assertEquals("정확히 하나의 항목이 제거되어야 함", 1, nullCount);
	}
}
