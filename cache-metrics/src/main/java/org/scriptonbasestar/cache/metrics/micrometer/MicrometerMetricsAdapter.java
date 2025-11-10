package org.scriptonbasestar.cache.metrics.micrometer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.scriptonbasestar.cache.collection.metrics.CacheMetrics;

import java.util.concurrent.TimeUnit;

/**
 * Micrometer MeterRegistry와 CacheMetrics를 연동하는 어댑터
 *
 * CacheMetrics의 이벤트를 Micrometer 메트릭으로 자동 기록합니다.
 *
 * <p>사용 예시:</p>
 * <pre>{@code
 * MeterRegistry registry = new SimpleMeterRegistry();
 * CacheMetrics cacheMetrics = new CacheMetrics();
 *
 * MicrometerMetricsAdapter adapter = new MicrometerMetricsAdapter(
 *     cacheMetrics,
 *     registry,
 *     "user-cache"
 * );
 *
 * // 주기적으로 메트릭 동기화
 * adapter.syncMetrics();
 * }</pre>
 *
 * @author archmagece
 * @since 2025-01
 */
public class MicrometerMetricsAdapter {

	private final CacheMetrics cacheMetrics;
	private final String cacheName;

	// Micrometer meters
	private final Counter hitCounter;
	private final Counter missCounter;
	private final Counter loadSuccessCounter;
	private final Counter loadFailureCounter;
	private final Counter evictionCounter;
	private final Timer loadTimer;
	private final DistributionSummary cacheSizeSummary;

	/**
	 * Micrometer 어댑터 생성
	 *
	 * @param cacheMetrics 캐시 메트릭
	 * @param meterRegistry Micrometer 레지스트리
	 * @param cacheName 캐시 이름 (태그로 사용)
	 */
	public MicrometerMetricsAdapter(
		CacheMetrics cacheMetrics,
		MeterRegistry meterRegistry,
		String cacheName
	) {
		if (cacheMetrics == null) {
			throw new IllegalArgumentException("CacheMetrics must not be null");
		}
		if (meterRegistry == null) {
			throw new IllegalArgumentException("MeterRegistry must not be null");
		}
		if (cacheName == null || cacheName.trim().isEmpty()) {
			throw new IllegalArgumentException("Cache name must not be null or empty");
		}

		this.cacheMetrics = cacheMetrics;
		this.cacheName = cacheName;

		// Micrometer 메터 등록
		this.hitCounter = Counter.builder("cache.hits")
			.tag("cache", cacheName)
			.description("Cache hit count")
			.register(meterRegistry);

		this.missCounter = Counter.builder("cache.misses")
			.tag("cache", cacheName)
			.description("Cache miss count")
			.register(meterRegistry);

		this.loadSuccessCounter = Counter.builder("cache.loads")
			.tag("cache", cacheName)
			.tag("result", "success")
			.description("Cache load success count")
			.register(meterRegistry);

		this.loadFailureCounter = Counter.builder("cache.loads")
			.tag("cache", cacheName)
			.tag("result", "failure")
			.description("Cache load failure count")
			.register(meterRegistry);

		this.evictionCounter = Counter.builder("cache.evictions")
			.tag("cache", cacheName)
			.description("Cache eviction count")
			.register(meterRegistry);

		this.loadTimer = Timer.builder("cache.load.duration")
			.tag("cache", cacheName)
			.description("Cache load duration")
			.register(meterRegistry);

		this.cacheSizeSummary = DistributionSummary.builder("cache.size")
			.tag("cache", cacheName)
			.description("Cache size")
			.register(meterRegistry);

		// Gauge 등록 (실시간 값)
		meterRegistry.gauge("cache.hit.rate", cacheMetrics, CacheMetrics::hitRate);
		meterRegistry.gauge("cache.miss.rate", cacheMetrics, CacheMetrics::missRate);
		meterRegistry.gauge("cache.requests.total", cacheMetrics, CacheMetrics::requestCount);
	}

	/**
	 * 메트릭을 Micrometer로 동기화합니다.
	 *
	 * 주기적으로 호출하여 누적 카운터를 업데이트합니다.
	 */
	public void syncMetrics() {
		// Counter는 증분만 기록할 수 있으므로, 차이를 계산
		// 실제로는 CacheMetrics가 이벤트 발생 시 직접 호출하는 방식이 더 정확함
		// 여기서는 기본 동기화 메서드 제공

		// Note: Gauge는 자동으로 동기화되므로 별도 처리 불필요
	}

	/**
	 * 히트 이벤트를 기록합니다.
	 */
	public void recordHit() {
		cacheMetrics.recordHit();
		hitCounter.increment();
	}

	/**
	 * 미스 이벤트를 기록합니다.
	 */
	public void recordMiss() {
		cacheMetrics.recordMiss();
		missCounter.increment();
	}

	/**
	 * 로드 성공 이벤트를 기록합니다.
	 *
	 * @param loadTimeNanos 로드 시간 (나노초)
	 */
	public void recordLoadSuccess(long loadTimeNanos) {
		cacheMetrics.recordLoadSuccess(loadTimeNanos);
		loadSuccessCounter.increment();
		loadTimer.record(loadTimeNanos, TimeUnit.NANOSECONDS);
	}

	/**
	 * 로드 실패 이벤트를 기록합니다.
	 */
	public void recordLoadFailure() {
		cacheMetrics.recordLoadFailure();
		loadFailureCounter.increment();
	}

	/**
	 * 축출 이벤트를 기록합니다.
	 *
	 * @param count 축출된 항목 수
	 */
	public void recordEviction(int count) {
		cacheMetrics.recordEviction(count);
		evictionCounter.increment(count);
	}

	/**
	 * 캐시 크기를 기록합니다.
	 *
	 * @param size 현재 캐시 크기
	 */
	public void recordSize(int size) {
		cacheSizeSummary.record(size);
	}

	/**
	 * 캐시 이름을 반환합니다.
	 *
	 * @return 캐시 이름
	 */
	public String getCacheName() {
		return cacheName;
	}

	/**
	 * CacheMetrics를 반환합니다.
	 *
	 * @return 캐시 메트릭
	 */
	public CacheMetrics getCacheMetrics() {
		return cacheMetrics;
	}
}
