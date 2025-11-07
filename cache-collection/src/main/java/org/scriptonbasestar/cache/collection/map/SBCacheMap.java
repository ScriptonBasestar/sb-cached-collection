package org.scriptonbasestar.cache.collection.map;

import lombok.extern.slf4j.Slf4j;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;
import org.scriptonbasestar.cache.core.util.TimeCheckerUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author archmagece
 * @with sb-tools-java
 * @since 2015-08-26 11
 *
 *		@Autowired
 *		UserRepository repository;
 *
 * 		SBCacheMap<Long, String> cacheMap = new SBCacheMap<>(new SBCacheMapLoader<Long, String>() {
 * 		@Override
 * 		public String loadOne(Long id) {
 * 			return repository.findOne(id);
 * 		}
 * 		@Override
 * 		public Map<Long, String> loadAll() {
 * 			List<User> repository.findAll();
 * 				Map<Long, String> map = new HashMap<>();
 * 				map.put(3L, "i333");
 * 				map.put(5L, "i555");
 * 				map.put(6L, "i666");
 * 				map.put(7L, "i777");
 * 				return map;
 * 			}
 * 		}, expireSecond);
 *
 * 	TODO forced timeout 기능이 있어야함. 자주 조회하더라도 일정시간 지나면 무조건 폐기할 필요 있음.
 */
@Slf4j
public class SBCacheMap<K, V> implements AutoCloseable {

	private final ConcurrentHashMap<K, Long> timeoutChecker;
	private final ConcurrentHashMap<K, V> data;
	private final int timeoutSec;
	private final SBCacheMapLoader<K, V> cacheLoader;
	private final Object lock = new Object();
	private final ScheduledExecutorService cleanupExecutor;
	private final AtomicBoolean closed = new AtomicBoolean(false);

	public SBCacheMap(SBCacheMapLoader<K,V> cacheLoader, int timeoutSec) {
		this(cacheLoader, timeoutSec, false, 5);
	}

	/**
	 * 자동 정리 기능을 설정할 수 있는 생성자
	 *
	 * @param cacheLoader 캐시 로더
	 * @param timeoutSec 타임아웃 시간(초)
	 * @param enableAutoCleanup 자동 정리 활성화 여부
	 * @param cleanupIntervalMinutes 정리 주기(분)
	 */
	public SBCacheMap(SBCacheMapLoader<K,V> cacheLoader, int timeoutSec,
					  boolean enableAutoCleanup, int cleanupIntervalMinutes) {
		this.timeoutChecker = new ConcurrentHashMap<>();
		this.data = new ConcurrentHashMap<>();
		this.cacheLoader = cacheLoader;
		this.timeoutSec = timeoutSec;

		if (enableAutoCleanup) {
			this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "SBCacheMap-Cleanup");
				t.setDaemon(true);
				return t;
			});
			this.cleanupExecutor.scheduleAtFixedRate(
				this::removeExpired,
				cleanupIntervalMinutes,
				cleanupIntervalMinutes,
				TimeUnit.MINUTES
			);
			log.debug("Auto cleanup enabled with interval: {} minutes", cleanupIntervalMinutes);
		} else {
			this.cleanupExecutor = null;
		}
	}

	/**
	 * 람다 함수를 사용하여 간단하게 캐시 맵을 생성합니다.
	 * loadAll은 기본 구현(빈 맵 반환)을 사용합니다.
	 *
	 * @param loadOneFunction 단일 키 로드 함수
	 * @param timeoutSec 타임아웃 시간(초)
	 * @param <K> 키 타입
	 * @param <V> 값 타입
	 * @return 생성된 SBCacheMap 인스턴스
	 */
	public static <K, V> SBCacheMap<K, V> create(
			LoadOneFunction<K, V> loadOneFunction,
			int timeoutSec) {
		SBCacheMapLoader<K, V> loader = new SBCacheMapLoader<K, V>() {
			@Override
			public V loadOne(K key) throws SBCacheLoadFailException {
				try {
					return loadOneFunction.load(key);
				} catch (Exception e) {
					throw new SBCacheLoadFailException(e);
				}
			}
		};
		return new SBCacheMap<>(loader, timeoutSec);
	}

	/**
	 * 람다 함수를 사용하는 단순화된 로더 인터페이스
	 *
	 * @param <K> 키 타입
	 * @param <V> 값 타입
	 */
	@FunctionalInterface
	public interface LoadOneFunction<K, V> {
		V load(K key) throws Exception;
	}

	/**
	 * Builder 패턴을 사용하여 SBCacheMap을 생성합니다.
	 *
	 * @param <K> 키 타입
	 * @param <V> 값 타입
	 * @return Builder 인스턴스
	 */
	public static <K, V> Builder<K, V> builder() {
		return new Builder<>();
	}

	/**
	 * SBCacheMap Builder 클래스
	 *
	 * @param <K> 키 타입
	 * @param <V> 값 타입
	 */
	public static class Builder<K, V> {
		private SBCacheMapLoader<K, V> loader;
		private int timeoutSec = 60; // 기본값 60초
		private boolean enableAutoCleanup = false;
		private int cleanupIntervalMinutes = 5; // 기본값 5분

		public Builder<K, V> loader(SBCacheMapLoader<K, V> loader) {
			this.loader = loader;
			return this;
		}

		public Builder<K, V> timeoutSec(int timeoutSec) {
			this.timeoutSec = timeoutSec;
			return this;
		}

		public Builder<K, V> enableAutoCleanup(boolean enable) {
			this.enableAutoCleanup = enable;
			return this;
		}

		public Builder<K, V> cleanupIntervalMinutes(int minutes) {
			this.cleanupIntervalMinutes = minutes;
			return this;
		}

		public SBCacheMap<K, V> build() {
			if (loader == null) {
				throw new IllegalStateException("loader must be set");
			}
			return new SBCacheMap<>(loader, timeoutSec, enableAutoCleanup, cleanupIntervalMinutes);
		}
	}

	public V put(K key, V val) {
		log.trace("put data - key : {} , value : {}", key, val);
		// ThreadLocalRandom을 사용하여 0부터 timeoutSec까지의 jitter 추가 (cache stampede 방지)
		long jitter = ThreadLocalRandom.current().nextLong(timeoutSec);
		timeoutChecker.put(key, System.currentTimeMillis() + (timeoutSec + jitter) * 1000);
		return data.put(key, val);
	}

	public void putAll(Map<? extends K, ? extends V> m) {
		log.trace("putAll data");
		for (K key : m.keySet()) {
			timeoutChecker.put(key, System.currentTimeMillis() + 1000 * timeoutSec);
		}
		data.putAll(m);
	}

	public V get(final K key) {
		log.trace("get data - key : {}", key);
		synchronized (lock){
			if (data.containsKey(key) && timeoutChecker.containsKey(key) && TimeCheckerUtil.checkExpired(timeoutChecker.get(key), timeoutSec)){
				return data.get(key);
			}
		}
		try{
			V val = cacheLoader.loadOne(key);
			synchronized (lock){
				put(key, val);
				return data.get(key);
			}
		}catch (SBCacheLoadFailException e){
			synchronized (lock) {
				data.remove(key);
				timeoutChecker.remove(key);
			}
			throw e;
		}
	}

	/**
	 * 필요해보이면 구현예정
	 */
	public void getAll() {
		throw new UnsupportedOperationException("getAll() not yet implemented");
	}

	public void postponeOne(K key) {
		synchronized (lock) {
			timeoutChecker.put(key, System.currentTimeMillis() + 1000 * timeoutSec);
		}
	}

	public void postponeAll() {
		synchronized (lock) {
			for (K key : timeoutChecker.keySet()) {
				postponeOne(key);
			}
		}
	}

	/**
	 * 새로고침 필요한 데이터.. 1초후에 만료되도록 셋
	 *
	 * @param key
	 */
	public void expireOne(K key) {
		synchronized (lock) {
			timeoutChecker.put(key, System.currentTimeMillis() + 1000 * 1);
		}
	}

	public void expireAll() {
		synchronized (lock) {
			for (K key : timeoutChecker.keySet()) {
				expireOne(key);
			}
		}
	}

	public V remove(Object key) {
		synchronized (lock) {
			timeoutChecker.remove(key);
			return data.remove(key);
		}
	}

