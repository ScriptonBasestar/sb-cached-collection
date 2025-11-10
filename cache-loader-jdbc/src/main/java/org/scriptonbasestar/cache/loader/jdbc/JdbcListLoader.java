package org.scriptonbasestar.cache.loader.jdbc;

import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;
import org.scriptonbasestar.cache.core.loader.SBCacheListLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Functional interface for mapping a ResultSet row to an object.
 *
 * @param <T> the type of the result object
 */
@FunctionalInterface
interface RowMapper<T> {
	/**
	 * Map a single row of ResultSet to an object.
	 *
	 * @param rs the ResultSet (cursor is already positioned at the row)
	 * @return the mapped object
	 * @throws SQLException if SQL error occurs
	 */
	T map(ResultSet rs) throws SQLException;
}

/**
 * JDBC 기반 List 로더
 * SQL 쿼리를 통해 데이터베이스에서 리스트 데이터를 로드합니다.
 *
 * <p>사용 예시:</p>
 * <pre>{@code
 * DataSource dataSource = ...; // JDBC DataSource
 *
 * String selectQuery = "SELECT id, name, email FROM users ORDER BY id";
 *
 * JdbcListLoader<User> loader = new JdbcListLoader<>(
 *     dataSource,
 *     selectQuery,
 *     (rs) -> new User(
 *         rs.getLong("id"),
 *         rs.getString("name"),
 *         rs.getString("email")
 *     )
 * );
 *
 * SBCacheList<User> cache = SBCacheList.<User>builder()
 *     .loader(loader)
 *     .timeoutSec(60)
 *     .build();
 * }</pre>
 *
 * @param <T> 리스트 항목 타입
 * @author archmagece
 * @since 2025-01
 */
public class JdbcListLoader<T> implements SBCacheListLoader<T> {

	private static final Logger log = LoggerFactory.getLogger(JdbcListLoader.class);

	private final DataSource dataSource;
	private final String selectQuery;
	private final RowMapper<T> rowMapper;

	/**
	 * JDBC List 로더 생성자
	 *
	 * @param dataSource JDBC DataSource
	 * @param selectQuery 리스트 조회 쿼리 (예: "SELECT id, name FROM table ORDER BY id")
	 * @param rowMapper ResultSet에서 객체를 추출하는 매퍼
	 */
	public JdbcListLoader(
		DataSource dataSource,
		String selectQuery,
		RowMapper<T> rowMapper
	) {
		if (dataSource == null) {
			throw new IllegalArgumentException("DataSource must not be null");
		}
		if (selectQuery == null || selectQuery.trim().isEmpty()) {
			throw new IllegalArgumentException("selectQuery must not be null or empty");
		}
		if (rowMapper == null) {
			throw new IllegalArgumentException("rowMapper must not be null");
		}

		this.dataSource = dataSource;
		this.selectQuery = selectQuery;
		this.rowMapper = rowMapper;
	}

	@Override
	public T loadOne(int index) throws SBCacheLoadFailException {
		throw new UnsupportedOperationException(
			"JdbcListLoader does not support loading by index. Use loadAll() instead."
		);
	}

	@Override
	public List<T> loadAll() throws SBCacheLoadFailException {
		log.debug("Loading list data from database");

		List<T> result = new ArrayList<>();

		try (Connection conn = dataSource.getConnection();
			 PreparedStatement pstmt = conn.prepareStatement(selectQuery);
			 ResultSet rs = pstmt.executeQuery()) {

			while (rs.next()) {
				T item = rowMapper.map(rs);
				result.add(item);
			}

			log.debug("Loaded {} items", result.size());
			return result;

		} catch (SQLException e) {
			log.error("Failed to load list data", e);
			throw new SBCacheLoadFailException("JDBC list load failed", e);
		}
	}
}
