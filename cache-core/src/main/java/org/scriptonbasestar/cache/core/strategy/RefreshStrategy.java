package org.scriptonbasestar.cache.core.strategy;

/**
 * Refresh strategy for cache entries.
 * <p>
 * Determines when and how cache entries are refreshed/reloaded.
 * </p>
 *
 * <h3>Strategy Comparison:</h3>
 * <table border="1">
 * <tr>
 *   <th>Strategy</th>
 *   <th>Refresh Timing</th>
 *   <th>First Request</th>
 *   <th>Use Case</th>
 * </tr>
 * <tr>
 *   <td>ON_MISS</td>
 *   <td>After expiry</td>
 *   <td>Slow (loads synchronously)</td>
 *   <td>Default, simple caching</td>
 * </tr>
 * <tr>
 *   <td>REFRESH_AHEAD</td>
 *   <td>Before expiry</td>
 *   <td>Always fast</td>
 *   <td>High-traffic, latency-sensitive</td>
 * </tr>
 * </table>
 *
 * <h3>Usage Examples:</h3>
 * <pre>{@code
 * // ON_MISS (default)
 * SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
 *     .loader(loader)
 *     .timeoutSec(300)
 *     .refreshStrategy(RefreshStrategy.ON_MISS)
 *     .build();
 *
 * // REFRESH_AHEAD (proactive)
 * SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
 *     .loader(loader)
 *     .timeoutSec(300)
 *     .refreshStrategy(RefreshStrategy.REFRESH_AHEAD)
 *     .refreshAheadFactor(0.8)  // Refresh at 80% of TTL
 *     .build();
 *
 * // User always gets fast response
 * V value = cache.get(key);  // Fast, even if near expiry
 * }</pre>
 *
 * @author archmagece
 * @since 2025-01
 */
public enum RefreshStrategy {
	/**
	 * Refresh on cache miss (after expiry). Default strategy.
	 * <p>
	 * Entry is loaded synchronously when requested after expiration.
	 * First request after expiry will be slow.
	 * </p>
	 *
	 * <h4>Characteristics:</h4>
	 * <ul>
	 *   <li>Simplest strategy</li>
	 *   <li>No background threads</li>
	 *   <li>First miss is slow</li>
	 *   <li>Subsequent hits are fast</li>
	 * </ul>
	 *
	 * <h4>Timeline:</h4>
	 * <pre>
	 * Time:    0s    100s   200s   300s   305s
	 * Status:  Load  Hit    Hit    Expire Slow-Load
	 * </pre>
	 *
	 * <h4>Use Cases:</h4>
	 * <ul>
	 *   <li>Low-traffic applications</li>
	 *   <li>Latency not critical</li>
	 *   <li>Simple caching needs</li>
	 * </ul>
	 */
	ON_MISS,

	/**
	 * Refresh ahead of expiry (proactive background refresh).
	 * <p>
	 * Entry is refreshed in background before TTL expires.
	 * Users always get fast response with fresh data.
	 * </p>
	 *
	 * <h4>Characteristics:</h4>
	 * <ul>
	 *   <li>Background refresh thread</li>
	 *   <li>Always fast response</li>
	 *   <li>Fresh data maintained</li>
	 *   <li>Graceful degradation on refresh failure</li>
	 * </ul>
	 *
	 * <h4>Timeline:</h4>
	 * <pre>
	 * Time:    0s    100s   200s   240s      300s   305s
	 * Status:  Load  Hit    Hit    BG-Refresh Hit   Hit
	 *                              (at 80%)
	 * </pre>
	 *
	 * <h4>Configuration:</h4>
	 * <pre>{@code
	 * SBCacheMap.<K, V>builder()
	 *     .refreshStrategy(RefreshStrategy.REFRESH_AHEAD)
	 *     .refreshAheadFactor(0.8)      // Refresh at 80% of TTL
	 *     .refreshAheadThreads(2)       // Optional: thread pool size
	 *     .build();
	 * }</pre>
	 *
	 * <h4>Behavior:</h4>
	 * <ul>
	 *   <li>When TTL reaches N% (e.g., 80%), background refresh starts</li>
	 *   <li>Old value served to users during refresh</li>
	 *   <li>On refresh success: new value replaces old value</li>
	 *   <li>On refresh failure: old value retained, retry on next access</li>
	 * </ul>
	 *
	 * <h4>Use Cases:</h4>
	 * <ul>
	 *   <li>High-traffic APIs (always fast)</li>
	 *   <li>Latency-sensitive applications</li>
	 *   <li>Real-time dashboards</li>
	 *   <li>Data that changes frequently</li>
	 * </ul>
	 *
	 * <h4>Trade-offs:</h4>
	 * <ul>
	 *   <li><b>Pro:</b> Always fast response time</li>
	 *   <li><b>Pro:</b> Fresher data</li>
	 *   <li><b>Pro:</b> Better user experience</li>
	 *   <li><b>Con:</b> More frequent loader calls</li>
	 *   <li><b>Con:</b> Background thread overhead</li>
	 *   <li><b>Con:</b> Slightly higher DB load</li>
	 * </ul>
	 *
	 * <h4>Failure Handling:</h4>
	 * <p>
	 * If background refresh fails:
	 * </p>
	 * <ol>
	 *   <li>Log the error</li>
	 *   <li>Keep serving stale data (better than no data)</li>
	 *   <li>Mark entry for retry on next get()</li>
	 *   <li>Do not extend TTL (prevent infinite staleness)</li>
	 * </ol>
	 *
	 * <h4>Example Scenario:</h4>
	 * <pre>{@code
	 * // Product catalog with 5-minute TTL
	 * SBCacheMap<Long, Product> cache = SBCacheMap.<Long, Product>builder()
	 *     .loader(productRepository::findById)
	 *     .timeoutSec(300)  // 5 minutes
	 *     .refreshStrategy(RefreshStrategy.REFRESH_AHEAD)
	 *     .refreshAheadFactor(0.8)  // Refresh at 4 minutes
	 *     .build();
	 *
	 * // Timeline:
	 * // 0:00 - Product loaded (price: $100)
	 * // 3:59 - User gets $100 (fast, from cache)
	 * // 4:00 - Background refresh starts (price updated to $110)
	 * // 4:05 - User gets $110 (fast, fresh data)
	 * // 5:00 - TTL would expire, but already refreshed at 4:00
	 * }</pre>
	 */
	REFRESH_AHEAD
}
