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
 * @author archmagece
 * @with sb-tools-java
 * @since 2015-08-26 11
 *
 * 들만들어짐
 *
 * ConcurrentMap 참고.
 *
 */
@Slf4j
public class SBAsyncCacheMap<K, V> implements AutoCloseable {

	private final ConcurrentHashMap<K, Long> timeoutChecker;
	private final ConcurrentHashMap<K, V> data;
	private final int timeoutSec;
	private final SBCacheMapLoader<K, V> cacheLoader;

	private final ExecutorService executor;
	//async
	private final int NUMBER_OF_THREAD = 5;

	//if fail throw exception?? or use old data
	private boolean isDataDurable;


	public SBAsyncCacheMap(SBCacheMapLoader cacheLoader, int timeoutSec) {
		this.timeoutChecker = new ConcurrentHashMap<>();
		this.data = new ConcurrentHashMap<>();
		this.cacheLoader = cacheLoader;
		this.timeoutSec = timeoutSec;

		//async
		this.isDataDurable = false;
		this.executor = Executors.newFixedThreadPool(NUMBER_OF_THREAD);
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
