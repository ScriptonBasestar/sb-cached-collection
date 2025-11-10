package org.scriptonbasestar.cache.collection.eviction;

import org.scriptonbasestar.cache.core.strategy.EvictionStrategy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Least Recently Used (LRU) eviction strategy.
 * <p>
 * Evicts the entry that hasn't been accessed for the longest time.
 * Uses LinkedHashMap with access-order mode for O(1) operations.
 * </p>
 *
 * @param <K> the type of cache keys
 * @author archmagece
 * @since 2025-01
 */
public class LruEvictionStrategy<K> implements EvictionStrategy<K> {

	private final LinkedHashMap<K, Long> accessOrder;

	public LruEvictionStrategy() {
		// accessOrder=true for LRU behavior
		this.accessOrder = new LinkedHashMap<K, Long>(16, 0.75f, true) {
			// No removal in this map, just for ordering
		};
	}

	@Override
	public void onAccess(K key) {
		// Update access order by touching the entry
		accessOrder.put(key, System.currentTimeMillis());
	}

	@Override
	public void onInsert(K key) {
		accessOrder.put(key, System.currentTimeMillis());
	}

	@Override
	public void onRemove(K key) {
		accessOrder.remove(key);
	}

	@Override
	public K selectEvictionCandidate() {
		// LinkedHashMap with accessOrder=true maintains LRU order
		// The first entry is the least recently used
		if (accessOrder.isEmpty()) {
			return null;
		}
		return accessOrder.keySet().iterator().next();
	}

	@Override
	public void clear() {
		accessOrder.clear();
	}
}
