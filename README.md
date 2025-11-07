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

### SBCacheList 사용

```java
SBCacheListLoader<Product> loader = new SBCacheListLoader<Product>() {
    @Override
    public List<Product> loadAll() {
        return productRepository.findAllActive();
    }
};

SBCacheList<Product> cacheList = new SBCacheList<>(loader, 300);  // 5분 TTL
List<Product> products = cacheList.getList();
```

### 비동기 캐시 맵 (이전 데이터 반환 + 백그라운드 갱신)

```java
try (SBAsyncCacheMap<Long, User> asyncCache = new SBAsyncCacheMap<>(loader, 60)) {
    User user = asyncCache.get(1L);  // 만료된 경우에도 이전 데이터를 즉시 반환하고 백그라운드에서 갱신
} // 자동으로 리소스 정리
```

### 새로운 방식: Builder 패턴 (권장)

```java
try (SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
        .loader(loader)
        .timeoutSec(300)                // 접근 기반 TTL (5분)
        .forcedTimeoutSec(3600)         // 절대 만료 시간 (1시간) - 자주 조회해도 1시간 후 폐기
        .enableAutoCleanup(true)        // 자동 정리 활성화
        .cleanupIntervalMinutes(10)     // 10분마다 만료된 항목 제거
        .build()) {

    User user = cache.get(1L);
}
```

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
- ✅ **cache-loader-inmemory 제거**: 불필요한 모듈 정리

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
