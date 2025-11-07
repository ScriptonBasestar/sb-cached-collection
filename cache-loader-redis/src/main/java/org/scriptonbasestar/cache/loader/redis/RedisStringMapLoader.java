package org.scriptonbasestar.cache.loader.redis;

import org.scriptonbasestar.cache.core.loader.SBCacheMapLoader;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPooled;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Redis를 백엔드로 사용하는 String 타입 캐시 로더
 *
 * 사용 예시:
 * <pre>
 * JedisPooled jedis = new JedisPooled("localhost", 6379);
 * RedisStringMapLoader loader = new RedisStringMapLoader(jedis, "users:");
 *
 * SBCacheMap<String, String> cache = new SBCacheMap<>(loader, 60);
 * String user = cache.get("user123");  // Redis에서 "users:user123" 키 조회
 * </pre>
 *
 * @author archmagece
 * @since 2025-01
 */
public class RedisStringMapLoader implements SBCacheMapLoader<String, String>, AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(RedisStringMapLoader.class);

	private final JedisPooled jedis;
	private final String keyPrefix;
	private final boolean autoClose;

	/**
	 * Redis 로더 생성 (자동 close 비활성화)
	 *
	 * @param jedis Jedis 연결 인스턴스
	 * @param keyPrefix Redis 키 접두사 (예: "users:", "products:")
	 */
	public RedisStringMapLoader(JedisPooled jedis, String keyPrefix) {
		this(jedis, keyPrefix, false);
	}

	/**
	 * Redis 로더 생성
	 *
	 * @param jedis Jedis 연결 인스턴스
	 * @param keyPrefix Redis 키 접두사 (예: "users:", "products:")
	 * @param autoClose close() 호출 시 Jedis 인스턴스도 함께 종료할지 여부
	 */
	public RedisStringMapLoader(JedisPooled jedis, String keyPrefix, boolean autoClose) {
		this.jedis = jedis;
		this.keyPrefix = keyPrefix != null ? keyPrefix : "";
		this.autoClose = autoClose;
		log.debug("RedisStringMapLoader initialized with prefix: {}", this.keyPrefix);
	}

	@Override
	public String loadOne(String key) throws SBCacheLoadFailException {
		try {
			String redisKey = keyPrefix + key;
			log.trace("Loading from Redis: {}", redisKey);

			String value = jedis.get(redisKey);

			if (value == null) {
				log.debug("Key not found in Redis: {}", redisKey);
				throw new SBCacheLoadFailException("Key not found: " + key);
			}

			return value;
		} catch (Exception e) {
			log.error("Failed to load from Redis: {}", key, e);
			throw new SBCacheLoadFailException("Failed to load key: " + key, e);
		}
	}

	@Override
	public Map<String, String> loadAll() throws SBCacheLoadFailException {
		try {
			String pattern = keyPrefix + "*";
			log.trace("Loading all keys matching pattern: {}", pattern);

			Set<String> keys = jedis.keys(pattern);
			Map<String, String> result = new HashMap<>();

			for (String redisKey : keys) {
				String value = jedis.get(redisKey);
				if (value != null) {
					// 접두사 제거하여 실제 키로 변환
					String actualKey = redisKey.substring(keyPrefix.length());
					result.put(actualKey, value);
				}
			}

			log.debug("Loaded {} keys from Redis", result.size());
			return result;
		} catch (Exception e) {
			log.error("Failed to load all from Redis", e);
			throw new SBCacheLoadFailException("Failed to load all keys", e);
		}
	}

	/**
	 * Redis에 값을 저장합니다 (캐시 write-through 패턴용)
	 *
	 * @param key 키
	 * @param value 값
	 * @param ttlSeconds TTL (초), 0이면 만료 없음
	 */
	public void save(String key, String value, int ttlSeconds) {
		try {
			String redisKey = keyPrefix + key;

			if (ttlSeconds > 0) {
				jedis.setex(redisKey, ttlSeconds, value);
				log.trace("Saved to Redis with TTL: {} ({}s)", redisKey, ttlSeconds);
			} else {
				jedis.set(redisKey, value);
				log.trace("Saved to Redis: {}", redisKey);
			}
		} catch (Exception e) {
			log.error("Failed to save to Redis: {}", key, e);
		}
	}

	/**
	 * Redis에서 키를 삭제합니다
	 *
	 * @param key 삭제할 키
	 */
	public void delete(String key) {
		try {
			String redisKey = keyPrefix + key;
			jedis.del(redisKey);
			log.trace("Deleted from Redis: {}", redisKey);
		} catch (Exception e) {
			log.error("Failed to delete from Redis: {}", key, e);
		}
	}

	/**
	 * Redis 연결 종료 (autoClose=true인 경우만)
	 */
	@Override
	public void close() {
		if (autoClose && jedis != null) {
			log.debug("Closing RedisStringMapLoader");
			jedis.close();
		}
	}
}
