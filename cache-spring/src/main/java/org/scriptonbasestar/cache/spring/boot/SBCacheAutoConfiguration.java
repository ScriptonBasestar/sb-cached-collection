package org.scriptonbasestar.cache.spring.boot;

import org.scriptonbasestar.cache.collection.map.SBCacheMap;
import org.scriptonbasestar.cache.collection.metrics.CacheMetrics;
import org.scriptonbasestar.cache.core.loader.SBCacheMapLoader;
import org.scriptonbasestar.cache.spring.SBCacheManager;
import org.scriptonbasestar.cache.spring.actuator.CompositeCacheHealthIndicator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Spring Boot Auto-Configuration for SB Cache.
 * <p>
 * This auto-configuration will be triggered when:
 * <ul>
 *   <li>SBCacheMap class is on the classpath</li>
 *   <li>No CacheManager bean is already defined</li>
 *   <li>sb-cache.* properties are configured (optional)</li>
 * </ul>
 * </p>
 *
 * <h3>Zero-Configuration Usage:</h3>
 * <pre>{@code
 * # Just add dependency - auto-configuration creates default CacheManager
 * dependencies {
 *     implementation 'org.scriptonbasestar.cache:cache-spring-boot-starter'
 * }
 * }</pre>
 *
 * <h3>Configuration Example:</h3>
 * <pre>{@code
 * # application.yml
 * sb-cache:
 *   default-ttl: 300
 *   enable-metrics: true
 *   enable-jmx: true
 *   max-size: 10000
 *   auto-cleanup:
 *     enabled: true
 *     interval-minutes: 10
 *   caches:
 *     users:
 *       ttl: 300
 *       max-size: 1000
 *     products:
 *       ttl: 600
 *       max-size: 5000
 * }</pre>
 *
 * @author archmagece
 * @since 2025-01
 */
@Configuration
@ConditionalOnClass(SBCacheMap.class)
@EnableConfigurationProperties(SBCacheProperties.class)
@EnableCaching
public class SBCacheAutoConfiguration {

	private final SBCacheProperties properties;
	private ScheduledExecutorService cleanupExecutor;

	@Autowired
	public SBCacheAutoConfiguration(SBCacheProperties properties) {
		this.properties = properties;
	}

	/**
	 * Creates a default CacheManager if none is already defined.
	 * <p>
	 * The CacheManager will be configured based on sb-cache.* properties.
	 * If no specific caches are configured, only an empty CacheManager is created.
	 * Applications can still add caches programmatically.
	 * </p>
	 *
	 * @return configured SBCacheManager
	 */
	@Bean
	@ConditionalOnMissingBean(CacheManager.class)
	public CacheManager cacheManager() {
		SBCacheManager cacheManager = new SBCacheManager();

		// Create caches from configuration
		for (Map.Entry<String, SBCacheProperties.CacheConfig> entry : properties.getCaches().entrySet()) {
			String cacheName = entry.getKey();
			SBCacheProperties.CacheConfig config = entry.getValue();

			// Apply defaults
			config.applyDefaults(properties);

			// Create cache with configuration
			SBCacheMap<Object, Object> cacheMap = createCacheMap(cacheName, config);
			cacheManager.addCache(cacheName, cacheMap);
		}

		// Setup auto-cleanup if enabled
		if (properties.getAutoCleanup().isEnabled()) {
			setupAutoCleanup(cacheManager);
		}

		return cacheManager;
	}

	/**
	 * Creates a health indicator for monitoring all caches.
	 * <p>
	 * Only activated when Spring Boot Actuator is on the classpath.
	 * </p>
	 *
	 * @param cacheManager the cache manager to monitor
	 * @return health indicator
	 */
	@Bean
	@ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
	@ConditionalOnMissingBean(name = "sbCacheHealthIndicator")
	public HealthIndicator sbCacheHealthIndicator(CacheManager cacheManager) {
		if (!(cacheManager instanceof SBCacheManager)) {
			return null;
		}

		SBCacheManager sbCacheManager = (SBCacheManager) cacheManager;
		Map<String, CacheMetrics> metricsMap = new HashMap<>();

		// Collect metrics from all caches
		sbCacheManager.getAllCaches().forEach((name, cache) -> {
			if (cache instanceof org.scriptonbasestar.cache.spring.SBCache) {
				org.scriptonbasestar.cache.spring.SBCache sbCache =
					(org.scriptonbasestar.cache.spring.SBCache) cache;
				CacheMetrics metrics = sbCache.getCacheMap().metrics();
				if (metrics != null) {
					metricsMap.put(name, metrics);
				}
			}
		});

		return new CompositeCacheHealthIndicator(metricsMap);
	}

