package org.scriptonbasestar.cache.spring;

import org.scriptonbasestar.cache.collection.map.SBCacheMap;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import java.util.concurrent.Callable;

/**
 * Spring Cache 인터페이스 구현체
 * SBCacheMap을 Spring Cache 추상화로 래핑합니다.
 *
 * @author archmagece
 * @since 2025-01
 */
public class SBCache implements Cache {

	private final String name;
	private final SBCacheMap<Object, Object> cacheMap;

	public SBCache(String name, SBCacheMap<Object, Object> cacheMap) {
		this.name = name;
		this.cacheMap = cacheMap;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Object getNativeCache() {
		return cacheMap;
	}

	@Override
	public ValueWrapper get(Object key) {
		// getIfPresent()를 사용하여 로더 호출 없이 캐시된 값만 조회
		Object value = cacheMap.getIfPresent(key);
		return value != null ? new SimpleValueWrapper(value) : null;
	}

	@Override
	public <T> T get(Object key, Class<T> type) {
		// getIfPresent()를 사용하여 로더 호출 없이 캐시된 값만 조회
		Object value = cacheMap.getIfPresent(key);
		if (value != null && type != null && !type.isInstance(value)) {
			throw new IllegalStateException(
				"Cached value is not of required type [" + type.getName() + "]: " + value
			);
		}
		return type != null ? type.cast(value) : null;
	}

	@Override
	public <T> T get(Object key, Callable<T> valueLoader) {
		// Spring Cache의 get(key, Callable) 패턴 지원
		// 캐시 미스 시 valueLoader를 호출하여 값을 로드하고 캐시에 저장
		Object value = cacheMap.getIfPresent(key);
		if (value != null) {
			return (T) value;
		}

		// 캐시 미스 - valueLoader로 값 생성
		try {
			T loadedValue = valueLoader.call();
			if (loadedValue != null) {
				cacheMap.put(key, loadedValue);
			}
			return loadedValue;
		} catch (Exception e) {
			throw new ValueRetrievalException(key, valueLoader, e);
		}
	}

	@Override
	public void put(Object key, Object value) {
		cacheMap.put(key, value);
	}

	@Override
	public ValueWrapper putIfAbsent(Object key, Object value) {
		// putIfAbsent: 키가 없을 때만 저장하고 기존 값 반환
		Object existingValue = cacheMap.getIfPresent(key);
		if (existingValue == null) {
			cacheMap.put(key, value);
			return null;
		}
		return new SimpleValueWrapper(existingValue);
	}

	@Override
	public void evict(Object key) {
		cacheMap.remove(key);
	}

	@Override
	public void clear() {
		cacheMap.removeAll();
	}

	/**
	 * SBCacheMap 인스턴스를 직접 반환합니다.
	 * 고급 기능 (metrics, warmup 등)에 접근할 때 사용합니다.
	 *
	 * @return SBCacheMap 인스턴스
	 */
	public SBCacheMap<Object, Object> getCacheMap() {
		return cacheMap;
	}
}
