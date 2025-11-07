package org.scriptonbasestar.cache.collection.map;

import lombok.extern.slf4j.Slf4j;
import org.scriptonbasestar.cache.core.util.TimeCheckerUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 비동기 캐시 Map 구현체
 *
 * <p><strong>⚠️ Deprecated:</strong> 이 클래스는 더 이상 권장되지 않습니다.
 * {@link SBCacheMap}에 {@link org.scriptonbasestar.cache.collection.strategy.LoadStrategy#ASYNC}를 사용하세요.</p>
 *
 * <h3>마이그레이션 가이드:</h3>
 * <pre>{@code
 * // Before (Deprecated)
 * SBAsyncCacheMap<String, Data> cache = SBAsyncCacheMap.<String, Data>builder()
 *     .loader(key -> loadData(key))
 *     .timeoutSec(300)
 *     .numberOfThreads(10)
 *     .build();
 *
 * // After (Recommended)
 * SBCacheMap<String, Data> cache = SBCacheMap.<String, Data>builder()
 *     .loader(key -> loadData(key))
 *     .timeoutSec(300)
 *     .loadStrategy(LoadStrategy.ASYNC)  // ASYNC 전략 사용
 *     .build();
 * }</pre>
 *
 * <p>ASYNC 전략은 캐시 미스 시 만료된 데이터를 즉시 반환하고 백그라운드에서 새 데이터를 로드합니다.</p>
 *
 * @author archmagece
 * @since 2015-08-26
 * @deprecated SBCacheMap with LoadStrategy.ASYNC를 사용하세요
 */
@Slf4j
@Deprecated(since = "2.0.0", forRemoval = true)
public class SBAsyncCacheMap<K, V> implements AutoCloseable {

	private final ConcurrentHashMap<K, Long> timeoutChecker;
	private final ConcurrentHashMap<K, V> data;
	private final int timeoutSec;
	private final SBCacheMapLoader<K, V> cacheLoader;

	private final ExecutorService executor;
	//async
	private final int numberOfThreads;


	public SBAsyncCacheMap(SBCacheMapLoader cacheLoader, int timeoutSec) {
		this(cacheLoader, timeoutSec, 5);
	}

	public SBAsyncCacheMap(SBCacheMapLoader cacheLoader, int timeoutSec, int numberOfThreads) {
		this.timeoutChecker = new ConcurrentHashMap<>();
		this.data = new ConcurrentHashMap<>();
		this.cacheLoader = cacheLoader;
		this.timeoutSec = timeoutSec;
		this.numberOfThreads = numberOfThreads;

		//async
		this.executor = Executors.newFixedThreadPool(numberOfThreads);
	}

	/**
	 * Builder 패턴을 사용하여 SBAsyncCacheMap을 생성합니다.
	 *
	 * @param <K> 키 타입
	 * @param <V> 값 타입
	 * @return Builder 인스턴스
	 */
	public static <K, V> Builder<K, V> builder() {
		return new Builder<>();
	}

	/**
	 * SBAsyncCacheMap Builder 클래스
	 *
	 * @param <K> 키 타입
	 * @param <V> 값 타입
	 */
	public static class Builder<K, V> {
		private SBCacheMapLoader<K, V> loader;
		private int timeoutSec = 60; // 기본값 60초
		private int numberOfThreads = 5; // 기본값 5개 스레드

		public Builder<K, V> loader(SBCacheMapLoader<K, V> loader) {
			this.loader = loader;
			return this;
		}

		public Builder<K, V> timeoutSec(int timeoutSec) {
			this.timeoutSec = timeoutSec;
			return this;
		}

		/**
		 * 비동기 작업을 처리할 스레드 풀 크기를 설정합니다.
		 *
		 * @param threads 스레드 수 (기본값: 5)
		 * @return Builder 인스턴스
		 */
		public Builder<K, V> numberOfThreads(int threads) {
			this.numberOfThreads = threads;
			return this;
		}

		public SBAsyncCacheMap<K, V> build() {
			if (loader == null) {
				throw new IllegalStateException("loader must be set");
			}
			return new SBAsyncCacheMap<>(loader, timeoutSec, numberOfThreads);
		}
	}

	public V put(K key, V val) {
		log.trace("put data - key : {} , val : {}", key, val);
		// ThreadLocalRandom을 사용하여 0~timeoutSec 범위의 jitter 추가 (cache stampede 방지)
		long baseTimeoutMs = timeoutSec * 1000L;
		long jitterMs = ThreadLocalRandom.current().nextLong(timeoutSec * 1000L);
		timeoutChecker.put(key, System.currentTimeMillis() + baseTimeoutMs + jitterMs);
		return data.put(key, val);
	}

	public void putAll(Map<? extends K, ? extends V> m) {
		log.trace("putAll data - size : {}", m.size());
		for (K key : m.keySet()) {
			timeoutChecker.put(key, System.currentTimeMillis() + 1000 * timeoutSec);
		}
		data.putAll(m);
	}

	public V get(K key) {
		//sync
		if (!data.containsKey(key) || timeoutChecker.containsKey(key) && TimeCheckerUtil.checkExpired(timeoutChecker.get(key), timeoutSec)){
//			works.add(key);
			executor.execute(new FutureRunner(key));
		}
		return data.get(key);
	}

	/**
	 * 새로고침 필요한 데이터.. 1초후에 만료되도록 셋
	 *
	 * @param key
	 */
	public void expireOne(K key) {
		timeoutChecker.put(key, System.currentTimeMillis() + 1000 * 1);
	}

	public void expireAll() {
		for (K key: timeoutChecker.keySet()) {
			expireOne(key);
		}
	}

	public V remove(Object key) {
		timeoutChecker.remove(key);
		return data.remove(key);
	}

	public void removeAll() {
		timeoutChecker.clear();
		data.clear();
	}

//	private final Queue<K> works = new ConcurrentLinkedQueue<>();
	private class FutureRunner implements Runnable {
		private final K key;
		public FutureRunner(K key){
			this.key = key;
		}
		@Override
		public void run() {
			try {
				put(key, cacheLoader.loadOne(key));
//				data.put(k, cacheLoader.loadOne(k));
			} catch (Exception e) {
//				if(allowExpiredData){
//					//sync
//					timeCheckerExpire.get(key).plusSeconds(addTimeOutSec);
//				}else{
//					//sync
//					data.remove(key);
//					timeCheckerFirstPut.remove(key);
//					timeCheckerExpire.remove(key);
//				}
			}
		}
	}

	/**
	 * ExecutorService를 graceful하게 종료합니다.
	 * try-with-resources 또는 명시적 close() 호출 시 자동으로 실행됩니다.
	 */
	@Override
	public void close() {
		log.debug("Shutting down SBAsyncCacheMap executor");
		executor.shutdown();
		try {
			if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
				log.warn("Executor did not terminate in time, forcing shutdown");
				executor.shutdownNow();
				if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
					log.error("Executor did not terminate after forced shutdown");
				}
			}
		} catch (InterruptedException e) {
			log.warn("Interrupted while waiting for executor termination", e);
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

}
