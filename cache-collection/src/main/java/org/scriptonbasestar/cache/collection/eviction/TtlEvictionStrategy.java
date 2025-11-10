package org.scriptonbasestar.cache.collection.eviction;

import org.scriptonbasestar.cache.core.strategy.EvictionStrategy;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Time To Live (TTL) based eviction strategy.
 * <p>
 * Evicts the oldest entry by creation time (not access time).
 * Useful for time-sensitive data where age matters more than usage.
 * </p>
 *
 * @param <K> the type of cache keys
 * @author archmagece
 * @since 2025-01
 */
public class TtlEvictionStrategy<K> implements EvictionStrategy<K> {

	private final ConcurrentHashMap<K, Long> creationTimes;

	public TtlEvictionStrategy() {
		this.creationTimes = new ConcurrentHashMap<>();
	}

	@Override
	public void onAccess(K key) {
		// TTL doesn't care about access - do nothing
	}

	@Override
	public void onInsert(K key) {
		creationTimes.put(key, System.currentTimeMillis());
	}

	@Override
	public void onRemove(K key) {
		creationTimes.remove(key);
	}

	@Override
	public K selectEvictionCandidate() {
		if (creationTimes.isEmpty()) {
			return null;
		}

		// Find key with earliest creation time
		return creationTimes.entrySet().stream()
			.min(Comparator.comparingLong(Map.Entry::getValue))
			.map(Map.Entry::getKey)
			.orElse(null);
	}

	@Override
	public void clear() {
		creationTimes.clear();
	}
}
