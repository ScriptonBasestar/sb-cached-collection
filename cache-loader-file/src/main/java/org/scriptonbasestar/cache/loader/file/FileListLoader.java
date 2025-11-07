package org.scriptonbasestar.cache.loader.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;
import org.scriptonbasestar.cache.core.loader.SBCacheListLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 파일 기반 List 로더
 * JSON 파일에서 리스트 데이터를 로드합니다.
 *
 * <p>사용 예시:</p>
 * <pre>{@code
 * // users.json 파일 내용:
 * // [
 * //   {"id": 1, "name": "Alice", "email": "alice@example.com"},
 * //   {"id": 2, "name": "Bob", "email": "bob@example.com"}
 * // ]
 *
 * FileListLoader<User> loader = new FileListLoader<>(
 *     new File("users.json"),
 *     new TypeReference<List<User>>() {}
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
public class FileListLoader<T> implements SBCacheListLoader<T> {

	private static final Logger log = LoggerFactory.getLogger(FileListLoader.class);

	private final File file;
	private final TypeReference<List<T>> typeReference;
	private final ObjectMapper objectMapper;

	/**
	 * FileListLoader 생성자
	 *
	 * @param file JSON 파일
	 * @param typeReference List 타입 정보
	 */
	public FileListLoader(File file, TypeReference<List<T>> typeReference) {
		this(file, typeReference, new ObjectMapper());
	}

	/**
	 * FileListLoader 생성자 (커스텀 ObjectMapper)
	 *
	 * @param file JSON 파일
	 * @param typeReference List 타입 정보
	 * @param objectMapper Jackson ObjectMapper
	 */
	public FileListLoader(File file, TypeReference<List<T>> typeReference, ObjectMapper objectMapper) {
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
	public T loadOne(int index) throws SBCacheLoadFailException {
		log.debug("Loading item at index: {}", index);

		List<T> allData = loadAll();
		if (index < 0 || index >= allData.size()) {
			return null;
		}
		return allData.get(index);
	}

	@Override
	public List<T> loadAll() throws SBCacheLoadFailException {
		log.debug("Loading all data from file: {}", file.getAbsolutePath());

		if (!file.exists()) {
			log.warn("File does not exist: {}", file.getAbsolutePath());
			return new ArrayList<>();
		}

		try {
			List<T> data = objectMapper.readValue(file, typeReference);
			log.debug("Loaded {} items from file", data.size());
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
