# Architecture Documentation

SB Cached Collection의 시스템 아키텍처 및 설계 문서입니다.

## 목차

1. [개요](#개요)
2. [모듈 구조](#모듈-구조)
3. [레이어 아키텍처](#레이어-아키텍처)
4. [핵심 디자인 패턴](#핵심-디자인-패턴)
5. [확장 포인트](#확장-포인트)
6. [스레드 안전성](#스레드-안전성)
7. [메모리 관리](#메모리-관리)
8. [성능 최적화](#성능-최적화)

---

## 개요

SB Cached Collection은 **확장 가능한 캐시 프레임워크**로, 다음과 같은 아키텍처 원칙을 따릅니다:

### 설계 원칙

1. **모듈화 (Modularity)**: 기능별로 독립적인 모듈로 분리
2. **확장성 (Extensibility)**: Strategy 패턴을 통한 유연한 확장
3. **느슨한 결합 (Loose Coupling)**: 인터페이스 기반 의존성
4. **높은 응집도 (High Cohesion)**: 관련 기능을 모듈 내부로 집중
5. **의존성 역전 (Dependency Inversion)**: 추상화에 의존, 구체화에 의존하지 않음

### 아키텍처 특징

- ✅ **Multi-module Maven 프로젝트**: 명확한 책임 분리
- ✅ **Strategy 패턴**: 런타임 동작 변경 가능
- ✅ **Builder 패턴**: 복잡한 객체 생성 간소화
- ✅ **Template Method 패턴**: 공통 알고리즘 재사용
- ✅ **Spring Integration**: 표준 CacheManager 구현
- ✅ **GC 협력**: Java Reference API 활용

---

## 모듈 구조

SB Cached Collection은 8개의 Maven 모듈로 구성되어 있습니다.

```
sb-cached-collection/
├── pom.xml (parent)
├── cache-core/                    # 핵심 인터페이스 및 전략
├── cache-collection/              # 캐시 구현체
├── cache-loader-jdbc/             # JDBC Loader
├── cache-loader-file/             # File Loader
├── cache-loader-redis/            # Redis Loader (미래 확장)
├── cache-metrics/                 # 통계 및 모니터링
└── cache-spring/                  # Spring Framework 통합
```

### 모듈별 책임

#### 1. cache-core

**역할**: 핵심 인터페이스와 전략 정의

**제공 기능**:
- 전략 인터페이스 (ReferenceType, EvictionPolicy, LoadStrategy, WriteStrategy, RefreshStrategy)
- Loader/Writer 인터페이스
- Exception 정의

**의존성**: 없음 (독립 모듈)

**핵심 패키지**:
```
org.scriptonbasestar.cache.core
├── strategy/
│   ├── ReferenceType.java
│   ├── EvictionPolicy.java
│   ├── EvictionStrategy.java
│   ├── LoadStrategy.java
│   ├── WriteStrategy.java
│   └── RefreshStrategy.java
├── loader/
│   ├── SBCacheMapLoader.java
│   └── SBCacheListLoader.java
├── writer/
│   └── SBCacheMapWriter.java
└── exception/
    ├── SBCacheLoadFailException.java
    └── SBCacheWriteFailException.java
```

---

#### 2. cache-collection

**역할**: 실제 캐시 구현

**제공 기능**:
- SBCacheMap (Map 기반 캐시)
- ReferenceBasedStorage (Reference 타입 저장소)
- 각종 EvictionStrategy 구현체

**의존성**: cache-core

**핵심 클래스**:
```
org.scriptonbasestar.cache.collection
├── map/
│   ├── SBCacheMap.java              # 메인 캐시 구현
│   └── SBCacheMap.Builder.java      # Builder 패턴
├── storage/
│   └── ReferenceBasedStorage.java   # Reference 기반 저장소
└── strategy/
    ├── LRUEvictionStrategy.java
    ├── LFUEvictionStrategy.java
    ├── FIFOEvictionStrategy.java
    ├── RandomEvictionStrategy.java
    └── TTLEvictionStrategy.java
```

---

#### 3. cache-loader-jdbc

**역할**: JDBC 기반 데이터 로딩

**제공 기능**:
- JDBCLoader: SQL 쿼리로 데이터 로딩
- DataSource 통합

**의존성**: cache-core

---

#### 4. cache-loader-file

**역할**: 파일 시스템 기반 데이터 로딩

**제공 기능**:
- FileLoader: 파일에서 데이터 로딩
- 다양한 파일 포맷 지원

**의존성**: cache-core

---

#### 5. cache-metrics

**역할**: 통계 및 모니터링

**제공 기능**:
- Hit/Miss 카운트
- 로딩 시간 측정
- JMX 통합
- Prometheus/Micrometer 통합

**의존성**: cache-collection

---

#### 6. cache-spring

**역할**: Spring Framework 통합

**제공 기능**:
- SBCacheManager (Spring CacheManager)
- SBCache (Spring Cache)
- Auto-Configuration
- Actuator 통합

**의존성**: cache-collection, Spring Framework

**핵심 클래스**:
```
org.scriptonbasestar.cache.spring
├── SBCacheManager.java                      # CacheManager 구현
├── SBCache.java                             # Cache 구현
├── boot/
│   ├── SBCacheAutoConfiguration.java        # Auto-Configuration
│   └── SBCacheProperties.java               # 설정 Properties
└── actuator/
    ├── CacheHealthIndicator.java            # Health Indicator
    └── CompositeCacheHealthIndicator.java   # 복합 Health Indicator
```

---

### 모듈 의존성 그래프

```
┌─────────────────────────────────────────┐
│           cache-core                    │  ← 핵심 인터페이스 (의존성 없음)
│  (인터페이스, 전략, Exception)            │
└─────────────────────────────────────────┘
               ▲
               │ depends on
               │
    ┌──────────┴───────────┬───────────────┐
    │                      │               │
┌───┴──────────────┐  ┌───┴─────────┐  ┌──┴────────────┐
│ cache-collection │  │ cache-      │  │ cache-        │
│  (구현체)         │  │  loader-*   │  │  metrics      │
└──────────────────┘  │  (Loaders)  │  │  (통계)       │
               ▲      └─────────────┘  └───────────────┘
               │                              ▲
               │                              │
          ┌────┴─────────┐                   │
          │ cache-spring │                   │
          │ (Spring 통합) │───────────────────┘
          └──────────────┘
```

**의존성 흐름**:
1. `cache-core`: 독립 모듈 (의존성 없음)
2. `cache-collection`, `cache-loader-*`: `cache-core`에 의존
3. `cache-metrics`: `cache-collection`에 의존
4. `cache-spring`: `cache-collection`, `cache-metrics`에 의존

---

## 레이어 아키텍처

SB Cached Collection은 4-tier 레이어 아키텍처를 따릅니다.

```
┌─────────────────────────────────────────────────────────┐
│                   Application Layer                     │
│          (User Code, Spring @Cacheable, etc.)           │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                   Integration Layer                     │
│        (SBCacheManager, SBCache, Spring Bridge)         │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                   Business Layer                        │
│     (SBCacheMap, Strategy 실행, Loader/Writer 호출)      │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                   Storage Layer                         │
│   (ReferenceBasedStorage, ConcurrentHashMap, GC 협력)   │
└─────────────────────────────────────────────────────────┘
```

### Layer 1: Application Layer

**책임**: 사용자 코드, 비즈니스 로직

**예시**:
```java
@Service
public class UserService {
    @Cacheable("users")
    public User getUser(Long id) {
        return userRepository.findById(id).orElse(null);
    }
}
```

---

### Layer 2: Integration Layer

**책임**: Spring Cache Abstraction과의 통합

**주요 클래스**:
- `SBCacheManager`: Spring `CacheManager` 구현
- `SBCache`: Spring `Cache` 인터페이스 구현

**역할**:
- Spring Cache 어노테이션 처리
- 캐시 이름 기반 라우팅
- Null 값 처리

---

### Layer 3: Business Layer

**책임**: 캐시 핵심 로직

**주요 클래스**:
- `SBCacheMap`: 메인 캐시 구현
- `EvictionStrategy`: 축출 전략 실행
- `Loader/Writer`: 데이터 소스 통합

**주요 기능**:
- TTL 만료 체크
- 캐시 미스 시 로딩
- 축출 정책 실행
- Write-Through/Write-Behind 처리
- Refresh-Ahead 백그라운드 갱신
- 통계 수집

**처리 흐름**:
```
get(key)
  → TTL 체크
  → 캐시 히트?
     YES → 통계 업데이트 → 반환
     NO  → Loader 호출 → 캐시 저장 → 축출 체크 → 반환
```

---

### Layer 4: Storage Layer

**책임**: 실제 데이터 저장 및 메모리 관리

**주요 클래스**:
- `ReferenceBasedStorage`: Reference 타입 저장소
- `ConcurrentHashMap`: 스레드 안전한 저장소

**Reference 타입별 저장 방식**:

| ReferenceType | 저장 방식 | GC 동작 |
|---------------|----------|---------|
| STRONG | `storage.put(key, value)` | 회수 안 됨 |
| SOFT | `storage.put(key, new SoftReference<>(value))` | 메모리 부족 시 회수 |
| WEAK | `storage.put(key, new WeakReference<>(value))` | 다음 GC에서 회수 |

**GC 협력 메커니즘**:
```java
// ReferenceQueue를 통한 자동 정리
ReferenceQueue<V> queue = new ReferenceQueue<>();
SoftReference<V> ref = new SoftReference<>(value, queue);

// 백그라운드 정리
Reference<?> cleared = queue.poll();
if (cleared != null) {
    K key = reverseMap.remove(cleared);
    storage.remove(key);
}
```

---

## 핵심 디자인 패턴

### 1. Strategy 패턴

**목적**: 런타임에 알고리즘 변경 가능

**적용 위치**:
- EvictionPolicy (LRU, LFU, FIFO, RANDOM, TTL)
- LoadStrategy (SYNC, ASYNC)
- WriteStrategy (READ_ONLY, WRITE_THROUGH, WRITE_BEHIND)
- RefreshStrategy (ON_MISS, REFRESH_AHEAD)
- ReferenceType (STRONG, SOFT, WEAK)

**구조**:
```
┌─────────────────┐
│ EvictionPolicy  │ (Enum)
├─────────────────┤
│ + LRU           │
│ + LFU           │
│ + FIFO          │
└────────┬────────┘
         │ creates
         ▼
┌─────────────────────┐
│ EvictionStrategy<K> │ (Interface)
├─────────────────────┤
│ + onAccess(K)       │
│ + onPut(K)          │
│ + selectVictim()    │
└─────────────────────┘
         ▲
         │ implements
    ┌────┴────┬────────┬──────────┐
    │         │        │          │
┌───┴────┐ ┌─┴─────┐ ┌┴──────┐ ┌─┴────────┐
│  LRU   │ │  LFU  │ │ FIFO  │ │ RANDOM   │
└────────┘ └───────┘ └───────┘ └──────────┘
```

**예시**:
```java
// 런타임에 정책 변경
SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
    .evictionPolicy(EvictionPolicy.LRU)  // 전략 선택
    .build();

// 내부 동작
EvictionStrategy<K> strategy = evictionPolicy.createStrategy();
strategy.onAccess(key);  // 액세스 기록
K victim = strategy.selectVictim(keys);  // 희생자 선택
```

---

### 2. Builder 패턴

**목적**: 복잡한 객체 생성을 단순화

**적용 위치**: `SBCacheMap.Builder`

**장점**:
- 가독성 향상 (메서드 체이닝)
- 선택적 파라미터 지원
- 불변 객체 생성
- 유효성 검증 집중화

**구조**:
```java
public class SBCacheMap<K, V> {

    // 생성자는 protected (Builder를 통해서만 생성)
    protected SBCacheMap(Builder<K, V> builder) {
        this.timeoutSec = builder.timeoutSec;
        this.maxSize = builder.maxSize;
        // ...
    }

    public static class Builder<K, V> {
        // 필수 필드
        private int timeoutSec;

        // 선택 필드 (기본값)
        private int maxSize = 0;
        private EvictionPolicy evictionPolicy = EvictionPolicy.LRU;
        private ReferenceType referenceType = ReferenceType.STRONG;
        // ...

        public Builder<K, V> timeoutSec(int timeoutSec) {
            this.timeoutSec = timeoutSec;
            return this;
        }

        public Builder<K, V> maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public SBCacheMap<K, V> build() {
            validate();  // 유효성 검증
            return new SBCacheMap<>(this);
        }
    }

    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }
}
```

**사용 예시**:
```java
SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
    .timeoutSec(300)              // 필수
    .maxSize(1000)                // 선택
    .evictionPolicy(EvictionPolicy.LRU)  // 선택
    .referenceType(ReferenceType.SOFT)   // 선택
    .enableMetrics(true)          // 선택
    .build();
```

---

### 3. Template Method 패턴

**목적**: 공통 알고리즘 재사용, 확장 포인트 제공

**적용 위치**: `SBCacheMap.get()` 메서드

**구조**:
```java
public V get(Object key) throws SBCacheLoadFailException {
    // 1. 공통: TTL 체크
    if (isExpired(key)) {
        remove(key);
    }

    // 2. 공통: 캐시 조회
    V value = data.get(key);

    if (value != null) {
        // 3-1. 히트: 공통 처리
        onCacheHit(key);
        return value;
    } else {
        // 3-2. 미스: 확장 포인트
        return handleCacheMiss(key);  // Template Method
    }
}

// 확장 포인트: Loader 전략에 따라 동작 달라짐
protected V handleCacheMiss(K key) throws SBCacheLoadFailException {
    if (loader == null) {
        return null;
    }

    switch (loadStrategy) {
        case SYNC:
            return loadSync(key);  // 동기 로딩
        case ASYNC:
            return loadAsync(key); // 비동기 로딩 (중복 방지)
        default:
            return loadSync(key);
    }
}
```

---

### 4. Observer 패턴 (변형)

**목적**: 상태 변경 시 전략 객체에 통지

**적용 위치**: EvictionStrategy 통지

**구조**:
```java
public V put(K key, V value) {
    V oldValue = data.put(key, value);

    // Observer: 전략 객체에 이벤트 통지
    if (evictionStrategy != null) {
        evictionStrategy.onPut(key);  // 삽입 이벤트
    }

    // 축출 필요 시
    if (needsEviction()) {
        K victim = evictionStrategy.selectVictim(data.keySet());
        remove(victim);
    }

    return oldValue;
}

public V get(Object key) {
    V value = data.get(key);

    if (value != null && evictionStrategy != null) {
        evictionStrategy.onAccess((K) key);  // 액세스 이벤트
    }

    return value;
}
```

---

### 5. Decorator 패턴 (Reference 래핑)

**목적**: 객체에 동적으로 기능 추가 (GC 협력)

**적용 위치**: `ReferenceBasedStorage.wrapValue()`

**구조**:
```java
// 원본 값
V value = new User("Alice");

// Decorator: Reference로 감싸기
Object wrapped = wrapValue(key, value);

// STRONG: value 그대로
// SOFT:   new SoftReference<>(value, queue)
// WEAK:   new WeakReference<>(value, queue)

// 사용 시 자동 언래핑
V unwrapped = unwrapValue(wrapped);
```

---

## 확장 포인트

SB Cached Collection은 다음과 같은 확장 포인트를 제공합니다.

### 1. Loader 확장

**인터페이스**: `SBCacheMapLoader<K, V>`

**확장 방법**:
```java
public class CustomLoader implements SBCacheMapLoader<String, MyData> {
    @Override
    public MyData loadOne(String key) throws SBCacheLoadFailException {
        // 커스텀 로딩 로직
        return externalApi.fetchData(key);
    }

    @Override
    public Map<String, MyData> loadAll() throws SBCacheLoadFailException {
        // 전체 로딩 로직
        return externalApi.fetchAllData();
    }
}
```

**사용 예시**:
```java
SBCacheMap<String, MyData> cache = SBCacheMap.<String, MyData>builder()
    .loader(new CustomLoader())
    .timeoutSec(300)
    .build();
```

---

### 2. Writer 확장

**인터페이스**: `SBCacheMapWriter<K, V>`

**확장 방법**:
```java
public class CustomWriter implements SBCacheMapWriter<Long, User> {
    @Override
    public void writeOne(Long key, User value) throws SBCacheWriteFailException {
        // 커스텀 쓰기 로직
        database.save(value);
    }

    @Override
    public void writeAll(Map<Long, User> entries) throws SBCacheWriteFailException {
        // 일괄 쓰기 로직
        database.batchSave(entries.values());
    }

    @Override
    public void deleteOne(Long key) throws SBCacheWriteFailException {
        database.delete(key);
    }

    @Override
    public void deleteAll(Collection<Long> keys) throws SBCacheWriteFailException {
        database.batchDelete(keys);
    }
}
```

---

### 3. EvictionStrategy 확장

**인터페이스**: `EvictionStrategy<K>`

**확장 방법**:
```java
public class MRUEvictionStrategy<K> implements EvictionStrategy<K> {
    private final Map<K, Long> accessTimes = new ConcurrentHashMap<>();

    @Override
    public void onAccess(K key) {
        accessTimes.put(key, System.nanoTime());
    }

    @Override
    public void onPut(K key) {
        accessTimes.put(key, System.nanoTime());
    }

    @Override
    public K selectVictim(Set<K> keys) {
        // MRU: 가장 최근에 사용된 항목 제거
        return keys.stream()
            .max(Comparator.comparing(accessTimes::get))
            .orElse(keys.iterator().next());
    }

    @Override
    public void onRemove(K key) {
        accessTimes.remove(key);
    }

    @Override
    public void clear() {
        accessTimes.clear();
    }
}
```

---

### 4. Spring CacheResolver 확장

**목적**: 동적 캐시 선택

**확장 방법**:
```java
@Bean
public CacheResolver customCacheResolver(CacheManager cacheManager) {
    return context -> {
        Object[] args = context.getArgs();
        String cacheName;

        // 동적 캐시 선택 로직
        if (args.length > 0 && args[0] instanceof String) {
            String region = (String) args[0];
            cacheName = "cache-" + region;
        } else {
            cacheName = "default";
        }

        return Collections.singleton(cacheManager.getCache(cacheName));
    };
}
```

---

## 스레드 안전성

SB Cached Collection은 멀티스레드 환경에서 안전하게 동작합니다.

### Thread-Safe 보장 메커니즘

#### 1. ConcurrentHashMap 사용

**위치**: `ReferenceBasedStorage.storage`

**특징**:
- Lock-free 읽기 (get)
- 세그먼트 기반 잠금 (put, remove)
- 높은 동시성 성능

```java
private final ConcurrentHashMap<K, Object> storage = new ConcurrentHashMap<>();
```

---

#### 2. 동기화 블록 (synchronized)

**위치**: `SBCacheMap.get()`, `put()`, `remove()`

**목적**: 복합 연산 원자성 보장

```java
public V get(Object key) throws SBCacheLoadFailException {
    synchronized (lock) {
        // TTL 체크 + 캐시 조회 + 로딩: 원자적 수행
        if (isExpired(key)) {
            remove(key);
        }
        V value = data.get(key);
        if (value == null && loader != null) {
            value = loader.loadOne((K) key);
            data.put((K) key, value);
        }
        return value;
    }
}
```

---

#### 3. ASYNC LoadStrategy (중복 로딩 방지)

**문제**: 동일한 키에 대해 여러 스레드가 동시에 로딩 시도

**해결**: 첫 번째 스레드만 로딩, 나머지는 대기

```java
private final ConcurrentHashMap<K, CompletableFuture<V>> loadingKeys = new ConcurrentHashMap<>();

protected V loadAsync(K key) throws SBCacheLoadFailException {
    CompletableFuture<V> future = loadingKeys.computeIfAbsent(key, k ->
        CompletableFuture.supplyAsync(() -> {
            try {
                return loader.loadOne(k);
            } finally {
                loadingKeys.remove(k);
            }
        })
    );

    return future.join();  // 대기
}
```

**동작**:
```
Thread 1: miss → loadingKeys.put(key, future) → load → return
Thread 2: miss → loadingKeys.get(key) → future.join() → (Thread 1 완료 대기) → return
Thread 3: miss → loadingKeys.get(key) → future.join() → (Thread 1 완료 대기) → return
```

---

#### 4. Volatile 필드

**목적**: 가시성 보장

```java
private volatile boolean closed = false;

public void close() {
    if (closed) {
        return;
    }
    closed = true;  // 모든 스레드에게 즉시 보임
    // 리소스 정리...
}
```

---

#### 5. AtomicLong (통계 카운터)

**목적**: Lock-free 카운팅

```java
private final AtomicLong hitCount = new AtomicLong(0);
private final AtomicLong missCount = new AtomicLong(0);

private void onCacheHit() {
    hitCount.incrementAndGet();  // 원자적 증가
}

private void onCacheMiss() {
    missCount.incrementAndGet();
}
```

---

### 교착 상태 (Deadlock) 방지

**원칙**: 잠금 순서 일관성 유지

```java
// ❌ 잘못된 예: 순환 잠금
synchronized (lockA) {
    synchronized (lockB) {
        // ...
    }
}

// 다른 곳에서:
synchronized (lockB) {
    synchronized (lockA) {  // Deadlock 발생!
        // ...
    }
}
```

**SB Cached Collection의 해결**:
- 단일 잠금 객체 사용 (`private final Object lock`)
- 잠금 범위 최소화 (필요한 부분만)
- 외부 호출 시 잠금 해제 (Loader/Writer 호출 전)

---

## 메모리 관리

### 1. ReferenceType에 따른 메모리 동작

| ReferenceType | 메모리 동작 | GC 영향 | 사용 시기 |
|---------------|------------|---------|----------|
| STRONG | 명시적 제거 전까지 유지 | 없음 | 소규모 캐시 |
| SOFT | 메모리 부족 시 회수 | OOM 전에 회수 | 대용량 캐시 |
| WEAK | 다음 GC에서 회수 | 매우 큼 | 임시 캐시 |

---

### 2. ReferenceQueue 자동 정리

**목적**: GC에 의해 회수된 항목 자동 제거

```java
private void processQueue() {
    if (referenceQueue == null) {
        return;
    }

    Reference<? extends V> ref;
    while ((ref = referenceQueue.poll()) != null) {
        K key = reverseMap.remove(ref);
        if (key != null) {
            storage.remove(key);  // 회수된 항목 정리
        }
    }
}
```

**호출 시점**:
- `get()`, `put()`, `size()`, `keySet()` 등 주요 메서드 실행 시

---

### 3. MaxSize 축출

**목적**: 메모리 사용량 제한

**동작**:
```java
if (maxSize > 0 && data.size() >= maxSize) {
    K victim = evictionStrategy.selectVictim(data.keySet());
    remove(victim);
}
```

---

### 4. Auto Cleanup (TTL 만료 자동 정리)

**목적**: 만료된 항목 주기적으로 제거

**구현**:
```java
if (autoCleanup) {
    ScheduledExecutorService cleaner = Executors.newScheduledThreadPool(1);
    cleaner.scheduleAtFixedRate(() -> {
        Set<K> expiredKeys = findExpiredKeys();
        expiredKeys.forEach(this::remove);
    }, timeoutSec, timeoutSec, TimeUnit.SECONDS);
}
```

---

## 성능 최적화

### 1. ConcurrentHashMap 선택

**이유**:
- Lock-free 읽기 (높은 동시성)
- 세그먼트 기반 잠금 (쓰기 분산)
- O(1) 평균 시간 복잡도

**대안 비교**:

| 구현 | 읽기 성능 | 쓰기 성능 | 동시성 | Thread-Safe |
|------|----------|----------|--------|-------------|
| HashMap | O(1) | O(1) | 낮음 | ❌ |
| Hashtable | O(1) | O(1) | 매우 낮음 | ✅ (전체 잠금) |
| Collections.synchronizedMap | O(1) | O(1) | 낮음 | ✅ (전체 잠금) |
| **ConcurrentHashMap** | **O(1)** | **O(1)** | **높음** | **✅ (세그먼트 잠금)** |

---

### 2. ASYNC LoadStrategy

**효과**: Thundering Herd 방지

**성능 비교**:

**SYNC (중복 로딩)**:
```
Thread 1: load (300ms)
Thread 2: load (300ms)
Thread 3: load (300ms)
총 DB 쿼리: 3회, 총 시간: 300ms (병렬), 900ms (순차)
```

**ASYNC (한 번만 로딩)**:
```
Thread 1: load (300ms)
Thread 2: wait → return
Thread 3: wait → return
총 DB 쿼리: 1회, 총 시간: 300ms
```

---

### 3. Write-Behind 배치 처리

**효과**: 쓰기 성능 향상

**동작**:
```java
// Write-Behind: 큐에 추가만
queue.add(new WriteRequest(key, value));  // O(1)

// 백그라운드 배치 처리
scheduledExecutor.scheduleAtFixedRate(() -> {
    List<WriteRequest> batch = queue.drain();
    writer.writeAll(batch);  // 일괄 쓰기
}, 1, 1, TimeUnit.SECONDS);
```

**성능 비교**:

| 전략 | 단일 쓰기 | 100개 쓰기 | DB 부하 |
|------|----------|-----------|---------|
| WRITE_THROUGH | 10ms | 1000ms | 높음 |
| **WRITE_BEHIND** | **1ms** | **100ms** | **낮음** |

---

### 4. Refresh-Ahead 사전 갱신

**효과**: 캐시 미스 최소화

**동작**:
```java
if (refreshStrategy == RefreshStrategy.REFRESH_AHEAD) {
    long age = System.currentTimeMillis() - creationTimes.get(key);
    if (age > timeoutSec * 500) {  // 50% 도달
        executor.submit(() -> {
            V fresh = loader.loadOne(key);
            put(key, fresh);
        });
    }
}
```

**효과**:
- 사용자는 항상 캐시 히트 경험
- 백그라운드에서 갱신 → 응답 시간 단축

---

### 5. 통계 수집 최적화

**방법**: AtomicLong 사용

```java
// ❌ 느림: synchronized
private long hitCount = 0;
public synchronized void incrementHit() {
    hitCount++;
}

// ✅ 빠름: AtomicLong
private final AtomicLong hitCount = new AtomicLong(0);
public void incrementHit() {
    hitCount.incrementAndGet();  // Lock-free
}
```

---

## 참고 자료

- [USER_GUIDE.md](USER_GUIDE.md) - 종합 사용자 가이드
- [SPRING_INTEGRATION.md](SPRING_INTEGRATION.md) - Spring 통합 가이드
- [API_REFERENCE.md](API_REFERENCE.md) - 전체 API 레퍼런스
- [Java Concurrency in Practice](https://jcip.net/) - 동시성 패턴
- [Effective Java (3rd Edition)](https://www.oreilly.com/library/view/effective-java-3rd/9780134686097/) - 디자인 패턴
