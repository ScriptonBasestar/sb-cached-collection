package org.scriptonbasestar.cache.core.loader;

import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;

import java.util.Collections;
import java.util.Map;

/**
 * @author archmagece
 * @with sb-tools-java
 * @since 2015-08-26 11
 */
public interface SBCacheMapLoader<K,V> {
	/**
	 * 단일 키에 대한 값을 로드합니다.
	 *
	 * @param key 로드할 키
	 * @return 로드된 값
	 * @throws SBCacheLoadFailException 로드 실패 시
	 */
	V loadOne(K key) throws SBCacheLoadFailException;

	/**
	 * 모든 키-값 쌍을 로드합니다.
	 * 기본 구현은 빈 맵을 반환하며, 필요시 오버라이드하여 사용합니다.
	 *
	 * @return 로드된 모든 키-값 맵
	 * @throws SBCacheLoadFailException 로드 실패 시
	 */
	default Map<K, V> loadAll() throws SBCacheLoadFailException {
		return Collections.emptyMap();
	}
}
