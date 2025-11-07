package org.scriptonbasestar.cache.loader.jdbc;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scriptonbasestar.cache.collection.map.SBCacheMap;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * JdbcMapLoader 테스트
 *
 * @author archmagece
 * @since 2025-01
 */
public class JdbcMapLoaderTest {

	private DataSource dataSource;
	private JdbcMapLoader<String, String> loader;

	@Before
	public void setUp() throws SQLException {
		// H2 in-memory database 설정
		JdbcDataSource ds = new JdbcDataSource();
		ds.setURL("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
		ds.setUser("sa");
		ds.setPassword("");
		this.dataSource = ds;

		// 테이블 생성 및 테스트 데이터 삽입
		try (Connection conn = dataSource.getConnection();
			 Statement stmt = conn.createStatement()) {

			stmt.execute("DROP TABLE IF EXISTS cache_data");
			stmt.execute("CREATE TABLE cache_data (cache_key VARCHAR(255) PRIMARY KEY, cache_value VARCHAR(255))");
			stmt.execute("INSERT INTO cache_data (cache_key, cache_value) VALUES ('key1', 'value1')");
			stmt.execute("INSERT INTO cache_data (cache_key, cache_value) VALUES ('key2', 'value2')");
			stmt.execute("INSERT INTO cache_data (cache_key, cache_value) VALUES ('key3', 'value3')");
		}

		// 로더 생성
		loader = new JdbcMapLoader<>(
			dataSource,
			"SELECT cache_value FROM cache_data WHERE cache_key = ?",
			"SELECT cache_key, cache_value FROM cache_data",
			rs -> rs.getString("cache_key"),
			rs -> rs.getString("cache_value")
		);
	}

	@After
	public void tearDown() throws SQLException {
		// 테이블 정리
		try (Connection conn = dataSource.getConnection();
			 Statement stmt = conn.createStatement()) {
			stmt.execute("DROP TABLE IF EXISTS cache_data");
		}
	}

	@Test
	public void testLoadOne() throws SBCacheLoadFailException {
		// Given
		String key = "key1";

		// When
		String value = loader.loadOne(key);

		// Then
		assertNotNull(value);
		assertEquals("value1", value);
	}

	@Test
	public void testLoadOneNonExistentKey() throws SBCacheLoadFailException {
		// Given
		String key = "nonexistent";

		// When
		String value = loader.loadOne(key);

		// Then
		assertNull("Non-existent key should return null", value);
	}

	@Test
	public void testLoadAll() throws SBCacheLoadFailException {
		// When
		Map<String, String> allData = loader.loadAll();

		// Then
		assertNotNull(allData);
		assertEquals(3, allData.size());
		assertEquals("value1", allData.get("key1"));
		assertEquals("value2", allData.get("key2"));
		assertEquals("value3", allData.get("key3"));
	}

	@Test
	public void testWithSBCacheMap() {
		// Given
		SBCacheMap<String, String> cache = new SBCacheMap<>(loader, 60);

		// When
		String value1 = cache.get("key1");
		String value2 = cache.get("key2");

		// Then
		assertNotNull(value1);
		assertNotNull(value2);
		assertEquals("value1", value1);
		assertEquals("value2", value2);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullDataSource() {
		new JdbcMapLoader<>(
			null,
			"SELECT value FROM table WHERE key = ?",
			"SELECT key, value FROM table",
			rs -> rs.getString("key"),
			rs -> rs.getString("value")
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullSelectQuery() {
		new JdbcMapLoader<>(
			dataSource,
			null,
			"SELECT key, value FROM table",
			rs -> rs.getString("key"),
			rs -> rs.getString("value")
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testEmptySelectQuery() {
		new JdbcMapLoader<>(
			dataSource,
			"",
			"SELECT key, value FROM table",
			rs -> rs.getString("key"),
			rs -> rs.getString("value")
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullKeyMapper() {
		new JdbcMapLoader<>(
			dataSource,
			"SELECT value FROM table WHERE key = ?",
			"SELECT key, value FROM table",
			null,
			rs -> rs.getString("value")
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullValueMapper() {
		new JdbcMapLoader<>(
			dataSource,
			"SELECT value FROM table WHERE key = ?",
			"SELECT key, value FROM table",
			rs -> rs.getString("key"),
			null
		);
	}
}
