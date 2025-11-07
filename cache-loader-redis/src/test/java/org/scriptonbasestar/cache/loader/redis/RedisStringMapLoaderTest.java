package org.scriptonbasestar.cache.loader.redis;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.scriptonbasestar.cache.collection.map.SBCacheMap;
import redis.clients.jedis.JedisPooled;

import static org.junit.Assert.*;

/**
 * RedisStringMapLoader 테스트
 *
 * 실제 Redis 서버 필요 (localhost:6379)
 * Redis가 없으면 @Ignore로 스킵됩니다.
 *
 * @author archmagece
 * @since 2025-01
 */
public class RedisStringMapLoaderTest {

	private JedisPooled jedis;
	private RedisStringMapLoader loader;

	@Before
	public void setUp() {
		try {
			jedis = new JedisPooled("localhost", 6379);
			jedis.ping(); // Redis 연결 확인
			loader = new RedisStringMapLoader(jedis, "test:");

			// 테스트 데이터 준비
			jedis.set("test:user1", "John Doe");
			jedis.set("test:user2", "Jane Smith");
		} catch (Exception e) {
			System.err.println("Redis not available: " + e.getMessage());
		}
	}

	@Test
	@Ignore("Requires Redis server running on localhost:6379")
	public void testLoadOne() throws Exception {
		String value = loader.loadOne("user1");
		assertEquals("John Doe", value);
	}

	@Test
	@Ignore("Requires Redis server running on localhost:6379")
	public void testLoadAll() throws Exception {
		var allData = loader.loadAll();
		assertTrue(allData.size() >= 2);
		assertEquals("John Doe", allData.get("user1"));
		assertEquals("Jane Smith", allData.get("user2"));
	}

	@Test
	@Ignore("Requires Redis server running on localhost:6379")
	public void testWithSBCacheMap() throws Exception {
		SBCacheMap<String, String> cache = new SBCacheMap<>(loader, 60);

		String user1 = cache.get("user1");
		assertEquals("John Doe", user1);

		// 두 번째 호출은 캐시에서 반환
		String user1Cached = cache.get("user1");
		assertEquals("John Doe", user1Cached);

		cache.close();
	}

	@Test
	@Ignore("Requires Redis server running on localhost:6379")
	public void testSaveAndDelete() throws Exception {
		loader.save("user3", "Bob Wilson", 60);

		String value = loader.loadOne("user3");
		assertEquals("Bob Wilson", value);

		loader.delete("user3");

		try {
			loader.loadOne("user3");
			fail("Should throw exception for deleted key");
		} catch (Exception e) {
			// Expected
		}
	}
}
