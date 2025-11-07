# sb-cache-java

시간 기반 캐싱 전략을 제공하는 경량 Java 라이브러리

## 제작 의도

자주 접근하는 데이터(DB 쿼리 결과, API 응답 등)를 효율적으로 캐싱하면서 자동으로 만료 및 갱신이 필요한 상황에 대응하기 위해 개발되었습니다. Google Guava Cache에서 영감을 받았지만, 더 간단하고 가벼운 구조로 설계했습니다.

### 주요 특징

- **TTL(Time To Live) 기반 자동 만료**: 설정한 시간이 지나면 자동으로 캐시 무효화
- **동기/비동기 로딩**: 데이터를 즉시 로드할지, 백그라운드에서 로드할지 선택 가능
- **스레드 안전**: 멀티스레드 환경에서 안전하게 사용 가능
- **플러그인 로더 시스템**: 인터페이스 구현으로 다양한 데이터 소스 지원
- **Cache Stampede 방지**: 랜덤 TTL 변동으로 동시 갱신 부하 분산

## 용도

- **데이터베이스 쿼리 결과 캐싱**: 자주 조회되는 엔티티나 참조 데이터
- **API 응답 캐싱**: 외부 API 호출 결과를 일정 시간 동안 재사용
- **설정 데이터 관리**: 주기적으로 변경되는 설정값 캐싱
- **백엔드 부하 감소**: 반복적인 조회 작업을 캐시로 처리

## 모듈 구조

```
sb-cache-java/
├── cache-core/              # 핵심 유틸리티, 예외, 시간 체크 로직
├── cache-collection/        # 인메모리 캐시 구현체 (SBCacheMap, SBCacheList)
└── cache-loader-redis/      # Redis 백엔드 로더 (선택적)
```

**모듈 의존성:**
- `cache-core`: 기반 라이브러리 (독립적)
- `cache-collection`: 인메모리 캐시 구현체 (cache-core 의존)
- `cache-loader-redis`: Redis 연동 로더 (cache-collection 의존, 선택적 사용)

## 사용법

### Maven 의존성

```xml
<dependency>
    <groupId>org.scriptonbasestar.cache</groupId>
    <artifactId>cache-collection</artifactId>
    <version>sb-cache-20181013-1-DEV</version>
</dependency>
```

### SBCacheMap 기본 사용

```java
// 1. 로더 구현
SBCacheMapLoader<Long, User> loader = new SBCacheMapLoader<Long, User>() {
    @Override
    public User loadOne(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public Map<Long, User> loadAll() {
        return userRepository.findAll();
    }
};

// 2. 캐시 맵 생성 (60초 TTL)
SBCacheMap<Long, User> cacheMap = new SBCacheMap<>(loader, 60);

// 3. 사용
User user = cacheMap.get(1L);  // 첫 호출: DB 조회
User sameUser = cacheMap.get(1L);  // 두번째: 캐시에서 반환
Thread.sleep(61000);
User refreshedUser = cacheMap.get(1L);  // 61초 후: 다시 DB 조회
```

### SBCacheList 사용 (리스트 전체 캐싱)

#### 기본 사용

```java
SBCacheListLoader<Product> loader = new SBCacheListLoader<Product>() {
    @Override
    public List<Product> loadAll() {
        return productRepository.findAllActive();
    }

    @Override
    public Product loadOne(int index) {
        return productRepository.findByIndex(index);
    }
};

// 기본 생성자 (5분 TTL)
try (SBCacheList<Product> cacheList = new SBCacheList<>(loader, 300)) {
    List<Product> products = cacheList.getList();
} // 자동으로 리소스 정리
```

#### Builder 패턴 (권장)

```java
try (SBCacheList<Product> cacheList = SBCacheList.<Product>builder()
        .loader(loader)
        .timeoutSec(300)                  // 접근 기반 TTL (5분)
        .forcedTimeoutSec(3600)           // 절대 만료 시간 (1시간)
        .maxSize(1000)                    // 최대 1000개 (초과 시 경고)
        .enableMetrics(true)              // 통계 수집
        .enableAutoCleanup(true)          // 자동 정리
        .cleanupIntervalMinutes(10)       // 10분마다 확인
        .loadStrategy(LoadStrategy.ALL)   // 비동기 갱신 (기본값)
        .build()) {

    List<Product> products = cacheList.getList();

    // 통계 확인
    CacheMetrics metrics = cacheList.metrics();
    System.out.println("Hit rate: " + metrics.hitRate() * 100 + "%");

    // 수동 갱신
    cacheList.refresh();
}
```

