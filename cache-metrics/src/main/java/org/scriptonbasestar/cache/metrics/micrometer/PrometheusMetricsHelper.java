package org.scriptonbasestar.cache.metrics.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.scriptonbasestar.cache.collection.metrics.CacheMetrics;

/**
 * Prometheus 메트릭 간편 설정 헬퍼
 *
 * Prometheus 레지스트리를 쉽게 생성하고 캐시 메트릭을 연동합니다.
 *
 * <p>사용 예시:</p>
 * <pre>{@code
 * // Prometheus 레지스트리 생성 및 어댑터 연결
 * PrometheusMeterRegistry registry = PrometheusMetricsHelper.createPrometheusRegistry();
 * CacheMetrics metrics = new CacheMetrics();
 * MicrometerMetricsAdapter adapter = PrometheusMetricsHelper.bindMetrics(metrics, registry, "users");
 *
 * // Prometheus 포맷으로 메트릭 출력
 * String prometheusFormat = registry.scrape();
 * }</pre>
 *
 * @author archmagece
 * @since 2025-01
 */
public class PrometheusMetricsHelper {

	/**
	 * 기본 설정으로 Prometheus 레지스트리를 생성합니다.
	 *
	 * @return PrometheusMeterRegistry
	 */
	public static PrometheusMeterRegistry createPrometheusRegistry() {
		return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
	}

	/**
	 * 커스텀 설정으로 Prometheus 레지스트리를 생성합니다.
	 *
	 * @param config Prometheus 설정
	 * @return PrometheusMeterRegistry
	 */
	public static PrometheusMeterRegistry createPrometheusRegistry(PrometheusConfig config) {
		return new PrometheusMeterRegistry(config);
	}

	/**
	 * CacheMetrics를 MeterRegistry에 바인딩합니다.
	 *
	 * @param cacheMetrics 캐시 메트릭
	 * @param meterRegistry 메터 레지스트리
	 * @param cacheName 캐시 이름
	 * @return MicrometerMetricsAdapter
	 */
	public static MicrometerMetricsAdapter bindMetrics(
		CacheMetrics cacheMetrics,
		MeterRegistry meterRegistry,
		String cacheName
	) {
		return new MicrometerMetricsAdapter(cacheMetrics, meterRegistry, cacheName);
	}

	/**
	 * Prometheus 스크래핑 포맷으로 메트릭을 출력합니다.
	 *
	 * @param registry Prometheus 레지스트리
	 * @return Prometheus 포맷 문자열
	 */
	public static String scrapeMetrics(PrometheusMeterRegistry registry) {
		return registry.scrape();
	}

	/**
	 * 여러 캐시의 메트릭을 한번에 바인딩합니다.
	 *
	 * @param metricsMap 캐시 이름과 메트릭의 맵
	 * @param meterRegistry 메터 레지스트리
	 * @return 어댑터 배열
	 */
	public static MicrometerMetricsAdapter[] bindMultipleMetrics(
		java.util.Map<String, CacheMetrics> metricsMap,
		MeterRegistry meterRegistry
	) {
		return metricsMap.entrySet().stream()
			.map(entry -> new MicrometerMetricsAdapter(
				entry.getValue(),
				meterRegistry,
				entry.getKey()
			))
			.toArray(MicrometerMetricsAdapter[]::new);
	}

	private PrometheusMetricsHelper() {
		// 유틸리티 클래스
	}
}
