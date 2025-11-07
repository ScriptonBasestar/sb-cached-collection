package org.scriptonbasestar.cache.spring;

import org.junit.Before;
import org.junit.Test;
import org.scriptonbasestar.cache.collection.map.SBCacheMap;
import org.scriptonbasestar.cache.core.loader.SBCacheMapLoader;
import org.springframework.cache.Cache;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * SBCacheManager 테스트
 *
 * @author archmagece
 * @since 2025-01
 */
public class SBCacheManagerTest {

	private SBCacheManager cacheManager;
	private AtomicInteger loadCount;

	@Before
	public void setUp() {
		cacheManager = new SBCacheManager();
		loadCount = new AtomicInteger(0);
	}

	@Test
	public void testAddAndGetCache() {
		// Given
		SBCacheMapLoader<Object, Object> loader = key -> {
			loadCount.incrementAndGet();
			return "value-" + key;
		};

		SBCacheMap<Object, Object> cacheMap = new SBCacheMap<>(loader, 60);

		// When
		cacheManager.addCache("testCache", cacheMap);
		Cache cache = cacheManager.getCache("testCache");

		// Then
		assertNotNull("Cache should not be null", cache);
		assertEquals("testCache", cache.getName());
		assertTrue("Cache should be instance of SBCache", cache instanceof SBCache);
	}

	@Test
	public void testGetCacheNames() {
		// Given
		SBCacheMapLoader<Object, Object> loader = key -> "value-" + key;

		cacheManager.addCache("cache1", new SBCacheMap<>(loader, 60));
		cacheManager.addCache("cache2", new SBCacheMap<>(loader, 60));

		// When
		Collection<String> cacheNames = cacheManager.getCacheNames();

		// Then
		assertEquals(2, cacheNames.size());
		assertTrue(cacheNames.contains("cache1"));
		assertTrue(cacheNames.contains("cache2"));
	}

	@Test
	public void testCachePutAndGet() {
		// Given
		SBCacheMapLoader<Object, Object> loader = key -> {
			loadCount.incrementAndGet();
			return "loaded-" + key;
		};

		cacheManager.addCache("users", new SBCacheMap<>(loader, 60));
		Cache cache = cacheManager.getCache("users");

		// When
		cache.put("key1", "value1");
		Cache.ValueWrapper result = cache.get("key1");

		// Then
		assertNotNull(result);
		assertEquals("value1", result.get());
		assertEquals(0, loadCount.get()); // 로드하지 않음 (직접 put)
	}

	@Test
	public void testCacheEvict() {
		// Given
		SBCacheMapLoader<Object, Object> loader = key -> "value-" + key;

		cacheManager.addCache("users", new SBCacheMap<>(loader, 60));
		Cache cache = cacheManager.getCache("users");

		cache.put("key1", "value1");

		// When
		cache.evict("key1");
		Cache.ValueWrapper result = cache.get("key1");

		// Then
		assertNull("Evicted key should return null", result);
	}

	@Test
	public void testCacheClear() {
		// Given
		SBCacheMapLoader<Object, Object> loader = key -> "value-" + key;

		cacheManager.addCache("users", new SBCacheMap<>(loader, 60));
		Cache cache = cacheManager.getCache("users");

		cache.put("key1", "value1");
		cache.put("key2", "value2");

		// When
		cache.clear();

		// Then
		assertNull(cache.get("key1"));
		assertNull(cache.get("key2"));
	}

	@Test
	public void testRemoveCache() {
		// Given
		SBCacheMapLoader<Object, Object> loader = key -> "value-" + key;

		cacheManager.addCache("tempCache", new SBCacheMap<>(loader, 60));

		// When
		Cache removed = cacheManager.removeCache("tempCache");

		// Then
		assertNotNull(removed);
		assertNull("Removed cache should not be retrievable", cacheManager.getCache("tempCache"));
	}

	@Test
	public void testFluentApi() {
		// Given & When
		SBCacheMapLoader<Object, Object> loader = key -> "value-" + key;

		cacheManager
			.addCache("cache1", new SBCacheMap<>(loader, 60))
			.addCache("cache2", new SBCacheMap<>(loader, 120))
			.addCache("cache3", new SBCacheMap<>(loader, 300));

		// Then
		assertEquals(3, cacheManager.getCacheNames().size());
		assertNotNull(cacheManager.getCache("cache1"));
		assertNotNull(cacheManager.getCache("cache2"));
		assertNotNull(cacheManager.getCache("cache3"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testAddCacheWithNullName() {
		SBCacheMapLoader<Object, Object> loader = key -> "value";
		cacheManager.addCache(null, new SBCacheMap<>(loader, 60));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testAddCacheWithEmptyName() {
		SBCacheMapLoader<Object, Object> loader = key -> "value";
		cacheManager.addCache("", new SBCacheMap<>(loader, 60));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testAddCacheWithNullCacheMap() {
		cacheManager.addCache("test", null);
	}
}
