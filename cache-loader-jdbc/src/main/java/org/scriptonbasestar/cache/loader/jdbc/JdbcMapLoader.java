package org.scriptonbasestar.cache.loader.jdbc;

import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;
import org.scriptonbasestar.cache.core.loader.SBCacheMapLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC-based implementation of SBCacheMapLoader using Spring JdbcTemplate.
 * <p>
 * This loader executes SQL queries to load cache data from a relational database.
 * It supports both single-key and bulk loading operations.
 * </p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Flexible SQL queries for loadOne() and loadAll()</li>
 *   <li>RowMapper-based object mapping</li>
 *   <li>Support for parameterized queries</li>
 *   <li>Bulk loading for better performance</li>
 * </ul>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * // Simple usage with RowMapper
 * JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
 *
 * JdbcMapLoader<Long, Product> loader = new JdbcMapLoader<>(
 *     jdbcTemplate,
 *     "SELECT * FROM products WHERE id = ?",
 *     "SELECT * FROM products WHERE active = true",
 *     (rs, rowNum) -> {
 *         Product p = new Product();
 *         p.setId(rs.getLong("id"));
 *         p.setName(rs.getString("name"));
 *         p.setPrice(rs.getBigDecimal("price"));
 *         return p;
 *     },
 *     Product::getId
 * );
 *
 * // Use with SBCacheMap
 * SBCacheMap<Long, Product> cache = SBCacheMap.<Long, Product>builder()
 *     .loader(loader)
 *     .timeoutSec(300)
 *     .build();
 *
 * // Single load
 * Product product = cache.get(1L);
 *
 * // Bulk load (calls loadAll())
 * cache.loadAll();
 * }</pre>
 *
 * <h3>Constructor Variants:</h3>
 * <pre>{@code
 * // 1. Full constructor (supports both loadOne and loadAll)
 * new JdbcMapLoader<>(jdbcTemplate, loadOneSql, loadAllSql, rowMapper, keyExtractor);
 *
 * // 2. LoadOne-only constructor (loadAll returns empty map)
 * new JdbcMapLoader<>(jdbcTemplate, loadOneSql, rowMapper);
 * }</pre>
 *
 * <h3>SQL Examples:</h3>
 * <pre>{@code
 * // Load single user by ID
 * String loadOneSql = "SELECT * FROM users WHERE user_id = ?";
 *
 * // Load all active users
 * String loadAllSql = "SELECT * FROM users WHERE active = true";
 *
 * // Load with JOIN
 * String loadOneSql = "SELECT u.*, p.name as profile_name " +
 *                     "FROM users u LEFT JOIN profiles p ON u.id = p.user_id " +
 *                     "WHERE u.id = ?";
 * }</pre>
 *
 * <h3>Thread Safety:</h3>
 * <p>
 * This class is thread-safe as it relies on Spring's JdbcTemplate,
 * which is designed for concurrent use.
 * </p>
 *
 * @param <K> the type of cache key
 * @param <V> the type of cache value
 * @author archmagece
 * @since 2025-01
 */
public class JdbcMapLoader<K, V> implements SBCacheMapLoader<K, V> {

	private final JdbcTemplate jdbcTemplate;
	private final String loadOneSql;
	private final String loadAllSql;
	private final RowMapper<V> rowMapper;
	private final KeyExtractor<K, V> keyExtractor;

	/**
	 * Functional interface for extracting the key from a value object.
	 *
	 * @param <K> key type
	 * @param <V> value type
	 */
	@FunctionalInterface
	public interface KeyExtractor<K, V> {
		/**
		 * Extract the key from the given value object.
		 *
		 * @param value the value object
		 * @return the extracted key
		 */
		K extractKey(V value);
	}

	/**
	 * Full constructor supporting both loadOne() and loadAll().
	 *
	 * @param jdbcTemplate  Spring JdbcTemplate instance
	 * @param loadOneSql    SQL query for loading a single entry (must accept one parameter)
	 * @param loadAllSql    SQL query for loading all entries (no parameters)
	 * @param rowMapper     RowMapper for converting ResultSet to value object
	 * @param keyExtractor  Function to extract key from value object (used in loadAll)
	 */
	public JdbcMapLoader(JdbcTemplate jdbcTemplate,
	                     String loadOneSql,
	                     String loadAllSql,
	                     RowMapper<V> rowMapper,
	                     KeyExtractor<K, V> keyExtractor) {
		if (jdbcTemplate == null) {
			throw new IllegalArgumentException("jdbcTemplate cannot be null");
		}
		if (loadOneSql == null || loadOneSql.trim().isEmpty()) {
			throw new IllegalArgumentException("loadOneSql cannot be null or empty");
		}
		if (rowMapper == null) {
			throw new IllegalArgumentException("rowMapper cannot be null");
		}

		this.jdbcTemplate = jdbcTemplate;
		this.loadOneSql = loadOneSql;
		this.loadAllSql = loadAllSql;
		this.rowMapper = rowMapper;
		this.keyExtractor = keyExtractor;
	}

	/**
	 * Simplified constructor for loadOne-only scenarios.
	 * <p>
	 * loadAll() will return an empty map when using this constructor.
	 * </p>
	 *
	 * @param jdbcTemplate Spring JdbcTemplate instance
	 * @param loadOneSql   SQL query for loading a single entry
	 * @param rowMapper    RowMapper for converting ResultSet to value object
	 */
	public JdbcMapLoader(JdbcTemplate jdbcTemplate,
	                     String loadOneSql,
	                     RowMapper<V> rowMapper) {
		this(jdbcTemplate, loadOneSql, null, rowMapper, null);
	}

	/**
	 * Load a single value by key using the configured loadOneSql query.
	 *
	 * @param key the key to load
	 * @return the loaded value
	 * @throws SBCacheLoadFailException if the query fails or returns no results
	 */
	@Override
	public V loadOne(K key) throws SBCacheLoadFailException {
		if (key == null) {
			throw new SBCacheLoadFailException("Key cannot be null");
		}

		try {
			List<V> results = jdbcTemplate.query(loadOneSql, rowMapper, key);

			if (results.isEmpty()) {
				throw new SBCacheLoadFailException("No result found for key: " + key);
			}

			return results.get(0);
		} catch (Exception e) {
			throw new SBCacheLoadFailException("Failed to load data for key: " + key, e);
		}
	}

	/**
	 * Load all entries using the configured loadAllSql query.
	 * <p>
	 * If loadAllSql or keyExtractor is not configured, returns an empty map.
	 * </p>
	 *
	 * @return a map of all loaded key-value pairs
	 * @throws SBCacheLoadFailException if the query fails
	 */
	@Override
	public Map<K, V> loadAll() throws SBCacheLoadFailException {
		if (loadAllSql == null || keyExtractor == null) {
			return Collections.emptyMap();
		}

		try {
			List<V> results = jdbcTemplate.query(loadAllSql, rowMapper);

			Map<K, V> resultMap = new HashMap<>();
			for (V value : results) {
				K key = keyExtractor.extractKey(value);
				resultMap.put(key, value);
			}

			return resultMap;
		} catch (Exception e) {
			throw new SBCacheLoadFailException("Failed to load all data", e);
		}
	}
}
