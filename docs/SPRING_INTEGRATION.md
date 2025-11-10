# Spring Integration Guide

SB Cached Collection의 Spring Framework 통합 가이드입니다.

## 목차

1. [개요](#개요)
2. [의존성 추가](#의존성-추가)
3. [CacheManager 설정](#cachemanager-설정)
4. [@Cacheable 어노테이션 사용](#cacheable-어노테이션-사용)
5. [Spring Boot Auto-Configuration](#spring-boot-auto-configuration)
6. [고급 설정](#고급-설정)
7. [모니터링 & Actuator](#모니터링--actuator)
8. [실전 예제](#실전-예제)

---

## 개요

SB Cached Collection은 Spring Cache Abstraction과 완벽하게 통합됩니다:

- ✅ **Spring CacheManager 구현**: `SBCacheManager`
- ✅ **Spring Cache 구현**: `SBCache`
- ✅ **Spring Boot Auto-Configuration**: `@EnableAutoConfiguration`으로 자동 설정
- ✅ **Actuator 통합**: Health, Metrics, Cache 관리 엔드포인트

---

## 의존성 추가

### Maven

```xml
<dependencies>
    <!-- SB Cached Collection Spring 통합 -->
    <dependency>
        <groupId>org.scriptonbasestar.cache</groupId>
        <artifactId>cache-spring</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- Spring Boot Starter Cache (선택 사항) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-cache</artifactId>
    </dependency>

    <!-- Spring Boot Actuator (모니터링용, 선택 사항) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

### Gradle

```gradle
dependencies {
    implementation 'org.scriptonbasestar.cache:cache-spring:1.0.0-SNAPSHOT'
    implementation 'org.springframework.boot:spring-boot-starter-cache'
    implementation 'org.springframework.boot:spring-boot-starter-actuator' // 선택 사항
}
```

---

## CacheManager 설정

### 1. 기본 설정 (Java Config)

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new SBCacheManager()
            .addCache("users", createUserCache())
            .addCache("products", createProductCache());
    }

    private SBCacheMap<Object, Object> createUserCache() {
        return SBCacheMap.<Object, Object>builder()
            .timeoutSec(300)  // 5분 TTL
            .maxSize(1000)
            .evictionPolicy(EvictionPolicy.LRU)
            .enableMetrics(true)
            .build();
    }

    private SBCacheMap<Object, Object> createProductCache() {
        return SBCacheMap.<Object, Object>builder()
            .timeoutSec(600)  // 10분 TTL
            .maxSize(5000)
            .referenceType(ReferenceType.SOFT)  // 메모리 압박 시 자동 회수
            .enableMetrics(true)
            .build();
    }
}
```

### 2. Loader와 함께 사용

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Bean
    public CacheManager cacheManager() {
        return new SBCacheManager()
            .addCache("users", createUserCache())
            .addCache("products", createProductCache());
    }

    private SBCacheMap<Object, Object> createUserCache() {
        return SBCacheMap.<Object, Object>builder()
            .loader(new SBCacheMapLoader<Object, Object>() {
                @Override
                public Object loadOne(Object key) throws SBCacheLoadFailException {
                    Long userId = (Long) key;
                    return userRepository.findById(userId)
                        .orElseThrow(() -> new SBCacheLoadFailException("User not found: " + userId));
                }

                @Override
                public Map<Object, Object> loadAll() throws SBCacheLoadFailException {
                    List<User> users = userRepository.findAll();
                    return users.stream()
                        .collect(Collectors.toMap(User::getId, user -> user));
                }
            })
            .timeoutSec(300)
            .maxSize(1000)
            .loadStrategy(LoadStrategy.ASYNC)  // 비동기 로딩
            .build();
    }

    private SBCacheMap<Object, Object> createProductCache() {
        return SBCacheMap.<Object, Object>builder()
            .loader(new SBCacheMapLoader<Object, Object>() {
                @Override
                public Object loadOne(Object key) throws SBCacheLoadFailException {
                    String productId = (String) key;
                    return productRepository.findById(productId)
                        .orElseThrow(() -> new SBCacheLoadFailException("Product not found: " + productId));
                }

                @Override
                public Map<Object, Object> loadAll() throws SBCacheLoadFailException {
                    List<Product> products = productRepository.findAll();
                    return products.stream()
                        .collect(Collectors.toMap(Product::getId, product -> product));
                }
            })
            .timeoutSec(600)
            .maxSize(5000)
            .referenceType(ReferenceType.SOFT)
            .refreshStrategy(RefreshStrategy.REFRESH_AHEAD)  // 미리 갱신
            .build();
    }
}
```

### 3. Write-Through/Write-Behind 설정

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Autowired
    private UserRepository userRepository;

    @Bean
    public CacheManager cacheManager() {
        return new SBCacheManager()
            .addCache("users", createWriteThroughCache());
    }

    private SBCacheMap<Object, Object> createWriteThroughCache() {
        return SBCacheMap.<Object, Object>builder()
            .loader(createUserLoader())
            .writer(new SBCacheMapWriter<Object, Object>() {
                @Override
                public void writeOne(Object key, Object value) throws SBCacheWriteFailException {
                    User user = (User) value;
                    userRepository.save(user);
                }

                @Override
                public void writeAll(Map<Object, Object> entries) throws SBCacheWriteFailException {
                    List<User> users = entries.values().stream()
                        .map(obj -> (User) obj)
                        .collect(Collectors.toList());
                    userRepository.saveAll(users);
                }

                @Override
                public void deleteOne(Object key) throws SBCacheWriteFailException {
                    Long userId = (Long) key;
                    userRepository.deleteById(userId);
                }

                @Override
                public void deleteAll(Collection<Object> keys) throws SBCacheWriteFailException {
                    List<Long> userIds = keys.stream()
                        .map(key -> (Long) key)
                        .collect(Collectors.toList());
                    userRepository.deleteAllById(userIds);
                }
            })
            .writeStrategy(WriteStrategy.WRITE_THROUGH)  // 동기 쓰기
            .timeoutSec(300)
            .maxSize(1000)
            .build();
    }

    private SBCacheMapLoader<Object, Object> createUserLoader() {
        return new SBCacheMapLoader<Object, Object>() {
            @Override
            public Object loadOne(Object key) throws SBCacheLoadFailException {
                Long userId = (Long) key;
                return userRepository.findById(userId)
                    .orElseThrow(() -> new SBCacheLoadFailException("User not found: " + userId));
            }

            @Override
            public Map<Object, Object> loadAll() throws SBCacheLoadFailException {
                List<User> users = userRepository.findAll();
                return users.stream()
                    .collect(Collectors.toMap(User::getId, user -> user));
            }
        };
    }
}
```

---

## @Cacheable 어노테이션 사용

### 1. 기본 사용법

```java
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Cacheable(value = "users", key = "#id")
    public User getUserById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
    }

    @Cacheable(value = "users", key = "#email")
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + email));
    }

    @CachePut(value = "users", key = "#user.id")
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    @CacheEvict(value = "users", allEntries = true)
    public void deleteAllUsers() {
        userRepository.deleteAll();
    }
}
```

### 2. SpEL 표현식 활용

```java
@Service
public class ProductService {

    @Cacheable(value = "products",
               key = "#category + ':' + #page + ':' + #size",
               condition = "#page < 10")  // 10페이지 이하만 캐싱
    public List<Product> getProductsByCategory(String category, int page, int size) {
        return productRepository.findByCategory(category, PageRequest.of(page, size));
    }

    @Cacheable(value = "products",
               key = "#root.methodName + ':' + #filter.category",
               unless = "#result.size() == 0")  // 빈 결과는 캐싱 안 함
    public List<Product> searchProducts(ProductFilter filter) {
        return productRepository.search(filter);
    }
}
```

### 3. 조건부 캐싱

```java
@Service
public class OrderService {

    @Cacheable(value = "orders",
               key = "#userId",
               condition = "#userId != null && #status == 'COMPLETED'")
    public List<Order> getUserOrders(Long userId, String status) {
        return orderRepository.findByUserIdAndStatus(userId, status);
    }

    @CachePut(value = "orders",
              key = "#order.id",
              unless = "#order.status == 'CANCELLED'")  // CANCELLED 주문은 캐싱 안 함
    public Order updateOrder(Order order) {
        return orderRepository.save(order);
    }
}
```

---

## Spring Boot Auto-Configuration

### 1. application.yml 설정

```yaml
sb:
  cache:
    enabled: true
    caches:
      users:
        timeout-sec: 300
        max-size: 1000
        eviction-policy: LRU
        reference-type: STRONG
        enable-metrics: true
      products:
        timeout-sec: 600
        max-size: 5000
        eviction-policy: LRU
        reference-type: SOFT
        enable-metrics: true
      sessions:
        timeout-sec: 1800  # 30분
        max-size: 10000
        eviction-policy: TTL
        reference-type: WEAK

# Actuator 엔드포인트 활성화
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,caches
  endpoint:
    health:
      show-details: always
```

### 2. application.properties 설정

```properties
# SB Cache 기본 설정
sb.cache.enabled=true

# Users 캐시
sb.cache.caches.users.timeout-sec=300
sb.cache.caches.users.max-size=1000
sb.cache.caches.users.eviction-policy=LRU
sb.cache.caches.users.reference-type=STRONG
sb.cache.caches.users.enable-metrics=true

# Products 캐시
sb.cache.caches.products.timeout-sec=600
sb.cache.caches.products.max-size=5000
sb.cache.caches.products.eviction-policy=LRU
sb.cache.caches.products.reference-type=SOFT
sb.cache.caches.products.enable-metrics=true

# Actuator
management.endpoints.web.exposure.include=health,metrics,caches
management.endpoint.health.show-details=always
```

### 3. Auto-Configuration 활성화

```java
@SpringBootApplication
@EnableCaching  // 필수
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

Auto-Configuration이 자동으로 `SBCacheManager`를 생성하고 `application.yml`의 설정을 적용합니다.

---

## 고급 설정

### 1. 다중 CacheManager 사용

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @Primary
    public CacheManager primaryCacheManager() {
        return new SBCacheManager()
            .addCache("users", createUserCache());
    }

    @Bean("secondaryCacheManager")
    public CacheManager secondaryCacheManager() {
        return new SBCacheManager()
            .addCache("reports", createReportCache());
    }
}

@Service
public class UserService {

    @Cacheable(value = "users", cacheManager = "primaryCacheManager")
    public User getUser(Long id) {
        // ...
    }
}

@Service
public class ReportService {

    @Cacheable(value = "reports", cacheManager = "secondaryCacheManager")
    public Report getReport(String id) {
        // ...
    }
}
```

### 2. 커스텀 KeyGenerator

```java
@Configuration
public class CacheConfig {

    @Bean
    public KeyGenerator customKeyGenerator() {
        return (target, method, params) -> {
            StringBuilder key = new StringBuilder();
            key.append(target.getClass().getSimpleName());
            key.append(":");
            key.append(method.getName());
            for (Object param : params) {
                key.append(":").append(param);
            }
            return key.toString();
        };
    }
}

@Service
public class UserService {

    @Cacheable(value = "users", keyGenerator = "customKeyGenerator")
    public User getUser(Long id, String role) {
        // key: "UserService:getUser:123:ADMIN"
        // ...
    }
}
```

### 3. CacheResolver 커스터마이즈

```java
@Configuration
public class CacheConfig {

    @Bean
    public CacheResolver customCacheResolver(CacheManager cacheManager) {
        return context -> {
            String cacheName;
            Object[] args = context.getArgs();

            // 동적으로 캐시 선택
            if (args.length > 0 && args[0] instanceof String) {
                String type = (String) args[0];
                cacheName = type.equals("premium") ? "premiumCache" : "normalCache";
            } else {
                cacheName = "defaultCache";
            }

            return Collections.singleton(cacheManager.getCache(cacheName));
        };
    }
}

@Service
public class ContentService {

    @Cacheable(cacheResolver = "customCacheResolver")
    public Content getContent(String type, Long id) {
        // type에 따라 premiumCache 또는 normalCache 사용
        // ...
    }
}
```

### 4. 캐시 워밍업 (Cache Warming)

```java
@Component
public class CacheWarmer {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private UserRepository userRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpCache() {
        Cache userCache = cacheManager.getCache("users");
        if (userCache instanceof SBCache) {
            SBCache sbCache = (SBCache) userCache;

            // 전체 데이터 로드
            List<User> users = userRepository.findAll();
            for (User user : users) {
                sbCache.put(user.getId(), user);
            }

            System.out.println("User cache warmed up with " + users.size() + " entries");
        }
    }
}
```

---

## 모니터링 & Actuator

### 1. Health Indicator

Spring Boot Actuator가 있으면 자동으로 캐시 헬스 체크 엔드포인트 제공:

**GET /actuator/health**

```json
{
  "status": "UP",
  "components": {
    "sbCache": {
      "status": "UP",
      "details": {
        "users": {
          "status": "UP",
          "size": 234,
          "maxSize": 1000,
          "hitRate": 0.87,
          "missRate": 0.13
        },
        "products": {
          "status": "UP",
          "size": 1523,
          "maxSize": 5000,
          "hitRate": 0.92,
          "missRate": 0.08
        }
      }
    }
  }
}
```

### 2. Metrics

**GET /actuator/metrics/cache.gets?tag=cache:users**

```json
{
  "name": "cache.gets",
  "measurements": [
    { "statistic": "COUNT", "value": 1523 }
  ],
  "availableTags": [
    { "tag": "result", "values": ["hit", "miss"] },
    { "tag": "cache", "values": ["users", "products"] }
  ]
}
```

**GET /actuator/metrics/cache.size?tag=cache:users**

```json
{
  "name": "cache.size",
  "measurements": [
    { "statistic": "VALUE", "value": 234 }
  ]
}
```

### 3. 캐시 통계 조회

```java
@RestController
@RequestMapping("/admin/cache")
public class CacheAdminController {

    @Autowired
    private CacheManager cacheManager;

    @GetMapping("/stats")
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();

        if (cacheManager instanceof SBCacheManager) {
            SBCacheManager sbCacheManager = (SBCacheManager) cacheManager;

            for (Map.Entry<String, Cache> entry : sbCacheManager.getAllCaches().entrySet()) {
                String cacheName = entry.getKey();
                Cache cache = entry.getValue();

                if (cache instanceof SBCache) {
                    SBCache sbCache = (SBCache) cache;
                    SBCacheMap<?, ?> cacheMap = sbCache.getCacheMap();

                    Map<String, Object> cacheStats = new HashMap<>();
                    cacheStats.put("size", cacheMap.size());
                    cacheStats.put("maxSize", cacheMap.getMaxSize());
                    cacheStats.put("hitRate", cacheMap.getHitRate());
                    cacheStats.put("missRate", cacheMap.getMissRate());
                    cacheStats.put("loadCount", cacheMap.getLoadCount());
                    cacheStats.put("totalLoadTime", cacheMap.getTotalLoadTime());

                    stats.put(cacheName, cacheStats);
                }
            }
        }

        return stats;
    }

    @DeleteMapping("/{cacheName}")
    public void clearCache(@PathVariable String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    @DeleteMapping
    public void clearAllCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }
}
```

---

## 실전 예제

### 예제 1: REST API 캐싱

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getUserById(id);  // @Cacheable 적용됨
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
        return userService.createUser(user);  // @CachePut 적용
    }

    @PutMapping("/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        return userService.updateUser(user);  // @CachePut 적용
    }

    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);  // @CacheEvict 적용
    }
}

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Cacheable(value = "users", key = "#id")
    public User getUserById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
    }

    @CachePut(value = "users", key = "#result.id")
    public User createUser(User user) {
        return userRepository.save(user);
    }

    @CachePut(value = "users", key = "#user.id")
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
```

### 예제 2: 페이지네이션 캐싱

```java
@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Cacheable(value = "productPages",
               key = "#category + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<Product> getProducts(String category, Pageable pageable) {
        return productRepository.findByCategory(category, pageable);
    }

    @Cacheable(value = "productSearch",
               key = "#query + ':' + #page",
               condition = "#query.length() > 2")  // 3글자 이상만 캐싱
    public List<Product> searchProducts(String query, int page) {
        return productRepository.search(query, PageRequest.of(page, 20));
    }
}

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new SBCacheManager()
            .addCache("productPages", SBCacheMap.<Object, Object>builder()
                .timeoutSec(600)  // 10분
                .maxSize(100)     // 100페이지까지
                .evictionPolicy(EvictionPolicy.LRU)
                .build())
            .addCache("productSearch", SBCacheMap.<Object, Object>builder()
                .timeoutSec(300)  // 5분
                .maxSize(500)
                .evictionPolicy(EvictionPolicy.LFU)  // 인기 검색어 우선
                .build());
    }
}
```

### 예제 3: 조건부 캐싱 & 다이나믹 TTL

```java
@Service
public class PricingService {

