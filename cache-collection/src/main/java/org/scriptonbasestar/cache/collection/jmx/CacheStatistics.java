package org.scriptonbasestar.cache.collection.jmx;

import org.scriptonbasestar.cache.collection.metrics.CacheMetrics;

/**
 * Default implementation of {@link CacheStatisticsMXBean} for JMX monitoring.
 * <p>
 * This class wraps {@link CacheMetrics} and exposes cache statistics via JMX.
 * It can be registered with the platform MBeanServer for monitoring in JConsole/VisualVM.
 * </p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Real-time cache statistics (hits, misses, load times, evictions)</li>
 *   <li>Percentage calculations (hit rate, miss rate, fill percent)</li>
 *   <li>Operations (reset statistics, get summary)</li>
 *   <li>Thread-safe delegation to CacheMetrics</li>
 * </ul>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * CacheMetrics metrics = new CacheMetrics();
 * CacheStatistics mbean = new CacheStatistics(metrics, "users");
 *
 * MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
 * ObjectName name = new ObjectName("org.scriptonbasestar.cache:type=SBCacheMap,name=users");
 * mbs.registerMBean(mbean, name);
 * }</pre>
 *
 * @author archmagece
 * @since 2025-01
 */
public class CacheStatistics implements CacheStatisticsMXBean {

	private final CacheMetrics metrics;
	private final String cacheName;
	private volatile int currentSize = -1;
	private volatile int maxSize = -1;

	/**
	 * Creates a new CacheStatistics instance.
	 *
	 * @param metrics    the cache metrics to expose
	 * @param cacheName  the cache name for identification
	 * @throws IllegalArgumentException if metrics or cacheName is null/empty
	 */
	public CacheStatistics(CacheMetrics metrics, String cacheName) {
		if (metrics == null) {
			throw new IllegalArgumentException("metrics must not be null");
		}
		if (cacheName == null || cacheName.trim().isEmpty()) {
			throw new IllegalArgumentException("cacheName must not be null or empty");
		}
		this.metrics = metrics;
		this.cacheName = cacheName;
	}

	@Override
	public String getCacheName() {
		return cacheName;
	}

	@Override
	public long getRequestCount() {
		return metrics.requestCount();
	}

	@Override
	public long getHitCount() {
		return metrics.hitCount();
	}

	@Override
	public long getMissCount() {
		return metrics.missCount();
	}

	@Override
	public double getHitRatePercent() {
		return metrics.hitRate() * 100.0;
	}

	@Override
	public double getMissRatePercent() {
		return metrics.missRate() * 100.0;
	}

	@Override
	public long getLoadSuccessCount() {
		return metrics.loadSuccessCount();
	}

	@Override
	public long getLoadFailureCount() {
		return metrics.loadFailureCount();
	}

	@Override
	public double getAverageLoadTimeMillis() {
		return metrics.averageLoadPenalty() / 1_000_000.0;
	}

	@Override
	public double getTotalLoadTimeMillis() {
		// totalLoadTime은 CacheMetrics에 private이므로 averageLoadPenalty * count로 계산
		long loadCount = metrics.loadSuccessCount();
		if (loadCount == 0) {
			return 0.0;
		}
		return (metrics.averageLoadPenalty() * loadCount) / 1_000_000.0;
	}

	@Override
	public long getEvictionCount() {
		return metrics.evictionCount();
	}

	@Override
	public int getCurrentSize() {
		return currentSize;
	}

	@Override
	public int getMaxSize() {
		return maxSize;
	}

	@Override
	public double getFillPercent() {
		if (currentSize < 0 || maxSize <= 0) {
			return -1.0;
		}
		return (currentSize * 100.0) / maxSize;
	}

	@Override
	public void resetStatistics() {
		metrics.reset();
	}

	@Override
	public String getStatisticsSummary() {
		StringBuilder sb = new StringBuilder();
		sb.append("Cache Statistics for '").append(cacheName).append("':\n");
		sb.append("  Requests: ").append(getRequestCount()).append("\n");
		sb.append("  Hits: ").append(getHitCount()).append(" (").append(String.format("%.2f", getHitRatePercent())).append("%)\n");
		sb.append("  Misses: ").append(getMissCount()).append(" (").append(String.format("%.2f", getMissRatePercent())).append("%)\n");
		sb.append("  Load Success: ").append(getLoadSuccessCount()).append("\n");
		sb.append("  Load Failure: ").append(getLoadFailureCount()).append("\n");
		sb.append("  Avg Load Time: ").append(String.format("%.2f", getAverageLoadTimeMillis())).append(" ms\n");
		sb.append("  Evictions: ").append(getEvictionCount()).append("\n");

		if (currentSize >= 0) {
			sb.append("  Current Size: ").append(currentSize);
			if (maxSize > 0) {
				sb.append(" / ").append(maxSize);
				sb.append(" (").append(String.format("%.1f", getFillPercent())).append("%)");
			}
			sb.append("\n");
		}

		return sb.toString();
	}

	/**
	 * Updates the current cache size.
	 * <p>
	 * This method should be called by the cache implementation
	 * to keep the size information up-to-date for JMX monitoring.
	 * </p>
	 *
	 * @param currentSize the current number of entries in the cache
	 */
	public void updateCurrentSize(int currentSize) {
		this.currentSize = currentSize;
	}

	/**
	 * Sets the maximum cache size (capacity).
	 * <p>
	 * This method should be called during cache initialization
	 * if the cache has a size limit.
	 * </p>
	 *
	 * @param maxSize the maximum number of entries allowed, or -1 for unlimited
	 */
	public void setMaxSize(int maxSize) {
		this.maxSize = maxSize;
	}

	/**
	 * Gets the underlying CacheMetrics instance.
	 *
	 * @return cache metrics
	 */
	public CacheMetrics getMetrics() {
		return metrics;
	}

	@Override
	public String toString() {
		return "CacheStatistics{" +
			"cacheName='" + cacheName + '\'' +
			", requests=" + getRequestCount() +
			", hits=" + getHitCount() +
			", hitRate=" + String.format("%.2f%%", getHitRatePercent()) +
			", currentSize=" + currentSize +
			", maxSize=" + maxSize +
			'}';
	}
}
