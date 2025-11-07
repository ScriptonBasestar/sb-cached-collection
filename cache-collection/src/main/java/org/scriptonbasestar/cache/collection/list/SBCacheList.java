package org.scriptonbasestar.cache.collection.list;

import org.scriptonbasestar.cache.collection.metrics.CacheMetrics;
import org.scriptonbasestar.cache.core.loader.SBCacheListLoader;
import org.scriptonbasestar.cache.core.strategy.LoadStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 시간 기반 자동 갱신 캐시 리스트
 *
 * <p>전체 리스트를 한 번에 로드하고 주기적으로 갱신하는 캐시입니다.
 * Map 기반 캐시({@link org.scriptonbasestar.cache.collection.map.SBCacheMap})와 달리,
 * 전체 데이터를 하나의 단위로 관리합니다.</p>
 *
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li>Access-based TTL: 마지막 접근 후 일정 시간이 지나면 자동 갱신</li>
 *   <li>Forced timeout: 최초 생성 후 절대 시간이 지나면 무조건 갱신</li>
 *   <li>Auto cleanup: 주기적으로 만료 확인 및 자동 갱신</li>
 *   <li>Max size: 최대 리스트 크기 제한 (초과 시 경고)</li>
 *   <li>Metrics: 히트율, 미스율, 갱신 횟수 등 통계</li>
 *   <li>LoadStrategy.ALL: 백그라운드 비동기 갱신 (기본 동작)</li>
 *   <li>LoadStrategy.ONE: 동기 갱신 (제한적 사용)</li>
 * </ul>
 *
 * <h3>사용 예시:</h3>
 * <pre>{@code
 * // Builder 패턴 (권장)
 * try (SBCacheList<Product> cacheList = SBCacheList.<Product>builder()
 *         .loader(() -> productRepository.findAll())
 *         .timeoutSec(300)                  // 5분마다 자동 갱신
 *         .forcedTimeoutSec(3600)           // 1시간 후 무조건 갱신
 *         .maxSize(1000)                    // 최대 1000개 (초과 시 경고)
 *         .enableMetrics(true)              // 통계 수집
 *         .enableAutoCleanup(true)          // 자동 정리
 *         .cleanupIntervalMinutes(10)       // 10분마다 확인
 *         .loadStrategy(LoadStrategy.ALL)   // 비동기 갱신
 *         .build()) {
 *
 *     List<Product> products = cacheList.getList();
 *
 *     // 통계 확인
 *     CacheMetrics metrics = cacheList.metrics();
 *     System.out.println("Hit rate: " + metrics.hitRate() * 100 + "%");
 * }
 * }</pre>
 *
 * @author archmagece
 * @since 2016-11-06
 */
