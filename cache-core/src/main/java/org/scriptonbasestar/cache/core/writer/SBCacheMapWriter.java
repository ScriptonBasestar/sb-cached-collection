package org.scriptonbasestar.cache.core.writer;

import java.util.Map;

/**
 * Interface for writing cache entries to an underlying data source.
 * <p>
 * Used with {@code WriteStrategy.WRITE_THROUGH} and {@code WriteStrategy.WRITE_BEHIND}
 * to persist cache updates to a database, file system, or other storage.
 * </p>
 *
 * <h3>Implementation Examples:</h3>
 *
 * <h4>JPA/Hibernate Writer:</h4>
 * <pre>{@code
 * public class UserCacheWriter implements SBCacheMapWriter<Long, User> {
 *     private final UserRepository repository;
 *
 *     @Override
 *     public void write(Long key, User value) throws Exception {
 *         repository.save(value);
 *     }
 *
 *     @Override
 *     public void writeAll(Map<Long, User> entries) throws Exception {
 *         repository.saveAll(entries.values());
 *     }
 *
 *     @Override
 *     public void delete(Long key) throws Exception {
 *         repository.deleteById(key);
 *     }
 *
 *     @Override
 *     public void deleteAll(Iterable<Long> keys) throws Exception {
 *         repository.deleteAllById(keys);
 *     }
 * }
 * }</pre>
 *
 * <h4>JDBC Writer:</h4>
 * <pre>{@code
 * public class ProductCacheWriter implements SBCacheMapWriter<Long, Product> {
 *     private final JdbcTemplate jdbcTemplate;
 *
 *     @Override
 *     public void write(Long key, Product value) throws Exception {
 *         jdbcTemplate.update(
 *             "INSERT INTO products (id, name, price) VALUES (?, ?, ?) " +
 *             "ON DUPLICATE KEY UPDATE name = ?, price = ?",
 *             key, value.getName(), value.getPrice(),
 *             value.getName(), value.getPrice()
 *         );
 *     }
 *
 *     @Override
 *     public void delete(Long key) throws Exception {
 *         jdbcTemplate.update("DELETE FROM products WHERE id = ?", key);
 *     }
 * }
 * }</pre>
 *
 * <h4>Redis Writer:</h4>
 * <pre>{@code
 * public class RedisCacheWriter<K, V> implements SBCacheMapWriter<K, V> {
 *     private final RedisTemplate<K, V> redisTemplate;
 *
 *     @Override
 *     public void write(K key, V value) throws Exception {
 *         redisTemplate.opsForValue().set(key, value);
 *     }
 *
 *     @Override
 *     public void writeAll(Map<K, V> entries) throws Exception {
 *         redisTemplate.opsForValue().multiSet(entries);
 *     }
 *
 *     @Override
 *     public void delete(K key) throws Exception {
 *         redisTemplate.delete(key);
 *     }
 * }
 * }</pre>
 *
 * <h3>Error Handling:</h3>
 * <p>
 * Implementations should throw exceptions on write failures.
 * The cache will handle errors based on the WriteStrategy:
 * </p>
 * <ul>
 *   <li>WRITE_THROUGH: Exception propagated to caller immediately</li>
 *   <li>WRITE_BEHIND: Logged and optionally retried</li>
 * </ul>
 *
 * <h3>Thread Safety:</h3>
 * <p>
 * Implementations must be thread-safe as they may be called concurrently
 * from multiple threads, especially with WRITE_BEHIND strategy.
 * </p>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 * @author archmagece
 * @since 2025-01
 */
public interface SBCacheMapWriter<K, V> {

	/**
	 * Write a single entry to the data source.
	 * <p>
	 * Called for each put() operation in WRITE_THROUGH mode,
	 * or in batches for WRITE_BEHIND mode.
	 * </p>
	 *
	 * @param key the entry key
	 * @param value the entry value
	 * @throws Exception if write fails (will be handled by cache)
	 */
	void write(K key, V value) throws Exception;

	/**
	 * Write multiple entries to the data source in a single operation.
	 * <p>
	 * Optional optimization for batch writes. Default implementation
	 * calls write() for each entry individually.
	 * </p>
	 * <p>
	 * Particularly useful for WRITE_BEHIND strategy to reduce
	 * database round trips.
	 * </p>
	 *
	 * @param entries map of entries to write
	 * @throws Exception if any write fails
	 */
	default void writeAll(Map<? extends K, ? extends V> entries) throws Exception {
		for (Map.Entry<? extends K, ? extends V> entry : entries.entrySet()) {
			write(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * Delete a single entry from the data source.
	 * <p>
	 * Called when remove() is invoked on the cache.
	 * </p>
	 *
	 * @param key the key to delete
	 * @throws Exception if delete fails
	 */
	void delete(K key) throws Exception;

	/**
	 * Delete multiple entries from the data source in a single operation.
	 * <p>
	 * Optional optimization for batch deletes. Default implementation
	 * calls delete() for each key individually.
	 * </p>
	 *
	 * @param keys keys to delete
	 * @throws Exception if any delete fails
	 */
	default void deleteAll(Iterable<? extends K> keys) throws Exception {
		for (K key : keys) {
			delete(key);
		}
	}

	/**
	 * Optional: Called before cache shutdown to flush any pending writes.
	 * <p>
	 * Particularly important for WRITE_BEHIND strategy to ensure
	 * all queued writes are persisted.
	 * </p>
	 * <p>
	 * Default implementation does nothing.
	 * </p>
	 *
	 * @throws Exception if flush fails
	 */
	default void flush() throws Exception {
		// Default: no-op
	}
}
