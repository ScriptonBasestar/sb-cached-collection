/**
 * Micrometer 기반 캐시 메트릭 통합
 *
 * <p>Micrometer MeterRegistry와 SB Cache의 메트릭을 연동합니다.</p>
 *
 * <h3>주요 클래스</h3>
 * <ul>
 *   <li>{@link org.scriptonbasestar.cache.metrics.micrometer.MicrometerMetricsAdapter} - Micrometer 어댑터</li>
 *   <li>{@link org.scriptonbasestar.cache.metrics.micrometer.PrometheusMetricsHelper} - Prometheus 편의 클래스</li>
 * </ul>
 *
 * <h3>지원 메트릭</h3>
 * <ul>
 *   <li>cache.hits - 캐시 히트 횟수 (Counter)</li>
 *   <li>cache.misses - 캐시 미스 횟수 (Counter)</li>
 *   <li>cache.loads{result=success|failure} - 로드 횟수 (Counter)</li>
 *   <li>cache.evictions - 축출 횟수 (Counter)</li>
 *   <li>cache.load.duration - 로드 시간 (Timer)</li>
 *   <li>cache.size - 캐시 크기 (DistributionSummary)</li>
 *   <li>cache.hit.rate - 히트율 (Gauge)</li>
 *   <li>cache.miss.rate - 미스율 (Gauge)</li>
 *   <li>cache.requests.total - 총 요청 수 (Gauge)</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // Prometheus 레지스트리 생성
 * PrometheusMeterRegistry registry = PrometheusMetricsHelper.createPrometheusRegistry();
 *
 * // 캐시 메트릭 생성
 * CacheMetrics metrics = new CacheMetrics();
 *
 * // 어댑터로 연결
 * MicrometerMetricsAdapter adapter = PrometheusMetricsHelper.bindMetrics(
 *     metrics,
 *     registry,
 *     "user-cache"
 * );
 *
 * // 메트릭 기록
 * adapter.recordHit();
 * adapter.recordMiss();
 * adapter.recordLoadSuccess(1000000L);
 *
 * // Prometheus 포맷 출력
 * String prometheusFormat = registry.scrape();
 * System.out.println(prometheusFormat);
 * }</pre>
 *
 * <h3>Prometheus 메트릭 예시</h3>
 * <pre>
 * # HELP cache_hits_total Cache hit count
 * # TYPE cache_hits_total counter
 * cache_hits_total{cache="user-cache",} 15234.0
 *
 * # HELP cache_misses_total Cache miss count
 * # TYPE cache_misses_total counter
 * cache_misses_total{cache="user-cache",} 892.0
 *
 * # HELP cache_hit_rate
 * # TYPE cache_hit_rate gauge
 * cache_hit_rate{cache="user-cache",} 0.945
 *
 * # HELP cache_load_duration_seconds Cache load duration
 * # TYPE cache_load_duration_seconds summary
 * cache_load_duration_seconds{cache="user-cache",quantile="0.5",} 0.012
 * cache_load_duration_seconds{cache="user-cache",quantile="0.95",} 0.032
 * cache_load_duration_seconds{cache="user-cache",quantile="0.99",} 0.087
 * </pre>
 *
 * @author archmagece
 * @since 2025-01
 */
package org.scriptonbasestar.cache.metrics.micrometer;
