package org.scriptonbasestar.cache.collection.jmx;

import org.scriptonbasestar.cache.collection.metrics.CacheMetrics;

import javax.management.*;
import java.lang.management.ManagementFactory;

/**
 * Helper class for JMX MBean registration and management.
 * <p>
 * This utility class simplifies the process of registering cache statistics
 * with the platform MBeanServer for monitoring in JConsole/VisualVM.
 * </p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Simple MBean registration with automatic naming</li>
 *   <li>Safe unregistration with error handling</li>
 *   <li>Standardized ObjectName pattern</li>
 *   <li>Duplicate registration detection</li>
 * </ul>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * // Register cache with JMX
 * CacheMetrics metrics = new CacheMetrics();
 * CacheStatistics mbean = JmxHelper.registerCache(metrics, "users");
 *
 * // Monitor in JConsole:
 * // MBeans -> org.scriptonbasestar.cache -> SBCacheMap -> users
 *
 * // Unregister when done
 * JmxHelper.unregisterCache("users");
 * }</pre>
 *
 * <h3>ObjectName Pattern:</h3>
 * <pre>
 * org.scriptonbasestar.cache:type=SBCacheMap,name={cacheName}
 * </pre>
 *
 * @author archmagece
 * @since 2025-01
 */
public final class JmxHelper {

	private static final String DOMAIN = "org.scriptonbasestar.cache";
	private static final String TYPE = "SBCacheMap";

	private JmxHelper() {
		// Utility class
	}

	/**
	 * Registers a cache with JMX using the provided metrics.
	 * <p>
	 * Creates a {@link CacheStatistics} MBean and registers it with the
	 * platform MBeanServer using a standardized ObjectName.
	 * </p>
	 *
	 * @param metrics    the cache metrics to expose
	 * @param cacheName  the cache name (used in ObjectName)
	 * @return the registered CacheStatistics instance
	 * @throws IllegalArgumentException if metrics or cacheName is null/empty
	 * @throws JmxRegistrationException if registration fails
	 */
	public static CacheStatistics registerCache(CacheMetrics metrics, String cacheName) {
		if (metrics == null) {
			throw new IllegalArgumentException("metrics must not be null");
		}
		if (cacheName == null || cacheName.trim().isEmpty()) {
			throw new IllegalArgumentException("cacheName must not be null or empty");
		}

		CacheStatistics mbean = new CacheStatistics(metrics, cacheName);

		try {
			MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
			ObjectName objectName = createObjectName(cacheName);

			// Unregister if already exists
			if (mbs.isRegistered(objectName)) {
				mbs.unregisterMBean(objectName);
			}

			mbs.registerMBean(mbean, objectName);
			return mbean;

		} catch (JMException e) {
			throw new JmxRegistrationException("Failed to register JMX MBean for cache: " + cacheName, e);
		}
	}

	/**
	 * Registers a cache with JMX, including size information.
	 * <p>
	 * This is a convenience method that sets maxSize on the MBean
	 * before registration.
	 * </p>
	 *
	 * @param metrics    the cache metrics to expose
	 * @param cacheName  the cache name
	 * @param maxSize    the maximum cache size (-1 for unlimited)
	 * @return the registered CacheStatistics instance
	 * @throws IllegalArgumentException if metrics or cacheName is null/empty
	 * @throws JmxRegistrationException if registration fails
	 */
	public static CacheStatistics registerCache(CacheMetrics metrics, String cacheName, int maxSize) {
		CacheStatistics mbean = registerCache(metrics, cacheName);
		mbean.setMaxSize(maxSize);
		return mbean;
	}

	/**
	 * Unregisters a cache from JMX.
	 * <p>
	 * This method is safe to call even if the cache is not registered.
	 * Any errors during unregistration are silently ignored.
	 * </p>
	 *
	 * @param cacheName the cache name to unregister
	 */
	public static void unregisterCache(String cacheName) {
		if (cacheName == null || cacheName.trim().isEmpty()) {
			return;
		}

		try {
			MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
			ObjectName objectName = createObjectName(cacheName);

			if (mbs.isRegistered(objectName)) {
				mbs.unregisterMBean(objectName);
			}
		} catch (Exception e) {
			// Silently ignore unregistration errors
		}
	}

	/**
	 * Checks if a cache is registered with JMX.
	 *
	 * @param cacheName the cache name to check
	 * @return true if registered, false otherwise
	 */
	public static boolean isRegistered(String cacheName) {
		if (cacheName == null || cacheName.trim().isEmpty()) {
			return false;
		}

		try {
			MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
			ObjectName objectName = createObjectName(cacheName);
			return mbs.isRegistered(objectName);
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Creates a standardized ObjectName for a cache.
	 * <p>
	 * Pattern: org.scriptonbasestar.cache:type=SBCacheMap,name={cacheName}
	 * </p>
	 *
	 * @param cacheName the cache name
	 * @return the ObjectName
	 * @throws MalformedObjectNameException if the name is invalid
	 */
	public static ObjectName createObjectName(String cacheName) throws MalformedObjectNameException {
		// Sanitize cache name for ObjectName (replace invalid characters)
		String safeName = cacheName.replaceAll("[,=:\"\\*\\?]", "_");
		return new ObjectName(DOMAIN + ":type=" + TYPE + ",name=" + safeName);
	}

	/**
	 * Exception thrown when JMX registration fails.
	 */
	public static class JmxRegistrationException extends RuntimeException {
		public JmxRegistrationException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
