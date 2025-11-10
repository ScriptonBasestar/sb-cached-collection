# API Reference

SB Cached Collection의 전체 API 레퍼런스입니다.

## 목차

1. [cache-core 모듈](#cache-core-모듈)
   - [Strategy 패키지](#strategy-패키지)
   - [Loader 패키지](#loader-패키지)
   - [Writer 패키지](#writer-패키지)
   - [Exception 패키지](#exception-패키지)
2. [cache-collection 모듈](#cache-collection-모듈)
   - [SBCacheMap](#sbcachemap)
   - [ReferenceBasedStorage](#referencebasedstorage)
3. [cache-loader 모듈](#cache-loader-모듈)
   - [JDBCLoader](#jdbcloader)
   - [FileLoader](#fileloader)
4. [cache-spring 모듈](#cache-spring-모듈)
   - [SBCacheManager](#sbcachemanager)
   - [SBCache](#sbcache)
   - [Auto-Configuration](#auto-configuration)

---

## cache-core 모듈

핵심 인터페이스와 전략 패턴 구현체를 제공하는 모듈입니다.

### Strategy 패키지

#### ReferenceType

**위치**: `org.scriptonbasestar.cache.core.strategy.ReferenceType`

캐시 항목의 참조 타입을 정의하는 Enum입니다.

**Enum 상수**:

| 상수 | 설명 | GC 동작 | 사용 시기 |
|------|------|---------|----------|
| `STRONG` | 강한 참조 (기본값) | GC가 절대 회수하지 않음 | 대부분의 경우, 예측 가능한 캐시 |
| `SOFT` | 약한 참조 | 메모리 부족 시에만 회수 | 대용량 캐시, 이미지/파일 캐시 |
| `WEAK` | 매우 약한 참조 | 다음 GC 사이클에서 회수 | 임시 캐시, 메모리 누수 방지 |

**메서드**:

```java
public String getDescription()
```
- **반환**: 참조 타입에 대한 설명 문자열
- **예시**: `"Strong reference - Never garbage collected"`

```java
public boolean isGcManaged()
```
- **반환**: GC에 의해 자동 회수 가능하면 `true`
- **설명**: SOFT, WEAK인 경우 `true`, STRONG인 경우 `false`

**사용 예시**:

```java
SBCacheMap<String, byte[]> imageCache = SBCacheMap.<String, byte[]>builder()
    .timeoutSec(3600)
    .referenceType(ReferenceType.SOFT)  // 메모리 압박 시 자동 회수
    .build();
```

---

#### EvictionPolicy

**위치**: `org.scriptonbasestar.cache.core.strategy.EvictionPolicy`

캐시가 `maxSize`에 도달했을 때 어떤 항목을 제거할지 결정하는 정책입니다.

**Enum 상수**:

| 정책 | 전략 | 설명 | 사용 시기 |
|------|------|------|----------|
| `LRU` | Least Recently Used | 가장 오래 전에 액세스된 항목 제거 | 일반적인 캐시 (기본값) |
| `LFU` | Least Frequently Used | 가장 적게 액세스된 항목 제거 | 인기도 기반 캐시 |
| `FIFO` | First In First Out | 가장 먼저 추가된 항목 제거 | 시간순 데이터 |
| `RANDOM` | Random | 무작위 항목 제거 | 성능 우선, 정책 무관 |
| `TTL` | Time To Live | 가장 오래된 항목 (생성 시간 기준) 제거 | 시간 기반 만료 |

**메서드**:

```java
public EvictionStrategy<K> createStrategy()
```
- **반환**: 해당 정책의 `EvictionStrategy` 구현체
- **설명**: 각 정책에 맞는 전략 객체 생성

**사용 예시**:

```java
// LRU 정책 (가장 최근에 사용되지 않은 항목 제거)
SBCacheMap<Long, User> userCache = SBCacheMap.<Long, User>builder()
    .timeoutSec(300)
    .maxSize(1000)
    .evictionPolicy(EvictionPolicy.LRU)
    .build();

// LFU 정책 (가장 적게 사용된 항목 제거)
SBCacheMap<String, Product> productCache = SBCacheMap.<String, Product>builder()
    .timeoutSec(600)
    .maxSize(5000)
    .evictionPolicy(EvictionPolicy.LFU)
    .build();
```

---

#### LoadStrategy

**위치**: `org.scriptonbasestar.cache.core.strategy.LoadStrategy`

캐시 미스 시 데이터를 로딩하는 방식을 정의합니다.

**Enum 상수**:

| 전략 | 설명 | 동작 방식 | 사용 시기 |
|------|------|----------|----------|
| `SYNC` | 동기 로딩 (기본값) | 요청 스레드가 직접 로딩 | 빠른 데이터 소스 |
| `ASYNC` | 비동기 로딩 | 첫 요청 스레드만 로딩, 나머지는 대기 | 느린 데이터 소스, Thundering Herd 방지 |

**동작 비교**:

**SYNC (동기)**:
```
Thread 1: miss → load → return
Thread 2: miss → load → return
Thread 3: miss → load → return
결과: 동일한 키에 대해 3번 로딩 발생 (중복)
```

**ASYNC (비동기)**:
```
Thread 1: miss → load → return
Thread 2: miss → wait → (Thread 1 완료) → return
Thread 3: miss → wait → (Thread 1 완료) → return
결과: 동일한 키에 대해 1번만 로딩 (효율적)
```

**사용 예시**:

```java
// ASYNC 로딩 (중복 로딩 방지)
SBCacheMap<String, Report> reportCache = SBCacheMap.<String, Report>builder()
    .loader(heavyReportLoader)
    .loadStrategy(LoadStrategy.ASYNC)  // 한 스레드만 로딩
    .timeoutSec(600)
    .build();
```

---

#### WriteStrategy

**위치**: `org.scriptonbasestar.cache.core.strategy.WriteStrategy`

캐시 쓰기 시 백엔드 저장소와의 동기화 방식을 정의합니다.

**Enum 상수**:

| 전략 | 설명 | 동작 방식 | 장점 | 단점 |
|------|------|----------|------|------|
| `READ_ONLY` | 읽기 전용 | 쓰기 작업 없음 | 단순, 안전 | 쓰기 불가 |
| `WRITE_THROUGH` | 동기 쓰기 | 캐시와 백엔드에 동시 쓰기 | 데이터 일관성 보장 | 쓰기 지연 증가 |
| `WRITE_BEHIND` | 비동기 쓰기 | 캐시에만 먼저 쓰고, 백엔드는 나중에 | 쓰기 성능 향상 | 데이터 손실 위험 |

**동작 비교**:

**WRITE_THROUGH (동기 쓰기)**:
```java
cache.put(key, value);
// 1. 캐시에 쓰기
// 2. writer.writeOne() 호출 (동기)
// 3. 성공 시 반환
// → 느리지만 즉시 영속화
```

**WRITE_BEHIND (비동기 쓰기)**:
```java
cache.put(key, value);
// 1. 캐시에 쓰기
// 2. 쓰기 큐에 추가
// 3. 즉시 반환
// 4. 백그라운드 스레드가 나중에 writer.writeOne() 호출
// → 빠르지만 지연 영속화
```

**사용 예시**:

```java
// WRITE_THROUGH: 즉시 DB 반영
SBCacheMap<Long, User> userCache = SBCacheMap.<Long, User>builder()
    .loader(userLoader)
    .writer(userWriter)
    .writeStrategy(WriteStrategy.WRITE_THROUGH)
    .timeoutSec(300)
    .build();

// WRITE_BEHIND: 비동기 DB 반영
SBCacheMap<String, SessionData> sessionCache = SBCacheMap.<String, SessionData>builder()
    .loader(sessionLoader)
    .writer(sessionWriter)
    .writeStrategy(WriteStrategy.WRITE_BEHIND)
    .timeoutSec(1800)
    .build();
```

---

#### RefreshStrategy

**위치**: `org.scriptonbasestar.cache.core.strategy.RefreshStrategy`

캐시 항목의 갱신 방식을 정의합니다.

**Enum 상수**:

| 전략 | 설명 | 동작 방식 | 사용 시기 |
|------|------|----------|----------|
| `ON_MISS` | 미스 시 갱신 (기본값) | 캐시 미스 시에만 로딩 | 일반적인 캐시 |
| `REFRESH_AHEAD` | 미리 갱신 | TTL 50% 도달 시 백그라운드 갱신 | 항상 최신 데이터 필요 |

**동작 비교**:

**ON_MISS (미스 시 갱신)**:
```
T=0: cache.get(key) → miss → load (300ms) → return
T=300: 캐시 히트 (즉시 반환)
T=600: TTL 만료 → cache.get(key) → miss → load (300ms) → return
```

**REFRESH_AHEAD (미리 갱신)**:
```
T=0: cache.get(key) → miss → load (300ms) → return
T=300: 캐시 히트 (즉시 반환) + 백그라운드 갱신 시작
T=600: 캐시 히트 (즉시 반환, 이미 갱신됨)
```

**사용 예시**:

```java
// REFRESH_AHEAD: 항상 신선한 데이터 유지
SBCacheMap<String, StockPrice> stockCache = SBCacheMap.<String, StockPrice>builder()
    .loader(stockPriceLoader)
    .timeoutSec(60)  // 1분 TTL
    .refreshStrategy(RefreshStrategy.REFRESH_AHEAD)  // 30초 후 백그라운드 갱신
    .build();
```

---

#### EvictionStrategy<K>

**위치**: `org.scriptonbasestar.cache.core.strategy.EvictionStrategy`

축출 정책의 실제 구현 인터페이스입니다.

**메서드**:

```java
void onAccess(K key)
```
- **설명**: 항목이 액세스될 때 호출
- **파라미터**: `key` - 액세스된 키
- **용도**: LRU, LFU 등에서 액세스 기록 추적

```java
void onPut(K key)
```
- **설명**: 항목이 추가될 때 호출
- **파라미터**: `key` - 추가된 키
- **용도**: FIFO, TTL 등에서 삽입 시간 기록

```java
void onRemove(K key)
```
- **설명**: 항목이 제거될 때 호출
- **파라미터**: `key` - 제거된 키
- **용도**: 내부 메타데이터 정리

```java
K selectVictim(Set<K> keys)
```
- **반환**: 제거할 항목의 키
- **파라미터**: `keys` - 현재 캐시에 있는 모든 키
- **설명**: maxSize 초과 시 어떤 항목을 제거할지 선택

```java
void clear()
```
- **설명**: 모든 내부 상태 초기화
- **용도**: `cache.removeAll()` 호출 시

---

### Loader 패키지

#### SBCacheMapLoader<K, V>

**위치**: `org.scriptonbasestar.cache.core.loader.SBCacheMapLoader`

Map 기반 캐시의 데이터 로딩 인터페이스입니다.

**메서드**:

```java
V loadOne(K key) throws SBCacheLoadFailException
```
- **반환**: 로딩된 값
- **파라미터**: `key` - 로딩할 키
- **예외**: `SBCacheLoadFailException` - 로딩 실패 시
- **설명**: 단일 키에 대한 값 로딩

```java
Map<K, V> loadAll() throws SBCacheLoadFailException
```
- **반환**: 모든 키-값 쌍
- **예외**: `SBCacheLoadFailException` - 로딩 실패 시
- **설명**: 전체 데이터 로딩 (캐시 워밍업용)

**구현 예시**:

```java
SBCacheMapLoader<Long, User> userLoader = new SBCacheMapLoader<>() {
    @Override
    public User loadOne(Long id) throws SBCacheLoadFailException {
        return userRepository.findById(id)
            .orElseThrow(() -> new SBCacheLoadFailException("User not found: " + id));
    }

    @Override
    public Map<Long, User> loadAll() throws SBCacheLoadFailException {
        List<User> users = userRepository.findAll();
        return users.stream()
            .collect(Collectors.toMap(User::getId, user -> user));
    }
};
```

---

#### SBCacheListLoader<K, V>

**위치**: `org.scriptonbasestar.cache.core.loader.SBCacheListLoader`

List 기반 캐시의 데이터 로딩 인터페이스입니다.

**메서드**:

```java
List<V> loadOne(K key) throws SBCacheLoadFailException
```
- **반환**: 로딩된 값 리스트
- **파라미터**: `key` - 로딩할 키
- **예외**: `SBCacheLoadFailException` - 로딩 실패 시
- **설명**: 단일 키에 대한 값 리스트 로딩

```java
Map<K, List<V>> loadAll() throws SBCacheLoadFailException
```
- **반환**: 모든 키-값리스트 쌍
- **예외**: `SBCacheLoadFailException` - 로딩 실패 시
- **설명**: 전체 데이터 로딩

**사용 예시**:

```java
// 사용자별 주문 목록 로딩
SBCacheListLoader<Long, Order> orderLoader = new SBCacheListLoader<>() {
    @Override
    public List<Order> loadOne(Long userId) throws SBCacheLoadFailException {
        return orderRepository.findByUserId(userId);
    }

    @Override
    public Map<Long, List<Order>> loadAll() throws SBCacheLoadFailException {
        List<Order> allOrders = orderRepository.findAll();
        return allOrders.stream()
            .collect(Collectors.groupingBy(Order::getUserId));
    }
};
```

---

### Writer 패키지

#### SBCacheMapWriter<K, V>

**위치**: `org.scriptonbasestar.cache.core.writer.SBCacheMapWriter`

캐시 데이터를 백엔드 저장소에 쓰는 인터페이스입니다.

**메서드**:

```java
void writeOne(K key, V value) throws SBCacheWriteFailException
```
- **파라미터**:
  - `key` - 쓸 키
  - `value` - 쓸 값
- **예외**: `SBCacheWriteFailException` - 쓰기 실패 시
- **설명**: 단일 항목 쓰기

```java
void writeAll(Map<K, V> entries) throws SBCacheWriteFailException
```
- **파라미터**: `entries` - 쓸 항목들
- **예외**: `SBCacheWriteFailException` - 쓰기 실패 시
- **설명**: 일괄 쓰기 (성능 최적화)

```java
void deleteOne(K key) throws SBCacheWriteFailException
```
- **파라미터**: `key` - 삭제할 키
- **예외**: `SBCacheWriteFailException` - 삭제 실패 시
- **설명**: 단일 항목 삭제

```java
void deleteAll(Collection<K> keys) throws SBCacheWriteFailException
```
- **파라미터**: `keys` - 삭제할 키들
- **예외**: `SBCacheWriteFailException` - 삭제 실패 시
- **설명**: 일괄 삭제

**구현 예시**:

```java
SBCacheMapWriter<Long, User> userWriter = new SBCacheMapWriter<>() {
    @Override
    public void writeOne(Long id, User user) throws SBCacheWriteFailException {
        try {
            userRepository.save(user);
        } catch (Exception e) {
            throw new SBCacheWriteFailException("Failed to write user: " + id, e);
        }
    }

    @Override
    public void writeAll(Map<Long, User> entries) throws SBCacheWriteFailException {
        try {
            userRepository.saveAll(entries.values());
        } catch (Exception e) {
            throw new SBCacheWriteFailException("Failed to write users", e);
        }
    }

    @Override
    public void deleteOne(Long id) throws SBCacheWriteFailException {
        try {
            userRepository.deleteById(id);
        } catch (Exception e) {
            throw new SBCacheWriteFailException("Failed to delete user: " + id, e);
        }
    }

    @Override
    public void deleteAll(Collection<Long> ids) throws SBCacheWriteFailException {
        try {
            userRepository.deleteAllById(ids);
        } catch (Exception e) {
            throw new SBCacheWriteFailException("Failed to delete users", e);
        }
    }
};
```

---

### Exception 패키지

#### SBCacheLoadFailException

**위치**: `org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException`

캐시 로딩 실패 시 발생하는 예외입니다.

**생성자**:

```java
public SBCacheLoadFailException(String message)
public SBCacheLoadFailException(String message, Throwable cause)
```

**사용 예시**:

```java
@Override
public User loadOne(Long id) throws SBCacheLoadFailException {
    try {
        return userRepository.findById(id)
            .orElseThrow(() -> new SBCacheLoadFailException("User not found: " + id));
    } catch (DatabaseException e) {
        throw new SBCacheLoadFailException("Database error while loading user: " + id, e);
    }
}
```

---

#### SBCacheWriteFailException

**위치**: `org.scriptonbasestar.cache.core.exception.SBCacheWriteFailException`

캐시 쓰기 실패 시 발생하는 예외입니다.

**생성자**:

```java
public SBCacheWriteFailException(String message)
public SBCacheWriteFailException(String message, Throwable cause)
```

---

## cache-collection 모듈

실제 캐시 구현체를 제공하는 모듈입니다.

### SBCacheMap<K, V>

**위치**: `org.scriptonbasestar.cache.collection.map.SBCacheMap`

Map 기반의 메인 캐시 구현체입니다.

#### 생성자

```java
// 기본 생성자
public SBCacheMap(int timeoutSec)
public SBCacheMap(int timeoutSec, int maxSize)
public SBCacheMap(int timeoutSec, int maxSize, EvictionPolicy evictionPolicy)
public SBCacheMap(int timeoutSec, int maxSize, EvictionPolicy evictionPolicy, ReferenceType referenceType)

// Loader 포함
public SBCacheMap(SBCacheMapLoader<K, V> loader, int timeoutSec)
public SBCacheMap(SBCacheMapLoader<K, V> loader, int timeoutSec, int maxSize)
public SBCacheMap(SBCacheMapLoader<K, V> loader, int timeoutSec, int maxSize, EvictionPolicy evictionPolicy)
```

#### Builder 패턴

```java
SBCacheMap<K, V> cache = SBCacheMap.<K, V>builder()
    // 필수 설정
    .timeoutSec(300)

    // 선택 설정
    .loader(myLoader)
    .writer(myWriter)
    .maxSize(1000)
    .evictionPolicy(EvictionPolicy.LRU)
    .referenceType(ReferenceType.STRONG)
    .loadStrategy(LoadStrategy.ASYNC)
    .writeStrategy(WriteStrategy.WRITE_THROUGH)
    .refreshStrategy(RefreshStrategy.REFRESH_AHEAD)
    .enableMetrics(true)
    .autoCleanup(true)
    .absoluteExpiry(true)

    .build();
```

#### 주요 메서드

**기본 Map 연산**:

```java
V get(Object key) throws SBCacheLoadFailException
```
- **반환**: 캐시된 값 또는 로딩된 값
- **예외**: `SBCacheLoadFailException` - 로딩 실패 시
- **설명**: 캐시에서 값 조회, 미스 시 loader 호출

```java
V put(K key, V value)
```
- **반환**: 이전 값 (없으면 null)
- **설명**: 캐시에 값 저장, writer가 있으면 백엔드에도 쓰기

```java
V remove(Object key)
```
- **반환**: 제거된 값 (없으면 null)
- **설명**: 캐시에서 값 제거, writer가 있으면 백엔드에서도 삭제

```java
void removeAll()
```
- **설명**: 모든 캐시 항목 제거

```java
int size()
```
- **반환**: 현재 캐시 크기

```java
Map<K, V> getAll()
```
- **반환**: 모든 캐시 항목의 불변 복사본

---

**통계 메서드**:

```java
double getHitRate()
```
- **반환**: 캐시 히트율 (0.0 ~ 1.0)
- **설명**: `hitCount / (hitCount + missCount)`

```java
double getMissRate()
```
- **반환**: 캐시 미스율 (0.0 ~ 1.0)

```java
long getLoadCount()
```
- **반환**: 총 로딩 횟수

```java
long getTotalLoadTime()
```
- **반환**: 총 로딩 시간 (밀리초)

```java
double getAverageLoadTime()
```
- **반환**: 평균 로딩 시간 (밀리초)

---

**설정 조회 메서드**:

```java
int getTimeoutSec()
int getMaxSize()
EvictionPolicy getEvictionPolicy()
ReferenceType getReferenceType()
LoadStrategy getLoadStrategy()
WriteStrategy getWriteStrategy()
RefreshStrategy getRefreshStrategy()
boolean isMetricsEnabled()
boolean isAutoCleanup()
boolean isAbsoluteExpiry()
```

---

**리소스 관리**:

```java
void close()
```
- **설명**: 캐시 종료, 백그라운드 스레드 정리
- **용도**: try-with-resources 또는 명시적 종료

**사용 예시**:

```java
try (SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
        .loader(userLoader)
        .timeoutSec(300)
        .build()) {

    User user = cache.get(123L);
    cache.put(456L, newUser);

} // 자동으로 close() 호출됨
```

---

### ReferenceBasedStorage<K, V>

**위치**: `org.scriptonbasestar.cache.collection.storage.ReferenceBasedStorage`

Reference 기반 캐시 저장소입니다. GC와 협력하여 메모리를 관리합니다.

#### 생성자

```java
public ReferenceBasedStorage(ReferenceType referenceType)
```

#### 주요 메서드

```java
V put(K key, V value)
V get(K key)
V remove(K key)
void clear()
int size()
Set<K> keySet()
void putAll(Map<? extends K, ? extends V> map)
Map<K, V> toMap()
ReferenceType getReferenceType()
```

**내부 동작**:

- **STRONG**: 값을 그대로 저장 (`storage.put(key, value)`)
- **SOFT**: `SoftReference`로 감싸서 저장 (`storage.put(key, new SoftReference<>(value))`)
- **WEAK**: `WeakReference`로 감싸서 저장 (`storage.put(key, new WeakReference<>(value))`)
- **ReferenceQueue**: GC에 의해 회수된 Reference 자동 정리

---

## cache-loader 모듈

다양한 데이터 소스를 위한 Loader 구현체를 제공합니다.

### JDBCLoader

**위치**: `org.scriptonbasestar.cache.loader.jdbc.JDBCLoader`

JDBC를 통해 데이터베이스에서 데이터를 로딩하는 Loader입니다.

**생성자**:

```java
public JDBCLoader(DataSource dataSource, String tableName, String keyColumn, String valueColumn)
```

**파라미터**:
- `dataSource`: JDBC DataSource
- `tableName`: 테이블 이름
- `keyColumn`: 키 컬럼명
- `valueColumn`: 값 컬럼명

**사용 예시**:

```java
DataSource dataSource = new HikariDataSource(config);

SBCacheMapLoader<String, String> jdbcLoader = new JDBCLoader(
    dataSource,
    "config_table",
    "config_key",
    "config_value"
);

SBCacheMap<String, String> configCache = SBCacheMap.<String, String>builder()
    .loader(jdbcLoader)
    .timeoutSec(600)
    .build();

String value = configCache.get("app.name");  // SELECT config_value FROM config_table WHERE config_key = ?
```

---

### FileLoader

**위치**: `org.scriptonbasestar.cache.loader.file.FileLoader`

파일 시스템에서 데이터를 로딩하는 Loader입니다.

**생성자**:

```java
public FileLoader(String baseDirectory, String fileExtension)
```

**파라미터**:
- `baseDirectory`: 기본 디렉토리 경로
- `fileExtension`: 파일 확장자 (예: "json", "txt")

**사용 예시**:

```java
SBCacheMapLoader<String, String> fileLoader = new FileLoader(
    "/var/data/configs",
    "json"
);

SBCacheMap<String, String> fileCache = SBCacheMap.<String, String>builder()
    .loader(fileLoader)
    .timeoutSec(300)
    .referenceType(ReferenceType.SOFT)  // 파일은 메모리 압박 시 회수
    .build();

String content = fileCache.get("app-config");  // /var/data/configs/app-config.json 읽기
```

---

## cache-spring 모듈

Spring Framework 통합을 제공하는 모듈입니다.

### SBCacheManager

**위치**: `org.scriptonbasestar.cache.spring.SBCacheManager`

Spring `CacheManager` 구현체입니다.

#### 생성자

```java
public SBCacheManager()
public SBCacheManager(boolean allowNullValues)
```

#### 메서드

```java
SBCacheManager addCache(String name, SBCacheMap<Object, Object> cacheMap)
```
- **반환**: this (fluent API)
- **설명**: 캐시 추가

```java
SBCacheManager addCache(SBCache cache)
```
- **반환**: this (fluent API)
- **설명**: 미리 생성된 SBCache 추가

```java
Cache getCache(String name)
```
- **반환**: 캐시 인스턴스 (없으면 null)
- **설명**: 캐시 조회

```java
Collection<String> getCacheNames()
```
- **반환**: 모든 캐시 이름

```java
Map<String, Cache> getAllCaches()
```
- **반환**: 모든 캐시 (불변 맵)

```java
Cache removeCache(String name)
```
- **반환**: 제거된 캐시
- **설명**: 캐시 제거 및 리소스 정리

```java
void removeAll()
```
- **설명**: 모든 캐시 제거

```java
boolean isAllowNullValues()
```
- **반환**: null 값 허용 여부

**사용 예시**:

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new SBCacheManager()
            .addCache("users", SBCacheMap.<Object, Object>builder()
                .timeoutSec(300)
                .maxSize(1000)
                .enableMetrics(true)
                .build())
            .addCache("products", SBCacheMap.<Object, Object>builder()
                .timeoutSec(600)
                .maxSize(5000)
                .build());
    }
}
```

---

### SBCache

**위치**: `org.scriptonbasestar.cache.spring.SBCache`

Spring `Cache` 인터페이스 구현체입니다.

#### 생성자

```java
public SBCache(String name, SBCacheMap<Object, Object> cacheMap)
```

#### 메서드

```java
String getName()
```
- **반환**: 캐시 이름

```java
Object getNativeCache()
```
- **반환**: 네이티브 캐시 객체 (SBCacheMap)

```java
ValueWrapper get(Object key)
```
- **반환**: 캐시된 값 래퍼 (없으면 null)

```java
<T> T get(Object key, Class<T> type)
```
- **반환**: 타입 캐스팅된 값

```java
<T> T get(Object key, Callable<T> valueLoader)
```
- **반환**: 캐시된 값 또는 valueLoader 결과
- **설명**: 캐시 미스 시 valueLoader 호출

```java
void put(Object key, Object value)
```
- **설명**: 캐시에 값 저장

```java
ValueWrapper putIfAbsent(Object key, Object value)
```
- **반환**: 기존 값 (없으면 null)
- **설명**: 키가 없을 때만 저장

```java
void evict(Object key)
```
- **설명**: 특정 항목 제거

```java
void clear()
```
- **설명**: 모든 항목 제거

```java
SBCacheMap<Object, Object> getCacheMap()
```
- **반환**: 내부 SBCacheMap 인스턴스

**사용 예시**:

```java
@Service
public class UserService {

    @Autowired
    private CacheManager cacheManager;

    public User getUser(Long id) {
        Cache cache = cacheManager.getCache("users");

        User user = cache.get(id, User.class);
        if (user == null) {
            user = userRepository.findById(id).orElse(null);
            cache.put(id, user);
        }

        return user;
    }
}
```

---

### Auto-Configuration

**위치**: `org.scriptonbasestar.cache.spring.boot.SBCacheAutoConfiguration`

Spring Boot 자동 설정 클래스입니다.

#### application.yml 설정

```yaml
sb:
  cache:
    enabled: true
    caches:
      cacheName1:
        timeout-sec: 300
        max-size: 1000
        eviction-policy: LRU
        reference-type: STRONG
        load-strategy: ASYNC
        write-strategy: WRITE_THROUGH
        refresh-strategy: ON_MISS
        enable-metrics: true
        auto-cleanup: true
        absolute-expiry: false
```

#### 설정 클래스: SBCacheProperties

**위치**: `org.scriptonbasestar.cache.spring.boot.SBCacheProperties`

**필드**:

| 프로퍼티 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `enabled` | boolean | true | 캐시 활성화 여부 |
| `caches` | Map | {} | 캐시별 설정 |

**CacheConfiguration 필드**:

| 프로퍼티 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `timeout-sec` | int | 300 | TTL (초) |
| `max-size` | int | 0 | 최대 크기 (0=무제한) |
| `eviction-policy` | String | "LRU" | 축출 정책 |
| `reference-type` | String | "STRONG" | 참조 타입 |
| `load-strategy` | String | "SYNC" | 로딩 전략 |
| `write-strategy` | String | "READ_ONLY" | 쓰기 전략 |
| `refresh-strategy` | String | "ON_MISS" | 갱신 전략 |
| `enable-metrics` | boolean | false | 통계 활성화 |
| `auto-cleanup` | boolean | true | 자동 정리 |
| `absolute-expiry` | boolean | false | 절대 만료 |

---

## 사용 예시 모음

### 예시 1: 기본 캐시

```java
SBCacheMap<String, User> cache = SBCacheMap.<String, User>builder()
    .timeoutSec(300)
    .build();

cache.put("user1", new User("Alice"));
User user = cache.get("user1");
```

### 예시 2: Loader 포함

```java
SBCacheMap<Long, Product> cache = SBCacheMap.<Long, Product>builder()
    .loader(productLoader)
    .timeoutSec(600)
    .maxSize(5000)
    .build();

Product product = cache.get(123L);  // 자동 로딩
```

### 예시 3: Write-Through 캐시

```java
SBCacheMap<String, Config> cache = SBCacheMap.<String, Config>builder()
    .loader(configLoader)
    .writer(configWriter)
    .writeStrategy(WriteStrategy.WRITE_THROUGH)
    .timeoutSec(3600)
    .build();

cache.put("app.name", new Config("MyApp"));  // DB에도 즉시 쓰기
```

### 예시 4: SOFT Reference 캐시

```java
SBCacheMap<String, byte[]> imageCache = SBCacheMap.<String, byte[]>builder()
    .loader(imageLoader)
    .timeoutSec(3600)
    .referenceType(ReferenceType.SOFT)  // 메모리 압박 시 GC
    .build();
```

### 예시 5: Spring 통합

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new SBCacheManager()
            .addCache("users", SBCacheMap.<Object, Object>builder()
                .timeoutSec(300)
                .build());
    }
}

@Service
public class UserService {

    @Cacheable("users")
    public User getUser(Long id) {
        return userRepository.findById(id).orElse(null);
    }
}
```

---

## 참고 자료

- [USER_GUIDE.md](USER_GUIDE.md) - 종합 사용자 가이드
- [SPRING_INTEGRATION.md](SPRING_INTEGRATION.md) - Spring 통합 가이드
