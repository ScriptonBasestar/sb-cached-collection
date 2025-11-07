package org.scriptonbasestar.cache.collection.metrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 캐시 통계 및 메트릭 정보를 제공합니다.
 *
 * 스레드 안전하며 오버헤드가 거의 없도록 AtomicLong을 사용합니다.
 *
 * @author archmagece
 * @since 2025-01
 */
public class CacheMetrics {

	private final AtomicLong hitCount = new AtomicLong(0);
	private final AtomicLong missCount = new AtomicLong(0);
	private final AtomicLong loadSuccessCount = new AtomicLong(0);
	private final AtomicLong loadFailureCount = new AtomicLong(0);
	private final AtomicLong evictionCount = new AtomicLong(0);
	private final AtomicLong totalLoadTime = new AtomicLong(0);  // 나노초

	/**
	 * 캐시 히트 횟수를 증가시킵니다.
	 */
	public void recordHit() {
		hitCount.incrementAndGet();
	}

	/**
	 * 캐시 미스 횟수를 증가시킵니다.
	 */
	public void recordMiss() {
		missCount.incrementAndGet();
	}

	/**
	 * 캐시 로드 성공을 기록합니다.
	 *
	 * @param loadTimeNanos 로드에 걸린 시간 (나노초)
	 */
	public void recordLoadSuccess(long loadTimeNanos) {
		loadSuccessCount.incrementAndGet();
		totalLoadTime.addAndGet(loadTimeNanos);
	}

	/**
	 * 캐시 로드 실패를 기록합니다.
	 */
	public void recordLoadFailure() {
		loadFailureCount.incrementAndGet();
	}

	/**
	 * 캐시 항목 제거를 기록합니다.
	 *
	 * @param count 제거된 항목 수
	 */
	public void recordEviction(int count) {
		evictionCount.addAndGet(count);
	}

	/**
	 * 캐시 히트 횟수를 반환합니다.
	 *
	 * @return 히트 횟수
	 */
	public long hitCount() {
		return hitCount.get();
	}

	/**
	 * 캐시 미스 횟수를 반환합니다.
	 *
	 * @return 미스 횟수
	 */
	public long missCount() {
		return missCount.get();
	}

	/**
	 * 총 요청 횟수를 반환합니다 (히트 + 미스).
	 *
	 * @return 총 요청 횟수
	 */
	public long requestCount() {
		return hitCount.get() + missCount.get();
	}

	/**
	 * 캐시 히트율을 계산합니다.
	 *
	 * @return 히트율 (0.0 ~ 1.0), 요청이 없으면 0.0
	 */
	public double hitRate() {
		long requests = requestCount();
		return requests == 0 ? 0.0 : (double) hitCount.get() / requests;
	}

	/**
	 * 캐시 미스율을 계산합니다.
	 *
	 * @return 미스율 (0.0 ~ 1.0), 요청이 없으면 0.0
	 */
	public double missRate() {
		long requests = requestCount();
		return requests == 0 ? 0.0 : (double) missCount.get() / requests;
	}

	/**
	 * 로드 성공 횟수를 반환합니다.
	 *
	 * @return 로드 성공 횟수
	 */
	public long loadSuccessCount() {
		return loadSuccessCount.get();
	}

	/**
	 * 로드 실패 횟수를 반환합니다.
	 *
	 * @return 로드 실패 횟수
	 */
	public long loadFailureCount() {
		return loadFailureCount.get();
	}

	/**
	 * 제거된 항목 수를 반환합니다.
	 *
	 * @return 제거된 항목 수
	 */
	public long evictionCount() {
		return evictionCount.get();
	}

	/**
	 * 평균 로드 시간을 계산합니다.
	 *
	 * @return 평균 로드 시간 (나노초), 로드가 없으면 0.0
	 */
	public double averageLoadPenalty() {
		long loads = loadSuccessCount.get();
		return loads == 0 ? 0.0 : (double) totalLoadTime.get() / loads;
	}

	/**
	 * 모든 통계를 초기화합니다.
	 */
	public void reset() {
		hitCount.set(0);
		missCount.set(0);
		loadSuccessCount.set(0);
		loadFailureCount.set(0);
		evictionCount.set(0);
		totalLoadTime.set(0);
	}

	@Override
	public String toString() {
		return String.format(
			"CacheMetrics{requests=%d, hits=%d, misses=%d, hitRate=%.2f%%, " +
			"loadSuccess=%d, loadFailure=%d, evictions=%d, avgLoadTime=%.2fμs}",
			requestCount(),
			hitCount(),
			missCount(),
			hitRate() * 100,
			loadSuccessCount(),
			loadFailureCount(),
			evictionCount(),
			averageLoadPenalty() / 1000  // 나노초 → 마이크로초
		);
	}
}
