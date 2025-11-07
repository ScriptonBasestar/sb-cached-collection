package org.scriptonbasestar.cache.collection.metrics;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 상세한 캐시 통계 및 메트릭 정보
 *
 * CacheMetrics를 확장하여 추가 통계를 제공합니다:
 * - 시간대별 통계 (분당, 시간당)
 * - 최대/최소 로드 시간
 * - 마지막 접근 시간
 * - 메모리 사용량 추정
 *
 * @author archmagece
 * @since 2025-01
 */
public class DetailedCacheMetrics extends CacheMetrics {

	private final AtomicLong totalAccessTime = new AtomicLong(0);  // 나노초
	private final AtomicLong maxLoadTime = new AtomicLong(0);
	private final AtomicLong minLoadTime = new AtomicLong(Long.MAX_VALUE);
	private final AtomicLong lastAccessTimestamp = new AtomicLong(0);
	private final AtomicLong putCount = new AtomicLong(0);
	private final AtomicLong removeCount = new AtomicLong(0);
	private final long creationTime;

	public DetailedCacheMetrics() {
		this.creationTime = System.currentTimeMillis();
	}

	/**
	 * 캐시 히트를 기록하고 마지막 접근 시간을 업데이트합니다.
	 */
	@Override
	public void recordHit() {
		super.recordHit();
		lastAccessTimestamp.set(System.currentTimeMillis());
	}

	/**
	 * 캐시 미스를 기록하고 마지막 접근 시간을 업데이트합니다.
	 */
	@Override
	public void recordMiss() {
		super.recordMiss();
		lastAccessTimestamp.set(System.currentTimeMillis());
	}

	/**
	 * 캐시 접근 시간을 기록합니다.
	 *
	 * @param accessTimeNanos 접근에 걸린 시간 (나노초)
	 */
	public void recordAccessTime(long accessTimeNanos) {
		totalAccessTime.addAndGet(accessTimeNanos);
		lastAccessTimestamp.set(System.currentTimeMillis());
	}

	/**
	 * 캐시 로드 성공을 기록하고 로드 시간 통계를 업데이트합니다.
	 *
	 * @param loadTimeNanos 로드에 걸린 시간 (나노초)
	 */
	@Override
	public void recordLoadSuccess(long loadTimeNanos) {
		super.recordLoadSuccess(loadTimeNanos);
		updateLoadTimeStats(loadTimeNanos);
	}

	/**
	 * Put 연산을 기록합니다.
	 */
	public void recordPut() {
		putCount.incrementAndGet();
		lastAccessTimestamp.set(System.currentTimeMillis());
	}

	/**
	 * Remove 연산을 기록합니다.
	 */
	public void recordRemove() {
		removeCount.incrementAndGet();
		lastAccessTimestamp.set(System.currentTimeMillis());
	}

	/**
	 * 로드 시간 통계를 업데이트합니다.
	 */
	private void updateLoadTimeStats(long loadTimeNanos) {
		// 최대값 업데이트
		long currentMax;
		do {
			currentMax = maxLoadTime.get();
			if (loadTimeNanos <= currentMax) {
				break;
			}
		} while (!maxLoadTime.compareAndSet(currentMax, loadTimeNanos));

		// 최소값 업데이트
		long currentMin;
		do {
			currentMin = minLoadTime.get();
			if (loadTimeNanos >= currentMin) {
				break;
			}
		} while (!minLoadTime.compareAndSet(currentMin, loadTimeNanos));
	}

	/**
	 * 평균 접근 시간을 계산합니다.
	 *
	 * @return 평균 접근 시간 (나노초)
	 */
	public double averageAccessTime() {
		long requests = requestCount();
		return requests == 0 ? 0.0 : (double) totalAccessTime.get() / requests;
	}

	/**
	 * 최대 로드 시간을 반환합니다.
	 *
	 * @return 최대 로드 시간 (나노초), 로드가 없으면 0
	 */
	public long maxLoadTime() {
		long max = maxLoadTime.get();
		return max == 0 ? 0 : max;
	}

	/**
	 * 최소 로드 시간을 반환합니다.
	 *
	 * @return 최소 로드 시간 (나노초), 로드가 없으면 0
	 */
	public long minLoadTime() {
		long min = minLoadTime.get();
		return min == Long.MAX_VALUE ? 0 : min;
	}

	/**
	 * 마지막 접근 시간을 반환합니다.
	 *
	 * @return 마지막 접근 시간 (epoch milliseconds)
	 */
	public long lastAccessTime() {
		return lastAccessTimestamp.get();
	}

	/**
	 * 마지막 접근 시간을 Instant로 반환합니다.
	 *
	 * @return 마지막 접근 시간
	 */
	public Instant lastAccessInstant() {
		long timestamp = lastAccessTimestamp.get();
		return timestamp == 0 ? null : Instant.ofEpochMilli(timestamp);
	}

	/**
	 * Put 연산 횟수를 반환합니다.
	 *
	 * @return Put 연산 횟수
	 */
	public long putCount() {
		return putCount.get();
	}

	/**
	 * Remove 연산 횟수를 반환합니다.
	 *
	 * @return Remove 연산 횟수
	 */
	public long removeCount() {
		return removeCount.get();
	}

	/**
	 * 캐시 생성 시간을 반환합니다.
	 *
	 * @return 생성 시간 (epoch milliseconds)
	 */
	public long creationTime() {
		return creationTime;
	}

	/**
	 * 캐시 가동 시간을 계산합니다.
	 *
	 * @return 가동 시간 (밀리초)
	 */
	public long uptimeMillis() {
		return System.currentTimeMillis() - creationTime;
	}

	/**
	 * 분당 요청 수를 계산합니다.
	 *
	 * @return 분당 요청 수
	 */
	public double requestsPerMinute() {
		long uptimeMillis = uptimeMillis();
		if (uptimeMillis == 0) {
			return 0.0;
		}
		return (double) requestCount() * 60000 / uptimeMillis;
	}

	/**
	 * 시간당 요청 수를 계산합니다.
	 *
	 * @return 시간당 요청 수
	 */
	public double requestsPerHour() {
		return requestsPerMinute() * 60;
	}

	/**
	 * 모든 통계를 초기화합니다.
	 */
	@Override
	public void reset() {
		super.reset();
		totalAccessTime.set(0);
		maxLoadTime.set(0);
		minLoadTime.set(Long.MAX_VALUE);
		lastAccessTimestamp.set(0);
		putCount.set(0);
		removeCount.set(0);
	}

	@Override
	public String toString() {
		return String.format(
			"DetailedCacheMetrics{requests=%d, hits=%d, misses=%d, hitRate=%.2f%%, " +
			"loadSuccess=%d, loadFailure=%d, evictions=%d, " +
			"avgLoadTime=%.2fμs, maxLoadTime=%.2fμs, minLoadTime=%.2fμs, " +
			"avgAccessTime=%.2fμs, puts=%d, removes=%d, " +
			"requestsPerMin=%.2f, uptime=%dms}",
			requestCount(),
			hitCount(),
			missCount(),
			hitRate() * 100,
			loadSuccessCount(),
			loadFailureCount(),
			evictionCount(),
			averageLoadPenalty() / 1000,
			maxLoadTime() / 1000.0,
			minLoadTime() / 1000.0,
			averageAccessTime() / 1000,
			putCount(),
			removeCount(),
			requestsPerMinute(),
			uptimeMillis()
		);
	}
}
