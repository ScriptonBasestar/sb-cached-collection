package org.scriptonbasestar.cache.spring.actuator;

import org.scriptonbasestar.cache.collection.metrics.CacheHealthCheck;
import org.scriptonbasestar.cache.collection.metrics.CacheMetrics;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Spring Boot Actuator HealthIndicator for SB Cache.
 * <p>
 * This component integrates SB Cache health checking with Spring Boot Actuator,
 * allowing cache health to be monitored via the /actuator/health endpoint.
 * </p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Automatic health status reporting (UP/DOWN)</li>
 *   <li>Detailed metrics in health response</li>
 *   <li>Customizable health thresholds</li>
 *   <li>Integration with Spring Boot management</li>
 * </ul>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * @Configuration
 * public class CacheConfig {
 *     @Bean
 *     public CacheHealthIndicator cacheHealthIndicator(CacheMetrics metrics) {
 *         return new CacheHealthIndicator("myCache", metrics);
 *     }
 * }
 *
 * // Access via:
 * // GET /actuator/health/cache
 * }</pre>
 *
 * <h3>Response Format:</h3>
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "details": {
 *     "cacheName": "users",
 *     "requestCount": 1000,
 *     "hitCount": 850,
 *     "hitRate": 85.0,
 *     "loadSuccessCount": 150,
 *     "loadFailureCount": 0,
 *     "averageLoadTime": 12.5
 *   }
 * }
 * }</pre>
 *
 * @author archmagece
 * @since 2025-01
 */
public class CacheHealthIndicator implements HealthIndicator {

	private final String cacheName;
	private final CacheHealthCheck healthCheck;
	private final CacheMetrics metrics;

	/**
	 * Creates a new CacheHealthIndicator with default health thresholds.
	 *
	 * @param cacheName the cache name for identification
	 * @param metrics   the cache metrics to monitor
	 */
	public CacheHealthIndicator(String cacheName, CacheMetrics metrics) {
		this(cacheName, metrics, CacheHealthCheck.HealthThresholds.DEFAULT);
	}

	/**
	 * Creates a new CacheHealthIndicator with custom health thresholds.
	 *
	 * @param cacheName  the cache name for identification
	 * @param metrics    the cache metrics to monitor
	 * @param thresholds custom health thresholds
	 */
	public CacheHealthIndicator(String cacheName, CacheMetrics metrics,
								  CacheHealthCheck.HealthThresholds thresholds) {
		if (cacheName == null || cacheName.trim().isEmpty()) {
			throw new IllegalArgumentException("cacheName must not be null or empty");
		}
		if (metrics == null) {
			throw new IllegalArgumentException("metrics must not be null");
		}
		if (thresholds == null) {
			throw new IllegalArgumentException("thresholds must not be null");
		}

		this.cacheName = cacheName;
		this.metrics = metrics;
		this.healthCheck = new CacheHealthCheck(metrics, thresholds);
	}

	@Override
	public Health health() {
		CacheHealthCheck.HealthStatus status = healthCheck.check();

		Health.Builder builder = status.isHealthy() ? Health.up() : Health.down();

		// 기본 메트릭 정보
		builder.withDetail("cacheName", cacheName);
		builder.withDetail("requestCount", metrics.requestCount());
		builder.withDetail("hitCount", metrics.hitCount());
		builder.withDetail("missCount", metrics.missCount());
		builder.withDetail("hitRate", String.format("%.2f%%", metrics.hitRate() * 100));
		builder.withDetail("loadSuccessCount", metrics.loadSuccessCount());
		builder.withDetail("loadFailureCount", metrics.loadFailureCount());
		builder.withDetail("evictionCount", metrics.evictionCount());
		builder.withDetail("averageLoadTime", String.format("%.2fms",
			metrics.averageLoadPenalty() / 1_000_000.0));

		// 경고 및 에러 정보
		if (status.warnings().length > 0) {
			builder.withDetail("warnings", status.warnings());
		}
		if (status.errors().length > 0) {
			builder.withDetail("errors", status.errors());
		}
		if (status.info().length > 0) {
			builder.withDetail("info", status.info());
		}

		return builder.build();
	}

	/**
	 * Gets the cache name.
	 *
	 * @return cache name
	 */
	public String getCacheName() {
		return cacheName;
	}

	/**
	 * Gets the underlying metrics.
	 *
	 * @return cache metrics
	 */
	public CacheMetrics getMetrics() {
		return metrics;
	}

	/**
	 * Checks if the cache is healthy.
	 *
	 * @return true if healthy, false otherwise
	 */
	public boolean isHealthy() {
		return healthCheck.isHealthy();
	}
}
