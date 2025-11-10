# SB Cache Java - 종합 사용 가이드

**버전**: sb-cache-20251107-1-DEV
**작성일**: 2025-11-10

---

## 📋 목차

1. [빠른 시작](#빠른-시작)
2. [핵심 개념](#핵심-개념)
3. [고급 기능](#고급-기능)
4. [Spring 통합](#spring-통합)
5. [성능 튜닝](#성능-튜닝)
6. [실전 예제](#실전-예제)
7. [모니터링](#모니터링)

---

## 🚀 빠른 시작

### Maven 의존성

```xml
<dependencies>
    <!-- 필수: 인메모리 캐시 -->
    <dependency>
        <groupId>org.scriptonbasestar.cache</groupId>
        <artifactId>cache-collection</artifactId>
        <version>sb-cache-20251107-1-DEV</version>
    </dependency>

    <!-- 선택: Spring 통합 -->
    <dependency>
        <groupId>org.scriptonbasestar.cache</groupId>
        <artifactId>cache-spring</artifactId>
        <version>sb-cache-20251107-1-DEV</version>
    </dependency>

    <!-- 선택: JDBC Loader -->
    <dependency>
        <groupId>org.scriptonbasestar.cache</groupId>
        <artifactId>cache-loader-jdbc</artifactId>
        <version>sb-cache-20251107-1-DEV</version>
    </dependency>
</dependencies>
```

### 가장 간단한 예제

```java
import org.scriptonbasestar.cache.collection.map.SBCacheMap;
import org.scriptonbasestar.cache.core.loader.SBCacheMapLoader;

// 1. 데이터 로더 구현
SBCacheMapLoader<Long, User> loader = new SBCacheMapLoader<>() {
    @Override
    public User loadOne(Long id) throws SBCacheLoadFailException {
        return userRepository.findById(id)
            .orElseThrow(() -> new SBCacheLoadFailException("User not found: " + id));
    }

    @Override
    public Map<Long, User> loadAll() throws SBCacheLoadFailException {
        return userRepository.findAll().stream()
            .collect(Collectors.toMap(User::getId, u -> u));
    }
};

// 2. 캐시 생성 (Builder 패턴 권장)
SBCacheMap<Long, User> userCache = SBCacheMap.<Long, User>builder()
    .loader(loader)
    .timeoutSec(300)  // 5분 TTL
    .build();

// 3. 사용
User user = userCache.get(1L);  // 첫 호출: DB 조회
User cached = userCache.get(1L);  // 캐시에서 반환 (빠름!)
```

---

## 💡 핵심 개념

### 1. TTL (Time To Live)

캐시 항목의 유효 시간을 설정합니다.

```java
SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
    .loader(loader)
    .timeoutSec(300)           // 기본 TTL: 5분
    .forcedTimeoutSec(3600)    // 절대 만료: 1시간 (조회 여부 무관)
    .build();

// 항목별 개별 TTL 설정
cache.put(key, value, 60);  // 이 항목만 1분 TTL
```

**TTL 동작 방식:**
- **Access-based TTL** (`timeoutSec`): 마지막 조회 시점부터 계산
- **Absolute TTL** (`forcedTimeoutSec`): 생성 시점부터 계산 (조회 여부 무관)

### 2. 로드 전략 (LoadStrategy)

캐시 미스 시 데이터를 어떻게 로드할지 결정합니다.

```java
// SYNC (기본값): 동기 로딩 - 데이터 로드 완료까지 대기
SBCacheMap<K, V> syncCache = SBCacheMap.<K, V>builder()
    .loader(loader)
    .loadStrategy(LoadStrategy.SYNC)
    .build();

// ASYNC: 비동기 로딩 - 기존 데이터 반환 + 백그라운드 갱신
SBCacheMap<K, V> asyncCache = SBCacheMap.<K, V>builder()
    .loader(loader)
    .loadStrategy(LoadStrategy.ASYNC)
    .build();
```

**비교:**

| 전략 | 첫 요청 | 만료 후 요청 | 데이터 신선도 | 응답 시간 |
|------|---------|--------------|---------------|-----------|
| SYNC | 느림 (로드 대기) | 느림 (재로드 대기) | 항상 최신 | 불안정 |
| ASYNC | 느림 (로드 대기) | 빠름 (이전 데이터 반환) | Eventual | 일정 |

### 3. 축출 정책 (EvictionPolicy)

캐시가 `maxSize`에 도달했을 때 어떤 항목을 제거할지 결정합니다.

```java
SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
    .loader(loader)
    .maxSize(1000)
    .evictionPolicy(EvictionPolicy.LRU)  // 가장 오래 사용되지 않은 항목 제거
    .build();
```

**사용 가능한 정책:**

| 정책 | 설명 | 사용 케이스 |
|------|------|-------------|
| **LRU** (기본값) | Least Recently Used - 가장 오래 사용되지 않은 항목 | 일반적인 캐시 |
| **LFU** | Least Frequently Used - 가장 적게 사용된 항목 | 인기도 기반 캐시 |
| **FIFO** | First In First Out - 가장 먼저 들어온 항목 | 순차 처리 |
| **RANDOM** | 무작위 선택 | 공평한 분산 필요 시 |
| **TTL** | TTL 기반 - 가장 오래된 항목 | 시간 기반 만료 |

### 4. 참조 타입 (ReferenceType)

메모리 압박 시 GC 동작을 제어합니다.

```java
// STRONG (기본값): GC가 절대 회수하지 않음
SBCacheMap<K, V> strongCache = SBCacheMap.<K, V>builder()
    .loader(loader)
    .referenceType(ReferenceType.STRONG)
    .build();

// SOFT: 메모리 부족 시에만 GC가 회수 (대용량 캐시에 적합)
SBCacheMap<K, V> softCache = SBCacheMap.<K, V>builder()
    .loader(loader)
    .referenceType(ReferenceType.SOFT)
    .maxSize(100000)  // 대용량 설정 가능
    .build();

// WEAK: 다음 GC 사이클에서 회수 (임시 캐시)
SBCacheMap<K, V> weakCache = SBCacheMap.<K, V>builder()
    .loader(loader)
    .referenceType(ReferenceType.WEAK)
    .build();
```

**비교:**

| 타입 | GC 동작 | 장점 | 단점 | 사용 시기 |
|------|---------|------|------|-----------|
| **STRONG** | 회수 안 됨 | 예측 가능, 높은 히트율 | OOM 위험 | 작은 캐시, 중요 데이터 |
| **SOFT** | 메모리 부족 시 | 자동 메모리 관리, OOM 방지 | 히트율 불안정 | 대용량 캐시, 이미지/파일 |
| **WEAK** | 다음 GC에서 | 메모리 절약 | 매우 낮은 히트율 | 임시 데이터, 메모리 민감 |

---

## 🔧 고급 기능

### 1. Write-Through / Write-Behind

캐시 업데이트를 데이터 소스에 자동 반영합니다.

#### Write-Through (즉시 동기 쓰기)

```java
// Writer 구현
SBCacheMapWriter<Long, User> writer = new SBCacheMapWriter<>() {
    @Override
    public void write(Long key, User value) {
        userRepository.save(value);
    }

    @Override
    public void delete(Long key) {
        userRepository.deleteById(key);
    }
};

// Write-Through 캐시
SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
    .loader(loader)
    .writer(writer)
    .writeStrategy(WriteStrategy.WRITE_THROUGH)
    .build();

// 캐시 업데이트 시 즉시 DB에도 반영
cache.put(1L, user);  // 1. 캐시 업데이트 2. DB 저장 (동기) 3. 리턴
```

#### Write-Behind (배치 비동기 쓰기)

```java
// Write-Behind 캐시 (고성능 쓰기)
SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
    .loader(loader)
    .writer(writer)
    .writeStrategy(WriteStrategy.WRITE_BEHIND)
    .writeBehindBatchSize(100)          // 100개 모이면 플러시
    .writeBehindIntervalSeconds(5)      // 또는 5초마다 플러시
    .build();

// 빠른 응답 (비동기 쓰기)
cache.put(1L, user);  // 1. 캐시 업데이트 2. 쓰기 큐에 추가 3. 즉시 리턴
                      // 나중에 배치로 DB 저장
```

**비교:**

| 전략 | 쓰기 시점 | 일관성 | 성능 | 데이터 손실 위험 |
|------|-----------|--------|------|------------------|
| **READ_ONLY** | 수동 | N/A | 최고 | N/A |
| **WRITE_THROUGH** | 즉시 (동기) | 강함 | 느림 | 없음 |
| **WRITE_BEHIND** | 배치 (비동기) | Eventual | 빠름 | 있음 (캐시 장애 시) |

### 2. Refresh-Ahead (선제적 갱신)

TTL 만료 전에 미리 데이터를 갱신하여 항상 빠른 응답을 보장합니다.

```java
SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
    .loader(loader)
    .timeoutSec(300)  // 5분 TTL
    .refreshStrategy(RefreshStrategy.REFRESH_AHEAD)
    .refreshAheadFactor(0.8)  // TTL의 80% 경과 시 백그라운드 갱신
    .build();
```

**동작 방식:**

```
시간:     0s      240s     300s
        -------|--------|--------
         로드   갱신시작   만료
                (80%)

사용자 요청: [빠름]  [빠름]  [빠름]
            (로드)  (캐시)  (새 데이터)
```

**장점:**
- 사용자는 항상 빠른 응답
- 데이터는 항상 최신 유지
- DB 부하 분산

### 3. 자동 정리 (Auto Cleanup)

백그라운드에서 주기적으로 만료된 항목을 제거합니다.

```java
SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
    .loader(loader)
    .enableAutoCleanup(true)
    .cleanupIntervalMinutes(10)  // 10분마다 정리
    .build();
```

**권장 사항:**
- 메모리가 제한적인 환경: `enableAutoCleanup(true)`
- 고성능 환경 (메모리 충분): `enableAutoCleanup(false)` (지연 정리)

### 4. 통계 및 모니터링

```java
// 통계 수집 활성화
SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
    .loader(loader)
    .enableMetrics(true)
    .build();

// 통계 조회
CacheMetrics metrics = cache.getMetrics();
System.out.println("Hit Rate: " + metrics.getHitRate());
System.out.println("Miss Rate: " + metrics.getMissRate());
System.out.println("Total Requests: " + metrics.getTotalRequests());
System.out.println("Average Load Time: " + metrics.getAverageLoadTimeMs() + "ms");
```

---

## 🍃 Spring 통합

### Spring Cache 어노테이션 사용

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(
        UserRepository userRepository,
        ProductRepository productRepository
    ) {
        // User 로더
        SBCacheMapLoader<Object, Object> userLoader = new SBCacheMapLoader<>() {
            @Override
            public Object loadOne(Object key) throws SBCacheLoadFailException {
                return userRepository.findById((Long) key).orElse(null);
            }

            @Override
            public Map<Object, Object> loadAll() {
                return userRepository.findAll().stream()
                    .collect(Collectors.toMap(u -> (Object) u.getId(), u -> (Object) u));
            }
        };

        // Product 로더
        SBCacheMapLoader<Object, Object> productLoader = new SBCacheMapLoader<>() {
            @Override
            public Object loadOne(Object key) throws SBCacheLoadFailException {
                return productRepository.findById((Long) key).orElse(null);
            }

            @Override
            public Map<Object, Object> loadAll() {
                return productRepository.findAll().stream()
                    .collect(Collectors.toMap(p -> (Object) p.getId(), p -> (Object) p));
            }
        };

        return new SBCacheManager()
            .addCache("users", SBCacheMap.<Object, Object>builder()
                .loader(userLoader)
                .timeoutSec(300)
                .maxSize(10000)
                .evictionPolicy(EvictionPolicy.LRU)
                .enableMetrics(true)
                .build())
            .addCache("products", SBCacheMap.<Object, Object>builder()
                .loader(productLoader)
                .timeoutSec(600)
                .maxSize(5000)
                .evictionPolicy(EvictionPolicy.LFU)
                .build());
    }
}

@Service
public class UserService {

    @Cacheable("users")
    public User getUser(Long id) {
        // 캐시 미스 시에만 실행됨
        return userRepository.findById(id).orElse(null);
    }

    @CachePut(value = "users", key = "#user.id")
    public User updateUser(User user) {
        User saved = userRepository.save(user);
        // 캐시도 자동 업데이트됨
        return saved;
    }

    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
        // 캐시에서도 제거됨
    }

    @CacheEvict(value = "users", allEntries = true)
    public void clearAllUsers() {
        // 전체 캐시 무효화
    }
}
```

### Spring Boot Auto-Configuration

`application.yml`:

```yaml
sb-cache:
  default-ttl: 300
  enable-metrics: true
  max-size: 10000
  eviction-policy: LRU
  reference-type: STRONG
  auto-cleanup:
    enabled: true
    interval-minutes: 10
```

---

## ⚡ 성능 튜닝

### 1. 적절한 축출 정책 선택

```java
// 시나리오별 권장 정책

// 일반적인 캐시 (최근 사용 기준)
.evictionPolicy(EvictionPolicy.LRU)

// 인기도 기반 (조회 빈도 기준)
.evictionPolicy(EvictionPolicy.LFU)

// 시간 순서 중요 (생성 순서 기준)
.evictionPolicy(EvictionPolicy.FIFO)

// 공평한 분산 (랜덤)
.evictionPolicy(EvictionPolicy.RANDOM)
```

### 2. 참조 타입 최적화

```java
// 작은 캐시 (< 10,000 항목)
.referenceType(ReferenceType.STRONG)
.maxSize(10000)

// 중간 크기 캐시 (10,000 ~ 100,000 항목)
.referenceType(ReferenceType.SOFT)
.maxSize(100000)

// 대용량 캐시 (> 100,000 항목) - 메모리 압박 대비
.referenceType(ReferenceType.SOFT)
.maxSize(0)  // 무제한, GC가 관리
```

### 3. Write-Behind 튜닝

```java
// 고성능 쓰기 환경
SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
    .loader(loader)
    .writer(writer)
    .writeStrategy(WriteStrategy.WRITE_BEHIND)
    .writeBehindBatchSize(1000)        // 배치 크기 증가
    .writeBehindIntervalSeconds(10)    // 플러시 간격 증가
    .build();
```

**권장 설정:**
- **저지연 필요**: `batchSize=100`, `interval=5s`
- **고처리량 필요**: `batchSize=1000`, `interval=10s`
- **균형**: `batchSize=500`, `interval=5s`

### 4. Refresh-Ahead 최적화

```java
// 응답 속도 최우선 (항상 캐시 히트)
.refreshStrategy(RefreshStrategy.REFRESH_AHEAD)
.refreshAheadFactor(0.9)  // TTL 90% 시점에 갱신

// 균형 (일반적 권장)
.refreshStrategy(RefreshStrategy.REFRESH_AHEAD)
.refreshAheadFactor(0.8)  // TTL 80% 시점에 갱신

// DB 부하 최소화
.refreshStrategy(RefreshStrategy.REFRESH_AHEAD)
.refreshAheadFactor(0.7)  // TTL 70% 시점에 갱신
```

---

## 📚 실전 예제

### 예제 1: 사용자 프로필 캐시

```java
@Service
public class UserProfileCache {

    private final SBCacheMap<Long, UserProfile> cache;

    public UserProfileCache(UserRepository userRepository) {
        SBCacheMapLoader<Long, UserProfile> loader = new SBCacheMapLoader<>() {
            @Override
            public UserProfile loadOne(Long userId) throws SBCacheLoadFailException {
                return userRepository.findProfileById(userId)
                    .orElseThrow(() -> new SBCacheLoadFailException("User not found: " + userId));
            }

            @Override
            public Map<Long, UserProfile> loadAll() {
                return userRepository.findAllProfiles().stream()
                    .collect(Collectors.toMap(UserProfile::getId, p -> p));
            }
        };

        this.cache = SBCacheMap.<Long, UserProfile>builder()
            .loader(loader)
            .timeoutSec(600)                          // 10분 TTL
            .forcedTimeoutSec(3600)                   // 1시간 절대 만료
            .maxSize(50000)                           // 최대 5만 사용자
            .evictionPolicy(EvictionPolicy.LRU)       // LRU 축출
            .referenceType(ReferenceType.SOFT)        // 메모리 압박 시 GC
            .refreshStrategy(RefreshStrategy.REFRESH_AHEAD)  // 선제 갱신
            .refreshAheadFactor(0.8)                  // 8분 시점에 갱신
            .enableMetrics(true)                      // 통계 수집
            .enableAutoCleanup(true)                  // 자동 정리
            .cleanupIntervalMinutes(15)               // 15분마다 정리
            .build();
    }

    public UserProfile getProfile(Long userId) {
        return cache.get(userId);
    }

    public void updateProfile(UserProfile profile) {
        cache.put(profile.getId(), profile);
    }

    public CacheMetrics getMetrics() {
        return cache.getMetrics();
    }
}
```

### 예제 2: API 응답 캐시 (Write-Behind)

```java
@Service
public class ApiResponseCache {

    private final SBCacheMap<String, ApiResponse> cache;

    public ApiResponseCache(
        ExternalApiClient apiClient,
        ApiResponseRepository repository
    ) {
        // 로더: 외부 API 호출
        SBCacheMapLoader<String, ApiResponse> loader = new SBCacheMapLoader<>() {
            @Override
            public ApiResponse loadOne(String endpoint) throws SBCacheLoadFailException {
                try {
                    return apiClient.call(endpoint);
                } catch (Exception e) {
                    throw new SBCacheLoadFailException("API call failed: " + endpoint, e);
                }
            }

            @Override
            public Map<String, ApiResponse> loadAll() {
                // 전체 로드는 지원하지 않음
                return Collections.emptyMap();
            }
        };

        // Writer: DB에 비동기 저장
        SBCacheMapWriter<String, ApiResponse> writer = new SBCacheMapWriter<>() {
            @Override
            public void write(String key, ApiResponse value) {
                repository.save(key, value);
            }

            @Override
            public void delete(String key) {
                repository.delete(key);
            }
        };

        this.cache = SBCacheMap.<String, ApiResponse>builder()
            .loader(loader)
            .writer(writer)
            .timeoutSec(1800)                         // 30분 TTL
            .writeStrategy(WriteStrategy.WRITE_BEHIND)  // 비동기 쓰기
            .writeBehindBatchSize(500)                // 500개 배치
            .writeBehindIntervalSeconds(10)           // 10초마다 플러시
            .loadStrategy(LoadStrategy.ASYNC)         // 비동기 로드
            .maxSize(10000)                           // 최대 1만 응답
            .evictionPolicy(EvictionPolicy.LFU)       // 인기도 기반 축출
            .enableMetrics(true)
            .build();
    }

    public ApiResponse getResponse(String endpoint) {
        return cache.get(endpoint);
    }
}
```

### 예제 3: 상품 카탈로그 캐시 (대용량)

```java
@Service
public class ProductCatalogCache {

    private final SBCacheMap<Long, Product> cache;

    public ProductCatalogCache(ProductRepository repository) {
        SBCacheMapLoader<Long, Product> loader = new SBCacheMapLoader<>() {
            @Override
            public Product loadOne(Long productId) throws SBCacheLoadFailException {
                return repository.findById(productId)
                    .orElseThrow(() -> new SBCacheLoadFailException("Product not found"));
            }

            @Override
            public Map<Long, Product> loadAll() throws SBCacheLoadFailException {
                return repository.findAll().stream()
                    .collect(Collectors.toMap(Product::getId, p -> p));
            }
        };

        this.cache = SBCacheMap.<Long, Product>builder()
            .loader(loader)
            .timeoutSec(7200)                         // 2시간 TTL
            .maxSize(0)                               // 무제한 (GC 관리)
            .referenceType(ReferenceType.SOFT)        // 메모리 부족 시 GC
            .evictionPolicy(EvictionPolicy.LRU)       // 사용되지 않으면 제거
            .enableMetrics(true)
            .enableAutoCleanup(false)                 // 지연 정리 (성능 우선)
            .build();
    }

    public Product getProduct(Long productId) {
        return cache.get(productId);
    }

    public void warmUp() {
        // 캐시 워밍업
        cache.warmupAll();
    }
}
```

---

## 📊 모니터링

### JMX 모니터링

```java
SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
    .loader(loader)
    .enableJmx("UserCache")  // JMX MBean 등록
    .build();
```

**JConsole/VisualVM에서 확인:**
```
org.scriptonbasestar.cache:type=SBCacheMap,name=UserCache

Attributes:
  - HitRate: 94.5%
  - MissRate: 5.5%
  - Size: 8234
  - TotalRequests: 15234
  - AverageLoadTime: 32ms

Operations:
  - clear()
  - refresh()
  - getStatistics()
```

### Prometheus/Micrometer 통합

```java
@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistry meterRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    @Bean
    public SBCacheMap<Long, User> userCache(
        SBCacheMapLoader<Long, User> loader,
        MeterRegistry registry
    ) {
        return SBCacheMap.<Long, User>builder()
            .loader(loader)
            .meterRegistry(registry)
            .cacheName("users")
            .build();
    }
}
```

**Prometheus 메트릭:**
```
cache_hits_total{cache="users"} 15234
cache_misses_total{cache="users"} 892
cache_evictions_total{cache="users"} 45
cache_size{cache="users"} 8234
cache_load_duration_seconds{cache="users",quantile="0.95"} 0.032
```

### Spring Boot Actuator Health Check

```java
@GetMapping("/actuator/cache-health")
public CacheHealth getCacheHealth(SBCacheManager cacheManager) {
    return CacheHealth.builder()
        .caches(cacheManager.getAllCaches())
        .totalHitRate(calculateTotalHitRate())
        .totalSize(calculateTotalSize())
        .status(Status.UP)
        .build();
}
```

---

## 🔍 문제 해결

### Q: 캐시 히트율이 낮아요

**A:** 다음을 확인하세요:

1. TTL이 너무 짧지 않은지 확인
   ```java
   .timeoutSec(300)  // 5분 → 더 길게 설정
   ```

2. 참조 타입 확인 (WEAK는 히트율이 매우 낮음)
   ```java
   .referenceType(ReferenceType.STRONG)  // WEAK → STRONG으로 변경
   ```

3. maxSize가 충분한지 확인
   ```java
   .maxSize(10000)  // 너무 작으면 자주 축출됨
   ```

### Q: 메모리 부족 오류가 발생해요

**A:** 다음 조치를 시도하세요:

1. 참조 타입을 SOFT로 변경
   ```java
   .referenceType(ReferenceType.SOFT)  // GC가 메모리 관리
   ```

2. maxSize 제한 설정
   ```java
   .maxSize(50000)  // 최대 크기 제한
   ```

3. 자동 정리 활성화
   ```java
   .enableAutoCleanup(true)
   .cleanupIntervalMinutes(5)
   ```

### Q: 첫 요청이 너무 느려요

**A:** Refresh-Ahead 전략을 사용하세요:

```java
.refreshStrategy(RefreshStrategy.REFRESH_AHEAD)
.refreshAheadFactor(0.8)  // TTL 80% 시점에 미리 갱신
```

또는 캐시 워밍업:

```java
cache.warmupAll();  // 애플리케이션 시작 시 전체 로드
```

---

## 📖 추가 자료

- [API 문서](./API_DOCS.md)
- [성능 벤치마크](./BENCHMARKS.md)
- [아키텍처 가이드](./ARCHITECTURE.md)
- [마이그레이션 가이드](./MIGRATION.md)

---

**라이센스**: MIT License
**작성자**: ScriptonBaseStar
**프로젝트**: https://github.com/scriptonbasestar/sb-cached-collection
