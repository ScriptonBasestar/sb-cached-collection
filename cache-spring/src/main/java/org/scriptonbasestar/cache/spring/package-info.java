/**
 * Spring Framework 통합 모듈
 *
 * <p>SB Cache를 Spring의 Cache 추상화와 통합합니다.
 * Spring의 {@code @Cacheable}, {@code @CacheEvict}, {@code @CachePut} 어노테이션을 지원합니다.</p>
 *
 * <h2>주요 클래스</h2>
 * <ul>
 *     <li>{@link org.scriptonbasestar.cache.spring.SBCacheManager} - Spring CacheManager 구현체</li>
 *     <li>{@link org.scriptonbasestar.cache.spring.SBCache} - Spring Cache 래퍼</li>
 * </ul>
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * @Configuration
 * @EnableCaching
 * public class CacheConfig {
 *
 *     @Bean
 *     public CacheManager cacheManager() {
 *         return new SBCacheManager()
 *             .addCache("users", SBCacheMap.<Object, Object>builder()
 *                 .loader(userLoader)
 *                 .timeoutSec(300)
 *                 .enableMetrics(true)
 *                 .build())
 *             .addCache("products", SBCacheMap.<Object, Object>builder()
 *                 .loader(productLoader)
 *                 .timeoutSec(600)
 *                 .build());
 *     }
 * }
 *
 * @Service
 * public class UserService {
 *
 *     @Cacheable("users")
 *     public User getUser(Long id) {
 *         return userRepository.findById(id);
 *     }
 *
 *     @CacheEvict("users")
 *     public void updateUser(User user) {
 *         userRepository.save(user);
 *     }
 *
 *     @CachePut(value = "users", key = "#user.id")
 *     public User saveUser(User user) {
 *         return userRepository.save(user);
 *     }
 * }
 * }</pre>
 *
 * @since 2025-01
 * @author archmagece
 */
package org.scriptonbasestar.cache.spring;
