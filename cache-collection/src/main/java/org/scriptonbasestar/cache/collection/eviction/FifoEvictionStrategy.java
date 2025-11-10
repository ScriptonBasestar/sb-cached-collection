package org.scriptonbasestar.cache.collection.eviction;

import org.scriptonbasestar.cache.core.strategy.EvictionStrategy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * First In First Out (FIFO) eviction strategy.
 * <p>
 * Evicts the oldest entry by insertion time.
 * Access frequency and recency are ignored.
 * </p>
 *
 * @param <K> the type of cache keys
 * @author archmagece
 * @since 2025-01
 */
public class FifoEvictionStrategy<K> implements EvictionStrategy<K> {

	private final LinkedHashMap<K, Long> insertionOrder;

	public FifoEvictionStrategy() {
		// accessOrder=false for insertion-order behavior
		this.insertionOrder = new LinkedHashMap<K, Long>(16, 0.75f, false);
	}

	@Override
	public void onAccess(K key) {
		// FIFO doesn't care about access - do nothing
	}

	@Override
	public void onInsert(K key) {
		insertionOrder.put(key, System.currentTimeMillis());
	}

	@Override
	public void onRemove(K key) {
		insertionOrder.remove(key);
	}

	@Override
	public K selectEvictionCandidate() {
		// LinkedHashMap with accessOrder=false maintains insertion order
		// The first entry is the oldest by insertion
		if (insertionOrder.isEmpty()) {
			return null;
		}
		return insertionOrder.keySet().iterator().next();
	}

	@Override
	public void clear() {
		insertionOrder.clear();
	}
}
