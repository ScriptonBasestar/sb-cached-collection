package org.scriptonbasestar.cache.loader.redis;

import org.scriptonbasestar.cache.core.loader.SBCacheMapLoader;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPooled;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Redis를 백엔드로 사용하는 직렬화 지원 캐시 로더
 * Java 객체를 직렬화하여 Redis에 저장/조회합니다.
 *
 * 사용 예시:
 * <pre>
 * JedisPooled jedis = new JedisPooled("localhost", 6379);
 * RedisSerializedMapLoader<Long, User> loader = new RedisSerializedMapLoader<>(jedis, "users:");
 *
 * SBCacheMap<Long, User> cache = new SBCacheMap<>(loader, 60);
 * User user = cache.get(123L);  // Redis에서 "users:123" 바이너리 데이터 조회 후 역직렬화
 * </pre>
 *
 * 주의: 값 타입(V)은 반드시 Serializable을 구현해야 합니다.
 *
 * @author archmagece
 * @since 2025-01
 */
public class RedisSerializedMapLoader<K, V extends Serializable> implements SBCacheMapLoader<K, V>, AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(RedisSerializedMapLoader.class);

	private final JedisPooled jedis;
	private final String keyPrefix;
	private final boolean autoClose;

	/**
	 * Redis 로더 생성 (자동 close 비활성화)
	 *
	 * @param jedis Jedis 연결 인스턴스
	 * @param keyPrefix Redis 키 접두사 (예: "users:", "products:")
	 */
	public RedisSerializedMapLoader(JedisPooled jedis, String keyPrefix) {
		this(jedis, keyPrefix, false);
	}

	/**
	 * Redis 로더 생성
	 *
	 * @param jedis Jedis 연결 인스턴스
	 * @param keyPrefix Redis 키 접두사 (예: "users:", "products:")
	 * @param autoClose close() 호출 시 Jedis 인스턴스도 함께 종료할지 여부
	 */
	public RedisSerializedMapLoader(JedisPooled jedis, String keyPrefix, boolean autoClose) {
		this.jedis = jedis;
		this.keyPrefix = keyPrefix != null ? keyPrefix : "";
		this.autoClose = autoClose;
		log.debug("RedisSerializedMapLoader initialized with prefix: {}", this.keyPrefix);
	}

	@Override
	public V loadOne(K key) throws SBCacheLoadFailException {
		try {
			String redisKey = keyPrefix + key.toString();
			log.trace("Loading from Redis: {}", redisKey);

			byte[] data = jedis.get(redisKey.getBytes());

			if (data == null) {
				log.debug("Key not found in Redis: {}", redisKey);
				throw new SBCacheLoadFailException("Key not found: " + key);
			}

			return deserialize(data);
		} catch (Exception e) {
			log.error("Failed to load from Redis: {}", key, e);
			throw new SBCacheLoadFailException("Failed to load key: " + key, e);
		}
	}

	@Override
	public Map<K, V> loadAll() throws SBCacheLoadFailException {
		try {
			String pattern = keyPrefix + "*";
			log.trace("Loading all keys matching pattern: {}", pattern);

			Set<byte[]> keys = jedis.keys((pattern).getBytes());
			Map<K, V> result = new HashMap<>();

			for (byte[] redisKeyBytes : keys) {
				byte[] data = jedis.get(redisKeyBytes);
				if (data != null) {
					// 접두사 제거하여 실제 키로 변환
					String redisKey = new String(redisKeyBytes);
					String actualKeyStr = redisKey.substring(keyPrefix.length());

					@SuppressWarnings("unchecked")
					K actualKey = (K) actualKeyStr;  // 문자열을 K 타입으로 변환 (제한적)

					V value = deserialize(data);
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
	 * @param value 값 (Serializable 구현 필수)
	 * @param ttlSeconds TTL (초), 0이면 만료 없음
	 */
	public void save(K key, V value, int ttlSeconds) {
		try {
			String redisKey = keyPrefix + key.toString();
			byte[] data = serialize(value);

			if (ttlSeconds > 0) {
				jedis.setex(redisKey.getBytes(), ttlSeconds, data);
				log.trace("Saved to Redis with TTL: {} ({}s)", redisKey, ttlSeconds);
			} else {
				jedis.set(redisKey.getBytes(), data);
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
	public void delete(K key) {
		try {
			String redisKey = keyPrefix + key.toString();
			jedis.del(redisKey.getBytes());
			log.trace("Deleted from Redis: {}", redisKey);
		} catch (Exception e) {
			log.error("Failed to delete from Redis: {}", key, e);
		}
	}

	/**
	 * 객체를 바이트 배열로 직렬화
	 */
	private byte[] serialize(V value) throws IOException {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
		     ObjectOutputStream oos = new ObjectOutputStream(baos)) {
			oos.writeObject(value);
			return baos.toByteArray();
		}
	}

	/**
	 * 바이트 배열을 객체로 역직렬화
	 */
	@SuppressWarnings("unchecked")
	private V deserialize(byte[] data) throws IOException, ClassNotFoundException {
		try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
		     ObjectInputStream ois = new ObjectInputStream(bais)) {
			return (V) ois.readObject();
		}
	}

	/**
	 * Redis 연결 종료 (autoClose=true인 경우만)
	 */
	@Override
	public void close() {
		if (autoClose && jedis != null) {
			log.debug("Closing RedisSerializedMapLoader");
			jedis.close();
		}
	}
}
