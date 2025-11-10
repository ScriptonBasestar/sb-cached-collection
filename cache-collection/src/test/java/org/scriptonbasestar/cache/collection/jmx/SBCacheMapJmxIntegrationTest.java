package org.scriptonbasestar.cache.collection.jmx;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scriptonbasestar.cache.collection.map.SBCacheMap;
import org.scriptonbasestar.cache.core.loader.SBCacheMapLoader;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * SBCacheMap JMX 통합 테스트
 *
 * @author archmagece
 * @since 2025-01
 */
public class SBCacheMapJmxIntegrationTest {

	private SBCacheMap<Long, String> cache;
	private String cacheName;
	private SBCacheMapLoader<Long, String> loader;

	@Before
	public void setUp() {
		cacheName = "test-cache-" + System.currentTimeMillis();
		loader = new SBCacheMapLoader<Long, String>() {
			@Override
			public String loadOne(Long key) {
				return "value-" + key;
			}

			@Override
			public Map<Long, String> loadAll() {
				Map<Long, String> map = new HashMap<>();
				map.put(1L, "value-1");
				map.put(2L, "value-2");
				map.put(3L, "value-3");
				return map;
			}
		};
	}

	@After
	public void tearDown() {
		if (cache != null) {
			cache.close();
		}
		JmxHelper.unregisterCache(cacheName);
	}

	@Test
	public void testEnableJmxInBuilder() {
		// When
		cache = SBCacheMap.<Long, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.maxSize(100)
			.enableJmx(cacheName)
			.build();

		// Then
		assertTrue(JmxHelper.isRegistered(cacheName));
		assertNotNull(cache.metrics());
	}

	@Test
	public void testJmxAutoEnablesMetrics() {
		// When
		cache = SBCacheMap.<Long, String>builder()
			.loader(loader)
			.enableJmx(cacheName)
			.build();

		// Then - JMX를 활성화하면 메트릭도 자동 활성화
		assertNotNull(cache.metrics());
	}

	@Test
	public void testJmxTracksHitsAndMisses() throws Exception {
		// Given
		cache = SBCacheMap.<Long, String>builder()
			.loader(loader)
			.enableJmx(cacheName)
			.build();

		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		ObjectName objectName = JmxHelper.createObjectName(cacheName);

		// When
		cache.get(1L);  // miss + load
		cache.get(1L);  // hit
		cache.get(1L);  // hit

		// Then
		assertEquals(3L, mbs.getAttribute(objectName, "RequestCount"));
		assertEquals(2L, mbs.getAttribute(objectName, "HitCount"));
		assertEquals(1L, mbs.getAttribute(objectName, "MissCount"));
	}

	@Test
	public void testJmxTracksCacheSize() throws Exception {
		// Given
		cache = SBCacheMap.<Long, String>builder()
			.loader(loader)
			.maxSize(10)
			.enableJmx(cacheName)
			.build();

		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		ObjectName objectName = JmxHelper.createObjectName(cacheName);

		// When
		cache.put(1L, "value1");
		cache.put(2L, "value2");
		cache.put(3L, "value3");

		// Then
		assertEquals(10, mbs.getAttribute(objectName, "MaxSize"));
		assertEquals(3, mbs.getAttribute(objectName, "CurrentSize"));
		assertEquals(30.0, (Double) mbs.getAttribute(objectName, "FillPercent"), 0.1);
	}

	@Test
	public void testJmxTracksRemove() throws Exception {
		// Given
		cache = SBCacheMap.<Long, String>builder()
			.loader(loader)
			.maxSize(10)
			.enableJmx(cacheName)
			.build();

		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		ObjectName objectName = JmxHelper.createObjectName(cacheName);

		cache.put(1L, "value1");
		cache.put(2L, "value2");
		cache.put(3L, "value3");
		assertEquals(3, mbs.getAttribute(objectName, "CurrentSize"));

		// When
		cache.remove(2L);

		// Then
		assertEquals(2, mbs.getAttribute(objectName, "CurrentSize"));
	}