    @Autowired
    private PriceRepository priceRepository;

    @Cacheable(value = "prices",
               key = "#productId + ':' + #region",
               condition = "#cacheEnabled && !#isPremiumUser",
               unless = "#result == null || #result.isExpired()")
    public Price getPrice(String productId, String region,
                         boolean isPremiumUser, boolean cacheEnabled) {
        return priceRepository.findPrice(productId, region);
    }
}

@Configuration
public class PriceCacheConfig {

    @Bean
    public CacheManager priceCacheManager() {
        return new SBCacheManager()
            .addCache("prices", SBCacheMap.<Object, Object>builder()
                .timeoutSec(60)  // 기본 1분
                .maxSize(10000)
                .referenceType(ReferenceType.SOFT)  // 메모리 압박 시 자동 회수
                .refreshStrategy(RefreshStrategy.REFRESH_AHEAD)  // 만료 전 갱신
                .build());
    }
}
```

### 예제 4: 이벤트 기반 캐시 무효화

```java
@Component
public class CacheInvalidationListener {

    @Autowired
    private CacheManager cacheManager;

    @EventListener
    public void handleUserUpdated(UserUpdatedEvent event) {
        Cache userCache = cacheManager.getCache("users");
        if (userCache != null) {
            userCache.evict(event.getUserId());
        }
    }

