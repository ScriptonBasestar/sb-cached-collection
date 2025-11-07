package org.scriptonbasestar.cache.loader.file;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scriptonbasestar.cache.collection.list.SBCacheList;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/**
 * FileListLoader 테스트
 *
 * @author archmagece
 * @since 2025-01
 */
public class FileListLoaderTest {

	private File testFile;
	private FileListLoader<User> loader;

	@Before
	public void setUp() throws IOException {
		// 임시 테스트 파일 생성
		testFile = File.createTempFile("cache-test-", ".json");

		// JSON 데이터 작성
		try (FileWriter writer = new FileWriter(testFile)) {
			writer.write("[\n");
			writer.write("  {\"id\": 1, \"name\": \"Alice\", \"email\": \"alice@example.com\"},\n");
			writer.write("  {\"id\": 2, \"name\": \"Bob\", \"email\": \"bob@example.com\"},\n");
			writer.write("  {\"id\": 3, \"name\": \"Charlie\", \"email\": \"charlie@example.com\"}\n");
			writer.write("]");
		}

		// 로더 생성
		loader = new FileListLoader<>(
			testFile,
			new TypeReference<List<User>>() {}
		);
	}

	@After
	public void tearDown() {
		// 테스트 파일 삭제
		if (testFile != null && testFile.exists()) {
			testFile.delete();
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
	public void testLoadOne() throws SBCacheLoadFailException {
		// When
		User user = loader.loadOne(0);

		// Then
		assertNotNull(user);
		assertEquals(Long.valueOf(1L), user.getId());
		assertEquals("Alice", user.getName());
	}

	@Test
	public void testLoadOneInvalidIndex() throws SBCacheLoadFailException {
		// When
		User user1 = loader.loadOne(-1);
		User user2 = loader.loadOne(999);

		// Then
		assertNull("Invalid index should return null", user1);
		assertNull("Invalid index should return null", user2);
	}

	@Test
	public void testFileNotExists() throws SBCacheLoadFailException {
		// Given
		File nonExistentFile = new File("non-existent-file.json");
		FileListLoader<User> loader = new FileListLoader<>(
			nonExistentFile,
			new TypeReference<List<User>>() {}
		);

		// When
		List<User> data = loader.loadAll();

		// Then
		assertNotNull(data);
		assertTrue(data.isEmpty());
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
	public void testNullFile() {
		new FileListLoader<>(
			null,
			new TypeReference<List<User>>() {}
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullTypeReference() {
		new FileListLoader<User>(
			testFile,
			null
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullObjectMapper() {
		new FileListLoader<>(
			testFile,
			new TypeReference<List<User>>() {},
			null
		);
	}

	@Test
	public void testFileExists() {
		// Then
		assertTrue(loader.fileExists());
	}

	@Test
	public void testGetLastModified() {
		// When
		long lastModified = loader.getLastModified();

		// Then
		assertTrue(lastModified > 0);
	}

	@Test(expected = SBCacheLoadFailException.class)
	public void testInvalidJson() throws IOException, SBCacheLoadFailException {
		// Given - 잘못된 JSON 작성
		try (FileWriter writer = new FileWriter(testFile)) {
			writer.write("[ invalid json ]");
		}

		// When - 로드 시도 (예외 발생 예상)
		loader.loadAll();
	}

	// ===== Test DTO =====

	public static class User {
		private Long id;
		private String name;
		private String email;

		public User() {
		}

		public User(Long id, String name, String email) {
			this.id = id;
			this.name = name;
			this.email = email;
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getEmail() {
			return email;
		}

		public void setEmail(String email) {
			this.email = email;
		}
	}
}
