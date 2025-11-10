# SB Cache - Spring Integration

Spring Framework와 Spring Boot를 위한 SB Cache 통합 모듈입니다.

## Features

- ✅ Spring Cache 추상화 (`@Cacheable`, `@CacheEvict`, `@CachePut`) 완전 지원
- ✅ Spring Boot Actuator Health Check 통합
- ✅ CacheManager 구현체 제공
- ✅ 메트릭 및 모니터링 지원
- ✅ 다중 캐시 관리

## Maven Dependency

```xml
<dependency>
    <groupId>org.scriptonbasestar.cache</groupId>
    <artifactId>cache-spring</artifactId>
    <version>sb-cache-20251107-1-DEV</version>
</dependency>
```

## Quick Start

### 1. Basic Configuration

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SBCacheMapLoader<Object, Object> userLoader = key -> {
            // Load from database
            return userRepository.findById((Long) key).orElse(null);
        };

        SBCacheMapLoader<Object, Object> productLoader = key -> {
            return productRepository.findById((Long) key).orElse(null);
        };

        return new SBCacheManager()
            .addCache("users", SBCacheMap.<Object, Object>builder()
                .loader(userLoader)
                .timeoutSec(300)          // 5 minutes TTL
                .maxSize(1000)            // LRU eviction
                .enableMetrics(true)      // Enable metrics
                .enableJmx("users")       // Enable JMX
                .build())
            .addCache("products", SBCacheMap.<Object, Object>builder()
                .loader(productLoader)
                .timeoutSec(600)          // 10 minutes TTL
                .enableMetrics(true)
                .build());
    }
}
```

### 2. Using Cache Annotations

```java
@Service
public class UserService {

    @Cacheable("users")
    public User getUser(Long id) {
        // This method will be cached
        return userRepository.findById(id).orElse(null);
    }

    @CachePut(value = "users", key = "#user.id")
    public User updateUser(User user) {
        // Always executed, result is cached
        return userRepository.save(user);
    }

    @CacheEvict("users")
    public void deleteUser(Long id) {
        // Evicts the cache entry
        userRepository.deleteById(id);
    }

    @CacheEvict(value = "users", allEntries = true)
    public void deleteAllUsers() {
        // Clears entire cache
        userRepository.deleteAll();
    }
}
```

### 3. Spring Boot Actuator Health Check

#### Configuration

```java
@Configuration
public class HealthCheckConfig {

    @Bean
    public CacheHealthIndicator userCacheHealthIndicator(
            SBCacheManager cacheManager) {
        SBCache cache = (SBCache) cacheManager.getCache("users");
        return new CacheHealthIndicator("users", cache.getCacheMap().metrics());
    }

    // Or use CompositeCacheHealthIndicator for all caches
    @Bean
    public CompositeCacheHealthIndicator cacheHealthIndicator(
            SBCacheManager cacheManager) {
        Map<String, CacheMetrics> metricsMap = new HashMap<>();

        for (String cacheName : cacheManager.getCacheNames()) {
            SBCache cache = (SBCache) cacheManager.getCache(cacheName);
            metricsMap.put(cacheName, cache.getCacheMap().metrics());
        }

        return new CompositeCacheHealthIndicator(metricsMap);
    }
}
```

#### Enable Health Endpoint

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
```

#### Access Health Endpoint

```bash
# Check overall health
curl http://localhost:8080/actuator/health

# Response:
{
  "status": "UP",
  "components": {
    "cacheHealthIndicator": {
      "status": "UP",
      "details": {
        "totalCaches": 2,
        "healthyCaches": 2,
        "unhealthyCaches": 0,
        "totalRequests": 5000,
        "overallHitRate": "87.50%",
        "caches": {
          "users": { "status": "UP", ... },
          "products": { "status": "UP", ... }
        }
      }
    }
  }
}
```

## Advanced Usage

### Custom TTL per Item

```java
@Service
public class UserService {

    @Autowired
    private CacheManager cacheManager;

    public void saveUserWithCustomTTL(User user, int ttlSeconds) {
        SBCache cache = (SBCache) cacheManager.getCache("users");
        SBCacheMap<Object, Object> cacheMap = cache.getCacheMap();

        // Put with custom TTL
        cacheMap.put(user.getId(), user, ttlSeconds);
    }
}
```

### Cache Warmup

