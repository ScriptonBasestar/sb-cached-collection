package org.scriptonbasestar.cache.collection.jmx;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scriptonbasestar.cache.collection.metrics.CacheMetrics;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

import static org.junit.Assert.*;

/**
 * JmxHelper 테스트
 *
 * @author archmagece
 * @since 2025-01
 */
public class JmxHelperTest {

	private CacheMetrics metrics;
	private String cacheName;

	@Before
	public void setUp() {
		metrics = new CacheMetrics();
		cacheName = "test-cache-" + System.currentTimeMillis();
	}

	@After
	public void tearDown() {
		// 테스트 후 정리
		JmxHelper.unregisterCache(cacheName);
	}

	@Test
	public void testRegisterCache() {
		// When
		CacheStatistics mbean = JmxHelper.registerCache(metrics, cacheName);

		// Then
		assertNotNull(mbean);
		assertEquals(cacheName, mbean.getCacheName());
		assertTrue(JmxHelper.isRegistered(cacheName));
	}

	@Test
	public void testRegisterCacheWithMaxSize() {
		// When
		CacheStatistics mbean = JmxHelper.registerCache(metrics, cacheName, 1000);

		// Then
		assertNotNull(mbean);
		assertEquals(1000, mbean.getMaxSize());
		assertTrue(JmxHelper.isRegistered(cacheName));
	}

	@Test
	public void testUnregisterCache() {
		// Given
		JmxHelper.registerCache(metrics, cacheName);
		assertTrue(JmxHelper.isRegistered(cacheName));

		// When
		JmxHelper.unregisterCache(cacheName);

		// Then
		assertFalse(JmxHelper.isRegistered(cacheName));
	}

	@Test
	public void testRegisterTwice() {
		// Given
		JmxHelper.registerCache(metrics, cacheName);

		// When - 동일한 이름으로 재등록 시도
		CacheStatistics mbean2 = JmxHelper.registerCache(metrics, cacheName);

		// Then - 기존 것이 제거되고 새로 등록됨
		assertNotNull(mbean2);
		assertTrue(JmxHelper.isRegistered(cacheName));
	}

	@Test
	public void testUnregisterNonExistent() {
		// When - 등록되지 않은 캐시 해제 시도
		JmxHelper.unregisterCache("non-existent-cache");

		// Then - 예외 없이 정상 처리
		assertFalse(JmxHelper.isRegistered("non-existent-cache"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRegisterWithNullMetrics() {
		JmxHelper.registerCache(null, cacheName);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRegisterWithNullCacheName() {
		JmxHelper.registerCache(metrics, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRegisterWithEmptyCacheName() {
		JmxHelper.registerCache(metrics, "");
	}

	@Test
	public void testIsRegisteredWithNull() {
		assertFalse(JmxHelper.isRegistered(null));
	}

	@Test
	public void testIsRegisteredWithEmpty() {
		assertFalse(JmxHelper.isRegistered(""));
	}

	@Test
	public void testCreateObjectName() throws Exception {
		// When
		ObjectName objectName = JmxHelper.createObjectName("my-cache");

		// Then
		assertNotNull(objectName);
		assertEquals("org.scriptonbasestar.cache", objectName.getDomain());
		assertEquals("SBCacheMap", objectName.getKeyProperty("type"));
		assertEquals("my-cache", objectName.getKeyProperty("name"));
	}

	@Test
	public void testCreateObjectNameWithSpecialCharacters() throws Exception {
		// Given - 특수 문자가 포함된 캐시 이름
		String nameWithSpecialChars = "cache:test,name=special*";

		// When
		ObjectName objectName = JmxHelper.createObjectName(nameWithSpecialChars);

		// Then - 특수 문자가 언더스코어로 치환됨
		assertNotNull(objectName);
		String sanitizedName = objectName.getKeyProperty("name");
		assertFalse(sanitizedName.contains(":"));
		assertFalse(sanitizedName.contains(","));
		assertFalse(sanitizedName.contains("*"));
	}

	@Test
	public void testMBeanAttributes() throws Exception {
		// Given
		metrics.recordHit();
		metrics.recordHit();
		metrics.recordMiss();
		CacheStatistics mbean = JmxHelper.registerCache(metrics, cacheName);

		// When - MBeanServer를 통해 직접 속성 조회
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		ObjectName objectName = JmxHelper.createObjectName(cacheName);

		// Then
		assertEquals(3L, mbs.getAttribute(objectName, "RequestCount"));
		assertEquals(2L, mbs.getAttribute(objectName, "HitCount"));
		assertEquals(1L, mbs.getAttribute(objectName, "MissCount"));
		assertEquals(66.666, (Double) mbs.getAttribute(objectName, "HitRatePercent"), 0.1);
	}

	@Test
	public void testMBeanOperations() throws Exception {
		// Given
		metrics.recordHit();
		metrics.recordMiss();
		JmxHelper.registerCache(metrics, cacheName);

		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		ObjectName objectName = JmxHelper.createObjectName(cacheName);

		// When - resetStatistics 연산 호출
		mbs.invoke(objectName, "resetStatistics", null, null);

		// Then
		assertEquals(0L, mbs.getAttribute(objectName, "RequestCount"));
		assertEquals(0L, mbs.getAttribute(objectName, "HitCount"));
		assertEquals(0L, mbs.getAttribute(objectName, "MissCount"));
	}

	@Test
	public void testMultipleCaches() {
		// Given
		String cacheName1 = "cache1-" + System.currentTimeMillis();
		String cacheName2 = "cache2-" + System.currentTimeMillis();

		try {
			CacheMetrics metrics1 = new CacheMetrics();
			CacheMetrics metrics2 = new CacheMetrics();

			// When
			JmxHelper.registerCache(metrics1, cacheName1);
			JmxHelper.registerCache(metrics2, cacheName2);

			// Then
			assertTrue(JmxHelper.isRegistered(cacheName1));
			assertTrue(JmxHelper.isRegistered(cacheName2));

		} finally {
			JmxHelper.unregisterCache(cacheName1);
			JmxHelper.unregisterCache(cacheName2);
		}
	}

	@Test
	public void testUnregisterWithNull() {
		// When - null로 해제 시도
		JmxHelper.unregisterCache(null);

		// Then - 예외 없이 정상 처리
	}

	@Test
	public void testUnregisterWithEmpty() {
		// When - 빈 문자열로 해제 시도
		JmxHelper.unregisterCache("");

		// Then - 예외 없이 정상 처리
	}
}
