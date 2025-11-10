package org.scriptonbasestar.cache.spring.boot;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for SB Cache.
 * <p>
 * Bind to {@code sb-cache.*} properties in application.yml/properties.
 * </p>
 *
 * <h3>Example Configuration:</h3>
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
public class SBCacheProperties {

	/**
	 * Default TTL in seconds for all caches.
	 */
	private int defaultTtl = 60;

	/**
	 * Enable metrics collection for all caches.
	 */
	private boolean enableMetrics = false;

	/**
	 * Enable JMX monitoring for all caches.
	 */
	private boolean enableJmx = false;

	/**
	 * Default maximum cache size (0 = unlimited).
	 */
	private int maxSize = 0;

	/**
	 * Auto cleanup configuration.
	 */
	private AutoCleanup autoCleanup = new AutoCleanup();

	/**
	 * Per-cache configurations.
	 */
	private Map<String, CacheConfig> caches = new HashMap<>();

	// Getters and Setters

	public int getDefaultTtl() {
		return defaultTtl;
	}

	public void setDefaultTtl(int defaultTtl) {
		this.defaultTtl = defaultTtl;
	}

	public boolean isEnableMetrics() {
		return enableMetrics;
	}

	public void setEnableMetrics(boolean enableMetrics) {
		this.enableMetrics = enableMetrics;
	}

	public boolean isEnableJmx() {
		return enableJmx;
	}

	public void setEnableJmx(boolean enableJmx) {
		this.enableJmx = enableJmx;
	}

	public int getMaxSize() {
		return maxSize;
	}

	public void setMaxSize(int maxSize) {
		this.maxSize = maxSize;
	}

	public AutoCleanup getAutoCleanup() {
		return autoCleanup;
	}

	public void setAutoCleanup(AutoCleanup autoCleanup) {
		this.autoCleanup = autoCleanup;
	}

	public Map<String, CacheConfig> getCaches() {
		return caches;
	}

	public void setCaches(Map<String, CacheConfig> caches) {
		this.caches = caches;
	}

	/**
	 * Auto cleanup configuration.
	 */
	public static class AutoCleanup {
		/**
		 * Enable automatic cleanup of expired entries.
		 */
		private boolean enabled = false;

		/**
		 * Cleanup interval in minutes.
		 */
		private int intervalMinutes = 5;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public int getIntervalMinutes() {
			return intervalMinutes;
		}

		public void setIntervalMinutes(int intervalMinutes) {
			this.intervalMinutes = intervalMinutes;
		}
	}

	/**
	 * Per-cache configuration.
	 */
	public static class CacheConfig {
		/**
		 * Cache-specific TTL in seconds (overrides default).
		 */
		private Integer ttl;

		/**
		 * Cache-specific max size (overrides default).
		 */
		private Integer maxSize;

		/**
		 * Forced timeout in seconds (absolute expiration).
		 */
		private Integer forcedTimeout;

		/**
		 * Enable metrics for this cache (overrides default).
		 */
		private Boolean enableMetrics;

		/**
		 * Enable JMX for this cache (overrides default).
		 */
		private Boolean enableJmx;

		public Integer getTtl() {
			return ttl;
		}

		public void setTtl(Integer ttl) {
			this.ttl = ttl;
		}

		public Integer getMaxSize() {
			return maxSize;
		}

		public void setMaxSize(Integer maxSize) {
			this.maxSize = maxSize;
		}

		public Integer getForcedTimeout() {
			return forcedTimeout;
		}

		public void setForcedTimeout(Integer forcedTimeout) {
			this.forcedTimeout = forcedTimeout;
		}

		public Boolean getEnableMetrics() {
			return enableMetrics;
		}

		public void setEnableMetrics(Boolean enableMetrics) {
			this.enableMetrics = enableMetrics;
		}

		public Boolean getEnableJmx() {
			return enableJmx;
		}

		public void setEnableJmx(Boolean enableJmx) {
			this.enableJmx = enableJmx;
		}

		/**
		 * Apply defaults from global properties.
		 *
		 * @param defaults global properties
		 */
		public void applyDefaults(SBCacheProperties defaults) {
			if (ttl == null) {
				ttl = defaults.getDefaultTtl();
			}
			if (maxSize == null) {
				maxSize = defaults.getMaxSize();
			}
			if (enableMetrics == null) {
				enableMetrics = defaults.isEnableMetrics();
			}
			if (enableJmx == null) {
				enableJmx = defaults.isEnableJmx();
			}
		}
	}
}
