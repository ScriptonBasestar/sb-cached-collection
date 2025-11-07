package org.scriptonbasestar.cache.loader.jdbc;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scriptonbasestar.cache.collection.list.SBCacheList;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.*;

/**
 * JdbcListLoader 테스트
 *
 * @author archmagece
 * @since 2025-01
 */
public class JdbcListLoaderTest {

	private DataSource dataSource;
	private JdbcListLoader<User> loader;

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

			stmt.execute("DROP TABLE IF EXISTS users");
			stmt.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(255), email VARCHAR(255))");
			stmt.execute("INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@example.com')");
			stmt.execute("INSERT INTO users (id, name, email) VALUES (2, 'Bob', 'bob@example.com')");
			stmt.execute("INSERT INTO users (id, name, email) VALUES (3, 'Charlie', 'charlie@example.com')");
		}

		// 로더 생성
		loader = new JdbcListLoader<>(
			dataSource,
			"SELECT id, name, email FROM users ORDER BY id",
			rs -> new User(
				rs.getLong("id"),
				rs.getString("name"),
				rs.getString("email")
			)
		);
	}

	@After
	public void tearDown() throws SQLException {
		// 테이블 정리
		try (Connection conn = dataSource.getConnection();
			 Statement stmt = conn.createStatement()) {
			stmt.execute("DROP TABLE IF EXISTS users");
		}
	}

	@Test
	public void testLoadAll() throws SBCacheLoadFailException {
		// When
		List<User> users = loader.loadAll();

		// Then
		assertNotNull(users);
		assertEquals(3, users.size());

		assertEquals(Long.valueOf(1L), users.get(0).getId());
		assertEquals("Alice", users.get(0).getName());
		assertEquals("alice@example.com", users.get(0).getEmail());

		assertEquals(Long.valueOf(2L), users.get(1).getId());
		assertEquals("Bob", users.get(1).getName());

		assertEquals(Long.valueOf(3L), users.get(2).getId());
		assertEquals("Charlie", users.get(2).getName());
	}

	@Test
	public void testEmptyResult() throws SQLException, SBCacheLoadFailException {
		// Given - 모든 데이터 삭제
		try (Connection conn = dataSource.getConnection();
			 Statement stmt = conn.createStatement()) {
			stmt.execute("DELETE FROM users");
		}

		// When
		List<User> users = loader.loadAll();

		// Then
		assertNotNull(users);
		assertTrue(users.isEmpty());
	}

	@Test
	public void testWithSBCacheList() {
		// Given
		SBCacheList<User> cache = SBCacheList.<User>builder()
			.loader(loader)
			.timeoutSec(60)
			.build();

		// When
		List<User> users = cache.getList();

		// Then
		assertNotNull(users);
		assertEquals(3, users.size());
		assertEquals("Alice", users.get(0).getName());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullDataSource() {
		new JdbcListLoader<>(
			null,
			"SELECT id, name FROM users",
			rs -> new User(rs.getLong("id"), rs.getString("name"), null)
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullSelectQuery() {
		new JdbcListLoader<>(
			dataSource,
			null,
			rs -> new User(rs.getLong("id"), rs.getString("name"), null)
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testEmptySelectQuery() {
		new JdbcListLoader<>(
			dataSource,
			"",
			rs -> new User(rs.getLong("id"), rs.getString("name"), null)
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullRowMapper() {
		new JdbcListLoader<User>(
			dataSource,
			"SELECT id, name FROM users",
			null
		);
	}

	// ===== Test DTO =====

	public static class User {
		private final Long id;
		private final String name;
		private final String email;

		public User(Long id, String name, String email) {
			this.id = id;
			this.name = name;
			this.email = email;
		}

		public Long getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public String getEmail() {
			return email;
		}
	}
}