    @EventListener
    public void handleProductPriceChanged(ProductPriceChangedEvent event) {
        Cache priceCache = cacheManager.getCache("prices");
        if (priceCache != null) {
            // 해당 제품의 모든 리전 가격 캐시 무효화
            priceCache.clear();  // 또는 선택적으로 evict()
        }
    }
}

@Service
public class UserService {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public User updateUser(User user) {
        User updated = userRepository.save(user);

        // 이벤트 발행 → 캐시 무효화
        eventPublisher.publishEvent(new UserUpdatedEvent(updated.getId()));

        return updated;
    }
}
```

---

## 트러블슈팅

### 1. @Cacheable이 동작하지 않음

**증상**: 메서드가 매번 실행되고 캐시가 사용되지 않음

**원인**:
- `@EnableCaching`이 누락됨
- 같은 클래스 내부에서 메서드 호출 (Spring AOP 프록시 우회)
- 캐시 키가 잘못 설정됨

**해결**:
```java
// ❌ 잘못된 예 (내부 호출)
@Service
public class UserService {

    public User getUser(Long id) {
        return getUserFromCache(id);  // 프록시를 거치지 않음!
    }

    @Cacheable("users")
    private User getUserFromCache(Long id) {
        return userRepository.findById(id).orElse(null);
    }
}