#### LoadStrategy 선택

```java
// LoadStrategy.ALL (기본값): 백그라운드 비동기 갱신
SBCacheList<Product> asyncList = SBCacheList.<Product>builder()
    .loader(loader)
    .timeoutSec(300)
    .loadStrategy(LoadStrategy.ALL)  // 만료 시 전체 리스트를 백그라운드에서 갱신
    .build();

// LoadStrategy.ONE: 동기 갱신 (특정 인덱스만 갱신)
SBCacheList<Product> syncList = SBCacheList.<Product>builder()
    .loader(loader)
    .timeoutSec(300)
    .loadStrategy(LoadStrategy.ONE)  // 만료 시 해당 인덱스만 즉시 갱신
    .build();

Product product = syncList.get(0);  // 만료 시 loader.loadOne(0) 호출
```

### 비동기 캐시 맵 (이전 데이터 반환 + 백그라운드 갱신)

```java
// 기본 생성자 사용
try (SBAsyncCacheMap<Long, User> asyncCache = new SBAsyncCacheMap<>(loader, 60)) {
    User user = asyncCache.get(1L);  // 만료된 경우에도 이전 데이터를 즉시 반환하고 백그라운드에서 갱신
} // 자동으로 리소스 정리

// Builder 패턴 사용 (스레드 풀 크기 설정 가능)
try (SBAsyncCacheMap<Long, User> asyncCache = SBAsyncCacheMap.<Long, User>builder()
        .loader(loader)
        .timeoutSec(60)
        .numberOfThreads(10)  // 스레드 풀 크기 설정 (기본값: 5)
        .build()) {
    User user = asyncCache.get(1L);
}
```

### 새로운 방식: Builder 패턴 (권장)

```java
try (SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
        .loader(loader)
        .timeoutSec(300)                // 접근 기반 TTL (5분)
        .forcedTimeoutSec(3600)         // 절대 만료 시간 (1시간) - 자주 조회해도 1시간 후 폐기
        .maxSize(10000)                 // 최대 1만개 (초과 시 LRU 제거)
        .enableMetrics(true)            // 통계 수집 활성화
        .enableAutoCleanup(true)        // 자동 정리 활성화
        .cleanupIntervalMinutes(10)     // 10분마다 만료된 항목 제거
        .build()) {

    User user = cache.get(1L);

    // 통계 확인
    CacheMetrics metrics = cache.metrics();
    System.out.println("Hit rate: " + metrics.hitRate() * 100 + "%");
    System.out.println("Average load time: " + metrics.averageLoadPenalty() / 1000 + "μs");
}
```

### 항목별 TTL 설정

```java
SBCacheMap<String, Config> cache = new SBCacheMap<>(loader, 300);

// 일반 데이터는 5분 (300초)
cache.put("user:123", userData);

// 중요한 설정은 30초만 캐싱
cache.put("admin:settings", adminSettings, 30);

// 정적 데이터는 1시간 캐싱
cache.put("static:menu", menuData, 3600);
```

### 캐시 워밍업

```java
SBCacheMap<Long, User> cache = new SBCacheMap<>(loader, 300);

// 방법 1: 전체 데이터 미리 로드 (loader.loadAll() 호출)
cache.warmUp();

// 방법 2: 특정 키만 미리 로드
List<Long> importantUserIds = Arrays.asList(1L, 2L, 3L, 100L);
cache.warmUp(importantUserIds);

// 이제 첫 요청도 빠름 (이미 캐시됨)
User user = cache.get(1L);  // 즉시 반환
```

### LoadStrategy 선택 (SYNC vs ASYNC)

SBCacheMap은 두 가지 로딩 전략을 지원합니다.

#### SYNC (기본값): 동기 로딩

```java
// 명시적으로 SYNC 지정 (기본값이므로 생략 가능)
SBCacheMap<Long, User> syncCache = SBCacheMap.<Long, User>builder()
    .loader(key -> userRepository.findById(key))
    .timeoutSec(300)
    .loadStrategy(LoadStrategy.SYNC)  // 생략 가능
    .build();

// 캐시 미스 시: 블로킹하여 데이터 로드 후 반환
User user = syncCache.get(1L);  // DB 조회 완료까지 대기
```

#### ASYNC: 비동기 로딩 (응답 속도 우선)

