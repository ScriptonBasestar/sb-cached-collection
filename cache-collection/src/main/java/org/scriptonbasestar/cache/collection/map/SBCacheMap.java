package org.scriptonbasestar.cache.collection.map;

import lombok.extern.slf4j.Slf4j;
import org.scriptonbasestar.cache.collection.metrics.CacheMetrics;
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
 * 	Features:
 * 	- Access-based TTL: 마지막 접근 후 일정 시간이 지나면 만료
 * 	- Forced timeout: 최초 생성 후 절대 시간이 지나면 무조건 만료 (Builder.forcedTimeoutSec()로 설정)
 * 	- Auto cleanup: 주기적으로 만료된 항목 자동 제거 (Builder.enableAutoCleanup()으로 설정)
 * 	- Max size: 최대 캐시 크기 제한 (Builder.maxSize()로 설정)
 * 	- Metrics: 히트율, 미스율 등 통계 정보 (Builder.enableMetrics()로 설정)
 * 	- Per-item TTL: 항목별로 다른 TTL 설정 가능 (put(key, value, customTtlSec))
 */
@Slf4j
public class SBCacheMap<K, V> implements AutoCloseable {

	private final ConcurrentHashMap<K, Long> timeoutChecker;  // 마지막 접근 기반 TTL
	private final ConcurrentHashMap<K, Long> absoluteExpiry;  // 절대 만료 시간 (forced timeout)
	private final ConcurrentHashMap<K, V> data;
	private final LinkedHashMap<K, Long> insertionOrder;  // LRU를 위한 삽입 순서 (maxSize > 0일 때만 사용)
	private final int timeoutSec;
	private final SBCacheMapLoader<K, V> cacheLoader;
	private final Object lock = new Object();
	private final Object lruLock = new Object();  // LRU 작업용 별도 락
	private final ScheduledExecutorService cleanupExecutor;
	private final AtomicBoolean closed = new AtomicBoolean(false);
	private final int forcedTimeoutSec;  // 절대 만료 시간 (0이면 비활성화)
	private final int maxSize;  // 최대 캐시 크기 (0이면 무제한)
	private final CacheMetrics metrics;  // 통계 (null이면 비활성화)

	public SBCacheMap(SBCacheMapLoader<K,V> cacheLoader, int timeoutSec) {
		this(cacheLoader, timeoutSec, false, 5, 0, 0, false);
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
		this(cacheLoader, timeoutSec, enableAutoCleanup, cleanupIntervalMinutes, 0, 0, false);
	}