	@Test
	public void testJmxTracksRemoveAll() throws Exception {
		// Given
		cache = SBCacheMap.<Long, String>builder()
			.loader(loader)
			.maxSize(10)
			.enableJmx(cacheName)
			.build();

		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		ObjectName objectName = JmxHelper.createObjectName(cacheName);

		cache.put(1L, "value1");
		cache.put(2L, "value2");
		cache.put(3L, "value3");
		assertEquals(3, mbs.getAttribute(objectName, "CurrentSize"));

		// When
		cache.removeAll();

		// Then
		assertEquals(0, mbs.getAttribute(objectName, "CurrentSize"));
	}

	@Test
	public void testManualJmxRegistration() {
		// Given
		cache = SBCacheMap.<Long, String>builder()
			.loader(loader)
			.enableMetrics(true)
			.build();

		// When - 수동으로 JMX 등록
		cache.registerJmx(cacheName);

		// Then
		assertTrue(JmxHelper.isRegistered(cacheName));
	}

	@Test
	public void testManualJmxUnregistration() {
		// Given
		cache = SBCacheMap.<Long, String>builder()
			.loader(loader)
			.enableJmx(cacheName)
			.build();

		assertTrue(JmxHelper.isRegistered(cacheName));

		// When
		cache.unregisterJmx();

		// Then
		assertFalse(JmxHelper.isRegistered(cacheName));
	}

	@Test
	public void testJmxAutoUnregistersOnClose() {
		// Given
		cache = SBCacheMap.<Long, String>builder()
			.loader(loader)
			.enableJmx(cacheName)
			.build();

		assertTrue(JmxHelper.isRegistered(cacheName));

		// When
		cache.close();

		// Then
		assertFalse(JmxHelper.isRegistered(cacheName));
	}

	@Test
	public void testJmxResetStatistics() throws Exception {
		// Given
		cache = SBCacheMap.<Long, String>builder()
			.loader(loader)
			.enableJmx(cacheName)
			.build();

		cache.get(1L);
		cache.get(1L);

		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		ObjectName objectName = JmxHelper.createObjectName(cacheName);

		assertEquals(2L, mbs.getAttribute(objectName, "RequestCount"));

		// When
		mbs.invoke(objectName, "resetStatistics", null, null);

		// Then
		assertEquals(0L, mbs.getAttribute(objectName, "RequestCount"));
	}

	@Test
	public void testJmxWithoutMetrics() {
		// Given - 메트릭 비활성화 상태에서 JMX 등록 시도
		cache = SBCacheMap.<Long, String>builder()
			.loader(loader)
			.enableMetrics(false)
			.build();

		// When
		cache.registerJmx(cacheName);

		// Then - JMX 등록 실패 (메트릭 필요)
		assertFalse(JmxHelper.isRegistered(cacheName));
	}

	@Test
	public void testJmxStatisticsAttributes() throws Exception {
		// Given
		cache = SBCacheMap.<Long, String>builder()
			.loader(loader)
			.maxSize(100)
			.enableJmx(cacheName)
			.build();

		cache.get(1L);
		cache.get(1L);
		cache.get(2L);

		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		ObjectName objectName = JmxHelper.createObjectName(cacheName);

		// Then - 모든 주요 속성 확인
		assertEquals(3L, mbs.getAttribute(objectName, "RequestCount"));
		assertEquals(1L, mbs.getAttribute(objectName, "HitCount"));
		assertEquals(2L, mbs.getAttribute(objectName, "MissCount"));
		assertEquals(33.333, (Double) mbs.getAttribute(objectName, "HitRatePercent"), 0.1);
		assertEquals(66.666, (Double) mbs.getAttribute(objectName, "MissRatePercent"), 0.1);
		assertEquals(2L, mbs.getAttribute(objectName, "LoadSuccessCount"));
		assertEquals(0L, mbs.getAttribute(objectName, "LoadFailureCount"));
	}

	@Test
	public void testDoubleRegistration() {
		// Given
		cache = SBCacheMap.<Long, String>builder()
			.loader(loader)
			.enableJmx(cacheName)
			.build();

		assertTrue(JmxHelper.isRegistered(cacheName));

		// When - 이미 등록된 상태에서 재등록 시도
		cache.registerJmx(cacheName);

		// Then - 여전히 등록된 상태 (중복 등록 무시됨)
		assertTrue(JmxHelper.isRegistered(cacheName));
	}
}
