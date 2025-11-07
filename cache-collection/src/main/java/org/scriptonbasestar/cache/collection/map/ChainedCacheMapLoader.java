package org.scriptonbasestar.cache.collection.map;

import lombok.extern.slf4j.Slf4j;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;

import java.util.Map;

/**
 * 다단계 캐싱을 지원하는 체이닝 로더
 *
 * 사용 예시:
 * <pre>
 * // L2: Redis 캐시 (1시간)
 * RedisSerializedMapLoader<Long, User> redisLoader = new RedisSerializedMapLoader<>(jedis, "users:");
 * SBCacheMap<Long, User> l2Cache = new SBCacheMap<>(redisLoader, 3600);
 *
 * // L1: 메모리 캐시 (1분) → L2로 체이닝
 * ChainedCacheMapLoader<Long, User> chainedLoader = new ChainedCacheMapLoader<>(l2Cache);
 * SBCacheMap<Long, User> l1Cache = new SBCacheMap<>(chainedLoader, 60);
 *
 * // 사용: L1 미스 → L2 확인 → Redis 확인
 * User user = l1Cache.get(123L);
 * </pre>
 *
 * @author archmagece
 * @since 2025-01
 * @param <K> 키 타입
 * @param <V> 값 타입
 */
@Slf4j
public class ChainedCacheMapLoader<K, V> implements SBCacheMapLoader<K, V> {

	private final SBCacheMap<K, V> nextLevelCache;

	/**
	 * 다음 레벨 캐시를 사용하는 체이닝 로더 생성
	 *
	 * @param nextLevelCache 다음 레벨 캐시 (L2, L3 등)
	 */
	public ChainedCacheMapLoader(SBCacheMap<K, V> nextLevelCache) {
		this.nextLevelCache = nextLevelCache;
		log.debug("ChainedCacheMapLoader initialized");
	}

	@Override
	public V loadOne(K key) throws SBCacheLoadFailException {
		try {
			log.trace("Loading from next level cache: {}", key);
			return nextLevelCache.get(key);
		} catch (Exception e) {
			log.debug("Failed to load from next level cache: {}", key, e);
			throw new SBCacheLoadFailException("Failed to load from next level: " + key, e);
		}
	}

	@Override
	public Map<K, V> loadAll() throws SBCacheLoadFailException {
		try {
			log.trace("Loading all from next level cache");
			// SBCacheMap의 내부 데이터를 직접 접근할 수 없으므로
			// 기본 구현 사용 (빈 맵 반환)
			return SBCacheMapLoader.super.loadAll();
		} catch (Exception e) {
			log.error("Failed to load all from next level cache", e);
			throw new SBCacheLoadFailException("Failed to load all from next level", e);
		}
	}
}
