package org.scriptonbasestar.cache.spring;

import org.scriptonbasestar.cache.collection.map.SBCacheMap;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring CacheManager 구현체
 * 여러 개의 SBCache 인스턴스를 관리합니다.
 *
 * <p>사용 예시:</p>
 * <pre>{@code
 * @Configuration
 * public class CacheConfig {
 *     @Bean
 *     public CacheManager cacheManager() {
 *         return new SBCacheManager()
 *             .addCache("users", SBCacheMap.<Object, Object>builder()
 *                 .loader(userLoader)
 *                 .timeoutSec(300)
 *                 .enableMetrics(true)
 *                 .build())
 *             .addCache("products", SBCacheMap.<Object, Object>builder()
 *                 .loader(productLoader)
 *                 .timeoutSec(600)
 *                 .build());
 *     }
 * }
 * }</pre>
 *
 * @author archmagece
 * @since 2025-01
 */
public class SBCacheManager implements CacheManager {

	private final Map<String, Cache> caches = new ConcurrentHashMap<>();
	private final boolean allowNullValues;

	public SBCacheManager() {
		this(true);
	}

	public SBCacheManager(boolean allowNullValues) {
		this.allowNullValues = allowNullValues;
	}

	/**
	 * 새로운 캐시를 추가합니다.
	 *
	 * @param name 캐시 이름
	 * @param cacheMap SBCacheMap 인스턴스
	 * @return this (fluent API)
	 */
	public SBCacheManager addCache(String name, SBCacheMap<Object, Object> cacheMap) {
		if (name == null || name.trim().isEmpty()) {
			throw new IllegalArgumentException("Cache name must not be null or empty");
		}
		if (cacheMap == null) {
			throw new IllegalArgumentException("CacheMap must not be null");
		}

		SBCache cache = new SBCache(name, cacheMap);
		caches.put(name, cache);
		return this;
	}

	/**
	 * 미리 생성된 SBCache를 추가합니다.
	 *
	 * @param cache SBCache 인스턴스
	 * @return this (fluent API)
	 */
	public SBCacheManager addCache(SBCache cache) {
		if (cache == null) {
			throw new IllegalArgumentException("Cache must not be null");
		}
		caches.put(cache.getName(), cache);
		return this;
	}

	@Override
	public Cache getCache(String name) {
		return caches.get(name);
	}

	@Override
	public Collection<String> getCacheNames() {
		return Collections.unmodifiableSet(caches.keySet());
	}

	/**
	 * 모든 캐시 인스턴스를 반환합니다.
	 *
	 * @return 캐시 맵 (unmodifiable)
	 */
	public Map<String, Cache> getAllCaches() {
		return Collections.unmodifiableMap(caches);
	}

	/**
	 * 특정 캐시를 제거합니다.
	 *
	 * @param name 캐시 이름
	 * @return 제거된 캐시, 없으면 null
	 */
	public Cache removeCache(String name) {
		Cache removed = caches.remove(name);
		if (removed instanceof SBCache) {
			// AutoCloseable 캐시는 자동으로 닫기
			try {
				((SBCache) removed).getCacheMap().close();
			} catch (Exception e) {
				// 로깅만 하고 계속 진행
			}
		}
		return removed;
	}

	/**
	 * 모든 캐시를 제거합니다.
	 */
	public void removeAll() {
		for (Cache cache : caches.values()) {
			if (cache instanceof SBCache) {
				try {
					((SBCache) cache).getCacheMap().close();
				} catch (Exception e) {
					// 로깅만 하고 계속 진행
				}
			}
		}
		caches.clear();
	}

	/**
	 * Null 값 허용 여부를 반환합니다.
	 *
	 * @return true if null values are allowed
	 */
	public boolean isAllowNullValues() {
		return allowNullValues;
	}
}