// ✅ 올바른 예
@Service
public class UserService {

    @Cacheable("users")
    public User getUser(Long id) {
        return userRepository.findById(id).orElse(null);
    }
}
```

### 2. 메모리 누수

**증상**: 메모리 사용량이 계속 증가

**원인**:
- maxSize가 설정되지 않음 (무제한 캐시)
- TTL이 너무 김
- ReferenceType이 STRONG으로 고정

**해결**:
```java
SBCacheMap.<Object, Object>builder()
    .timeoutSec(300)  // 5분 TTL
    .maxSize(10000)   // 최대 10,000개
    .referenceType(ReferenceType.SOFT)  // 메모리 압박 시 자동 회수
    .evictionPolicy(EvictionPolicy.LRU)
    .build()
```

### 3. 동시성 문제

**증상**: 동일한 키에 대해 여러 번 로딩이 발생

**원인**: 캐시 미스 시 여러 스레드가 동시에 로딩 시도

**해결**: LoadStrategy.ASYNC 사용
```java
SBCacheMap.<Object, Object>builder()
    .loader(myLoader)
    .loadStrategy(LoadStrategy.ASYNC)  // 한 스레드만 로딩
    .build()
```

---

## 참고 자료

- [Spring Framework Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [USER_GUIDE.md](USER_GUIDE.md) - SB Cached Collection 종합 가이드