	/**
	 * 모든 옵션을 설정할 수 있는 생성자 (내부용)
	 *
	 * @param cacheLoader 캐시 로더
	 * @param timeoutSec 타임아웃 시간(초) - 접근 기반 TTL
	 * @param enableAutoCleanup 자동 정리 활성화 여부
	 * @param cleanupIntervalMinutes 정리 주기(분)
	 * @param forcedTimeoutSec 절대 만료 시간(초) - 0이면 비활성화
	 * @param maxSize 최대 캐시 크기 - 0이면 무제한
	 * @param enableMetrics 통계 수집 활성화 여부
	 */
	protected SBCacheMap(SBCacheMapLoader<K,V> cacheLoader, int timeoutSec,
					  boolean enableAutoCleanup, int cleanupIntervalMinutes,
					  int forcedTimeoutSec, int maxSize, boolean enableMetrics) {
		this.timeoutChecker = new ConcurrentHashMap<>();
		this.absoluteExpiry = new ConcurrentHashMap<>();
		this.data = new ConcurrentHashMap<>();
		this.cacheLoader = cacheLoader;
		this.timeoutSec = timeoutSec;
		this.forcedTimeoutSec = forcedTimeoutSec;
		this.maxSize = maxSize;
		this.metrics = enableMetrics ? new CacheMetrics() : null;
		this.insertionOrder = maxSize > 0 ? new LinkedHashMap<K, Long>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<K, Long> eldest) {
				return false;  // 수동으로 제거
			}
		} : null;

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
		private int forcedTimeoutSec = 0; // 기본값: 비활성화
		private int maxSize = 0; // 기본값: 무제한
		private boolean enableMetrics = false; // 기본값: 비활성화

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

		/**
		 * 절대 만료 시간을 설정합니다 (forced timeout).
		 * 자주 조회하더라도 이 시간이 지나면 무조건 폐기됩니다.
		 *
		 * @param seconds 절대 만료 시간(초), 0이면 비활성화
		 * @return Builder 인스턴스
		 */
		public Builder<K, V> forcedTimeoutSec(int seconds) {
			this.forcedTimeoutSec = seconds;
			return this;
		}

		/**
		 * 최대 캐시 크기를 설정합니다.
		 * 크기 초과 시 가장 오래 전에 사용된 항목이 자동으로 제거됩니다 (LRU).
		 *
		 * @param size 최대 크기, 0이면 무제한
		 * @return Builder 인스턴스
		 */
		public Builder<K, V> maxSize(int size) {
			this.maxSize = size;
			return this;
		}

		/**
		 * 캐시 통계 수집을 활성화합니다.
		 * 히트율, 미스율, 평균 로드 시간 등을 측정합니다.
		 *
		 * @param enable true면 통계 수집 활성화
		 * @return Builder 인스턴스
		 */
		public Builder<K, V> enableMetrics(boolean enable) {
			this.enableMetrics = enable;
			return this;
		}

		public SBCacheMap<K, V> build() {
			if (loader == null) {
				throw new IllegalStateException("loader must be set");
			}
			return new SBCacheMap<>(loader, timeoutSec, enableAutoCleanup,
				cleanupIntervalMinutes, forcedTimeoutSec, maxSize, enableMetrics);
		}
	}

	public V put(K key, V val) {
		return put(key, val, timeoutSec);
	}

	/**
	 * 캐시에 항목을 저장하며, 해당 항목에 대해 커스텀 TTL을 설정합니다.
	 *
	 * @param key 키
	 * @param val 값
	 * @param customTtlSec 이 항목에 대한 TTL (초)
	 * @return 이전 값 (없으면 null)
	 */
	public V put(K key, V val, int customTtlSec) {
		log.trace("put data - key : {} , value : {}, ttl : {}", key, val, customTtlSec);

		// maxSize 체크 및 LRU 제거
		if (maxSize > 0) {
			evictIfNecessary();
		}

		long now = System.currentTimeMillis();

		// 접근 기반 TTL (jitter 포함)
		long baseTimeoutMs = customTtlSec * 1000L;
		long jitterMs = ThreadLocalRandom.current().nextLong(customTtlSec * 1000L);
		timeoutChecker.put(key, now + baseTimeoutMs + jitterMs);

		// 절대 만료 시간 설정 (forced timeout)
		if (forcedTimeoutSec > 0) {
			absoluteExpiry.put(key, now + (forcedTimeoutSec * 1000L));
		}

		// LRU 추적 (maxSize > 0일 때만)
		if (insertionOrder != null) {
			synchronized (lruLock) {
				insertionOrder.put(key, now);
			}
		}

		return data.put(key, val);
	}

	/**
	 * maxSize 초과 시 가장 오래된 항목 제거 (LRU)
	 */
	private void evictIfNecessary() {
		if (data.size() >= maxSize) {
			synchronized (lruLock) {
				if (data.size() >= maxSize && insertionOrder != null && !insertionOrder.isEmpty()) {
					// 가장 오래된 항목 찾기
					K eldestKey = insertionOrder.keySet().iterator().next();
					log.trace("Evicting eldest key due to maxSize: {}", eldestKey);

					// 제거
					data.remove(eldestKey);
					timeoutChecker.remove(eldestKey);
					absoluteExpiry.remove(eldestKey);
					insertionOrder.remove(eldestKey);

					// 메트릭 기록
					if (metrics != null) {
						metrics.recordEviction(1);
					}
				}
			}
		}
	}

	public void putAll(Map<? extends K, ? extends V> m) {
		log.trace("putAll data");
		long now = System.currentTimeMillis();
		for (K key : m.keySet()) {
			// 접근 기반 TTL
			timeoutChecker.put(key, now + 1000L * timeoutSec);

			// 절대 만료 시간 설정 (forced timeout)
			if (forcedTimeoutSec > 0) {
				absoluteExpiry.put(key, now + (forcedTimeoutSec * 1000L));
			}
		}
		data.putAll(m);
	}

	public V get(final K key) {
		log.trace("get data - key : {}", key);

		// 1. 캐시된 값과 타임스탬프 확인 (ConcurrentHashMap이므로 동기화 불필요)
		V cachedValue = data.get(key);
		Long timestamp = timeoutChecker.get(key);
		Long absoluteExpiration = absoluteExpiry.get(key);

		// 절대 만료 시간 확인 (forced timeout)
		if (forcedTimeoutSec > 0 && absoluteExpiration != null) {
			if (System.currentTimeMillis() > absoluteExpiration) {
				log.trace("Forced timeout expired for key: {}", key);
				// 절대 만료 시간 초과 - 재로드 필요
				cachedValue = null;
				timestamp = null;
			}
		}

		// 접근 기반 TTL 확인
		if (cachedValue != null && timestamp != null && TimeCheckerUtil.checkExpired(timestamp, timeoutSec)) {
			// 캐시 히트
			if (metrics != null) {
				metrics.recordHit();
			}
			// LRU 업데이트 (액세스 순서)
			if (insertionOrder != null) {
				synchronized (lruLock) {
					insertionOrder.put(key, System.currentTimeMillis());
				}
			}
			return cachedValue;
		}

		// 캐시 미스
		if (metrics != null) {
			metrics.recordMiss();
		}

		// 2. 캐시 미스 또는 만료 - 데이터 로드 (동기화 필요)
		synchronized (lock) {
			// Double-check: 다른 스레드가 이미 로드했을 수 있음
			cachedValue = data.get(key);
			timestamp = timeoutChecker.get(key);
			absoluteExpiration = absoluteExpiry.get(key);

			// 절대 만료 시간 재확인
			if (forcedTimeoutSec > 0 && absoluteExpiration != null) {
				if (System.currentTimeMillis() > absoluteExpiration) {
					cachedValue = null;
					timestamp = null;
				}
			}

			// 접근 기반 TTL 재확인
			if (cachedValue != null && timestamp != null && TimeCheckerUtil.checkExpired(timestamp, timeoutSec)) {
				return cachedValue;
			}

			// 실제 로드
			long loadStartTime = System.nanoTime();
			try {
				V val = cacheLoader.loadOne(key);
				put(key, val);

				// 로드 성공 메트릭
				if (metrics != null) {
					long loadTime = System.nanoTime() - loadStartTime;
					metrics.recordLoadSuccess(loadTime);
				}

				return val;
			} catch (SBCacheLoadFailException e) {
				data.remove(key);
				timeoutChecker.remove(key);
				absoluteExpiry.remove(key);

				// 로드 실패 메트릭
				if (metrics != null) {
					metrics.recordLoadFailure();
				}

				throw e;
			}
		}
	}

	/**
	 * 현재 캐시에 저장된 모든 항목을 반환합니다.
	 * 반환된 Map은 수정 불가능한(unmodifiable) 뷰입니다.
	 *
	 * @return 캐시에 저장된 모든 키-값 쌍의 수정 불가능한 뷰
	 */
	public Map<K, V> getAll() {
		return Collections.unmodifiableMap(data);
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
			absoluteExpiry.remove(key);
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
	 * 만료된 항목들을 제거합니다.
	 * 자동 정리가 활성화된 경우 주기적으로 호출됩니다.
	 */
	public void removeExpired() {
		// ConcurrentModificationException 방지를 위해 만료된 키 목록을 먼저 수집
		List<K> expiredKeys = new ArrayList<>();
		long now = System.currentTimeMillis();

		// 접근 기반 TTL 확인
		for (Map.Entry<K, Long> entry : timeoutChecker.entrySet()) {
			if (!TimeCheckerUtil.checkExpired(entry.getValue(), timeoutSec)) {
				expiredKeys.add(entry.getKey());
			}
		}

		// 절대 만료 시간 확인 (forced timeout)
		if (forcedTimeoutSec > 0) {
			for (Map.Entry<K, Long> entry : absoluteExpiry.entrySet()) {
				if (now > entry.getValue() && !expiredKeys.contains(entry.getKey())) {
					expiredKeys.add(entry.getKey());
				}
			}
		}

		// 수집된 키들을 제거
		for (K key : expiredKeys) {
			timeoutChecker.remove(key);
			absoluteExpiry.remove(key);
			data.remove(key);
			log.trace("Removed expired key: {}", key);
		}

		if (!expiredKeys.isEmpty()) {
			log.debug("Cleaned up {} expired entries", expiredKeys.size());
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
	 * 캐시 워밍업: loader.loadAll()을 호출하여 모든 데이터를 미리 로드합니다.
	 * 애플리케이션 시작 시 초기 지연을 방지하기 위해 사용합니다.
	 *
	 * @throws SBCacheLoadFailException 로드 실패 시
	 */
	public void warmUp() throws SBCacheLoadFailException {
		log.debug("Starting cache warm-up");
		Map<K, V> allData = cacheLoader.loadAll();
		if (allData != null && !allData.isEmpty()) {
			putAll(allData);
			log.info("Cache warmed up with {} items", allData.size());
		}
	}

	/**
	 * 특정 키 목록에 대해 캐시 워밍업을 수행합니다.
	 *
	 * @param keys 미리 로드할 키 목록
	 */
	public void warmUp(Collection<K> keys) {
		log.debug("Starting cache warm-up for {} keys", keys.size());
		int loadedCount = 0;
		for (K key : keys) {
			try {
				V value = cacheLoader.loadOne(key);
				put(key, value);
				loadedCount++;
			} catch (SBCacheLoadFailException e) {
				log.warn("Failed to warm up key: {}", key, e);
			}
		}
		log.info("Cache warmed up with {}/{} items", loadedCount, keys.size());
	}

	/**
	 * 캐시 통계를 반환합니다.
	 *
	 * @return CacheMetrics 인스턴스, 메트릭이 비활성화되어 있으면 null
	 */
	public CacheMetrics metrics() {
		return metrics;
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