```java
@Component
public class CacheWarmer implements ApplicationRunner {

    @Autowired
    private CacheManager cacheManager;

    @Override
    public void run(ApplicationArguments args) {
        SBCache cache = (SBCache) cacheManager.getCache("users");
        SBCacheMap<Object, Object> cacheMap = cache.getCacheMap();

        // Warm up cache on startup
        cacheMap.warmUp();
    }
}
```

### Accessing Metrics

```java
@RestController
@RequestMapping("/cache")
public class CacheController {

    @Autowired
    private CacheManager cacheManager;

    @GetMapping("/stats/{cacheName}")
    public Map<String, Object> getCacheStats(@PathVariable String cacheName) {
        SBCache cache = (SBCache) cacheManager.getCache(cacheName);
        if (cache == null) {
            return Collections.emptyMap();
        }

        CacheMetrics metrics = cache.getCacheMap().metrics();
        if (metrics == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("requestCount", metrics.requestCount());
        stats.put("hitCount", metrics.hitCount());
        stats.put("missCount", metrics.missCount());
        stats.put("hitRate", metrics.hitRate());
        stats.put("loadSuccessCount", metrics.loadSuccessCount());
        stats.put("evictionCount", metrics.evictionCount());

        return stats;
    }
}
```

### JMX Monitoring

```java
@Bean
public CacheManager cacheManager() {
    return new SBCacheManager()
        .addCache("users", SBCacheMap.<Object, Object>builder()
            .loader(userLoader)
            .timeoutSec(300)
            .enableMetrics(true)
            .enableJmx("users")  // Enable JMX monitoring
            .build());
}

// Access via JConsole:
// MBeans → org.scriptonbasestar.cache → SBCacheMap → users
```

## Configuration Options

### SBCacheMap Builder Options

| Option | Description | Default |
|--------|-------------|---------|
| `loader()` | Cache loader function | Required |
| `timeoutSec()` | Access-based TTL in seconds | 60 |
| `forcedTimeoutSec()` | Absolute expiration time | 0 (disabled) |
| `maxSize()` | Maximum cache size (LRU) | 0 (unlimited) |
| `enableMetrics()` | Enable statistics collection | false |
| `enableAutoCleanup()` | Enable periodic cleanup | false |
| `cleanupIntervalMinutes()` | Cleanup interval | 5 |
| `loadStrategy()` | SYNC or ASYNC loading | SYNC |
| `enableJmx()` | Enable JMX monitoring | false |

### Health Check Thresholds

```java
@Bean
public CacheHealthIndicator cacheHealthIndicator(CacheMetrics metrics) {
    return new CacheHealthIndicator(
        "users",
        metrics,
        CacheHealthCheck.HealthThresholds.STRICT  // 80% hit rate required
    );
}

// Available thresholds:
// - HealthThresholds.DEFAULT: 60% hit rate, 10% failure rate
// - HealthThresholds.STRICT: 80% hit rate, 5% failure rate
// - HealthThresholds.RELAXED: 40% hit rate, 20% failure rate
```

## Testing

### Integration Test Example

```java
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class CacheIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private CacheManager cacheManager;

    @Test
    public void testCaching() {
        // First call - cache miss
        User user1 = userService.getUser(1L);

        // Second call - cache hit
        User user2 = userService.getUser(1L);

        assertSame(user1, user2);

        // Verify cache statistics
        SBCache cache = (SBCache) cacheManager.getCache("users");
        CacheMetrics metrics = cache.getCacheMap().metrics();

        assertEquals(2, metrics.requestCount());
        assertEquals(1, metrics.hitCount());
        assertEquals(1, metrics.missCount());
    }
}
```

## Best Practices

1. **Always enable metrics in production**
   ```java
   .enableMetrics(true)
   ```

2. **Set appropriate TTL based on data volatility**
   ```java
   .timeoutSec(300)  // 5 minutes for frequently changing data
   .timeoutSec(3600) // 1 hour for stable data
   ```

3. **Use maxSize to prevent memory issues**
   ```java
   .maxSize(10000)  // LRU eviction after 10k entries
   ```

4. **Enable JMX for production monitoring**
   ```java
   .enableJmx("cacheName")
   ```

5. **Monitor health via Actuator**
   ```yaml
   management:
     endpoint:
       health:
         show-details: always
   ```

## See Also

- [cache-collection](../cache-collection/README.md) - Core cache implementation
- [cache-metrics](../cache-metrics/README.md) - Prometheus/Micrometer integration
- Spring Cache Documentation: https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#cache
- Spring Boot Actuator: https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html
