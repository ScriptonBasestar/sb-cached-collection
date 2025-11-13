package org.scriptonbasestar.cache.collection.map;

import org.scriptonbasestar.cache.collection.eviction.*;
import org.scriptonbasestar.cache.collection.jmx.CacheStatistics;
import org.scriptonbasestar.cache.collection.jmx.JmxHelper;
import org.scriptonbasestar.cache.collection.metrics.CacheMetrics;
import org.scriptonbasestar.cache.collection.storage.ReferenceBasedStorage;
import org.scriptonbasestar.cache.core.loader.SBCacheMapLoader;
import org.scriptonbasestar.cache.core.strategy.EvictionPolicy;
import org.scriptonbasestar.cache.core.strategy.EvictionStrategy;
import org.scriptonbasestar.cache.core.strategy.LoadStrategy;
import org.scriptonbasestar.cache.core.strategy.ReferenceType;
import org.scriptonbasestar.cache.core.strategy.RefreshStrategy;
import org.scriptonbasestar.cache.core.strategy.WriteStrategy;
import org.scriptonbasestar.cache.core.writer.SBCacheMapWriter;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;
import org.scriptonbasestar.cache.core.util.TimeCheckerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
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
public class SBCacheMap<K, V> implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(SBCacheMap.class);

	private final ConcurrentHashMap<K, Long> timeoutChecker;  // 마지막 접근 기반 TTL
	private final ConcurrentHashMap<K, Long> absoluteExpiry;  // 절대 만료 시간 (forced timeout)
	private final ReferenceBasedStorage<K, V> data;  // Reference 기반 저장소 (STRONG, SOFT, WEAK 지원)
	private final LinkedHashMap<K, Long> insertionOrder;  // LRU를 위한 삽입 순서 (maxSize > 0일 때만 사용)
	private final Set<K> refreshingKeys;  // 현재 백그라운드 갱신 중인 키들 (ASYNC 전용)
	private final int timeoutSec;
	private final SBCacheMapLoader<K, V> cacheLoader;
	private final Object lock = new Object();
	private final Object lruLock = new Object();  // LRU 작업용 별도 락
	private final ScheduledExecutorService cleanupExecutor;  // 자동 정리용
	private final ExecutorService asyncExecutor;  // 비동기 로드용 (LoadStrategy.ASYNC일 때 사용)
	private final AtomicBoolean closed = new AtomicBoolean(false);
	private final int forcedTimeoutSec;  // 절대 만료 시간 (0이면 비활성화)
	private final int maxSize;  // 최대 캐시 크기 (0이면 무제한)
	private final CacheMetrics metrics;  // 통계 (null이면 비활성화)
	private final LoadStrategy loadStrategy;  // 로드 전략 (SYNC 또는 ASYNC)
	private final WriteStrategy writeStrategy;  // 쓰기 전략 (READ_ONLY, WRITE_THROUGH, WRITE_BEHIND)
	private final SBCacheMapWriter<K, V> cacheWriter;  // 데이터 소스 writer (null이면 READ_ONLY)
	private final BlockingQueue<WriteOperation<K, V>> writeQueue;  // WRITE_BEHIND용 큐
	private final ExecutorService writeBehindExecutor;  // WRITE_BEHIND용 executor
	private final int writeBehindBatchSize;  // WRITE_BEHIND 배치 크기
	private final int writeBehindIntervalSeconds;  // WRITE_BEHIND 플러시 주기(초)
	private final int writeBehindMaxRetries;  // WRITE_BEHIND 최대 재시도 횟수
	private final int writeBehindRetryDelayMs;  // WRITE_BEHIND 재시도 대기 시간(밀리초)
	private final RefreshStrategy refreshStrategy;  // 갱신 전략 (ON_MISS, REFRESH_AHEAD)
	private final double refreshAheadFactor;  // Refresh-Ahead 시작 시점 (0.0~1.0, 예: 0.8 = TTL의 80%)
	private final ExecutorService refreshAheadExecutor;  // Refresh-Ahead용 executor
	private final ConcurrentHashMap<K, Long> creationTimes;  // 항목 생성 시간 (Refresh-Ahead 계산용)
	private final EvictionPolicy evictionPolicy;  // 축출 정책 (LRU, LFU, FIFO, RANDOM, TTL)
	private final EvictionStrategy<K> evictionStrategy;  // 축출 전략 인스턴스
	private final ReferenceType referenceType;  // 참조 타입 (STRONG, SOFT, WEAK)
	private volatile CacheStatistics jmxMBean;  // JMX MBean (null이면 비활성화)
	private volatile String jmxCacheName;  // JMX 캐시 이름

	public SBCacheMap(SBCacheMapLoader<K,V> cacheLoader, int timeoutSec) {
		this(cacheLoader, timeoutSec, false, 5, 0, 0, false, LoadStrategy.SYNC,
			WriteStrategy.READ_ONLY, null, 100, 5, 3, 1000,
			RefreshStrategy.ON_MISS, 0.8, EvictionPolicy.LRU, ReferenceType.STRONG);
	}

	/**
	 * 테스트용 간단한 생성자 (loader 없음, maxSize와 evictionPolicy만 지정)
	 *
	 * @param timeoutSec 타임아웃 시간(초)
	 * @param maxSize 최대 크기
	 * @param evictionPolicy 제거 정책
	 */
	public SBCacheMap(int timeoutSec, int maxSize, EvictionPolicy evictionPolicy) {
		this(null, timeoutSec, false, 5, 0, maxSize, false, LoadStrategy.SYNC,
			WriteStrategy.READ_ONLY, null, 100, 5, 3, 1000,
			RefreshStrategy.ON_MISS, 0.8, evictionPolicy, ReferenceType.STRONG);
	}

	/**
	 * 테스트용 간단한 생성자 (loader 없음, maxSize만 지정, evictionPolicy는 기본값 LRU)
	 *
	 * @param timeoutSec 타임아웃 시간(초)
	 * @param maxSize 최대 크기
	 */
	public SBCacheMap(int timeoutSec, int maxSize) {
		this(timeoutSec, maxSize, EvictionPolicy.LRU);
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
		this(cacheLoader, timeoutSec, enableAutoCleanup, cleanupIntervalMinutes, 0, 0, false, LoadStrategy.SYNC,
			WriteStrategy.READ_ONLY, null, 100, 5, 3, 1000,
			RefreshStrategy.ON_MISS, 0.8, EvictionPolicy.LRU, ReferenceType.STRONG);
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
	 * @param loadStrategy 로드 전략 (SYNC 또는 ASYNC)
	 * @param writeStrategy 쓰기 전략 (READ_ONLY, WRITE_THROUGH, WRITE_BEHIND)
	 * @param cacheWriter 데이터 소스 writer
	 * @param writeBehindBatchSize WRITE_BEHIND 배치 크기
	 * @param writeBehindIntervalSeconds WRITE_BEHIND 플러시 주기(초)
	 * @param writeBehindMaxRetries WRITE_BEHIND 최대 재시도 횟수
	 * @param writeBehindRetryDelayMs WRITE_BEHIND 재시도 대기 시간(밀리초)
	 * @param refreshStrategy 갱신 전략 (ON_MISS, REFRESH_AHEAD)
	 * @param refreshAheadFactor Refresh-Ahead 시작 시점 (0.0~1.0)
	 * @param evictionPolicy 축출 정책 (LRU, LFU, FIFO, RANDOM, TTL)
	 * @param referenceType 참조 타입 (STRONG, SOFT, WEAK)
	 */
	protected SBCacheMap(SBCacheMapLoader<K,V> cacheLoader, int timeoutSec,
					  boolean enableAutoCleanup, int cleanupIntervalMinutes,
					  int forcedTimeoutSec, int maxSize, boolean enableMetrics, LoadStrategy loadStrategy,
					  WriteStrategy writeStrategy, SBCacheMapWriter<K, V> cacheWriter,
					  int writeBehindBatchSize, int writeBehindIntervalSeconds,
					  int writeBehindMaxRetries, int writeBehindRetryDelayMs,
					  RefreshStrategy refreshStrategy, double refreshAheadFactor,
					  EvictionPolicy evictionPolicy, ReferenceType referenceType) {
		this.timeoutChecker = new ConcurrentHashMap<>();
		this.absoluteExpiry = new ConcurrentHashMap<>();
		// 참조 타입 초기화 (STRONG, SOFT, WEAK)
		this.referenceType = referenceType != null ? referenceType : ReferenceType.STRONG;
		this.data = new ReferenceBasedStorage<>(this.referenceType);
		this.refreshingKeys = ConcurrentHashMap.newKeySet();  // Thread-safe set for tracking background refreshes
		this.creationTimes = new ConcurrentHashMap<>();  // For Refresh-Ahead calculations
		this.cacheLoader = cacheLoader;
		this.timeoutSec = timeoutSec;
		this.forcedTimeoutSec = forcedTimeoutSec;
		this.maxSize = maxSize;
		this.metrics = enableMetrics ? new CacheMetrics() : null;
		this.loadStrategy = loadStrategy != null ? loadStrategy : LoadStrategy.SYNC;
		this.writeStrategy = writeStrategy != null ? writeStrategy : WriteStrategy.READ_ONLY;
		this.cacheWriter = cacheWriter;
		this.writeBehindBatchSize = writeBehindBatchSize;
		this.writeBehindIntervalSeconds = writeBehindIntervalSeconds;
		this.writeBehindMaxRetries = Math.max(0, writeBehindMaxRetries);  // 최소 0
		this.writeBehindRetryDelayMs = Math.max(0, writeBehindRetryDelayMs);  // 최소 0
		this.refreshStrategy = refreshStrategy != null ? refreshStrategy : RefreshStrategy.ON_MISS;
		this.refreshAheadFactor = Math.max(0.0, Math.min(1.0, refreshAheadFactor));  // Clamp to [0.0, 1.0]

		// 축출 정책 초기화
		this.evictionPolicy = evictionPolicy != null ? evictionPolicy : EvictionPolicy.LRU;
		this.evictionStrategy = createEvictionStrategy(this.evictionPolicy);

		this.insertionOrder = maxSize > 0 ? new LinkedHashMap<K, Long>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<K, Long> eldest) {
				return false;  // 수동으로 제거
			}
		} : null;

		// 자동 정리용 ExecutorService
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

		// 비동기 로드용 ExecutorService (LoadStrategy.ASYNC일 때만 생성)
		if (this.loadStrategy == LoadStrategy.ASYNC) {
			this.asyncExecutor = Executors.newFixedThreadPool(5, r -> {
				Thread t = new Thread(r, "SBCacheMap-AsyncLoader");
				t.setDaemon(true);
				return t;
			});
			log.debug("Async load strategy enabled with {} threads", 5);
		} else {
			this.asyncExecutor = null;
		}

		// WRITE_BEHIND용 ExecutorService와 큐 (WRITE_BEHIND일 때만 생성)
		if (this.writeStrategy == WriteStrategy.WRITE_BEHIND) {
			this.writeQueue = new LinkedBlockingQueue<>();
			this.writeBehindExecutor = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "SBCacheMap-WriteBehind");
				t.setDaemon(true);
				return t;
			});
			// 주기적으로 큐를 플러시
			scheduleWriteBehindFlush();
			log.debug("Write-behind strategy enabled: batchSize={}, interval={}s",
				writeBehindBatchSize, writeBehindIntervalSeconds);
		} else {
			this.writeQueue = null;
			this.writeBehindExecutor = null;
		}

		// REFRESH_AHEAD용 ExecutorService (REFRESH_AHEAD일 때만 생성)
		if (this.refreshStrategy == RefreshStrategy.REFRESH_AHEAD) {
			this.refreshAheadExecutor = Executors.newFixedThreadPool(2, r -> {
				Thread t = new Thread(r, "SBCacheMap-RefreshAhead");
				t.setDaemon(true);
				return t;
			});
			log.debug("Refresh-ahead strategy enabled: factor={}",  refreshAheadFactor);
		} else {
			this.refreshAheadExecutor = null;
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
		private LoadStrategy loadStrategy = LoadStrategy.SYNC; // 기본값: 동기
		private boolean enableJmx = false; // 기본값: 비활성화
		private String jmxCacheName = null; // JMX 캐시 이름
		private WriteStrategy writeStrategy = WriteStrategy.READ_ONLY; // 기본값: READ_ONLY
		private SBCacheMapWriter<K, V> writer = null; // Writer (null이면 READ_ONLY)
		private int writeBehindBatchSize = 100; // WRITE_BEHIND 배치 크기
		private int writeBehindIntervalSeconds = 5; // WRITE_BEHIND 플러시 주기(초)
		private int writeBehindMaxRetries = 3; // WRITE_BEHIND 최대 재시도 횟수 (기본값: 3)
		private int writeBehindRetryDelayMs = 1000; // WRITE_BEHIND 재시도 대기 시간(밀리초) (기본값: 1000ms)
		private RefreshStrategy refreshStrategy = RefreshStrategy.ON_MISS; // 기본값: ON_MISS
		private double refreshAheadFactor = 0.8; // Refresh-Ahead 시작 시점 (기본값: 80%)
		private EvictionPolicy evictionPolicy = EvictionPolicy.LRU; // 기본값: LRU
		private ReferenceType referenceType = ReferenceType.STRONG; // 기본값: STRONG

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

		/**
		 * 로드 전략을 설정합니다.
		 *
		 * <p>SYNC (기본값): 캐시 미스 시 즉시 로드하고 대기 (블로킹)</p>
		 * <p>ASYNC: 캐시 미스 시 이전 데이터 반환 + 백그라운드 갱신 (SBAsyncCacheMap 동작)</p>
		 *
		 * @param strategy 로드 전략 (SYNC 또는 ASYNC)
		 * @return Builder 인스턴스
		 */
		public Builder<K, V> loadStrategy(LoadStrategy strategy) {
			this.loadStrategy = strategy;
			return this;
		}

		/**
		 * JMX 모니터링을 활성화합니다.
		 * <p>
		 * JMX를 통해 JConsole/VisualVM에서 캐시 통계를 실시간으로 모니터링할 수 있습니다.
		 * enableMetrics()도 자동으로 활성화됩니다.
		 * </p>
		 *
		 * @param cacheName JMX ObjectName에 사용될 캐시 이름
		 * @return Builder 인스턴스
		 */
		public Builder<K, V> enableJmx(String cacheName) {
			this.enableJmx = true;
			this.jmxCacheName = cacheName;
			this.enableMetrics = true; // JMX는 메트릭 필요
			return this;
		}

		/**
		 * 쓰기 전략을 설정합니다.
		 *
		 * @param strategy 쓰기 전략 (READ_ONLY, WRITE_THROUGH, WRITE_BEHIND)
		 * @return Builder 인스턴스
		 */
		public Builder<K, V> writeStrategy(WriteStrategy strategy) {
			this.writeStrategy = strategy;
			return this;
		}

		/**
		 * CacheWriter를 설정합니다 (WRITE_THROUGH/WRITE_BEHIND 사용 시 필수).
		 *
		 * @param writer 데이터 소스 writer
		 * @return Builder 인스턴스
		 */
		public Builder<K, V> writer(SBCacheMapWriter<K, V> writer) {
			this.writer = writer;
			return this;
		}

		/**
		 * WRITE_BEHIND 배치 크기를 설정합니다.
		 *
		 * @param batchSize 배치 크기 (기본값: 100)
		 * @return Builder 인스턴스
		 */
		public Builder<K, V> writeBehindBatchSize(int batchSize) {
			this.writeBehindBatchSize = batchSize;
			return this;
		}

		/**
		 * WRITE_BEHIND 플러시 주기를 설정합니다.
		 *
		 * @param intervalSeconds 플러시 주기(초, 기본값: 5초)
		 * @return Builder 인스턴스
		 */
		public Builder<K, V> writeBehindIntervalSeconds(int intervalSeconds) {
			this.writeBehindIntervalSeconds = intervalSeconds;
			return this;
		}

		/**
		 * Write-behind 실패 시 최대 재시도 횟수를 설정합니다.
		 *
		 * @param maxRetries 최대 재시도 횟수 (기본값: 3)
		 * @return Builder 인스턴스
		 */
		public Builder<K, V> writeBehindMaxRetries(int maxRetries) {
			this.writeBehindMaxRetries = maxRetries;
			return this;
		}

		/**
		 * Write-behind 재시도 간 대기 시간(밀리초)을 설정합니다.
		 *
		 * @param retryDelayMs 재시도 대기 시간(밀리초) (기본값: 1000ms)
		 * @return Builder 인스턴스
		 */
		public Builder<K, V> writeBehindRetryDelayMs(int retryDelayMs) {
			this.writeBehindRetryDelayMs = retryDelayMs;
			return this;
		}

		/**
		 * Refresh 전략을 설정합니다.
		 *
		 * @param strategy Refresh 전략 (기본값: ON_MISS)
		 * @return Builder 인스턴스
		 */
		public Builder<K, V> refreshStrategy(RefreshStrategy strategy) {
			this.refreshStrategy = strategy;
			return this;
		}

		/**
		 * Refresh-Ahead 시작 시점을 설정합니다.
		 * <p>
		 * TTL의 N% 경과 시 백그라운드 갱신을 시작합니다.
		 * </p>
		 *
		 * @param factor Refresh-Ahead 시작 시점 (0.0~1.0, 기본값: 0.8 = 80%)
		 * @return Builder 인스턴스
		 */
		public Builder<K, V> refreshAheadFactor(double factor) {
			this.refreshAheadFactor = factor;
			return this;
		}

		/**
		 * 축출 정책 설정
		 * <p>
		 * maxSize > 0일 때만 의미가 있습니다.
		 * </p>
		 *
		 * @param policy 축출 정책 (LRU, LFU, FIFO, RANDOM, TTL)
		 * @return Builder
		 */
		public Builder<K, V> evictionPolicy(EvictionPolicy policy) {
			this.evictionPolicy = policy;
			return this;
		}

		/**
		 * 참조 타입을 설정합니다.
		 *
		 * <p>STRONG (기본값): 일반 참조, GC가 절대 회수하지 않음</p>
		 * <p>SOFT: 메모리 부족 시 GC가 회수 (대용량 캐시에 적합)</p>
		 * <p>WEAK: 다음 GC 사이클에서 회수 (임시 캐시에 적합)</p>
		 *
		 * @param type 참조 타입 (STRONG, SOFT, WEAK)
		 * @return Builder 인스턴스
		 */
		public Builder<K, V> referenceType(ReferenceType type) {
			this.referenceType = type;
			return this;
		}

		public SBCacheMap<K, V> build() {
			if (loader == null) {
				throw new IllegalStateException("loader must be set");
			}
			SBCacheMap<K, V> cache = new SBCacheMap<>(loader, timeoutSec, enableAutoCleanup,
				cleanupIntervalMinutes, forcedTimeoutSec, maxSize, enableMetrics, loadStrategy,
				writeStrategy, writer, writeBehindBatchSize, writeBehindIntervalSeconds,
				writeBehindMaxRetries, writeBehindRetryDelayMs,
				refreshStrategy, refreshAheadFactor, evictionPolicy, referenceType);

			// JMX 등록
			if (enableJmx && jmxCacheName != null) {
				cache.registerJmx(jmxCacheName);
			}

			return cache;
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

		// 접근 기반 TTL (jitter 포함 - 최대 10%)
		long baseTimeoutMs = customTtlSec * 1000L;
		long jitterMs = ThreadLocalRandom.current().nextLong(Math.max(1, baseTimeoutMs / 10));
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

		V oldValue = data.put(key, val);

		// Eviction strategy tracking
		if (evictionStrategy != null) {
			evictionStrategy.onInsert(key);
		}

		// Refresh-Ahead: 생성 시간 기록
		if (refreshStrategy == RefreshStrategy.REFRESH_AHEAD) {
			creationTimes.put(key, now);
		}

		// Write to data source (WRITE_THROUGH or WRITE_BEHIND)
		performWrite(key, val);

		updateJmxSize();
		return oldValue;
	}

	/**
	 * maxSize 초과 시 가장 오래된 항목 제거 (LRU)
	 */
	private void evictIfNecessary() {
		if (data.size() >= maxSize && evictionStrategy != null) {
			synchronized (lruLock) {
				if (data.size() >= maxSize) {
					// 제거 대상 선택 (정책에 따라)
					K victimKey = evictionStrategy.selectEvictionCandidate();
					if (victimKey != null) {
						log.trace("Evicting key due to maxSize (policy={}): {}", evictionPolicy, victimKey);

						// 제거
						data.remove(victimKey);
						timeoutChecker.remove(victimKey);
						absoluteExpiry.remove(victimKey);
						creationTimes.remove(victimKey);
						if (insertionOrder != null) {
							insertionOrder.remove(victimKey);
						}

						// 메트릭 기록
						if (metrics != null) {
							metrics.recordEviction(1);
						}

						// JMX 업데이트
						updateJmxSize();
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

	/**
	 * 캐시에서 값을 조회합니다. 값이 없으면 로더를 통해 로드하지 않고 null을 반환합니다.
	 * Spring Cache 통합 등에서 사용됩니다.
	 *
	 * @param key 조회할 키
	 * @return 캐시된 값, 없거나 만료된 경우 null
	 */
	public V getIfPresent(K key) {
		V cachedValue = data.get(key);
		Long timestamp = timeoutChecker.get(key);
		Long absoluteExpiration = absoluteExpiry.get(key);

		// 절대 만료 시간 확인
		if (forcedTimeoutSec > 0 && absoluteExpiration != null) {
			if (System.currentTimeMillis() > absoluteExpiration) {
				return null;
			}
		}

		// 접근 기반 TTL 확인
		if (cachedValue != null && timestamp != null && System.currentTimeMillis() < timestamp) {
			// 캐시 히트
			if (metrics != null) {
				metrics.recordHit();
			}
			return cachedValue;
		}

		// 캐시 미스 또는 만료
		if (metrics != null) {
			metrics.recordMiss();
		}
		return null;
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

		// 접근 기반 TTL 확인 (timestamp는 만료 시간을 저장)
		if (cachedValue != null && timestamp != null && System.currentTimeMillis() < timestamp) {
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

			// Eviction strategy tracking
			if (evictionStrategy != null) {
				evictionStrategy.onAccess(key);
			}

			// Refresh-Ahead: TTL의 N% 경과 시 백그라운드 갱신
			if (refreshStrategy == RefreshStrategy.REFRESH_AHEAD) {
				checkAndRefreshAhead(key);
			}

			return cachedValue;
		}

		// 캐시 미스
		if (metrics != null) {
			metrics.recordMiss();
		}

		// ASYNC 전략: 만료된 데이터가 있으면 즉시 반환 + 백그라운드 갱신
		if (loadStrategy == LoadStrategy.ASYNC && cachedValue != null && cacheLoader != null) {
			log.trace("ASYNC strategy: returning stale data for key: {} and triggering background refresh", key);

			// 이미 갱신 중이 아닌 경우에만 백그라운드 갱신 시작
			if (refreshingKeys.add(key)) {
				// 백그라운드에서 비동기 로드
				asyncExecutor.submit(() -> {
					try {
						log.trace("Background refresh started for key: {}", key);
						long loadStartTime = System.nanoTime();
						V val = cacheLoader.loadOne(key);
						put(key, val);

						// 로드 성공 메트릭
						if (metrics != null) {
							long loadTime = System.nanoTime() - loadStartTime;
							metrics.recordLoadSuccess(loadTime);
						}
						log.trace("Background refresh completed for key: {}", key);
					} catch (SBCacheLoadFailException e) {
						log.warn("Background refresh failed for key: {}", key, e);
						// 로드 실패 메트릭
						if (metrics != null) {
							metrics.recordLoadFailure();
						}
					} finally {
						// 갱신 완료 후 키 제거
						refreshingKeys.remove(key);
					}
				});
			} else {
				log.trace("Background refresh already in progress for key: {}", key);
			}

			// 만료된 데이터를 즉시 반환
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

			// 접근 기반 TTL 재확인 (timestamp는 만료 시간을 저장)
			if (cachedValue != null && timestamp != null && System.currentTimeMillis() < timestamp) {
				return cachedValue;
			}

			// Loader가 없으면 null 반환
			if (cacheLoader == null) {
				return null;
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
		return Collections.unmodifiableMap(data.toMap());
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
			// Type-safe casting
			@SuppressWarnings("unchecked")
			K typedKey = (K) key;

			timeoutChecker.remove(key);
			absoluteExpiry.remove(key);
			creationTimes.remove(key);  // Refresh-Ahead 생성 시간 정리
			V removed = data.remove(typedKey);
			if (evictionStrategy != null) {
				evictionStrategy.onRemove(typedKey);
			}

			// Delete from data source (WRITE_THROUGH or WRITE_BEHIND)
			performDelete(typedKey);

			updateJmxSize();
			return removed;
		}
	}

	/**
	 * 모든 캐시 항목을 제거합니다.
	 */
	public void removeAll() {
		synchronized (lock) {
			timeoutChecker.clear();
			absoluteExpiry.clear();
			creationTimes.clear();
			if (insertionOrder != null) {
				insertionOrder.clear();
			}
			if (evictionStrategy != null) {
				evictionStrategy.clear();
			}
			data.clear();
			updateJmxSize();
		}
	}

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
	 * JMX 모니터링을 등록합니다.
	 * <p>
	 * 이미 등록된 경우 무시됩니다.
	 * </p>
	 *
	 * @param cacheName JMX ObjectName에 사용될 캐시 이름
	 */
	public void registerJmx(String cacheName) {
		if (metrics == null) {
			log.warn("Cannot register JMX: metrics is not enabled");
			return;
		}
		if (jmxMBean != null) {
			log.debug("JMX already registered for cache: {}", cacheName);
			return;
		}

		try {
			this.jmxCacheName = cacheName;
			this.jmxMBean = JmxHelper.registerCache(metrics, cacheName, maxSize);
			updateJmxSize();
			log.info("Registered JMX MBean for cache: {}", cacheName);
		} catch (Exception e) {
			log.error("Failed to register JMX MBean for cache: {}", cacheName, e);
		}
	}

	/**
	 * JMX 모니터링을 해제합니다.
	 */
	public void unregisterJmx() {
		if (jmxCacheName != null) {
			try {
				JmxHelper.unregisterCache(jmxCacheName);
				this.jmxMBean = null;
				this.jmxCacheName = null;
				log.info("Unregistered JMX MBean for cache: {}", jmxCacheName);
			} catch (Exception e) {
				log.error("Failed to unregister JMX MBean", e);
			}
		}
	}

	/**
	 * JMX MBean의 currentSize를 업데이트합니다.
	 */
	private void updateJmxSize() {
		if (jmxMBean != null) {
			jmxMBean.updateCurrentSize(data.size());
		}
	}

	// ===== Write Strategy Methods =====

	/**
	 * WRITE_BEHIND 플러시 스케줄링
	 */
	private void scheduleWriteBehindFlush() {
		writeBehindExecutor.submit(() -> {
			while (!closed.get()) {
				try {
					Thread.sleep(writeBehindIntervalSeconds * 1000L);
					flushWriteBehindQueue();
				} catch (InterruptedException e) {
					log.debug("Write-behind flush interrupted");
					Thread.currentThread().interrupt();
					break;
				}
			}
		});
	}

	/**
	 * WRITE_BEHIND 큐를 플러시합니다.
	 */
	private void flushWriteBehindQueue() {
		if (writeQueue == null || writeQueue.isEmpty()) {
			return;
		}

		List<WriteOperation<K, V>> batch = new ArrayList<>(writeBehindBatchSize);
		writeQueue.drainTo(batch, writeBehindBatchSize);

		if (batch.isEmpty()) {
			return;
		}

		// PUT와 DELETE 분리
		Map<K, V> putMap = new HashMap<>();
		List<K> deleteKeys = new ArrayList<>();

		for (WriteOperation<K, V> op : batch) {
			if (op.getType() == WriteOperation.Type.PUT) {
				putMap.put(op.getKey(), op.getValue());
			} else {
				deleteKeys.add(op.getKey());
			}
		}

		// 배치 쓰기 (재시도 포함)
		if (!putMap.isEmpty()) {
			executeWithRetry(() -> {
				cacheWriter.writeAll(putMap);
				log.debug("Write-behind flushed {} PUT operations", putMap.size());
			}, "PUT operations", putMap.size());
		}

		if (!deleteKeys.isEmpty()) {
			executeWithRetry(() -> {
				cacheWriter.deleteAll(deleteKeys);
				log.debug("Write-behind flushed {} DELETE operations", deleteKeys.size());
			}, "DELETE operations", deleteKeys.size());
		}
	}

	/**
	 * Write-behind 작업을 재시도 로직과 함께 실행합니다.
	 *
	 * @param operation 실행할 작업 (Exception을 던질 수 있음)
	 * @param operationType 작업 타입 (로깅용)
	 * @param operationCount 작업 개수 (로깅용)
	 */
	private void executeWithRetry(WriteBehindOperation operation, String operationType, int operationCount) {
		int attempt = 0;
		Exception lastException = null;

		while (attempt <= writeBehindMaxRetries) {
			try {
				operation.execute();
				if (attempt > 0) {
					log.info("Write-behind {} succeeded after {} retries (count: {})",
						operationType, attempt, operationCount);
				}
				return;  // 성공 시 즉시 리턴
			} catch (Exception e) {
				lastException = e;
				attempt++;

				if (attempt <= writeBehindMaxRetries) {
					log.warn("Write-behind {} failed (attempt {}/{}), retrying in {}ms... (count: {})",
						operationType, attempt, writeBehindMaxRetries + 1, writeBehindRetryDelayMs, operationCount, e);

					// 재시도 전 대기
					if (writeBehindRetryDelayMs > 0) {
						try {
							Thread.sleep(writeBehindRetryDelayMs);
						} catch (InterruptedException ie) {
							Thread.currentThread().interrupt();
							log.error("Write-behind retry interrupted for {}", operationType);
							return;
						}
					}
				}
			}
		}

		// 모든 재시도 실패
		log.error("Write-behind {} failed after {} attempts (count: {}). Data may be lost!",
			operationType, attempt, operationCount, lastException);
	}

	/**
	 * Write-behind 작업을 나타내는 함수형 인터페이스 (Exception을 던질 수 있음)
	 */
	@FunctionalInterface
	private interface WriteBehindOperation {
		void execute() throws Exception;
	}

	/**
	 * 쓰기 작업을 수행합니다 (WriteStrategy에 따라 동작 다름)
	 */
	private void performWrite(K key, V value) {
		if (writeStrategy == WriteStrategy.READ_ONLY || cacheWriter == null) {
			return;
		}

		try {
			if (writeStrategy == WriteStrategy.WRITE_THROUGH) {
				// 동기 쓰기
				cacheWriter.write(key, value);
			} else if (writeStrategy == WriteStrategy.WRITE_BEHIND) {
				// 비동기 큐잉
				if (writeQueue != null) {
					writeQueue.offer(WriteOperation.put(key, value));
					// 배치 크기 도달 시 즉시 플러시
					if (writeQueue.size() >= writeBehindBatchSize) {
						writeBehindExecutor.submit(this::flushWriteBehindQueue);
					}
				}
			}
		} catch (Exception e) {
			if (writeStrategy == WriteStrategy.WRITE_THROUGH) {
				// WRITE_THROUGH는 예외를 즉시 전파
				throw new RuntimeException("Write-through failed for key: " + key, e);
			} else {
				// WRITE_BEHIND는 로그만
				log.error("Failed to queue write-behind operation for key: {}", key, e);
			}
		}
	}

	/**
	 * 삭제 작업을 수행합니다 (WriteStrategy에 따라 동작 다름)
	 */
	private void performDelete(K key) {
		if (writeStrategy == WriteStrategy.READ_ONLY || cacheWriter == null) {
			return;
		}

		try {
			if (writeStrategy == WriteStrategy.WRITE_THROUGH) {
				// 동기 삭제
				cacheWriter.delete(key);
			} else if (writeStrategy == WriteStrategy.WRITE_BEHIND) {
				// 비동기 큐잉
				if (writeQueue != null) {
					writeQueue.offer(WriteOperation.delete(key));
					if (writeQueue.size() >= writeBehindBatchSize) {
						writeBehindExecutor.submit(this::flushWriteBehindQueue);
					}
				}
			}
		} catch (Exception e) {
			if (writeStrategy == WriteStrategy.WRITE_THROUGH) {
				throw new RuntimeException("Write-through delete failed for key: " + key, e);
			} else {
				log.error("Failed to queue write-behind delete for key: {}", key, e);
			}
		}
	}

	/**
	 * Refresh-Ahead 체크: TTL의 N% 경과 시 백그라운드 갱신 시작
	 */
	private void checkAndRefreshAhead(K key) {
		Long creationTime = creationTimes.get(key);
		if (creationTime == null) {
			return;  // 생성 시간을 알 수 없으면 스킵
		}

		long now = System.currentTimeMillis();
		long elapsedTime = now - creationTime;
		long ttlMs = timeoutSec * 1000L;
		long refreshThreshold = (long) (ttlMs * refreshAheadFactor);

		// TTL의 N% 이상 경과 && 아직 갱신 중이 아닌 경우
		if (elapsedTime >= refreshThreshold && refreshingKeys.add(key)) {
			log.trace("Refresh-ahead triggered for key: {} (elapsed={}ms, threshold={}ms)",
				key, elapsedTime, refreshThreshold);

			// 백그라운드에서 비동기 갱신
			refreshAheadExecutor.submit(() -> {
				try {
					log.debug("Refresh-ahead: refreshing key: {}", key);
					long loadStartTime = System.nanoTime();
					V freshValue = cacheLoader.loadOne(key);

					// 갱신 성공: 새로운 값으로 교체
					put(key, freshValue);

					// 로드 성공 메트릭
					if (metrics != null) {
						long loadTime = System.nanoTime() - loadStartTime;
						metrics.recordLoadSuccess(loadTime);
					}
					log.debug("Refresh-ahead completed for key: {}", key);
				} catch (SBCacheLoadFailException e) {
					// 갱신 실패: 기존 데이터 유지 (Graceful degradation)
					log.warn("Refresh-ahead failed for key: {}, keeping stale data", key, e);

					// 로드 실패 메트릭
					if (metrics != null) {
						metrics.recordLoadFailure();
					}

					// 실패 시 생성 시간을 현재 시간으로 업데이트하여 다음 get()에서 재시도
					creationTimes.put(key, System.currentTimeMillis());
				} finally {
					// 갱신 완료 후 키 제거
					refreshingKeys.remove(key);
				}
			});
		}
	}

	/**
	 * 캐시 리소스를 정리합니다.
	 * 자동 정리가 활성화된 경우 스케줄러를 종료합니다.
	 * try-with-resources 또는 명시적 close() 호출 시 자동으로 실행됩니다.
	 */
	@Override
	public void close() {
		if (closed.compareAndSet(false, true)) {
			// JMX 해제
			unregisterJmx();

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
			if (asyncExecutor != null) {
				log.debug("Shutting down SBCacheMap async loader executor");
				asyncExecutor.shutdown();
				try {
					if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
						log.warn("Async loader executor did not terminate in time, forcing shutdown");
						asyncExecutor.shutdownNow();
					}
				} catch (InterruptedException e) {
					log.warn("Interrupted while waiting for async loader executor termination", e);
					asyncExecutor.shutdownNow();
					Thread.currentThread().interrupt();
				}
			}
		}

		// WRITE_BEHIND executor shutdown and final flush
		if (writeBehindExecutor != null) {
			log.debug("Shutting down write-behind executor and flushing queue");
			// Final flush before shutdown
			flushWriteBehindQueue();

			// Flush any remaining in writer
			if (cacheWriter != null) {
				try {
					cacheWriter.flush();
				} catch (Exception e) {
					log.error("Failed to flush cache writer", e);
				}
			}

			writeBehindExecutor.shutdown();
			try {
				if (!writeBehindExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
					log.warn("Write-behind executor did not terminate in time, forcing shutdown");
					writeBehindExecutor.shutdownNow();
				}
			} catch (InterruptedException e) {
				log.warn("Interrupted while waiting for write-behind executor termination", e);
				writeBehindExecutor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}

		// Refresh-Ahead executor shutdown
		if (refreshAheadExecutor != null) {
			log.debug("Shutting down refresh-ahead executor");
			refreshAheadExecutor.shutdown();
			try {
				if (!refreshAheadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
					log.warn("Refresh-ahead executor did not terminate in time, forcing shutdown");
					refreshAheadExecutor.shutdownNow();
				}
			} catch (InterruptedException e) {
				log.warn("Interrupted while waiting for refresh-ahead executor termination", e);
				refreshAheadExecutor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Write operation for WRITE_BEHIND queue.
	 */
	private static class WriteOperation<K, V> {
		enum Type {
			PUT, DELETE
		}

		private final Type type;
		private final K key;
		private final V value;

		private WriteOperation(Type type, K key, V value) {
			this.type = type;
			this.key = key;
			this.value = value;
		}

		static <K, V> WriteOperation<K, V> put(K key, V value) {
			return new WriteOperation<>(Type.PUT, key, value);
		}

		static <K, V> WriteOperation<K, V> delete(K key) {
			return new WriteOperation<>(Type.DELETE, key, null);
		}

		Type getType() {
			return type;
		}

		K getKey() {
			return key;
		}

		V getValue() {
			return value;
		}
	}

	/**
	 * Create EvictionStrategy instance based on policy.
	 *
	 * @param policy the eviction policy
	 * @return EvictionStrategy instance, or null if maxSize is 0 (unlimited)
	 */
	private EvictionStrategy<K> createEvictionStrategy(EvictionPolicy policy) {
		if (maxSize <= 0) {
			return null;  // No eviction needed for unlimited cache
		}

		switch (policy) {
			case LRU:
				return new LruEvictionStrategy<>();
			case LFU:
				return new LfuEvictionStrategy<>();
			case FIFO:
				return new FifoEvictionStrategy<>();
			case RANDOM:
				return new RandomEvictionStrategy<>();
			case TTL:
				return new TtlEvictionStrategy<>();
			default:
				throw new IllegalArgumentException("Unknown eviction policy: " + policy);
		}
	}
}
