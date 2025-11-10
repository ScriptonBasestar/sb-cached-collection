package org.scriptonbasestar.cache.spring.boot;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Enables SB Cache with Spring Boot Auto-Configuration.
 * <p>
 * This annotation combines {@link EnableCaching} with SB Cache auto-configuration,
 * allowing you to enable caching and configure SB Cache in one step.
 * </p>
 *
 * <h3>Basic Usage:</h3>
 * <pre>{@code
 * @SpringBootApplication
 * @EnableSBCache
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }</pre>
 *
 * <h3>With Configuration:</h3>
 * <pre>{@code
 * # application.yml
 * sb-cache:
 *   default-ttl: 300
 *   enable-metrics: true
 *   caches:
 *     users:
 *       ttl: 300
 *       max-size: 1000
 * }</pre>
 *
 * <h3>Programmatic Configuration:</h3>
 * <pre>{@code
 * @Configuration
 * @EnableSBCache
 * public class CacheConfig {
 *     @Bean
 *     public CacheManager cacheManager() {
 *         return new SBCacheManager()
 *             .addCache("users", SBCacheMap.<Object, Object>builder()
 *                 .loader(userLoader)
 *                 .timeoutSec(300)
 *                 .build());
 *     }
 * }
 * }</pre>
 *
 * <p>
 * If you define your own CacheManager bean, auto-configuration will back off
 * and use your custom configuration instead.
 * </p>
 *
 * @author archmagece
 * @since 2025-01
 * @see EnableCaching
 * @see SBCacheAutoConfiguration
 * @see SBCacheProperties
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableCaching
@Import(SBCacheAutoConfiguration.class)
public @interface EnableSBCache {
	/**
	 * Whether to enable proxy-based (false) or AspectJ-based (true) caching.
	 * <p>
	 * Default is false (proxy-based).
	 * </p>
	 *
	 * @return true to use AspectJ, false to use proxy
	 */
	boolean proxyTargetClass() default true;
}
