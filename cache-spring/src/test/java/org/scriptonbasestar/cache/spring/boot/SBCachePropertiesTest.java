package org.scriptonbasestar.cache.spring.boot;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * SBCacheProperties 테스트
 *
 * @author archmagece
 * @since 2025-01
 */
public class SBCachePropertiesTest {

	private SBCacheProperties properties;

	@Before
	public void setUp() {
		properties = new SBCacheProperties();
	}

	@Test
	public void testDefaultValues() {
		// Then
		assertEquals(60, properties.getDefaultTtl());
		assertFalse(properties.isEnableMetrics());
		assertFalse(properties.isEnableJmx());
		assertEquals(0, properties.getMaxSize());
		assertNotNull(properties.getAutoCleanup());
		assertFalse(properties.getAutoCleanup().isEnabled());
		assertEquals(5, properties.getAutoCleanup().getIntervalMinutes());
	}

	@Test
	public void testSettersAndGetters() {
		// When
		properties.setDefaultTtl(300);
		properties.setEnableMetrics(true);
		properties.setEnableJmx(true);
		properties.setMaxSize(10000);

		// Then
		assertEquals(300, properties.getDefaultTtl());
		assertTrue(properties.isEnableMetrics());
		assertTrue(properties.isEnableJmx());
		assertEquals(10000, properties.getMaxSize());
	}

	@Test
	public void testAutoCleanupConfiguration() {
		// When
		SBCacheProperties.AutoCleanup autoCleanup = properties.getAutoCleanup();
		autoCleanup.setEnabled(true);
		autoCleanup.setIntervalMinutes(10);

		// Then
		assertTrue(properties.getAutoCleanup().isEnabled());
		assertEquals(10, properties.getAutoCleanup().getIntervalMinutes());
	}

	@Test
	public void testCacheConfigDefaults() {
		// Given
		SBCacheProperties.CacheConfig config = new SBCacheProperties.CacheConfig();

		// Then - 기본값은 null
		assertNull(config.getTtl());
		assertNull(config.getMaxSize());
		assertNull(config.getForcedTimeout());
		assertNull(config.getEnableMetrics());
		assertNull(config.getEnableJmx());
	}

	@Test
	public void testCacheConfigSettersAndGetters() {
		// Given
		SBCacheProperties.CacheConfig config = new SBCacheProperties.CacheConfig();

		// When
		config.setTtl(300);
		config.setMaxSize(1000);
		config.setForcedTimeout(600);
		config.setEnableMetrics(true);
		config.setEnableJmx(true);

		// Then
		assertEquals(Integer.valueOf(300), config.getTtl());
		assertEquals(Integer.valueOf(1000), config.getMaxSize());
		assertEquals(Integer.valueOf(600), config.getForcedTimeout());
		assertEquals(Boolean.TRUE, config.getEnableMetrics());
		assertEquals(Boolean.TRUE, config.getEnableJmx());
	}

	@Test
	public void testCacheConfigApplyDefaults() {
		// Given
		properties.setDefaultTtl(300);
		properties.setEnableMetrics(true);
		properties.setEnableJmx(true);
		properties.setMaxSize(10000);

		SBCacheProperties.CacheConfig config = new SBCacheProperties.CacheConfig();
		config.setTtl(600); // Override TTL

		// When
		config.applyDefaults(properties);

		// Then
		assertEquals(Integer.valueOf(600), config.getTtl());  // 오버라이드 유지
		assertEquals(Integer.valueOf(10000), config.getMaxSize());  // 기본값 적용
		assertEquals(Boolean.TRUE, config.getEnableMetrics());  // 기본값 적용
		assertEquals(Boolean.TRUE, config.getEnableJmx());  // 기본값 적용
	}

	@Test
	public void testCacheConfigApplyDefaultsAllNull() {
		// Given
		properties.setDefaultTtl(300);
		properties.setEnableMetrics(true);
		properties.setEnableJmx(false);
		properties.setMaxSize(5000);

		SBCacheProperties.CacheConfig config = new SBCacheProperties.CacheConfig();

		// When - 모든 값이 null
		config.applyDefaults(properties);

		// Then - 모두 기본값 적용
		assertEquals(Integer.valueOf(300), config.getTtl());
		assertEquals(Integer.valueOf(5000), config.getMaxSize());
		assertEquals(Boolean.TRUE, config.getEnableMetrics());
		assertEquals(Boolean.FALSE, config.getEnableJmx());
	}

	@Test
	public void testMultipleCacheConfigurations() {
		// Given
		SBCacheProperties.CacheConfig usersConfig = new SBCacheProperties.CacheConfig();
		usersConfig.setTtl(300);
		usersConfig.setMaxSize(1000);

		SBCacheProperties.CacheConfig productsConfig = new SBCacheProperties.CacheConfig();
		productsConfig.setTtl(600);
		productsConfig.setMaxSize(5000);

		// When
		properties.getCaches().put("users", usersConfig);
		properties.getCaches().put("products", productsConfig);

		// Then
		assertEquals(2, properties.getCaches().size());
		assertTrue(properties.getCaches().containsKey("users"));
		assertTrue(properties.getCaches().containsKey("products"));

		assertEquals(Integer.valueOf(300), properties.getCaches().get("users").getTtl());
		assertEquals(Integer.valueOf(600), properties.getCaches().get("products").getTtl());
	}
}
