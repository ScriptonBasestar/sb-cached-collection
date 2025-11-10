/**
 * JMX monitoring support for SB Cache.
 * <p>
 * This package provides JMX (Java Management Extensions) integration for
 * real-time cache monitoring using tools like JConsole or VisualVM.
 * </p>
 *
 * <h2>Key Components:</h2>
 * <ul>
 *   <li>{@link org.scriptonbasestar.cache.collection.jmx.CacheStatisticsMXBean} - JMX MBean interface</li>
 *   <li>{@link org.scriptonbasestar.cache.collection.jmx.CacheStatistics} - Default MBean implementation</li>
 *   <li>{@link org.scriptonbasestar.cache.collection.jmx.JmxHelper} - Registration utility</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Create cache with metrics
 * CacheMetrics metrics = new CacheMetrics();
 * SBCacheMap<String, User> cache = SBCacheMap.<String, User>builder()
 *     .loader(userLoader)
 *     .metrics(metrics)
 *     .build();
 *
 * // Register with JMX
 * CacheStatistics mbean = JmxHelper.registerCache(metrics, "users", 10000);
 *
 * // Update size when cache changes
 * mbean.updateCurrentSize(cache.size());
 *
 * // Monitor in JConsole:
 * // MBeans -> org.scriptonbasestar.cache -> SBCacheMap -> users
 * //   Attributes: HitRatePercent, CurrentSize, AverageLoadTimeMillis, etc.
 * //   Operations: resetStatistics(), getStatisticsSummary()
 *
 * // Cleanup
 * JmxHelper.unregisterCache("users");
 * }</pre>
 *
 * <h2>Available Metrics:</h2>
 * <ul>
 *   <li><strong>Request Metrics:</strong> RequestCount, HitCount, MissCount</li>
 *   <li><strong>Rate Metrics:</strong> HitRatePercent, MissRatePercent</li>
 *   <li><strong>Load Metrics:</strong> LoadSuccessCount, LoadFailureCount, AverageLoadTimeMillis</li>
 *   <li><strong>Size Metrics:</strong> CurrentSize, MaxSize, FillPercent</li>
 *   <li><strong>Eviction Metrics:</strong> EvictionCount</li>
 * </ul>
 *
 * <h2>JConsole/VisualVM Monitoring:</h2>
 * <ol>
 *   <li>Launch JConsole: {@code jconsole} (from terminal)</li>
 *   <li>Connect to your Java process</li>
 *   <li>Navigate to MBeans tab</li>
 *   <li>Expand: org.scriptonbasestar.cache → SBCacheMap → [your cache name]</li>
 *   <li>View real-time attributes and invoke operations</li>
 * </ol>
 *
 * @author archmagece
 * @since 2025-01
 */
package org.scriptonbasestar.cache.collection.jmx;
