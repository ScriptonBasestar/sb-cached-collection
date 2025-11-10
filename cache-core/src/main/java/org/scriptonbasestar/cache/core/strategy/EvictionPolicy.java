package org.scriptonbasestar.cache.core.strategy;

/**
 * Cache eviction policy for size-based eviction.
 * <p>
 * Determines which entry to remove when cache exceeds maxSize.
 * </p>
 *
 * <h3>Policy Comparison:</h3>
 * <table border="1">
 * <tr>
 *   <th>Policy</th>
 *   <th>Eviction Criteria</th>
 *   <th>Best For</th>
 *   <th>Overhead</th>
 * </tr>
 * <tr>
 *   <td>LRU</td>
 *   <td>Least Recently Used</td>
 *   <td>General purpose, temporal locality</td>
 *   <td>Low (LinkedHashMap)</td>
 * </tr>
 * <tr>
 *   <td>LFU</td>
 *   <td>Least Frequently Used</td>
 *   <td>Hot keys, frequency-based access</td>
 *   <td>Medium (access counter)</td>
 * </tr>
 * <tr>
 *   <td>FIFO</td>
 *   <td>First In First Out</td>
 *   <td>Sequential processing, queue-like</td>
 *   <td>Low (insertion order)</td>
 * </tr>
 * <tr>
 *   <td>RANDOM</td>
 *   <td>Random selection</td>
 *   <td>Simple, no pattern assumed</td>
 *   <td>Very Low</td>
 * </tr>
 * <tr>
 *   <td>TTL</td>
 *   <td>Oldest by creation time</td>
 *   <td>Time-sensitive data</td>
 *   <td>Low (creation time)</td>
 * </tr>
 * </table>
 *
 * <h3>Usage Examples:</h3>
 * <pre>{@code
 * // LRU (default) - General purpose
 * SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
 *     .maxSize(1000)
 *     .evictionPolicy(EvictionPolicy.LRU)
 *     .build();
 *
 * // LFU - For hot keys that are accessed frequently
 * SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
 *     .maxSize(1000)
 *     .evictionPolicy(EvictionPolicy.LFU)
 *     .build();
 *
 * // FIFO - Sequential processing
 * SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
 *     .maxSize(1000)
 *     .evictionPolicy(EvictionPolicy.FIFO)
 *     .build();
 *
 * // RANDOM - Simple, no specific pattern
 * SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
 *     .maxSize(1000)
 *     .evictionPolicy(EvictionPolicy.RANDOM)
 *     .build();
 *
 * // TTL - Time-sensitive data
 * SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
 *     .maxSize(1000)
 *     .evictionPolicy(EvictionPolicy.TTL)
 *     .build();
 * }</pre>
 *
 * @author archmagece
 * @since 2025-01
 */
public enum EvictionPolicy {
	/**
	 * Least Recently Used (LRU) - Default policy.
	 * <p>
	 * Evicts the entry that hasn't been accessed for the longest time.
	 * Best for temporal locality patterns where recent access predicts future access.
	 * </p>
	 *
	 * <h4>Characteristics:</h4>
	 * <ul>
	 *   <li>Tracks access time</li>
	 *   <li>O(1) eviction using LinkedHashMap</li>
	 *   <li>Good hit rate for temporal locality</li>
	 * </ul>
	 *
	 * <h4>Example:</h4>
	 * <pre>
	 * Cache access pattern: A, B, C, A, D (maxSize=3)
	 * State: [A, B, C] → [A, B, C] (C accessed) → [A, C, D] (B evicted)
	 * </pre>
	 */
	LRU,

	/**
	 * Least Frequently Used (LFU).
	 * <p>
	 * Evicts the entry with the lowest access count.
	 * Best for identifying and retaining hot keys.
	 * </p>
	 *
	 * <h4>Characteristics:</h4>
	 * <ul>
	 *   <li>Tracks access frequency</li>
	 *   <li>Retains frequently accessed keys</li>
	 *   <li>Good for workloads with stable hot keys</li>
	 * </ul>
	 *
	 * <h4>Example:</h4>
	 * <pre>
	 * Access counts: A=5, B=2, C=3 (maxSize=3, need to evict)
	 * → Evict B (lowest count)
	 * </pre>
	 */
	LFU,

	/**
	 * First In First Out (FIFO).
	 * <p>
	 * Evicts the oldest entry by insertion time.
	 * Access frequency and recency are ignored.
	 * </p>
	 *
	 * <h4>Characteristics:</h4>
	 * <ul>
	 *   <li>Simple insertion order</li>
	 *   <li>No access tracking</li>
	 *   <li>Predictable eviction order</li>
	 * </ul>
	 *
	 * <h4>Example:</h4>
	 * <pre>
	 * Insertion order: A, B, C (maxSize=3)
	 * → Insert D → Evict A (first in)
	 * </pre>
	 */
	FIFO,

	/**
	 * Random eviction.
	 * <p>
	 * Evicts a random entry.
	 * Simplest policy with minimal overhead.
	 * </p>
	 *
	 * <h4>Characteristics:</h4>
	 * <ul>
	 *   <li>No tracking needed</li>
	 *   <li>Very low overhead</li>
	 *   <li>Unpredictable but fair</li>
	 * </ul>
	 *
	 * <h4>Use Cases:</h4>
	 * <ul>
	 *   <li>No clear access pattern</li>
	 *   <li>Minimal memory overhead required</li>
	 *   <li>Simple cache implementation</li>
	 * </ul>
	 */
	RANDOM,

	/**
	 * Time To Live (TTL) based eviction.
	 * <p>
	 * Evicts the oldest entry by creation time (not access time).
	 * Useful for time-sensitive data where age matters more than usage.
	 * </p>
	 *
	 * <h4>Characteristics:</h4>
	 * <ul>
	 *   <li>Based on creation timestamp</li>
	 *   <li>Oldest data evicted first</li>
	 *   <li>Complementary to TTL expiration</li>
	 * </ul>
	 *
	 * <h4>Example:</h4>
	 * <pre>
	 * Creation times: A=10:00, B=10:05, C=10:10 (maxSize=3)
	 * → Insert D at 10:15 → Evict A (oldest by creation time)
	 * </pre>
	 *
	 * <h4>Difference from FIFO:</h4>
	 * <p>
	 * TTL uses actual creation timestamp for more precise age-based eviction,
	 * while FIFO uses simple insertion order.
	 * </p>
	 */
	TTL
}