```java
// ASYNC 전략 사용
SBCacheMap<Long, User> asyncCache = SBCacheMap.<Long, User>builder()
    .loader(key -> userRepository.findById(key))
    .timeoutSec(300)
    .loadStrategy(LoadStrategy.ASYNC)  // 비동기 전략
    .build();

// 첫 조회: SYNC처럼 동작 (데이터가 없으므로)
User user1 = asyncCache.get(1L);  // 블로킹

// 5분 후 (TTL 만료)
// ASYNC 동작: 만료된 데이터를 즉시 반환 + 백그라운드에서 갱신
User user2 = asyncCache.get(1L);  // 즉시 반환 (stale data)
// 백그라운드에서 새 데이터 로드 중...

// 잠시 후 다시 조회하면 새 데이터 반환
Thread.sleep(500);
User user3 = asyncCache.get(1L);  // 갱신된 최신 데이터
```

**사용 사례 비교:**
- **SYNC**: 데이터 정확성이 중요한 경우 (금융 거래, 재고 관리)
- **ASYNC**: 응답 속도가 중요한 경우 (사용자 프로필, 통계 대시보드)

**참고:** `SBAsyncCacheMap`은 `@Deprecated` 되었으며, `SBCacheMap`에 `LoadStrategy.ASYNC`를 사용하세요.

### 새로운 방식: 람다 표현식 (가장 간단)

```java
SBCacheMap<Long, User> cache = SBCacheMap.create(
    id -> userRepository.findById(id),  // 람다로 로더 정의
    60
);

User user = cache.get(1L);
```

### Redis 백엔드 사용 (cache-loader-redis)

Redis를 영구 저장소로 사용하면서 메모리 캐싱의 이점도 얻을 수 있습니다.

#### Maven 의존성 추가

```xml
<dependency>
    <groupId>org.scriptonbasestar.cache</groupId>
    <artifactId>cache-loader-redis</artifactId>
    <version>sb-cache-20181013-1-DEV</version>
</dependency>
```

#### String 값 사용 (RedisStringMapLoader)

```java
// 1. Jedis 연결 생성
JedisPooled jedis = new JedisPooled("localhost", 6379);

// 2. Redis 로더 생성 (키 접두사 사용)
RedisStringMapLoader loader = new RedisStringMapLoader(jedis, "users:");

// 3. 캐시 맵 생성
try (SBCacheMap<String, String> cache = new SBCacheMap<>(loader, 60)) {
    // Redis의 "users:john" 키를 조회
    String userData = cache.get("john");

    // 메모리 캐시에 저장되며, 60초 후 자동 만료
    // 만료 시 자동으로 Redis에서 재조회
}
```

#### 객체 직렬화 사용 (RedisSerializedMapLoader)

```java
// Serializable 구현 필수
public class User implements Serializable {
    private Long id;
    private String name;
    private String email;
    // getters, setters...
}

// Redis 로더 생성
JedisPooled jedis = new JedisPooled("localhost", 6379);
RedisSerializedMapLoader<Long, User> loader =
    new RedisSerializedMapLoader<>(jedis, "users:");

// 캐시 사용
try (SBCacheMap<Long, User> cache = new SBCacheMap<>(loader, 300)) {
    User user = cache.get(123L);  // Redis에서 바이너리 데이터 조회 후 역직렬화
}
```

#### Write-Through 패턴 (캐시와 Redis 동시 업데이트)

```java
RedisStringMapLoader loader = new RedisStringMapLoader(jedis, "products:");

// Redis에 직접 저장 (TTL 포함)
loader.save("product123", "iPhone 15", 3600);  // 1시간 TTL

// 캐시를 통해 조회 (Redis → 메모리 캐시)
SBCacheMap<String, String> cache = new SBCacheMap<>(loader, 60);
String product = cache.get("product123");

// Redis에서 삭제
loader.delete("product123");
```

#### 2단 캐싱 (메모리 + Redis)

여러 서버 환경에서 로컬 메모리와 Redis를 함께 사용하여 최적의 성능을 얻을 수 있습니다.

```java
// L2: Redis 캐시 (1시간 TTL)
JedisPooled jedis = new JedisPooled("localhost", 6379);
RedisSerializedMapLoader<Long, User> redisLoader =
    new RedisSerializedMapLoader<>(jedis, "users:");
SBCacheMap<Long, User> l2Cache = new SBCacheMap<>(redisLoader, 3600);

// L1: 메모리 캐시 (1분 TTL) → L2로 체이닝
ChainedCacheMapLoader<Long, User> chainedLoader = new ChainedCacheMapLoader<>(l2Cache);
SBCacheMap<Long, User> l1Cache = new SBCacheMap<>(chainedLoader, 60);

// 사용
User user = l1Cache.get(123L);
// 1. L1(메모리) 확인 (60초 TTL)
// 2. L1 미스 → L2(메모리) 확인 (3600초 TTL)
// 3. L2 미스 → Redis 조회
```

