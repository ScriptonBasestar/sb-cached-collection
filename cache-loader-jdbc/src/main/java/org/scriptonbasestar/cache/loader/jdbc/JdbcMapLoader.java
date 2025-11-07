package org.scriptonbasestar.cache.loader.jdbc;

import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;
import org.scriptonbasestar.cache.core.loader.SBCacheMapLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * JDBC 기반 Map 로더
 * SQL 쿼리를 통해 데이터베이스에서 키-값 쌍을 로드합니다.
 *
 * <p>사용 예시:</p>
 * <pre>{@code
 * DataSource dataSource = ...; // JDBC DataSource
 *
 * // 단일 키 조회용 쿼리
 * String selectQuery = "SELECT value FROM cache_table WHERE key = ?";
 *
 * // 전체 데이터 조회용 쿼리
 * String selectAllQuery = "SELECT key, value FROM cache_table";
 *
 * JdbcMapLoader<String, String> loader = new JdbcMapLoader<>(
 *     dataSource,
 *     selectQuery,
 *     selectAllQuery,
 *     (rs) -> rs.getString("key"),      // 키 매퍼
 *     (rs) -> rs.getString("value")     // 값 매퍼
 * );
 *
 * SBCacheMap<String, String> cache = new SBCacheMap<>(loader, 60);
 * }</pre>
 *
 * @param <K> 키 타입
 * @param <V> 값 타입
 * @author archmagece
 * @since 2025-01
 */
public class JdbcMapLoader<K, V> implements SBCacheMapLoader<K, V> {

	private static final Logger log = LoggerFactory.getLogger(JdbcMapLoader.class);

	private final DataSource dataSource;
	private final String selectQuery;
	private final String selectAllQuery;
	private final RowMapper<K> keyMapper;
	private final RowMapper<V> valueMapper;

	/**
	 * JDBC Map 로더 생성자
	 *
	 * @param dataSource JDBC DataSource
	 * @param selectQuery 단일 키 조회 쿼리 (예: "SELECT value FROM table WHERE key = ?")
	 * @param selectAllQuery 전체 데이터 조회 쿼리 (예: "SELECT key, value FROM table")
	 * @param keyMapper ResultSet에서 키를 추출하는 매퍼 (loadAll용)
	 * @param valueMapper ResultSet에서 값을 추출하는 매퍼
	 */
	public JdbcMapLoader(
		DataSource dataSource,
		String selectQuery,
		String selectAllQuery,
		RowMapper<K> keyMapper,
		RowMapper<V> valueMapper
	) {
		if (dataSource == null) {
			throw new IllegalArgumentException("DataSource must not be null");
		}
		if (selectQuery == null || selectQuery.trim().isEmpty()) {
			throw new IllegalArgumentException("selectQuery must not be null or empty");
		}
		if (selectAllQuery == null || selectAllQuery.trim().isEmpty()) {
			throw new IllegalArgumentException("selectAllQuery must not be null or empty");
		}
		if (keyMapper == null) {
			throw new IllegalArgumentException("keyMapper must not be null");
		}
		if (valueMapper == null) {
			throw new IllegalArgumentException("valueMapper must not be null");
		}

		this.dataSource = dataSource;
		this.selectQuery = selectQuery;
		this.selectAllQuery = selectAllQuery;
		this.keyMapper = keyMapper;
		this.valueMapper = valueMapper;
	}

	@Override
	public V loadOne(K key) throws SBCacheLoadFailException {
		log.debug("Loading value for key: {}", key);

		try (Connection conn = dataSource.getConnection();
			 PreparedStatement pstmt = conn.prepareStatement(selectQuery)) {

			pstmt.setObject(1, key);

			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					V value = valueMapper.map(rs);
					log.trace("Loaded value for key {}: {}", key, value);
					return value;
				} else {
					log.trace("No value found for key: {}", key);
					return null;
				}
			}

		} catch (SQLException e) {
			log.error("Failed to load value for key: {}", key, e);
			throw new SBCacheLoadFailException("JDBC load failed for key: " + key, e);
		}
	}

	@Override
	public Map<K, V> loadAll() throws SBCacheLoadFailException {
		log.debug("Loading all key-value pairs");

		Map<K, V> result = new HashMap<>();

		try (Connection conn = dataSource.getConnection();
			 PreparedStatement pstmt = conn.prepareStatement(selectAllQuery);
			 ResultSet rs = pstmt.executeQuery()) {

			while (rs.next()) {
				K key = keyMapper.map(rs);
				V value = valueMapper.map(rs);
				result.put(key, value);
			}

			log.debug("Loaded {} key-value pairs", result.size());
			return result;

		} catch (SQLException e) {
			log.error("Failed to load all key-value pairs", e);
			throw new SBCacheLoadFailException("JDBC loadAll failed", e);
		}
	}

	/**
	 * ResultSet에서 객체를 추출하는 함수형 인터페이스
	 *
	 * @param <T> 추출할 객체 타입
	 */
	@FunctionalInterface
	public interface RowMapper<T> {
		/**
		 * ResultSet의 현재 행에서 객체를 추출합니다.
		 *
		 * @param rs ResultSet
		 * @return 추출된 객체
		 * @throws SQLException SQL 예외
		 */
		T map(ResultSet rs) throws SQLException;
	}
}