	/**
	 * Creates a SBCacheMap instance based on configuration.
	 *
	 * @param cacheName cache name
	 * @param config cache configuration
	 * @return configured SBCacheMap
	 */
	private SBCacheMap<Object, Object> createCacheMap(String cacheName, SBCacheProperties.CacheConfig config) {
		// Default loader: return null (cache-aside pattern)
		SBCacheMapLoader<Object, Object> loader = key -> null;

		SBCacheMap.Builder<Object, Object> builder = SBCacheMap.<Object, Object>builder()
			.loader(loader);

		// Apply TTL
		if (config.getTtl() != null && config.getTtl() > 0) {
			builder.timeoutSec(config.getTtl());
		}

		// Apply forced timeout if specified
		if (config.getForcedTimeout() != null && config.getForcedTimeout() > 0) {
			builder.forcedTimeoutSec(config.getForcedTimeout());
		}

		// Apply max size
		if (config.getMaxSize() != null && config.getMaxSize() > 0) {
			builder.maxSize(config.getMaxSize());
		}

		// Apply metrics
		if (Boolean.TRUE.equals(config.getEnableMetrics())) {
			builder.enableMetrics(true);
		}

		// Apply JMX
		if (Boolean.TRUE.equals(config.getEnableJmx())) {
			builder.enableJmx(cacheName);
		}

		return builder.build();
	}

	/**
	 * Sets up automatic cleanup of expired entries.
	 *
	 * @param cacheManager the cache manager
	 */
	private void setupAutoCleanup(SBCacheManager cacheManager) {
		int intervalMinutes = properties.getAutoCleanup().getIntervalMinutes();
		if (intervalMinutes <= 0) {
			intervalMinutes = 5; // default
		}

		cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "SBCache-AutoCleanup");
			t.setDaemon(true);
			return t;
		});

		cleanupExecutor.scheduleAtFixedRate(
			() -> cleanupExpiredEntries(cacheManager),
			intervalMinutes,
			intervalMinutes,
			TimeUnit.MINUTES
		);
	}

	/**
	 * Cleans up expired entries from all caches.
	 *
	 * @param cacheManager the cache manager
	 */
	private void cleanupExpiredEntries(SBCacheManager cacheManager) {
		cacheManager.getAllCaches().forEach((name, cache) -> {
			if (cache instanceof org.scriptonbasestar.cache.spring.SBCache) {
				org.scriptonbasestar.cache.spring.SBCache sbCache =
					(org.scriptonbasestar.cache.spring.SBCache) cache;
				sbCache.getCacheMap().removeExpired();
			}
		});
	}

	/**
	 * Shutdown hook for cleanup executor.
	 */
	@Bean
	public AutoCleanupShutdownHook autoCleanupShutdownHook() {
		return new AutoCleanupShutdownHook(this);
	}

	/**
	 * Shuts down the auto-cleanup executor.
	 */
	void shutdownCleanupExecutor() {
		if (cleanupExecutor != null && !cleanupExecutor.isShutdown()) {
			cleanupExecutor.shutdown();
			try {
				if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
					cleanupExecutor.shutdownNow();
				}
			} catch (InterruptedException e) {
				cleanupExecutor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Shutdown hook for auto-cleanup executor.
	 */
	static class AutoCleanupShutdownHook {
		private final SBCacheAutoConfiguration config;

		AutoCleanupShutdownHook(SBCacheAutoConfiguration config) {
			this.config = config;
		}

		public void destroy() {
			config.shutdownCleanupExecutor();
		}
	}
}