public class SBCacheList<E> implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(SBCacheList.class);

	private final List<E> data;  // 실제 데이터 (CopyOnWriteArrayList로 동시성 보장)
	private final AtomicLong lastAccessTime;  // 마지막 접근 시간 (밀리초)
	private final AtomicLong absoluteExpiryTime;  // 절대 만료 시간 (밀리초)
	private final int timeoutSec;  // 접근 기반 TTL
	private final int forcedTimeoutSec;  // 절대 만료 시간 (0이면 비활성화)
	private final int maxSize;  // 최대 크기 (0이면 무제한)
	private final ExecutorService executor;  // 비동기 로드용
	private final ScheduledExecutorService cleanupExecutor;  // 자동 정리용
	private final SBCacheListLoader<E> loader;
	private final LoadStrategy loadStrategy;
	private final CacheMetrics metrics;  // 통계 (null이면 비활성화)
	private final Object lock = new Object();  // 인스턴스별 동기화
	private final AtomicBoolean closed = new AtomicBoolean(false);

	/**
	 * 기본 생성자 (하위 호환성)
	 *
	 * @param loader 리스트 로더
	 * @param timeoutSec 타임아웃 시간(초)
	 */
	public SBCacheList(SBCacheListLoader<E> loader, int timeoutSec) {
		this(loader, timeoutSec, LoadStrategy.ALL, false, 5, 0, 0, false);
	}

	/**
	 * LoadStrategy를 지정하는 생성자 (하위 호환성)
	 *
	 * @param loader 리스트 로더
	 * @param loadStrategy 로드 전략 (ONE 또는 ALL)
	 */
	public SBCacheList(SBCacheListLoader<E> loader, LoadStrategy loadStrategy) {
		this(loader, 300, loadStrategy, false, 5, 0, 0, false);
	}

	/**
	 * 모든 옵션을 설정할 수 있는 생성자 (내부용)
	 *
	 * @param loader 리스트 로더
	 * @param timeoutSec 타임아웃 시간(초) - 접근 기반 TTL
	 * @param loadStrategy 로드 전략 (ONE 또는 ALL)
	 * @param enableAutoCleanup 자동 정리 활성화 여부
	 * @param cleanupIntervalMinutes 정리 주기(분)
	 * @param forcedTimeoutSec 절대 만료 시간(초) - 0이면 비활성화
	 * @param maxSize 최대 크기 - 0이면 무제한
	 * @param enableMetrics 통계 수집 활성화 여부
	 */
	protected SBCacheList(SBCacheListLoader<E> loader, int timeoutSec, LoadStrategy loadStrategy,
						  boolean enableAutoCleanup, int cleanupIntervalMinutes,
						  int forcedTimeoutSec, int maxSize, boolean enableMetrics) {
		this.data = new CopyOnWriteArrayList<>();  // 동시성 안전
		this.loader = loader;
		this.timeoutSec = timeoutSec;
		this.loadStrategy = loadStrategy;
		this.forcedTimeoutSec = forcedTimeoutSec;
		this.maxSize = maxSize;
		this.metrics = enableMetrics ? new CacheMetrics() : null;
		this.lastAccessTime = new AtomicLong(System.currentTimeMillis());
		this.absoluteExpiryTime = new AtomicLong(
			forcedTimeoutSec > 0 ? System.currentTimeMillis() + (forcedTimeoutSec * 1000L) : Long.MAX_VALUE
		);

		// 비동기 로드용 ExecutorService (LoadStrategy.ALL일 때 사용)
		this.executor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "SBCacheList-Loader");
			t.setDaemon(true);
			return t;
		});

		// 자동 정리용 ScheduledExecutorService
		if (enableAutoCleanup) {
			this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "SBCacheList-Cleanup");
				t.setDaemon(true);
				return t;
			});
			this.cleanupExecutor.scheduleAtFixedRate(
				this::checkAndReload,
				cleanupIntervalMinutes,
				cleanupIntervalMinutes,
				TimeUnit.MINUTES
			);
		} else {
			this.cleanupExecutor = null;
		}

		// 초기 데이터 로드
		log.debug("SBCacheList initializing - loading initial data");
		try {
			List<E> initialData = loader.loadAll();
			if (initialData != null) {
				data.addAll(initialData);
				checkMaxSize();
			}
		} catch (Exception e) {
			log.error("Failed to load initial data", e);
		}
	}

	/**
	 * Builder 패턴을 위한 정적 팩토리 메서드
	 *
	 * @param <E> 요소 타입
	 * @return Builder 인스턴스
	 */
	public static <E> Builder<E> builder() {
		return new Builder<>();
	}

	/**
	 * SBCacheList Builder 클래스
	 *
	 * @param <E> 요소 타입
	 */
	public static class Builder<E> {
		private SBCacheListLoader<E> loader;
		private int timeoutSec = 300;  // 기본값 5분
		private LoadStrategy loadStrategy = LoadStrategy.ALL;  // 기본값: 비동기
		private boolean enableAutoCleanup = false;
		private int cleanupIntervalMinutes = 5;
		private int forcedTimeoutSec = 0;  // 기본값: 비활성화
		private int maxSize = 0;  // 기본값: 무제한
		private boolean enableMetrics = false;

		public Builder<E> loader(SBCacheListLoader<E> loader) {
			this.loader = loader;
			return this;
		}

		public Builder<E> timeoutSec(int timeoutSec) {
			this.timeoutSec = timeoutSec;
			return this;
		}

		public Builder<E> loadStrategy(LoadStrategy loadStrategy) {
			this.loadStrategy = loadStrategy;
			return this;
		}

		public Builder<E> enableAutoCleanup(boolean enable) {
			this.enableAutoCleanup = enable;
			return this;
		}

		public Builder<E> cleanupIntervalMinutes(int minutes) {
			this.cleanupIntervalMinutes = minutes;
			return this;
		}

		public Builder<E> forcedTimeoutSec(int seconds) {
			this.forcedTimeoutSec = seconds;
			return this;
		}

		public Builder<E> maxSize(int size) {
			this.maxSize = size;
			return this;
		}

		public Builder<E> enableMetrics(boolean enable) {
			this.enableMetrics = enable;
			return this;
		}

		public SBCacheList<E> build() {
			if (loader == null) {
				throw new IllegalStateException("loader must be set");
			}
			return new SBCacheList<>(loader, timeoutSec, loadStrategy, enableAutoCleanup,
				cleanupIntervalMinutes, forcedTimeoutSec, maxSize, enableMetrics);
		}
	}

	/**
	 * 캐시된 리스트를 반환합니다.
	 * TTL이 만료되었거나 forced timeout에 도달한 경우 자동으로 갱신합니다.
	 *
	 * @return 캐시된 리스트 (수정 불가능한 뷰)
	 */
	public List<E> getList() {
		long now = System.currentTimeMillis();
		long lastAccess = lastAccessTime.get();
		long absoluteExpiry = absoluteExpiryTime.get();

		// 1. 절대 만료 시간 확인 (forced timeout)
		boolean forcedExpired = (forcedTimeoutSec > 0 && now >= absoluteExpiry);
		// 2. 접근 기반 TTL 확인
		boolean accessExpired = (now - lastAccess) > (timeoutSec * 1000L);

		if (forcedExpired || accessExpired) {
			if (metrics != null) {
				metrics.recordMiss();
			}

			log.debug("Cache expired (forced={}, access={}), reloading", forcedExpired, accessExpired);

			if (loadStrategy == LoadStrategy.ALL) {
				// 비동기 갱신 (백그라운드에서 실행)
				executor.execute(this::reloadAll);
			} else {
				// 동기 갱신 (즉시 실행)
				reloadAll();
			}

			// 타임스탬프 갱신
			lastAccessTime.set(now);
			if (forcedTimeoutSec > 0) {
				absoluteExpiryTime.set(now + (forcedTimeoutSec * 1000L));
			}
		} else {
			if (metrics != null) {
				metrics.recordHit();
			}
		}

		return Collections.unmodifiableList(new ArrayList<>(data));
	}

	/**
	 * 특정 인덱스의 요소를 반환합니다.
	 *
	 * @param index 인덱스
	 * @return 요소
	 */
	public E get(int index) {
		long now = System.currentTimeMillis();
		long lastAccess = lastAccessTime.get();
		long absoluteExpiry = absoluteExpiryTime.get();

		boolean forcedExpired = (forcedTimeoutSec > 0 && now >= absoluteExpiry);
		boolean accessExpired = (now - lastAccess) > (timeoutSec * 1000L);

		if (forcedExpired || accessExpired) {
			if (metrics != null) {
				metrics.recordMiss();
			}

			log.trace("Cache expired at get({}), reloading", index);

			if (loadStrategy == LoadStrategy.ONE) {
				// LoadStrategy.ONE: 해당 인덱스만 갱신
				try {
					E newValue = loader.loadOne(index);
					if (index < data.size()) {
						data.set(index, newValue);
					}
				} catch (Exception e) {
					log.error("Failed to reload index {}", index, e);
				}
			} else {
				// LoadStrategy.ALL: 전체 갱신
				executor.execute(this::reloadAll);
			}

			lastAccessTime.set(now);
			if (forcedTimeoutSec > 0) {
				absoluteExpiryTime.set(now + (forcedTimeoutSec * 1000L));
			}
		} else {
			if (metrics != null) {
				metrics.recordHit();
			}
		}

		return data.get(index);
	}

	/**
	 * 전체 데이터를 다시 로드합니다 (동기화 필요)
	 */
	private void reloadAll() {
		synchronized (lock) {
			long loadStartTime = System.nanoTime();
			try {
				List<E> newData = loader.loadAll();
				if (newData != null) {
					data.clear();
					data.addAll(newData);
					checkMaxSize();

					if (metrics != null) {
						long loadTime = System.nanoTime() - loadStartTime;
						metrics.recordLoadSuccess(loadTime);
					}

					log.debug("List reloaded successfully, size={}", data.size());
				}
			} catch (Exception e) {
				if (metrics != null) {
					metrics.recordLoadFailure();
				}
				log.error("Failed to reload list", e);
			}
		}
	}

	/**
	 * maxSize 초과 여부를 확인하고 경고를 출력합니다.
	 */
	private void checkMaxSize() {
		if (maxSize > 0 && data.size() > maxSize) {
			log.warn("List size {} exceeds maxSize {}. Consider increasing maxSize or filtering data.",
				data.size(), maxSize);
		}
	}

	/**
	 * 만료 여부를 확인하고 필요시 재로드합니다 (자동 정리용)
	 */
	private void checkAndReload() {
		long now = System.currentTimeMillis();
		long lastAccess = lastAccessTime.get();
		long absoluteExpiry = absoluteExpiryTime.get();

		boolean forcedExpired = (forcedTimeoutSec > 0 && now >= absoluteExpiry);
		boolean accessExpired = (now - lastAccess) > (timeoutSec * 1000L);

		if (forcedExpired || accessExpired) {
			log.debug("Auto cleanup triggered, reloading list");
			reloadAll();
			lastAccessTime.set(now);
			if (forcedTimeoutSec > 0) {
				absoluteExpiryTime.set(now + (forcedTimeoutSec * 1000L));
			}
		}
	}

	/**
	 * 캐시를 즉시 갱신합니다.
	 */
	public void refresh() {
		log.debug("Manual refresh triggered");
		reloadAll();
		long now = System.currentTimeMillis();
		lastAccessTime.set(now);
		if (forcedTimeoutSec > 0) {
			absoluteExpiryTime.set(now + (forcedTimeoutSec * 1000L));
		}
	}

	/**
	 * 현재 캐시 크기를 반환합니다.
	 *
	 * @return 캐시 크기
	 */
	public int size() {
		return data.size();
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
	 */
	@Override
	public void close() {
		if (closed.compareAndSet(false, true)) {
			log.debug("Closing SBCacheList");

			// ExecutorService 종료
			executor.shutdown();
			try {
				if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
					executor.shutdownNow();
				}
			} catch (InterruptedException e) {
				log.warn("Interrupted while waiting for executor termination", e);
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}

			// ScheduledExecutorService 종료
			if (cleanupExecutor != null) {
				cleanupExecutor.shutdown();
				try {
					if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
						cleanupExecutor.shutdownNow();
					}
				} catch (InterruptedException e) {
					log.warn("Interrupted while waiting for cleanup executor termination", e);
					cleanupExecutor.shutdownNow();
					Thread.currentThread().interrupt();
				}
			}

			log.info("SBCacheList closed");
		}
	}
}
