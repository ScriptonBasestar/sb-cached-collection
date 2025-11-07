package org.scriptonbasestar.cache.collection.strategy;

/**
 * 캐시 로딩 전략
 *
 * <ul>
 *   <li>SYNC: 동기 로딩 - 캐시 미스 시 즉시 로드하고 대기 (SBCacheMap 기본값)</li>
 *   <li>ASYNC: 비동기 로딩 - 캐시 미스 시 이전 데이터 반환 + 백그라운드 갱신 (SBAsyncCacheMap 동작)</li>
 *   <li>ONE: 개별 항목 로딩 - 특정 인덱스만 로드 (SBCacheList 전용)</li>
 *   <li>ALL: 전체 로딩 - 모든 데이터를 한 번에 로드 (SBCacheList 기본값)</li>
 * </ul>
 *
 * @author archmagece
 * @since 2016-11-14
 */
public enum LoadStrategy {
	/**
	 * 동기 로딩 (SBCacheMap 기본값)
	 * <p>캐시 미스 시 즉시 데이터를 로드하고 대기합니다. 로드가 완료될 때까지 블로킹됩니다.</p>
	 */
	SYNC,

	/**
	 * 비동기 로딩 (SBAsyncCacheMap 동작과 동일)
	 * <p>캐시 미스 시에도 만료된 이전 데이터를 즉시 반환하고, 백그라운드에서 새 데이터를 로드합니다.
	 * 응답 속도가 중요한 경우에 사용합니다.</p>
	 */
	ASYNC,

	/**
	 * 개별 항목 로딩 (SBCacheList 전용)
	 * <p>특정 인덱스의 항목만 로드합니다.</p>
	 */
	ONE,

	/**
	 * 전체 로딩 (SBCacheList 기본값)
	 * <p>모든 데이터를 한 번에 로드합니다.</p>
	 */
	ALL
}
