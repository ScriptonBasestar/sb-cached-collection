package org.scriptonbasestar.cache.loader.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;
import org.scriptonbasestar.cache.core.loader.SBCacheMapLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 파일 기반 Map 로더
 * JSON 파일에서 key-value 쌍을 로드합니다.
 *
 * <p>사용 예시:</p>
 * <pre>{@code
 * // data.json 파일 내용:
 * // {
 * //   "user1": {"name": "Alice", "age": 30},
 * //   "user2": {"name": "Bob", "age": 25}
 * // }
 *
 * FileMapLoader<String, User> loader = new FileMapLoader<>(
 *     new File("data.json"),
 *     new TypeReference<Map<String, User>>() {}
 * );
 *
 * SBCacheMap<String, User> cache = new SBCacheMap<>(loader, 60);
 * User user = cache.get("user1");
 * }</pre>
 *
 * @param <K> 키 타입
 * @param <V> 값 타입
 * @author archmagece
 * @since 2025-01
 */
public class FileMapLoader<K, V> implements SBCacheMapLoader<K, V> {

	private static final Logger log = LoggerFactory.getLogger(FileMapLoader.class);

	private final File file;
	private final TypeReference<Map<K, V>> typeReference;
	private final ObjectMapper objectMapper;

	/**
	 * FileMapLoader 생성자
	 *
	 * @param file JSON 파일
	 * @param typeReference Map 타입 정보
	 */
	public FileMapLoader(File file, TypeReference<Map<K, V>> typeReference) {
		this(file, typeReference, new ObjectMapper());
	}

	/**
	 * FileMapLoader 생성자 (커스텀 ObjectMapper)
	 *
	 * @param file JSON 파일
	 * @param typeReference Map 타입 정보
	 * @param objectMapper Jackson ObjectMapper
	 */
	public FileMapLoader(File file, TypeReference<Map<K, V>> typeReference, ObjectMapper objectMapper) {
		if (file == null) {
			throw new IllegalArgumentException("File must not be null");
		}
		if (typeReference == null) {
			throw new IllegalArgumentException("TypeReference must not be null");
		}
		if (objectMapper == null) {
			throw new IllegalArgumentException("ObjectMapper must not be null");
		}

		this.file = file;
		this.typeReference = typeReference;
		this.objectMapper = objectMapper;
	}

	@Override
	public V loadOne(K key) throws SBCacheLoadFailException {
		log.debug("Loading value for key: {}", key);

		Map<K, V> allData = loadAll();
		return allData.get(key);
	}

	@Override
	public Map<K, V> loadAll() throws SBCacheLoadFailException {
		log.debug("Loading all data from file: {}", file.getAbsolutePath());

		if (!file.exists()) {
			log.warn("File does not exist: {}", file.getAbsolutePath());
			return new HashMap<>();
		}

		try {
			Map<K, V> data = objectMapper.readValue(file, typeReference);
			log.debug("Loaded {} entries from file", data.size());
			return data;
		} catch (IOException e) {
			log.error("Failed to load data from file: {}", file.getAbsolutePath(), e);
			throw new SBCacheLoadFailException("Failed to load from file: " + file.getAbsolutePath(), e);
		}
	}

	/**
	 * 파일이 존재하는지 확인
	 */
	public boolean fileExists() {
		return file.exists();
	}

	/**
	 * 파일의 마지막 수정 시간
	 */
	public long getLastModified() {
		return file.lastModified();
	}
}
