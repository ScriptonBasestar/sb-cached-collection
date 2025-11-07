package org.scriptonbasestar.cache.collection.metrics;

import java.time.Instant;

/**
 * 캐시 메트릭의 불변 스냅샷
 *
 * 특정 시점의 메트릭 정보를 저장합니다.
 * 시계열 데이터 수집이나 모니터링에 유용합니다.
 *
 * @author archmagece
 * @since 2025-01
 */
public class MetricsSnapshot {

	private final long timestamp;
	private final long hitCount;
	private final long missCount;
	private final long loadSuccessCount;
	private final long loadFailureCount;
	private final long evictionCount;
	private final double averageLoadPenalty;
	private final double hitRate;
	private final double missRate;

	/**
	 * CacheMetrics로부터 스냅샷을 생성합니다.
	 *
	 * @param metrics 캐시 메트릭
	 */
	public MetricsSnapshot(CacheMetrics metrics) {
		this.timestamp = System.currentTimeMillis();
		this.hitCount = metrics.hitCount();
		this.missCount = metrics.missCount();
		this.loadSuccessCount = metrics.loadSuccessCount();
		this.loadFailureCount = metrics.loadFailureCount();
		this.evictionCount = metrics.evictionCount();
		this.averageLoadPenalty = metrics.averageLoadPenalty();
		this.hitRate = metrics.hitRate();
		this.missRate = metrics.missRate();
	}

	/**
	 * 스냅샷 생성 시간을 반환합니다.
	 *
	 * @return 타임스탬프 (epoch milliseconds)
	 */
	public long timestamp() {
		return timestamp;
	}

	/**
	 * 스냅샷 생성 시간을 Instant로 반환합니다.
	 *
	 * @return Instant
	 */
	public Instant instant() {
		return Instant.ofEpochMilli(timestamp);
	}

	/**
	 * 히트 횟수
	 */
	public long hitCount() {
		return hitCount;
	}

	/**
	 * 미스 횟수
	 */
	public long missCount() {
		return missCount;
	}

	/**
	 * 총 요청 횟수
	 */
	public long requestCount() {
		return hitCount + missCount;
	}

	/**
	 * 로드 성공 횟수
	 */
	public long loadSuccessCount() {
		return loadSuccessCount;
	}

	/**
	 * 로드 실패 횟수
	 */
	public long loadFailureCount() {
		return loadFailureCount;
	}

	/**
	 * 제거된 항목 수
	 */
	public long evictionCount() {
		return evictionCount;
	}

	/**
	 * 평균 로드 시간 (나노초)
	 */
	public double averageLoadPenalty() {
		return averageLoadPenalty;
	}

	/**
	 * 히트율 (0.0 ~ 1.0)
	 */
	public double hitRate() {
		return hitRate;
	}

	/**
	 * 미스율 (0.0 ~ 1.0)
	 */
	public double missRate() {
		return missRate;
	}

	/**
	 * 두 스냅샷 간의 차이를 계산합니다.
	 *
	 * @param previous 이전 스냅샷
	 * @return 차이값을 담은 새로운 스냅샷
	 */
	public MetricsSnapshot diff(MetricsSnapshot previous) {
		return new MetricsSnapshot(
			this.timestamp,
			this.hitCount - previous.hitCount,
			this.missCount - previous.missCount,
			this.loadSuccessCount - previous.loadSuccessCount,
			this.loadFailureCount - previous.loadFailureCount,
			this.evictionCount - previous.evictionCount,
			this.averageLoadPenalty,  // 평균값은 차이를 계산하지 않음
			calculateHitRate(this.hitCount - previous.hitCount, this.missCount - previous.missCount),
			calculateMissRate(this.hitCount - previous.hitCount, this.missCount - previous.missCount)
		);
	}

	/**
	 * 직접 값을 지정하여 스냅샷을 생성합니다 (내부용).
	 */
	private MetricsSnapshot(
		long timestamp,
		long hitCount,
		long missCount,
		long loadSuccessCount,
		long loadFailureCount,
		long evictionCount,
		double averageLoadPenalty,
		double hitRate,
		double missRate
	) {
		this.timestamp = timestamp;
		this.hitCount = hitCount;
		this.missCount = missCount;
		this.loadSuccessCount = loadSuccessCount;
		this.loadFailureCount = loadFailureCount;
		this.evictionCount = evictionCount;
		this.averageLoadPenalty = averageLoadPenalty;
		this.hitRate = hitRate;
		this.missRate = missRate;
	}

	private static double calculateHitRate(long hits, long misses) {
		long total = hits + misses;
		return total == 0 ? 0.0 : (double) hits / total;
	}

	private static double calculateMissRate(long hits, long misses) {
		long total = hits + misses;
		return total == 0 ? 0.0 : (double) misses / total;
	}

	@Override
	public String toString() {
		return String.format(
			"MetricsSnapshot{timestamp=%s, requests=%d, hits=%d, misses=%d, " +
			"hitRate=%.2f%%, loadSuccess=%d, loadFailure=%d, evictions=%d, avgLoadTime=%.2fμs}",
			instant(),
			requestCount(),
			hitCount,
			missCount,
			hitRate * 100,
			loadSuccessCount,
			loadFailureCount,
			evictionCount,
			averageLoadPenalty / 1000
		);
	}

	/**
	 * JSON 형식으로 변환합니다.
	 *
	 * @return JSON 문자열
	 */
	public String toJson() {
		return String.format(
			"{\"timestamp\":%d,\"hitCount\":%d,\"missCount\":%d," +
			"\"loadSuccessCount\":%d,\"loadFailureCount\":%d," +
			"\"evictionCount\":%d,\"averageLoadPenalty\":%.2f," +
			"\"hitRate\":%.4f,\"missRate\":%.4f}",
			timestamp,
			hitCount,
			missCount,
			loadSuccessCount,
			loadFailureCount,
			evictionCount,
			averageLoadPenalty,
			hitRate,
			missRate
		);
	}
}
