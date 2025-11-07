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
 * 	Features:
 * 	- Access-based TTL: 마지막 접근 후 일정 시간이 지나면 만료
 * 	- Forced timeout: 최초 생성 후 절대 시간이 지나면 무조건 만료 (Builder.forcedTimeoutSec()로 설정)
 * 	- Auto cleanup: 주기적으로 만료된 항목 자동 제거 (Builder.enableAutoCleanup()으로 설정)
 */
@Slf4j
public class SBCacheMap<K, V> implements AutoCloseable {

	private final ConcurrentHashMap<K, Long> timeoutChecker;  // 마지막 접근 기반 TTL
	private final ConcurrentHashMap<K, Long> absoluteExpiry;  // 절대 만료 시간 (forced timeout)
	private final ConcurrentHashMap<K, V> data;
	private final int timeoutSec;
	private final SBCacheMapLoader<K, V> cacheLoader;
	private final Object lock = new Object();
	private final ScheduledExecutorService cleanupExecutor;
	private final AtomicBoolean closed = new AtomicBoolean(false);
	private final int forcedTimeoutSec;  // 절대 만료 시간 (0이면 비활성화)

	public SBCacheMap(SBCacheMapLoader<K,V> cacheLoader, int timeoutSec) {
		this(cacheLoader, timeoutSec, false, 5, 0);
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
		this(cacheLoader, timeoutSec, enableAutoCleanup, cleanupIntervalMinutes, 0);
	}

	/**
	 * 모든 옵션을 설정할 수 있는 생성자
	 *
	 * @param cacheLoader 캐시 로더
	 * @param timeoutSec 타임아웃 시간(초) - 접근 기반 TTL
	 * @param enableAutoCleanup 자동 정리 활성화 여부
	 * @param cleanupIntervalMinutes 정리 주기(분)
	 * @param forcedTimeoutSec 절대 만료 시간(초) - 0이면 비활성화
	 */
	public SBCacheMap(SBCacheMapLoader<K,V> cacheLoader, int timeoutSec,
					  boolean enableAutoCleanup, int cleanupIntervalMinutes,
					  int forcedTimeoutSec) {
		this.timeoutChecker = new ConcurrentHashMap<>();
		this.absoluteExpiry = new ConcurrentHashMap<>();
		this.data = new ConcurrentHashMap<>();
		this.cacheLoader = cacheLoader;
		this.timeoutSec = timeoutSec;
		this.forcedTimeoutSec = forcedTimeoutSec;

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

		public SBCacheMap<K, V> build() {
			if (loader == null) {
				throw new IllegalStateException("loader must be set");
			}
			return new SBCacheMap<>(loader, timeoutSec, enableAutoCleanup, cleanupIntervalMinutes, forcedTimeoutSec);
		}
	}

	public V put(K key, V val) {
		log.trace("put data - key : {} , value : {}", key, val);

		long now = System.currentTimeMillis();

		// 접근 기반 TTL (jitter 포함)
		long baseTimeoutMs = timeoutSec * 1000L;
		long jitterMs = ThreadLocalRandom.current().nextLong(timeoutSec * 1000L);
		timeoutChecker.put(key, now + baseTimeoutMs + jitterMs);

		// 절대 만료 시간 설정 (forced timeout)
		if (forcedTimeoutSec > 0) {
			absoluteExpiry.put(key, now + (forcedTimeoutSec * 1000L));
		}

		return data.put(key, val);
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
			return cachedValue;
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
			try {
				V val = cacheLoader.loadOne(key);
				put(key, val);
				return val;
			} catch (SBCacheLoadFailException e) {
				data.remove(key);
				timeoutChecker.remove(key);
				absoluteExpiry.remove(key);
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