**데이터 흐름:**
```
App → L1(Memory 60s) → L2(Memory 3600s) → Redis
```

**장점:**
- 초고속: 대부분의 요청이 L1에서 처리 (나노초 단위)
- 효율적: Redis 조회 횟수 최소화 (분당 1회 이하)
- 유연함: 각 레벨의 TTL 독립 설정

## 빌드 방법

```bash
# 전체 빌드
mvn clean install

# 테스트 실행
mvn test
```

## 최신 개선사항 (2025)

### Phase 1: 치명적 버그 수정 및 안정성 개선
- ✅ **sun.* 패키지 제거**: Java 9+ 호환성 확보
- ✅ **ConcurrentHashMap 적용**: synchronizedMap 대비 2-5배 성능 향상
- ✅ **인스턴스 레벨 동기화**: 클래스 레벨 락으로 인한 병목 현상 해결
- ✅ **AutoCloseable 구현**: 리소스 자동 정리 (SBAsyncCacheMap, SBCacheMap)

### Phase 2: 현대화 및 최적화
- ✅ **java.time API 적용**: Date/Calendar 제거, Duration 지원
- ✅ **ThreadLocalRandom 사용**: Random 대비 멀티스레드 성능 개선
- ✅ **의존성 최신화**:
  - Lombok 1.16.16 → 1.18.30
  - SLF4J 1.7.25 → 1.7.36
  - Logback 1.2.2 → 1.2.13
  - JUnit 4.12 → 4.13.2

### Phase 3: 사용성 개선
- ✅ **Builder 패턴**: 가독성 높은 설정
- ✅ **람다 지원**: `SBCacheMap.create(key -> loader, timeout)` 간편 생성
- ✅ **자동 정리**: 선택적 만료 항목 자동 삭제 기능
- ✅ **Forced Timeout**: 자주 조회해도 절대 시간 후 무조건 폐기

### 버그 수정 및 최적화 (2025-01)
- 🐛 **Jitter 계산 오류 수정**: 정확한 cache stampede 방지
- 🐛 **ConcurrentModificationException 방지**: removeExpired() 안정화
- ⚡ **get() 메서드 최적화**: Double-check locking 패턴 적용
- ⚡ **동기화 범위 최소화**: 읽기 작업 성능 향상

### Phase 4: 외부 저장소 지원 (2025-01)
- ✅ **cache-loader-redis 구현**: Redis 백엔드 지원 (Jedis 5.1.0 기반)
- ✅ **RedisStringMapLoader**: String 타입 전용 간편 로더
- ✅ **RedisSerializedMapLoader**: 객체 직렬화 지원 범용 로더
- ✅ **Write-Through 패턴**: save(), delete() 메서드로 캐시-Redis 동시 업데이트
- ✅ **AutoCloseable 지원**: try-with-resources로 안전한 리소스 관리
- ✅ **ChainedCacheMapLoader**: 2단/3단 캐싱 지원 (메모리 → Redis 체이닝)
- ✅ **cache-loader-inmemory 제거**: 불필요한 모듈 정리

### Phase 5: 코드 정리 및 누락 기능 완성 (2025-01)
- ✅ **SBCacheList 버그 수정**: updatedAt 필드가 업데이트되지 않던 버그 수정 (LocalTime 불변성 오해)
- ✅ **Dead Code 제거**: 주석 처리된 SBCacheMapOld.java (162줄) 삭제
- ✅ **빈 인터페이스 제거**: Catcher/Shooter 인터페이스 4개 삭제 (구현체 없음)
- ✅ **미사용 Enum 제거**: InitStrategy, NoticeStrategy, SyncStrategy 삭제
- ✅ **불필요한 필드 제거**: SBAsyncCacheMap의 isDataDurable 필드 제거
- ✅ **getAll() 구현**: SBCacheMap에 현재 캐시 전체 조회 기능 추가
- ✅ **SBAsyncCacheMap Builder 패턴**: 설정 가능한 스레드 풀 크기 지원

### Phase 6: 핵심 유용성 개선 - SBCacheMap (2025-01)
- ✅ **CacheMetrics 클래스**: 히트율, 미스율, 평균 로드 시간 등 통계 수집
- ✅ **최대 크기 제한 (maxSize)**: LRU 방식으로 오래된 항목 자동 제거, OOM 방지
- ✅ **항목별 TTL 설정**: put(key, value, customTtlSec)로 항목마다 다른 만료 시간 설정
- ✅ **캐시 워밍업**: warmUp() / warmUp(keys) 메서드로 초기 지연 방지
- ✅ **Builder 확장**: maxSize, enableMetrics 옵션 추가

