package org.scriptonbasestar.cache.loader.file;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scriptonbasestar.cache.collection.map.SBCacheMap;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * FileMapLoader 테스트
 *
 * @author archmagece
 * @since 2025-01
 */
public class FileMapLoaderTest {

	private File testFile;
	private FileMapLoader<String, String> loader;

	@Before
	public void setUp() throws IOException {
		// 임시 테스트 파일 생성
		testFile = File.createTempFile("cache-test-", ".json");

		// JSON 데이터 작성
		try (FileWriter writer = new FileWriter(testFile)) {
			writer.write("{\n");
			writer.write("  \"key1\": \"value1\",\n");
			writer.write("  \"key2\": \"value2\",\n");
			writer.write("  \"key3\": \"value3\"\n");
			writer.write("}");
		}

		// 로더 생성
		loader = new FileMapLoader<>(
			testFile,
			new TypeReference<Map<String, String>>() {}
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
	public void testFileNotExists() throws SBCacheLoadFailException {
		// Given
		File nonExistentFile = new File("non-existent-file.json");
		FileMapLoader<String, String> loader = new FileMapLoader<>(
			nonExistentFile,
			new TypeReference<Map<String, String>>() {}
		);

		// When
		Map<String, String> data = loader.loadAll();

		// Then
		assertNotNull(data);
		assertTrue(data.isEmpty());
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
	public void testNullFile() {
		new FileMapLoader<>(
			null,
			new TypeReference<Map<String, String>>() {}
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullTypeReference() {
		new FileMapLoader<String, String>(
			testFile,
			null
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullObjectMapper() {
		new FileMapLoader<>(
			testFile,
			new TypeReference<Map<String, String>>() {},
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
			writer.write("{ invalid json }");
		}

		// When - 로드 시도 (예외 발생 예상)
		loader.loadAll();
	}
}
