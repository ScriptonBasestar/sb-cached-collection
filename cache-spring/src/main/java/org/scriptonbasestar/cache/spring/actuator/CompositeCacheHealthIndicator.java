package org.scriptonbasestar.cache.spring.actuator;

import org.scriptonbasestar.cache.collection.metrics.CacheMetrics;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.HashMap;
import java.util.Map;

/**
 * Composite Health Indicator for multiple SB Caches.
 * <p>
 * This component aggregates health status from multiple caches and reports
 * an overall system health status via Spring Boot Actuator.
 * </p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Monitors multiple caches simultaneously</li>
 *   <li>Aggregate health status (DOWN if any cache is unhealthy)</li>
 *   <li>Per-cache detailed metrics</li>
 *   <li>Overall statistics summary</li>
 * </ul>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * @Configuration
 * public class CacheConfig {
 *     @Bean
 *     public CompositeCacheHealthIndicator cacheHealthIndicator(
 *             Map<String, CacheMetrics> cacheMetricsMap) {
 *         return new CompositeCacheHealthIndicator(cacheMetricsMap);
 *     }
 * }
 *
 * // Access via:
 * // GET /actuator/health/caches
 * }</pre>
 *
 * <h3>Response Format:</h3>
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "details": {
 *     "totalCaches": 3,
 *     "healthyCaches": 3,
 *     "unhealthyCaches": 0,
 *     "totalRequests": 5000,
 *     "overallHitRate": "87.5%",
 *     "caches": {
 *       "users": { "status": "UP", ... },
 *       "products": { "status": "UP", ... },
 *       "sessions": { "status": "UP", ... }
 *     }
 *   }
 * }
 * }</pre>
 *
 * @author archmagece
 * @since 2025-01
 */
public class CompositeCacheHealthIndicator implements HealthIndicator {

	private final Map<String, CacheHealthIndicator> indicators;

	/**
	 * Creates a CompositeCacheHealthIndicator from a map of cache metrics.
	 *
	 * @param metricsMap map of cache name to CacheMetrics
	 */
	public CompositeCacheHealthIndicator(Map<String, CacheMetrics> metricsMap) {
		if (metricsMap == null) {
			throw new IllegalArgumentException("metricsMap must not be null");
		}

		this.indicators = new HashMap<>();
		for (Map.Entry<String, CacheMetrics> entry : metricsMap.entrySet()) {
			indicators.put(entry.getKey(),
				new CacheHealthIndicator(entry.getKey(), entry.getValue()));
		}
	}

	/**
	 * Creates a CompositeCacheHealthIndicator from individual indicators.
	 *
	 * @param indicators map of cache name to CacheHealthIndicator
	 */
	public CompositeCacheHealthIndicator(Map<String, CacheHealthIndicator> indicators, boolean dummy) {
		if (indicators == null) {
			throw new IllegalArgumentException("indicators must not be null");
		}
		this.indicators = new HashMap<>(indicators);
	}

	@Override
	public Health health() {
		if (indicators.isEmpty()) {
			return Health.up()
				.withDetail("totalCaches", 0)
				.withDetail("message", "No caches configured")
				.build();
		}

		// 각 캐시의 상태 수집
		Map<String, Health> cacheHealthMap = new HashMap<>();
		int healthyCount = 0;
		int unhealthyCount = 0;
		long totalRequests = 0;
		long totalHits = 0;

		for (Map.Entry<String, CacheHealthIndicator> entry : indicators.entrySet()) {
			String cacheName = entry.getKey();
			CacheHealthIndicator indicator = entry.getValue();
			Health cacheHealth = indicator.health();

			cacheHealthMap.put(cacheName, cacheHealth);

			if (indicator.isHealthy()) {
				healthyCount++;
			} else {
				unhealthyCount++;
			}

			// 통계 집계
			CacheMetrics metrics = indicator.getMetrics();
			totalRequests += metrics.requestCount();
			totalHits += metrics.hitCount();
		}

		// 전체 상태 결정 (하나라도 unhealthy면 DOWN)
		Health.Builder builder = (unhealthyCount == 0) ? Health.up() : Health.down();

		// 전체 통계
		builder.withDetail("totalCaches", indicators.size());
		builder.withDetail("healthyCaches", healthyCount);
		builder.withDetail("unhealthyCaches", unhealthyCount);
		builder.withDetail("totalRequests", totalRequests);

		if (totalRequests > 0) {
			double overallHitRate = (double) totalHits / totalRequests * 100;
			builder.withDetail("overallHitRate", String.format("%.2f%%", overallHitRate));
		}

		// 각 캐시별 상세 정보
		builder.withDetail("caches", cacheHealthMap);

		return builder.build();
	}

	/**
	 * Adds a cache to be monitored.
	 *
	 * @param cacheName cache name
	 * @param metrics   cache metrics
	 */
	public void addCache(String cacheName, CacheMetrics metrics) {
		if (cacheName == null || cacheName.trim().isEmpty()) {
			throw new IllegalArgumentException("cacheName must not be null or empty");
		}
		if (metrics == null) {
			throw new IllegalArgumentException("metrics must not be null");
		}
		indicators.put(cacheName, new CacheHealthIndicator(cacheName, metrics));
	}

	/**
	 * Removes a cache from monitoring.
	 *
	 * @param cacheName cache name
	 */
	public void removeCache(String cacheName) {
		indicators.remove(cacheName);
	}

	/**
	 * Gets the number of monitored caches.
	 *
	 * @return cache count
	 */
	public int getCacheCount() {
		return indicators.size();
	}

	/**
	 * Checks if all caches are healthy.
	 *
	 * @return true if all healthy, false otherwise
	 */
	public boolean isAllHealthy() {
		return indicators.values().stream().allMatch(CacheHealthIndicator::isHealthy);
	}
}