### Phase 7: SBCacheList 전면 개선 (2025-01)
- ✅ **Phase 1-3 현대화**: 클래스 레벨 동기화 제거, AutoCloseable 구현, Builder 패턴 추가
- ✅ **CopyOnWriteArrayList 사용**: 동시성 안전한 리스트 구현으로 변경
- ✅ **Phase 6 기능 추가**: CacheMetrics, forcedTimeout, maxSize (경고), autoCleanup
- ✅ **LoadStrategy.ONE 구현 완성**: 개별 인덱스 갱신 기능 정상 동작
- ✅ **getList() 메서드 추가**: 전체 리스트 조회용 메서드 (불변 뷰 반환)
- ✅ **refresh() 메서드 추가**: 수동 갱신 기능
- ✅ **AtomicLong 타임스탬프**: LocalTime 대신 밀리초 기반 정확한 만료 체크
- ✅ **전용 테스트 파일**: SBCacheListPhase6Test.java (14개 테스트 메서드)

### Phase 8: LoadStrategy 통합 및 SBAsyncCacheMap 통합 (2025-01)
- ✅ **LoadStrategy enum 확장**: SYNC/ASYNC 전략 추가
- ✅ **SBCacheMap에 ASYNC 전략 통합**: LoadStrategy.ASYNC로 비동기 로딩 지원
- ✅ **SBAsyncCacheMap @Deprecated**: SBCacheMap으로 통합됨 (2.0.0에서 제거 예정)
- ✅ **백그라운드 갱신**: 만료된 데이터를 즉시 반환하고 백그라운드에서 새 데이터 로드
- ✅ **ExecutorService 관리**: ASYNC 전략 사용 시 자동으로 스레드 풀 생성 및 종료
- ✅ **전용 테스트 파일**: SBCacheMapAsyncTest.java (8개 테스트 메서드)

**마이그레이션 가이드:**
```java
// Before (Deprecated) - SBAsyncCacheMap 사용
SBAsyncCacheMap<String, Data> cache = SBAsyncCacheMap.<String, Data>builder()
    .loader(key -> loadData(key))
    .timeoutSec(300)
    .numberOfThreads(10)
    .build();

// After (Recommended) - SBCacheMap with LoadStrategy.ASYNC
SBCacheMap<String, Data> cache = SBCacheMap.<String, Data>builder()
    .loader(key -> loadData(key))
    .timeoutSec(300)
    .loadStrategy(LoadStrategy.ASYNC)  // ASYNC 전략 설정
    .build();
```

**LoadStrategy 전략 비교:**
- **SYNC (기본값)**: 캐시 미스 시 블로킹하여 즉시 데이터를 로드
  - 사용 사례: 데이터 정확성이 중요한 경우
  - 장점: 항상 최신 데이터 보장
  - 단점: 로드 시간만큼 블로킹
- **ASYNC**: 캐시 미스 시 만료된 데이터를 즉시 반환하고 백그라운드에서 갱신
  - 사용 사례: 응답 속도가 중요한 경우
  - 장점: 즉시 응답, 사용자 경험 향상
  - 단점: 잠깐 오래된 데이터 반환 가능

**주요 변경사항:**
- `extends ArrayList<E>` → 독립적인 클래스 구조로 변경
- `static Object syncObject` → 인스턴스별 동기화 객체로 변경
- `LocalTime` → `AtomicLong` (밀리초 기반)으로 변경
- 하드코딩된 TTL (300초) → 설정 가능하도록 변경
- LoadStrategy.ONE 사용 시 `IndexOutOfBoundsException` 방지
- 리소스 누수 방지 (ExecutorService 자동 종료)

## 현재 상태

- **버전**: sb-cache-20181013-1-DEV (개발 버전)
- **Java**: 1.8+
- **활성 모듈**: cache-core, cache-collection, cache-loader-redis
- **성능**: 동시성 환경에서 2-5배 향상
- **안정성**: Java 9-21 완전 호환
- **외부 의존성**:
  - Jedis 5.1.0 (cache-loader-redis 사용 시)
  - Apache Commons Pool 2.12.0 (Jedis 연결 풀링)

## 참고

- [Google Guava Cache](https://github.com/google/guava/wiki/CachesExplained)
- [Hazelcast](https://hazelcast.org/)

## 라이선스

이 프로젝트의 라이선스는 별도로 명시되지 않았습니다.
