package org.scriptonbasestar.cache.collection.jmx;

/**
 * JMX MBean interface for cache statistics monitoring.
 * <p>
 * This interface defines the attributes and operations exposed via JMX
 * for real-time cache monitoring using tools like JConsole or VisualVM.
 * </p>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * // Register with JMX
 * MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
 * ObjectName name = new ObjectName("org.scriptonbasestar.cache:type=SBCacheMap,name=users");
 * CacheStatisticsMXBean mbean = new CacheStatistics(cacheMetrics, cacheName);
 * mbs.registerMBean(mbean, name);
 *
 * // Monitor in JConsole
 * // MBeans -> org.scriptonbasestar.cache -> SBCacheMap -> users
 * }</pre>
 *
 * @author archmagece
 * @since 2025-01
 */
public interface CacheStatisticsMXBean {

	/**
	 * Gets the cache name for identification.
	 *
	 * @return cache name
	 */
	String getCacheName();

	/**
	 * Gets the total number of cache requests (hits + misses).
	 *
	 * @return request count
	 */
	long getRequestCount();

	/**
	 * Gets the number of cache hits.
	 *
	 * @return hit count
	 */
	long getHitCount();

	/**
	 * Gets the number of cache misses.
	 *
	 * @return miss count
	 */
	long getMissCount();

	/**
	 * Gets the cache hit rate as a percentage (0-100).
	 *
	 * @return hit rate percentage
	 */
	double getHitRatePercent();

	/**
	 * Gets the cache miss rate as a percentage (0-100).
	 *
	 * @return miss rate percentage
	 */
	double getMissRatePercent();

	/**
	 * Gets the number of successful cache loads.
	 *
	 * @return load success count
	 */
	long getLoadSuccessCount();

	/**
	 * Gets the number of failed cache loads.
	 *
	 * @return load failure count
	 */
	long getLoadFailureCount();

	/**
	 * Gets the average load time in milliseconds.
	 *
	 * @return average load time in ms
	 */
	double getAverageLoadTimeMillis();

	/**
	 * Gets the total load time in milliseconds.
	 *
	 * @return total load time in ms
	 */
	double getTotalLoadTimeMillis();

	/**
	 * Gets the number of cache evictions.
	 *
	 * @return eviction count
	 */
	long getEvictionCount();

	/**
	 * Gets the current cache size.
	 * <p>
	 * Note: This returns -1 if size tracking is not available.
	 * Override this method if your cache implementation tracks size.
	 * </p>
	 *
	 * @return current size or -1 if not available
	 */
	int getCurrentSize();

	/**
	 * Gets the maximum cache size (capacity).
	 * <p>
	 * Note: This returns -1 if max size is not configured.
	 * Override this method if your cache has a size limit.
	 * </p>
	 *
	 * @return max size or -1 if unlimited
	 */
	int getMaxSize();

	/**
	 * Gets the cache fill percentage (0-100).
	 * <p>
	 * Returns -1 if size information is not available.
	 * </p>
	 *
	 * @return fill percentage or -1
	 */
	double getFillPercent();

	// Operations

	/**
	 * Resets all cache statistics to zero.
	 * <p>
	 * This operation does not clear the cache data, only the metrics.
	 * </p>
	 */
	void resetStatistics();

	/**
	 * Gets a summary of cache statistics as a formatted string.
	 *
	 * @return statistics summary
	 */
	String getStatisticsSummary();
}