//	public void removeAll() {
//		synchronized (SBCacheMap.class) {
//			timeoutChecker.clear();
//			data.clear();
//		}
//	}

	/**
	 * 만료된 값을 자동으로 지우는 규칙은 없는 상태.
	 * 이걸 굳이 따로 호출할 필요가 있는지도 좀 의문.
	 */
	public void removeExpired() {
		for(K key : timeoutChecker.keySet()){
			if(TimeCheckerUtil.checkExpired(timeoutChecker.get(key), timeoutSec)){
				synchronized (lock) {
					timeoutChecker.remove(key);
					data.remove(key);
				}
			}
		}
	}

	/**
	 * 강제로 모든데이터 교체
	 * 이게 있는게 맞나? 쓰이게 되면 생각해봄
	 */
//	public void loadAll() {
//		try {
//			Map<K,V> cacheTmp = cacheLoader.loadAll();
//			synchronized (SBCacheMap.class) {
//				data.clear();
//				data.putAll(cacheTmp);
//				postponeAll();
//			}
//		} catch (SBCacheLoadFailException e) {
//			log.error("loadAll 처리중 오류 {}", e.getMessage(), e);
//		}
//	}

	public Set<K> keySet(){
		return data.keySet();
	}

	public int size(){
		return data.size();
	}

	@Override
	public String toString() {
		return data.toString();
	}

	/**
	 * 캐시 리소스를 정리합니다.
	 * 자동 정리가 활성화된 경우 스케줄러를 종료합니다.
	 * try-with-resources 또는 명시적 close() 호출 시 자동으로 실행됩니다.
	 */
	@Override
	public void close() {
		if (closed.compareAndSet(false, true)) {
			if (cleanupExecutor != null) {
				log.debug("Shutting down SBCacheMap cleanup executor");
				cleanupExecutor.shutdown();
				try {
					if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
						log.warn("Cleanup executor did not terminate in time, forcing shutdown");
						cleanupExecutor.shutdownNow();
					}
				} catch (InterruptedException e) {
					log.warn("Interrupted while waiting for cleanup executor termination", e);
					cleanupExecutor.shutdownNow();
					Thread.currentThread().interrupt();
				}
			}
		}
	}
}
