package org.scriptonbasestar.cache.core.strategy;

/**
 * Write strategy for cache updates.
 * <p>
 * Determines how cache writes are propagated to the underlying data source.
 * </p>
 *
 * <h3>Strategy Comparison:</h3>
 * <table border="1">
 * <tr>
 *   <th>Strategy</th>
 *   <th>DB Write Timing</th>
 *   <th>Consistency</th>
 *   <th>Performance</th>
 *   <th>Use Case</th>
 * </tr>
 * <tr>
 *   <td>READ_ONLY</td>
 *   <td>Never (manual)</td>
 *   <td>N/A</td>
 *   <td>Fastest</td>
 *   <td>Read-heavy workloads</td>
 * </tr>
 * <tr>
 *   <td>WRITE_THROUGH</td>
 *   <td>Immediate (sync)</td>
 *   <td>Strong</td>
 *   <td>Slower writes</td>
 *   <td>Strong consistency needed</td>
 * </tr>
 * <tr>
 *   <td>WRITE_BEHIND</td>
 *   <td>Batched (async)</td>
 *   <td>Eventual</td>
 *   <td>Fast writes</td>
 *   <td>High write throughput</td>
 * </tr>
 * </table>
 *
 * <h3>Usage Examples:</h3>
 * <pre>{@code
 * // Read-Only (default)
 * SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
 *     .loader(loader)
 *     .writeStrategy(WriteStrategy.READ_ONLY)
 *     .build();
 *
 * // Write-Through (strong consistency)
 * SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
 *     .loader(loader)
 *     .writer(writer)
 *     .writeStrategy(WriteStrategy.WRITE_THROUGH)
 *     .build();
 *
 * cache.put(key, value);  // Immediately writes to DB
 *
 * // Write-Behind (high throughput)
 * SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
 *     .loader(loader)
 *     .writer(writer)
 *     .writeStrategy(WriteStrategy.WRITE_BEHIND)
 *     .writeBehindBatchSize(100)
 *     .writeBehindIntervalSeconds(5)
 *     .build();
 *
 * cache.put(key, value);  // Queued, written later in batch
 * }</pre>
 *
 * @author archmagece
 * @since 2025-01
 */
public enum WriteStrategy {
	/**
	 * Read-only cache. No automatic writes to data source.
	 * <p>
	 * Application is responsible for updating the data source manually.
	 * Cache is only updated via put() calls.
	 * </p>
	 *
	 * <h4>Characteristics:</h4>
	 * <ul>
	 *   <li>No CacheWriter required</li>
	 *   <li>Manual DB updates</li>
	 *   <li>Simplest strategy</li>
	 *   <li>Best for read-heavy workloads</li>
	 * </ul>
	 *
	 * <h4>Example:</h4>
	 * <pre>{@code
	 * cache.put(key, value);  // Only updates cache
	 * repository.save(value); // Manual DB update
	 * }</pre>
	 */
	READ_ONLY,

	/**
	 * Write-through cache. Writes to data source immediately (synchronously).
	 * <p>
	 * Every put() operation updates both cache and data source atomically.
	 * Ensures strong consistency between cache and data source.
	 * </p>
	 *
	 * <h4>Characteristics:</h4>
	 * <ul>
	 *   <li>CacheWriter required</li>
	 *   <li>Synchronous writes</li>
	 *   <li>Strong consistency</li>
	 *   <li>Slower write performance</li>
	 *   <li>No data loss on cache failure</li>
	 * </ul>
	 *
	 * <h4>Example:</h4>
	 * <pre>{@code
	 * cache.put(key, value);
	 * // 1. Update cache
	 * // 2. Write to DB (blocks until complete)
	 * // 3. Return
	 * }</pre>
	 *
	 * <h4>Use Cases:</h4>
	 * <ul>
	 *   <li>Financial transactions</li>
	 *   <li>Critical data that cannot be lost</li>
	 *   <li>Strong consistency requirements</li>
	 * </ul>
	 */
	WRITE_THROUGH,

	/**
	 * Write-behind cache (also known as Write-Back). Writes to data source asynchronously in batches.
	 * <p>
	 * put() operations update cache immediately and return.
	 * Writes are queued and flushed to data source periodically in batches.
	 * </p>
	 *
	 * <h4>Characteristics:</h4>
	 * <ul>
	 *   <li>CacheWriter required</li>
	 *   <li>Asynchronous writes</li>
	 *   <li>Eventual consistency</li>
	 *   <li>Fast write performance</li>
	 *   <li>Possible data loss on cache failure</li>
	 *   <li>Batch writes reduce DB load</li>
	 * </ul>
	 *
	 * <h4>Example:</h4>
	 * <pre>{@code
	 * cache.put(key, value);
	 * // 1. Update cache
	 * // 2. Add to write queue
	 * // 3. Return immediately
	 *
	 * // Later (after 5 seconds or 100 entries):
	 * // Batch write queued entries to DB
	 * }</pre>
	 *
	 * <h4>Configuration:</h4>
	 * <pre>{@code
	 * SBCacheMap.<K, V>builder()
	 *     .writeStrategy(WriteStrategy.WRITE_BEHIND)
	 *     .writeBehindBatchSize(100)        // Flush after 100 writes
	 *     .writeBehindIntervalSeconds(5)    // Flush every 5 seconds
	 *     .writeBehindMaxQueueSize(10000)   // Max queue size
	 *     .build();
	 * }</pre>
	 *
	 * <h4>Use Cases:</h4>
	 * <ul>
	 *   <li>High write throughput requirements</li>
	 *   <li>Analytics/logging data</li>
	 *   <li>Non-critical data</li>
	 *   <li>Batch processing friendly scenarios</li>
	 * </ul>
	 *
	 * <h4>Trade-offs:</h4>
	 * <ul>
	 *   <li>Risk of data loss if cache crashes before flush</li>
	 *   <li>Temporary inconsistency between cache and DB</li>
	 *   <li>More complex error handling</li>
	 *   <li>Requires careful tuning of batch size and interval</li>
	 * </ul>
	 */
	WRITE_BEHIND
}
