package org.scriptonbasestar.cache.collection.eviction;

import org.scriptonbasestar.cache.core.strategy.EvictionStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Random eviction strategy.
 * <p>
 * Evicts a random entry.
 * Simplest policy with minimal overhead.
 * </p>
 *
 * @param <K> the type of cache keys
 * @author archmagece
 * @since 2025-01
 */
public class RandomEvictionStrategy<K> implements EvictionStrategy<K> {

	private final ConcurrentHashMap<K, Boolean> keys;
	private final Random random;

	public RandomEvictionStrategy() {
		this.keys = new ConcurrentHashMap<>();
		this.random = new Random();
	}

	@Override
	public void onAccess(K key) {
		// Random doesn't care about access - do nothing
	}

	@Override
	public void onInsert(K key) {
		keys.put(key, Boolean.TRUE);
	}

	@Override
	public void onRemove(K key) {
		keys.remove(key);
	}

	@Override
	public K selectEvictionCandidate() {
		if (keys.isEmpty()) {
			return null;
		}

		// Convert to list for random access
		List<K> keyList = new ArrayList<>(keys.keySet());
		int randomIndex = random.nextInt(keyList.size());
		return keyList.get(randomIndex);
	}

	@Override
	public void clear() {
		keys.clear();
	}
}
