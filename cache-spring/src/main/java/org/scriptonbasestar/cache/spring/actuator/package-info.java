/**
 * Spring Boot Actuator integration for SB Cache health monitoring.
 * <p>
 * This package provides health check endpoints and indicators that integrate
 * SB Cache with Spring Boot Actuator's /actuator/health endpoint.
 * </p>
 *
 * <h2>Key Components:</h2>
 * <ul>
 *   <li>{@link org.scriptonbasestar.cache.spring.actuator.CacheHealthIndicator} - Single cache health indicator</li>
 *   <li>{@link org.scriptonbasestar.cache.spring.actuator.CompositeCacheHealthIndicator} - Multiple caches health indicator</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <h3>Single Cache:</h3>
 * <pre>{@code
 * @Configuration
 * public class CacheConfig {
 *     @Bean
 *     public SBCacheMap<String, User> userCache() {
 *         return SBCacheMap.<String, User>builder()
 *             .loader(userLoader)
 *             .enableMetrics(true)
 *             .build();
 *     }
 *
 *     @Bean
 *     public CacheHealthIndicator userCacheHealthIndicator(
 *             SBCacheMap<String, User> userCache) {
 *         return new CacheHealthIndicator("users", userCache.metrics());
 *     }
 * }
 * }</pre>
 *
 * <h3>Multiple Caches:</h3>
 * <pre>{@code
 * @Configuration
 * public class CacheConfig {
 *     @Bean
 *     public CompositeCacheHealthIndicator cacheHealthIndicator(
 *             Map<String, SBCacheMap<?, ?>> caches) {
 *         Map<String, CacheMetrics> metricsMap = new HashMap<>();
 *         for (Map.Entry<String, SBCacheMap<?, ?>> entry : caches.entrySet()) {
 *             metricsMap.put(entry.getKey(), entry.getValue().metrics());
 *         }
 *         return new CompositeCacheHealthIndicator(metricsMap);
 *     }
 * }
 * }</pre>
 *
 * <h2>Actuator Endpoints:</h2>
 * <ul>
 *   <li><strong>GET /actuator/health</strong> - Overall application health (includes cache health)</li>
 *   <li><strong>GET /actuator/health/cache</strong> - Detailed cache health (single cache)</li>
 *   <li><strong>GET /actuator/health/caches</strong> - Detailed health for all caches (composite)</li>
 * </ul>
 *
 * <h2>Response Format:</h2>
 * <h3>Single Cache Response:</h3>
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "details": {
 *     "cacheName": "users",
 *     "requestCount": 1000,
 *     "hitCount": 850,
 *     "hitRate": "85.00%",
 *     "loadSuccessCount": 150,
 *     "loadFailureCount": 0,
 *     "evictionCount": 25,
 *     "averageLoadTime": "12.50ms"
 *   }
 * }
 * }</pre>
 *
 * <h3>Composite Cache Response:</h3>
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "details": {
 *     "totalCaches": 3,
 *     "healthyCaches": 3,
 *     "unhealthyCaches": 0,
 *     "totalRequests": 5000,
 *     "overallHitRate": "87.50%",
 *     "caches": {
 *       "users": { "status": "UP", ... },
 *       "products": { "status": "UP", ... },
 *       "sessions": { "status": "UP", ... }
 *     }
 *   }
 * }
 * }</pre>
 *
 * <h2>Configuration:</h2>
 * <pre>
 * # application.yml
 * management:
 *   endpoints:
 *     web:
 *       exposure:
 *         include: health
 *   endpoint:
 *     health:
 *       show-details: always
 * </pre>
 *
 * @author archmagece
 * @since 2025-01
 */
package org.scriptonbasestar.cache.spring.actuator;
