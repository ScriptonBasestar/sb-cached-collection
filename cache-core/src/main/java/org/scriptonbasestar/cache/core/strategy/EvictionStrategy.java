package org.scriptonbasestar.cache.core.strategy;

/**
 * Strategy interface for cache eviction decisions.
 * <p>
 * Implementations determine which key to evict when cache exceeds maxSize.
 * </p>
 *
 * @param <K> the type of cache keys
 * @author archmagece
 * @since 2025-01
 */
public interface EvictionStrategy<K> {

	/**
	 * Called when a key is accessed (get operation).
	 * <p>
	 * Implementations can track access patterns (e.g., LRU updates access time, LFU increments counter).
	 * </p>
	 *
	 * @param key the key that was accessed
	 */
	void onAccess(K key);

	/**
	 * Called when a new entry is inserted.
	 * <p>
	 * Implementations can initialize tracking data (e.g., creation time, insertion order).
	 * </p>
	 *
	 * @param key the key that was inserted
	 */
	void onInsert(K key);

	/**
	 * Called when an entry is removed (explicitly or evicted).
	 * <p>
	 * Implementations should clean up tracking data.
	 * </p>
	 *
	 * @param key the key that was removed
	 */
	void onRemove(K key);

	/**
	 * Select a key to evict when cache exceeds maxSize.
	 * <p>
	 * This method is called when eviction is necessary.
	 * </p>
	 *
	 * @return the key to evict, or null if no suitable candidate
	 */
	K selectEvictionCandidate();

	/**
	 * Clear all tracking data.
	 * <p>
	 * Called when cache is cleared.
	 * </p>
	 */
	void clear();
}
