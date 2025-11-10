# Migration Guide

다른 캐시 라이브러리에서 SB Cached Collection으로 마이그레이션하는 가이드입니다.

## 목차

1. [Caffeine에서 마이그레이션](#caffeine에서-마이그레이션)
2. [Guava Cache에서 마이그레이션](#guava-cache에서-마이그레이션)
3. [EhCache에서 마이그레이션](#ehcache에서-마이그레이션)
4. [ConcurrentHashMap에서 마이그레이션](#concurrenthashmap에서-마이그레이션)
5. [Spring Cache 기본 구현에서 마이그레이션](#spring-cache-기본-구현에서-마이그레이션)
6. [Redis에서 마이그레이션](#redis에서-마이그레이션)

---

## Caffeine에서 마이그레이션

### 개요

Caffeine은 고성능 Java 캐시 라이브러리이며, SB Cached Collection과 매우 유사한 API를 제공합니다.

### 주요 차이점

| 기능 | Caffeine | SB Cached Collection |
|------|----------|---------------------|
| 성능 | 매우 빠름 (Window TinyLFU) | 빠름 (표준 LRU/LFU) |
| 축출 정책 | W-TinyLFU (고정) | LRU, LFU, FIFO, RANDOM, TTL (선택) |
| Reference 타입 | STRONG, WEAK | STRONG, SOFT, WEAK |
| Write-Behind | ❌ | ✅ |
| Refresh-Ahead | ✅ (refreshAfterWrite) | ✅ (RefreshStrategy.REFRESH_AHEAD) |
| Spring 통합 | ✅ | ✅ (더 다양한 설정) |
| 확장성 | 제한적 | 높음 (Strategy 패턴) |

### API 매핑

#### 1. 기본 캐시 생성

**Caffeine**:
```java
Cache<Long, User> cache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .recordStats()
    .build();

User user = cache.getIfPresent(123L);
cache.put(123L, user);
```

**SB Cached Collection**:
```java
SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
    .maxSize(10000)
    .timeoutSec(300)  // 5분
    .enableMetrics(true)
    .build();

User user = cache.get(123L);
cache.put(123L, user);
```

#### 2. Loader와 함께 사용

**Caffeine**:
```java
LoadingCache<Long, User> cache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .build(key -> userRepository.findById(key));

User user = cache.get(123L);  // 자동 로딩
```

**SB Cached Collection**:
```java
SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
    .loader(new SBCacheMapLoader<Long, User>() {
        @Override
        public User loadOne(Long key) throws SBCacheLoadFailException {
            return userRepository.findById(key)
                .orElseThrow(() -> new SBCacheLoadFailException("Not found: " + key));
        }

        @Override
        public Map<Long, User> loadAll() throws SBCacheLoadFailException {
            return userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        }
    })
    .maxSize(10000)
    .timeoutSec(300)
    .build();

User user = cache.get(123L);  // 자동 로딩
```

#### 3. Async Loading (AsyncLoadingCache)

**Caffeine**:
```java
AsyncLoadingCache<Long, User> cache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .buildAsync(key -> userRepository.findById(key));

CompletableFuture<User> userFuture = cache.get(123L);
User user = userFuture.join();
```

**SB Cached Collection**:
```java
SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
    .loader(userLoader)
    .maxSize(10000)
    .timeoutSec(300)
    .loadStrategy(LoadStrategy.ASYNC)  // 비동기 로딩
    .build();

User user = cache.get(123L);  // 동기 인터페이스지만 내부적으로 비동기
```

#### 4. Refresh-Ahead

**Caffeine**:
```java
LoadingCache<Long, User> cache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .refreshAfterWrite(30, TimeUnit.SECONDS)  // 30초 후 백그라운드 갱신
    .build(key -> userRepository.findById(key));
```

**SB Cached Collection**:
```java
SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
    .loader(userLoader)
    .maxSize(10000)
    .timeoutSec(60)  // 1분 TTL
    .refreshStrategy(RefreshStrategy.REFRESH_AHEAD)  // 30초 후 갱신
    .build();
```

#### 5. Reference 타입 (Weak Reference)

**Caffeine**:
```java
Cache<String, Image> cache = Caffeine.newBuilder()
    .maximumSize(1000)
    .weakValues()  // WEAK reference
    .build();
```

**SB Cached Collection**:
```java
SBCacheMap<String, Image> cache = SBCacheMap.<String, Image>builder()
    .maxSize(1000)
    .referenceType(ReferenceType.WEAK)  // WEAK reference
    .build();
```

**추가**: SB Cached Collection은 SOFT reference도 지원:
```java
SBCacheMap<String, Image> cache = SBCacheMap.<String, Image>builder()
    .maxSize(1000)
    .referenceType(ReferenceType.SOFT)  // SOFT reference (Caffeine은 미지원)
    .build();
```

#### 6. 통계 수집

**Caffeine**:
```java
Cache<Long, User> cache = Caffeine.newBuilder()
    .recordStats()
    .build();

CacheStats stats = cache.stats();
System.out.println("Hit rate: " + stats.hitRate());
System.out.println("Miss rate: " + stats.missRate());
```

**SB Cached Collection**:
```java
SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
    .enableMetrics(true)
    .build();

System.out.println("Hit rate: " + cache.getHitRate());
System.out.println("Miss rate: " + cache.getMissRate());
System.out.println("Average load time: " + cache.getAverageLoadTime() + "ms");
```

### 마이그레이션 체크리스트

- [ ] `Caffeine.newBuilder()` → `SBCacheMap.builder()`
- [ ] `maximumSize()` → `maxSize()`
- [ ] `expireAfterWrite()` → `timeoutSec()` (초 단위)
- [ ] `build(loader)` → `loader()` + `build()`
- [ ] `refreshAfterWrite()` → `refreshStrategy(RefreshStrategy.REFRESH_AHEAD)`
- [ ] `weakValues()` → `referenceType(ReferenceType.WEAK)`
- [ ] `recordStats()` → `enableMetrics(true)`
- [ ] `getIfPresent()` → `get()` (null 반환 가능)

---

## Guava Cache에서 마이그레이션

### 개요

Guava Cache는 Google의 범용 캐시 라이브러리이며, API가 Caffeine과 유사합니다.

### 주요 차이점

| 기능 | Guava Cache | SB Cached Collection |
|------|-------------|---------------------|
| 유지보수 | 유지보수 모드 | 활발한 개발 |
| 성능 | 보통 | 빠름 |
| 축출 정책 | LRU (고정) | LRU, LFU, FIFO, RANDOM, TTL |
| Reference 타입 | STRONG, SOFT, WEAK | STRONG, SOFT, WEAK |
| Write-Behind | ❌ | ✅ |
| Refresh-Ahead | ✅ (refreshAfterWrite) | ✅ |

### API 매핑

#### 1. 기본 캐시 생성

**Guava Cache**:
```java
Cache<Long, User> cache = CacheBuilder.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .recordStats()
    .build();

User user = cache.getIfPresent(123L);
cache.put(123L, user);
```

**SB Cached Collection**:
```java
SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
    .maxSize(10000)
    .timeoutSec(300)
    .enableMetrics(true)
    .build();

User user = cache.get(123L);
cache.put(123L, user);
```

#### 2. LoadingCache

**Guava Cache**:
```java
LoadingCache<Long, User> cache = CacheBuilder.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .build(new CacheLoader<Long, User>() {
        @Override
        public User load(Long key) throws Exception {
            return userRepository.findById(key);
        }
    });

User user = cache.get(123L);
```

**SB Cached Collection**:
```java
SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
    .loader(new SBCacheMapLoader<Long, User>() {
        @Override
        public User loadOne(Long key) throws SBCacheLoadFailException {
            return userRepository.findById(key)
                .orElseThrow(() -> new SBCacheLoadFailException("Not found"));
        }

        @Override
        public Map<Long, User> loadAll() throws SBCacheLoadFailException {
            return userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        }
    })
    .maxSize(10000)
    .timeoutSec(300)
    .build();

User user = cache.get(123L);
```

#### 3. Reference 타입

**Guava Cache**:
```java
Cache<String, Image> cache = CacheBuilder.newBuilder()
    .softValues()  // SOFT reference
    .build();
```

**SB Cached Collection**:
```java
SBCacheMap<String, Image> cache = SBCacheMap.<String, Image>builder()
    .referenceType(ReferenceType.SOFT)
    .build();
```

#### 4. Removal Listener

**Guava Cache**:
```java
Cache<Long, User> cache = CacheBuilder.newBuilder()
    .removalListener(notification -> {
        System.out.println("Removed: " + notification.getKey());
    })
    .build();
```

**SB Cached Collection** (커스텀 EvictionStrategy 구현):
```java
public class LoggingLRUStrategy<K> implements EvictionStrategy<K> {
    private final LRUEvictionStrategy<K> delegate = new LRUEvictionStrategy<>();

    @Override
    public void onRemove(K key) {
        System.out.println("Removed: " + key);
        delegate.onRemove(key);
    }

    // 나머지 메서드는 delegate에 위임
}

// 사용
SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
    .evictionPolicy(EvictionPolicy.LRU)  // 기본 LRU 사용
    .build();
```

### 마이그레이션 체크리스트

- [ ] `CacheBuilder.newBuilder()` → `SBCacheMap.builder()`
- [ ] `maximumSize()` → `maxSize()`
- [ ] `expireAfterWrite()` → `timeoutSec()`
- [ ] `CacheLoader` → `SBCacheMapLoader`
- [ ] `softValues()` → `referenceType(ReferenceType.SOFT)`
- [ ] `weakValues()` → `referenceType(ReferenceType.WEAK)`
- [ ] `recordStats()` → `enableMetrics(true)`
- [ ] `getIfPresent()` → `get()`

---

## EhCache에서 마이그레이션

### 개요

EhCache는 엔터프라이즈급 캐시 솔루션으로, 다양한 기능을 제공합니다.

### 주요 차이점

| 기능 | EhCache | SB Cached Collection |
|------|---------|---------------------|
| 분산 캐시 | ✅ (Terracotta) | ❌ (단일 JVM) |
| 디스크 저장 | ✅ | ❌ (메모리만) |
| JTA 트랜잭션 | ✅ | ❌ |
| Write-Behind | ✅ | ✅ |
| Reference 타입 | ✅ | ✅ |
| Spring 통합 | ✅ | ✅ |
| 단순성 | 복잡 | 단순 |

### API 매핑

#### 1. 기본 캐시 생성 (EhCache 3.x)

**EhCache**:
```java
CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
    .withCache("users",
        CacheConfigurationBuilder.newCacheConfigurationBuilder(
            Long.class, User.class,
            ResourcePoolsBuilder.heap(10000))
        .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(5)))
        .build())
    .build(true);

Cache<Long, User> cache = cacheManager.getCache("users", Long.class, User.class);
User user = cache.get(123L);
cache.put(123L, user);
```

**SB Cached Collection**:
```java
SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
    .maxSize(10000)
    .timeoutSec(300)
    .build();

User user = cache.get(123L);
cache.put(123L, user);
```

#### 2. CacheLoader (Read-Through)

**EhCache**:
```java
CacheLoaderWriter<Long, User> loaderWriter = new CacheLoaderWriter<Long, User>() {
    @Override
    public User load(Long key) throws Exception {
        return userRepository.findById(key);
    }

    @Override
    public void write(Long key, User value) throws Exception {
        userRepository.save(value);
    }

    @Override
    public void delete(Long key) throws Exception {
        userRepository.deleteById(key);
    }
};

Cache<Long, User> cache = cacheManager.createCache("users",
    CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, User.class,
        ResourcePoolsBuilder.heap(10000))
    .withLoaderWriter(loaderWriter)
    .build());
```

**SB Cached Collection**:
```java
SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
    .loader(new SBCacheMapLoader<Long, User>() {
        @Override
        public User loadOne(Long key) throws SBCacheLoadFailException {
            return userRepository.findById(key)
                .orElseThrow(() -> new SBCacheLoadFailException("Not found"));
        }

        @Override
        public Map<Long, User> loadAll() throws SBCacheLoadFailException {
            return userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        }
    })
    .writer(new SBCacheMapWriter<Long, User>() {
        @Override
        public void writeOne(Long key, User value) throws SBCacheWriteFailException {
            userRepository.save(value);
        }

        @Override
        public void writeAll(Map<Long, User> entries) throws SBCacheWriteFailException {
            userRepository.saveAll(entries.values());
        }

        @Override
        public void deleteOne(Long key) throws SBCacheWriteFailException {
            userRepository.deleteById(key);
        }

        @Override
        public void deleteAll(Collection<Long> keys) throws SBCacheWriteFailException {
            userRepository.deleteAllById(keys);
        }
    })
    .writeStrategy(WriteStrategy.WRITE_THROUGH)
    .maxSize(10000)
    .build();
```

#### 3. Write-Behind

**EhCache**:
```java
Cache<Long, User> cache = cacheManager.createCache("users",
    CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, User.class,
        ResourcePoolsBuilder.heap(10000))
    .withLoaderWriter(loaderWriter)
    .add(WriteBehindConfigurationBuilder.newBatchedWriteBehindConfiguration()
        .queueSize(1000)
        .concurrencyLevel(3)
        .batchSize(100)
        .build())
    .build());
```

**SB Cached Collection**:
```java
SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
    .loader(userLoader)
    .writer(userWriter)
    .writeStrategy(WriteStrategy.WRITE_BEHIND)  // 비동기 쓰기
    .maxSize(10000)
    .build();
```

### 마이그레이션 체크리스트

- [ ] `CacheManager` → 직접 `SBCacheMap` 생성 (또는 Spring `SBCacheManager`)
- [ ] `ResourcePoolsBuilder.heap()` → `maxSize()`
- [ ] `ExpiryPolicyBuilder.timeToLiveExpiration()` → `timeoutSec()`
- [ ] `CacheLoaderWriter` → `SBCacheMapLoader` + `SBCacheMapWriter`
- [ ] `WriteBehindConfiguration` → `writeStrategy(WriteStrategy.WRITE_BEHIND)`
- [ ] 디스크 저장 기능 → 필요 시 Redis/DB 사용

---

## ConcurrentHashMap에서 마이그레이션

### 개요

ConcurrentHashMap을 TTL, 축출 정책 없이 사용 중인 경우 SB Cached Collection으로 전환하여 고급 기능을 추가할 수 있습니다.

### API 매핑

#### 1. 기본 사용

**ConcurrentHashMap**:
```java
ConcurrentHashMap<Long, User> map = new ConcurrentHashMap<>();

User user = map.get(123L);
if (user == null) {
    user = userRepository.findById(123L);
    map.put(123L, user);
}
```

**SB Cached Collection**:
```java
SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
    .loader(key -> userRepository.findById(key)
        .orElseThrow(() -> new SBCacheLoadFailException("Not found")))
    .timeoutSec(300)  // 5분 TTL 추가
    .build();

User user = cache.get(123L);  // 자동 로딩
```

#### 2. computeIfAbsent 패턴

**ConcurrentHashMap**:
```java
User user = map.computeIfAbsent(123L, key -> userRepository.findById(key));
```

**SB Cached Collection**:
```java
// Loader 사용 시 동일한 효과
User user = cache.get(123L);
```

### 추가 이점

ConcurrentHashMap → SB Cached Collection 전환 시:
- ✅ **TTL 자동 만료**: 오래된 데이터 자동 정리
- ✅ **MaxSize 제한**: OOM 방지
- ✅ **축출 정책**: LRU, LFU 등
- ✅ **통계 수집**: Hit Rate, Load Time
- ✅ **Reference 타입**: SOFT/WEAK로 메모리 관리

---

## Spring Cache 기본 구현에서 마이그레이션

### 개요

Spring Cache의 기본 구현(`ConcurrentMapCacheManager`)을 사용 중인 경우 `SBCacheManager`로 전환하여 TTL, 축출 정책 등을 추가할 수 있습니다.

### 설정 변경

#### Before (ConcurrentMapCacheManager)

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("users", "products");
    }
}
```

**문제점**:
- ❌ TTL 없음 (무한정 유지)
- ❌ MaxSize 제한 없음 (OOM 위험)
- ❌ 축출 정책 없음
- ❌ 통계 없음

#### After (SBCacheManager)

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new SBCacheManager()
            .addCache("users", SBCacheMap.<Object, Object>builder()
                .timeoutSec(300)  // 5분 TTL
                .maxSize(10000)
                .evictionPolicy(EvictionPolicy.LRU)
                .referenceType(ReferenceType.SOFT)
                .enableMetrics(true)
                .build())
            .addCache("products", SBCacheMap.<Object, Object>builder()
                .timeoutSec(600)  // 10분 TTL
                .maxSize(5000)
                .evictionPolicy(EvictionPolicy.LFU)
                .enableMetrics(true)
                .build());
    }
}
```

**개선사항**:
- ✅ TTL 자동 만료
- ✅ MaxSize 제한
- ✅ 축출 정책 (LRU/LFU)
- ✅ 통계 수집

### @Cacheable 어노테이션은 변경 불필요

기존 코드는 그대로 사용 가능:
```java
@Service
public class UserService {

    @Cacheable("users")  // 변경 불필요
    public User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }
}
```

---

## Redis에서 마이그레이션

### 개요

Redis를 단순 캐시로만 사용 중인 경우, 응답 속도가 중요한 읽기 중심 워크로드에서는 인메모리 캐시로 전환하여 성능을 향상시킬 수 있습니다.

### 고려사항

**Redis 유지가 필요한 경우**:
- ✅ 분산 캐시 (여러 서버 간 공유)
- ✅ 영속성 필요
- ✅ Pub/Sub 기능 사용
- ✅ 대용량 데이터 (수십 GB 이상)

**SB Cached Collection 전환이 적합한 경우**:
- ✅ 단일 JVM 환경
- ✅ 응답 속도 최우선 (나노초 단위)
- ✅ 중소 규모 데이터 (수 GB 이하)
- ✅ 네트워크 비용 절감

### 성능 비교

| 캐시 유형 | 평균 응답 시간 | Throughput |
|----------|-------------|-----------|
| **Redis** (로컬) | 0.5-1ms | ~50K ops/sec |
| **SB Cached** | 0.001-0.01ms | ~165M ops/sec |
| **개선율** | **50-100배** | **3,000배** |

### 하이브리드 접근 (2-tier 캐싱)

Redis와 SB Cached Collection을 함께 사용:

```java
// L2: Redis (분산 캐시, 1시간 TTL)
// L1: SB Cached (로컬 메모리, 5분 TTL)

@Service
public class UserService {

    @Autowired
    private RedisTemplate<Long, User> redisTemplate;

    private final SBCacheMap<Long, User> localCache;

    public UserService() {
        this.localCache = SBCacheMap.<Long, User>builder()
            .loader(key -> {
                // L2(Redis)에서 조회
                User user = redisTemplate.opsForValue().get(key);
                if (user == null) {
                    // Redis에도 없으면 DB 조회
                    user = userRepository.findById(key).orElse(null);
                    if (user != null) {
                        redisTemplate.opsForValue().set(key, user, 1, TimeUnit.HOURS);
                    }
                }
                return user;
            })
            .timeoutSec(300)  // 5분 TTL
            .maxSize(10000)
            .referenceType(ReferenceType.SOFT)
            .build();
    }

    public User getUserById(Long id) {
        return localCache.get(id);
    }
}
```

**데이터 흐름**:
```
App → L1(메모리, 5분) → L2(Redis, 1시간) → DB
```

**장점**:
- 초고속: 대부분 L1에서 처리 (나노초)
- 분산: 서버 간 Redis 공유
- 효율적: Redis 조회 최소화

---

## 일반적인 마이그레이션 단계

### 1. 준비

- [ ] 의존성 추가 (`cache-collection` 또는 `cache-spring`)
- [ ] 현재 캐시 사용 패턴 분석 (TTL, 크기, 축출 정책)
- [ ] 테스트 환경에서 먼저 검증

### 2. 단계적 전환

**Phase 1**: 비중요 캐시부터 전환
```java
// 예: 설정 캐시, 참조 데이터 캐시
```

**Phase 2**: 중요 캐시 전환
```java
// 예: 사용자 캐시, 세션 캐시
```

**Phase 3**: 핵심 캐시 전환
```java
// 예: 주문 캐시, 결제 캐시
```

### 3. 모니터링

전환 후 확인 사항:
- [ ] Hit Rate (목표: > 80%)
- [ ] 응답 시간 (목표: < 10ms)
- [ ] 메모리 사용량 (목표: 안정적)
- [ ] GC 영향 (목표: Stop-The-World < 200ms)

### 4. 최적화

- [ ] TTL 조정 (접근 패턴 기반)
- [ ] MaxSize 조정 (메모리 사용량 기반)
- [ ] 축출 정책 선택 (워크로드 기반)
- [ ] Reference 타입 선택 (메모리 압박 기반)

---

## 트러블슈팅

### 문제 1: Hit Rate가 낮음 (< 50%)

**원인**:
- TTL이 너무 짧음
- MaxSize가 너무 작음

**해결**:
```java
// TTL 늘리기
.timeoutSec(600)  // 5분 → 10분

// MaxSize 늘리기
.maxSize(20000)  // 10,000 → 20,000
```

### 문제 2: 메모리 사용량이 계속 증가

**원인**:
- MaxSize 없음 또는 너무 큼
- STRONG Reference 사용

**해결**:
```java
// MaxSize 설정
.maxSize(10000)

// SOFT Reference 사용
.referenceType(ReferenceType.SOFT)
```

### 문제 3: DB 부하가 높음 (Thundering Herd)

**원인**:
- SYNC LoadStrategy 사용
- Refresh-Ahead 미사용

**해결**:
```java
// ASYNC LoadStrategy
.loadStrategy(LoadStrategy.ASYNC)

// Refresh-Ahead
.refreshStrategy(RefreshStrategy.REFRESH_AHEAD)
```

---

## 참고 자료

- [USER_GUIDE.md](USER_GUIDE.md) - 종합 사용자 가이드
- [SPRING_INTEGRATION.md](SPRING_INTEGRATION.md) - Spring 통합 가이드
- [API_REFERENCE.md](API_REFERENCE.md) - 전체 API 레퍼런스
- [BENCHMARKS.md](BENCHMARKS.md) - 성능 벤치마크
- [ARCHITECTURE.md](ARCHITECTURE.md) - 시스템 아키텍처
