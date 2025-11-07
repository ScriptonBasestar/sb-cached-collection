package org.scriptonbasestar.cache.collection.map;

import org.scriptonbasestar.cache.core.loader.SBCacheMapLoader;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author archmagece
 * @since 2016-11-07
 */
public class SBCacheMapTest {

	private final SBCacheMapDataFeeder dataFeeder = new SBCacheMapDataFeeder();
	private SBCacheMap<Long, String> cacheData = new SBCacheMap<>(new SBCacheMapLoader<Long, String>() {
		@Override
		public String loadOne(Long key) throws SBCacheLoadFailException {
			return dataFeeder.loadOne(key);
		}

		@Override
		public Map<Long, String> loadAll() throws SBCacheLoadFailException {
			return dataFeeder.loadAll();
		}
	}, 10);

	//로딩 딜레이 확인
	@Test
	public void subLoadOne로딩딜레이확인() {
		int 반복횟수 = 10;

		long start1 = System.currentTimeMillis();
		for (int i = 0; i < 반복횟수; i++) {
			System.out.println(dataFeeder.loadOne(0L));
		}
		long end1 = System.currentTimeMillis();
		long result1 = System.currentTimeMillis() - start1;
		System.out.println(result1);

		long start2 = System.currentTimeMillis();
		for (int i = 0; i < 반복횟수; i++) {
			System.out.println(cacheData.get(0L));
		}
		long end2 = System.currentTimeMillis();
		long result2 = end2 - start2;
		System.out.println(result2);

		System.out.println("결과시간 비교(ms) result1 : " + result1);
		System.out.println("결과시간 비교(ms) result2 : " + result2);
		System.out.println("! 결과시간이 resutl1 > result2인게 정상");
		Assert.assertTrue(result1 > result2);
	}

	@Test
	public void subLoadAll로딩딜레이확인() {
		int 반복횟수 = 5;

		long start1 = System.currentTimeMillis();
		for (int i = 0; i < 반복횟수; i++) {
			System.out.println(dataFeeder.loadAll());
		}
		long end1 = System.currentTimeMillis();
		long result1 = end1 - start1;
		System.out.println(result1);

		long start2 = System.currentTimeMillis();
		for (int i = 0; i < 반복횟수; i++) {
			System.out.println(cacheData.toString());
		}
		long end2 = System.currentTimeMillis();
		long result2 = end2 - start2;
		System.out.println(result2);

		System.out.println("결과시간 비교(ms) result1 : " + result1);
		System.out.println("결과시간 비교(ms) result2 : " + result2);
		System.out.println("! 결과시간이 resutl1 > result2인게 정상");
		Assert.assertTrue(result1 > result2);
	}

	@Test
	public void test타임아웃이후_가져오기() {
		long now;
		long timeSpent;

		now = System.currentTimeMillis();
		System.out.println("first move");
		for(int i=0;i<5;i++){
			cacheData.get((long) i);
		}
		timeSpent = System.currentTimeMillis() - now;
		System.out.println("시간소요 first move : " + timeSpent);

		now = System.currentTimeMillis();
		System.out.println("second move");
		for(long key : cacheData.keySet()){
			cacheData.get(key);
		}
		timeSpent = System.currentTimeMillis() - now;
		System.out.println("시간소요 second move : " + timeSpent);

		now = System.currentTimeMillis();
		System.out.println("third move");
		for(long key : cacheData.keySet()){
			cacheData.get(key);
		}
		timeSpent = System.currentTimeMillis() - now;
		System.out.println("시간소요 third move : " + timeSpent);

		System.out.println("========================");
		System.out.println("쉬는시간 10초");
		try {
			Thread.sleep(1000*20);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		now = System.currentTimeMillis();
		System.out.println("last move");
		for(long key : cacheData.keySet()){
			cacheData.get(key);
		}
		timeSpent = System.currentTimeMillis() - now;
		System.out.println("시간소요 last move : " + timeSpent);

	}

	private ExecutorService executor;
	@Test
	public void test멀티쓰레드환경에서싸이코_IN올OUT() throws InterruptedException {
		int maxThreadCount = 100;
		executor = Executors.newFixedThreadPool(maxThreadCount);


		Callable<Long> runnable = new Callable<Long>() {
			Random random = new Random();
			@Override
			public Long call() throws Exception {
//				long key = Math.abs(random.nextLong() % 30);
				long key = 3;
				String value0 = cacheData.get(key);
				cacheData.put(key, value0+"0");
				String value1 = cacheData.get(key);
				System.out.println(key + "  " + value0 + "  " + value1 + "  " + value0.equals(value1));
				Assert.assertEquals(value0, value1);
				Assert.assertTrue(value1.equals(value0+"0"));
				return null;
			}
		};
//		Runnable runnable = new Runnable() {
//			Random random = new Random();
//			@Override
//			public void run() {
////				long key = Math.abs(random.nextLong() % 30);
//				long key = 3;
//				String value0 = cacheData.get(key);
//				cacheData.put(key, value0+"0");
//				String value1 = cacheData.get(key);
//				System.out.println(key + "  " + value0 + "  " + value1 + "  " + value0.equals(value1));
//				Assert.assertEquals(value0, value1);
//				Assert.assertTrue(value1.equals(value0+"0"));
//			}
//		};
		for(int i=0;i<maxThreadCount;i++){
			executor.submit(runnable);
		}
		executor.awaitTermination(3, TimeUnit.SECONDS);
	}

}
