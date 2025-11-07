package org.scriptonbasestar.cache.collection.metrics;

/**
 * 캐시 헬스체크 유틸리티
 *
 * 캐시의 건강 상태를 평가하고 문제를 감지합니다.
 *
 * @author archmagece
 * @since 2025-01
 */
public class CacheHealthCheck {

	private final CacheMetrics metrics;
	private final HealthThresholds thresholds;

	/**
	 * 기본 임계값으로 헬스체크 생성
	 *
	 * @param metrics 캐시 메트릭
	 */
	public CacheHealthCheck(CacheMetrics metrics) {
		this(metrics, HealthThresholds.DEFAULT);
	}

	/**
	 * 커스텀 임계값으로 헬스체크 생성
	 *
	 * @param metrics 캐시 메트릭
	 * @param thresholds 건강 임계값
	 */
	public CacheHealthCheck(CacheMetrics metrics, HealthThresholds thresholds) {
		if (metrics == null) {
			throw new IllegalArgumentException("Metrics must not be null");
		}
		if (thresholds == null) {
			throw new IllegalArgumentException("Thresholds must not be null");
		}
		this.metrics = metrics;
		this.thresholds = thresholds;
	}

	/**
	 * 캐시의 전반적인 건강 상태를 확인합니다.
	 *
	 * @return 건강 상태 결과
	 */
	public HealthStatus check() {
		HealthStatus.Builder builder = new HealthStatus.Builder();

		// 히트율 검사
		double hitRate = metrics.hitRate();
		if (hitRate < thresholds.minHitRate) {
			builder.addWarning(String.format(
				"Low hit rate: %.2f%% (threshold: %.2f%%)",
				hitRate * 100, thresholds.minHitRate * 100
			));
		}

		// 로드 실패율 검사
		long totalLoads = metrics.loadSuccessCount() + metrics.loadFailureCount();
		if (totalLoads > 0) {
			double failureRate = (double) metrics.loadFailureCount() / totalLoads;
			if (failureRate > thresholds.maxLoadFailureRate) {
				builder.addError(String.format(
					"High load failure rate: %.2f%% (threshold: %.2f%%)",
					failureRate * 100, thresholds.maxLoadFailureRate * 100
				));
			}
		}

		// 평균 로드 시간 검사
		double avgLoadTime = metrics.averageLoadPenalty();
		if (avgLoadTime > thresholds.maxAverageLoadTimeNanos) {
			builder.addWarning(String.format(
				"High average load time: %.2fms (threshold: %.2fms)",
				avgLoadTime / 1_000_000, thresholds.maxAverageLoadTimeNanos / 1_000_000.0
			));
		}

		// 요청 수 검사
		long requests = metrics.requestCount();
		if (requests < thresholds.minRequests) {
			builder.addInfo(String.format(
				"Low request count: %d (threshold: %d)",
				requests, thresholds.minRequests
			));
		}

		return builder.build();
	}

	/**
	 * 캐시가 건강한지 확인합니다.
	 *
	 * @return 건강하면 true
	 */
	public boolean isHealthy() {
		return check().isHealthy();
	}

	/**
	 * 건강 임계값 설정
	 */
	public static class HealthThresholds {

		/**
		 * 기본 임계값
		 */
		public static final HealthThresholds DEFAULT = new HealthThresholds(
			0.5,    // minHitRate: 50%
			0.1,    // maxLoadFailureRate: 10%
			100_000_000L,  // maxAverageLoadTimeNanos: 100ms
			10      // minRequests
		);

		/**
		 * 엄격한 임계값
		 */
		public static final HealthThresholds STRICT = new HealthThresholds(
			0.8,    // minHitRate: 80%
			0.05,   // maxLoadFailureRate: 5%
			50_000_000L,   // maxAverageLoadTimeNanos: 50ms
			100     // minRequests
		);

		/**
		 * 느슨한 임계값
		 */
		public static final HealthThresholds RELAXED = new HealthThresholds(
			0.3,    // minHitRate: 30%
			0.2,    // maxLoadFailureRate: 20%
			500_000_000L,  // maxAverageLoadTimeNanos: 500ms
			5       // minRequests
		);

		public final double minHitRate;
		public final double maxLoadFailureRate;
		public final long maxAverageLoadTimeNanos;
		public final long minRequests;

		public HealthThresholds(
			double minHitRate,
			double maxLoadFailureRate,
			long maxAverageLoadTimeNanos,
			long minRequests
		) {
			this.minHitRate = minHitRate;
			this.maxLoadFailureRate = maxLoadFailureRate;
			this.maxAverageLoadTimeNanos = maxAverageLoadTimeNanos;
			this.minRequests = minRequests;
		}
	}

	/**
	 * 건강 상태 결과
	 */
	public static class HealthStatus {

		private final boolean healthy;
		private final String[] errors;
		private final String[] warnings;
		private final String[] info;

		private HealthStatus(boolean healthy, String[] errors, String[] warnings, String[] info) {
			this.healthy = healthy;
			this.errors = errors;
			this.warnings = warnings;
			this.info = info;
		}

		/**
		 * 건강한지 확인합니다.
		 *
		 * @return 에러가 없으면 true
		 */
		public boolean isHealthy() {
			return healthy;
		}

		/**
		 * 에러 메시지를 반환합니다.
		 *
		 * @return 에러 메시지 배열
		 */
		public String[] errors() {
			return errors;
		}

		/**
		 * 경고 메시지를 반환합니다.
		 *
		 * @return 경고 메시지 배열
		 */
		public String[] warnings() {
			return warnings;
		}

		/**
		 * 정보 메시지를 반환합니다.
		 *
		 * @return 정보 메시지 배열
		 */
		public String[] info() {
			return info;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("HealthStatus{healthy=").append(healthy);
			if (errors.length > 0) {
				sb.append(", errors=").append(java.util.Arrays.toString(errors));
			}
			if (warnings.length > 0) {
				sb.append(", warnings=").append(java.util.Arrays.toString(warnings));
			}
			if (info.length > 0) {
				sb.append(", info=").append(java.util.Arrays.toString(info));
			}
			sb.append("}");
			return sb.toString();
		}

		static class Builder {
			private final java.util.List<String> errors = new java.util.ArrayList<>();
			private final java.util.List<String> warnings = new java.util.ArrayList<>();
			private final java.util.List<String> info = new java.util.ArrayList<>();

			Builder addError(String message) {
				errors.add(message);
				return this;
			}

			Builder addWarning(String message) {
				warnings.add(message);
				return this;
			}

			Builder addInfo(String message) {
				info.add(message);
				return this;
			}

			HealthStatus build() {
				boolean healthy = errors.isEmpty();
				return new HealthStatus(
					healthy,
					errors.toArray(new String[0]),
					warnings.toArray(new String[0]),
					info.toArray(new String[0])
				);
			}
		}
	}
}
