package org.scriptonbasestar.cache.collection.eviction;

import org.scriptonbasestar.cache.core.strategy.EvictionStrategy;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Least Frequently Used (LFU) eviction strategy.
 * <p>
 * Evicts the entry with the lowest access count.
 * Best for identifying and retaining hot keys.
 * </p>
 *
 * @param <K> the type of cache keys
 * @author archmagece
 * @since 2025-01
 */
public class LfuEvictionStrategy<K> implements EvictionStrategy<K> {

	private final ConcurrentHashMap<K, AtomicLong> accessCounts;

	public LfuEvictionStrategy() {
		this.accessCounts = new ConcurrentHashMap<>();
	}

	@Override
	public void onAccess(K key) {
		accessCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
	}

	@Override
	public void onInsert(K key) {
		accessCounts.put(key, new AtomicLong(1)); // Start with count 1
	}

	@Override
	public void onRemove(K key) {
		accessCounts.remove(key);
	}

	@Override
	public K selectEvictionCandidate() {
		if (accessCounts.isEmpty()) {
			return null;
		}

		// Find key with minimum access count
		return accessCounts.entrySet().stream()
			.min(Comparator.comparingLong(e -> e.getValue().get()))
			.map(Map.Entry::getKey)
			.orElse(null);
	}

	@Override
	public void clear() {
		accessCounts.clear();
	}
}
