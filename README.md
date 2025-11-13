# SB Cached Collection

[![Java](https://img.shields.io/badge/Java-1.8%2B-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-Multi--module-blue.svg)](https://maven.apache.org/)
[![Spring](https://img.shields.io/badge/Spring-Framework%20%26%20Boot-brightgreen.svg)](https://spring.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-yellow.svg)](LICENSE)

**확장 가능하고 강력한 Java 캐시 프레임워크**

SB Cached Collection은 TTL, 축출 정책, 다양한 참조 타입, Write-Through/Write-Behind, Refresh-Ahead 전략을 지원하는 엔터프라이즈급 캐시 솔루션입니다.

---

## 🌟 주요 특징

### 핵심 기능

- ✅ **TTL (Time To Live)**: 자동 만료 및 갱신
- ✅ **다양한 축출 정책**: LRU, LFU, FIFO, RANDOM, TTL
- ✅ **Reference 타입 지원**: STRONG, SOFT, WEAK (GC 협력)
- ✅ **로딩 전략**: SYNC, ASYNC (Thundering Herd 방지)
- ✅ **쓰기 전략**: READ_ONLY, WRITE_THROUGH, WRITE_BEHIND
- ✅ **갱신 전략**: ON_MISS, REFRESH_AHEAD
- ✅ **스레드 안전**: ConcurrentHashMap 기반 고성능
- ✅ **Spring 통합**: CacheManager, @Cacheable 지원
- ✅ **통계 수집**: Hit Rate, Load Time, Metrics

### 엔터프라이즈 기능

- 🚀 **캐시 워밍업**: 애플리케이션 시작 시 사전 로딩
- 🔄 **Write-Through/Write-Behind**: 백엔드 저장소 동기화
- ⚡ **Refresh-Ahead**: 만료 전 백그라운드 갱신
- 📊 **JMX/Prometheus 통합**: 실시간 모니터링
- 🔌 **확장 가능**: Loader, Writer, EvictionStrategy 커스터마이징
- 🛡️ **메모리 안전**: MaxSize, Reference 타입으로 OOM 방지

---

## 📦 Quick Start

### Maven 의존성

```xml
<dependency>
    <groupId>org.scriptonbasestar.cache</groupId>
    <artifactId>cache-collection</artifactId>
    <version>sb-cache-20251107-1-DEV</version>
</dependency>

<!-- Spring 통합 (선택 사항) -->
<dependency>
    <groupId>org.scriptonbasestar.cache</groupId>
    <artifactId>cache-spring</artifactId>
    <version>sb-cache-20251107-1-DEV</version>
</dependency>
```

### 30초 만에 시작하기

```java
// 1. Loader 정의 (데이터 소스)
SBCacheMapLoader<Long, User> loader = new SBCacheMapLoader<>() {
    @Override
    public User loadOne(Long id) throws SBCacheLoadFailException {
        return userRepository.findById(id)
            .orElseThrow(() -> new SBCacheLoadFailException("User not found: " + id));
    }

    @Override
    public Map<Long, User> loadAll() throws SBCacheLoadFailException {
        return userRepository.findAll().stream()
            .collect(Collectors.toMap(User::getId, user -> user));
    }
};

// 2. 캐시 생성 (5분 TTL, 최대 1,000개, LRU)
SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
    .loader(loader)
    .timeoutSec(300)
    .maxSize(1000)
    .evictionPolicy(EvictionPolicy.LRU)
    .enableMetrics(true)
    .build();

// 3. 사용
User user = cache.get(123L);  // 첫 호출: DB 조회
User cached = cache.get(123L);  // 두 번째: 캐시 히트

// 4. 통계 확인
System.out.println("Hit Rate: " + cache.getHitRate());
System.out.println("Average Load Time: " + cache.getAverageLoadTime() + "ms");
```

---

## 📚 문서

- 📖 [**USER_GUIDE.md**](docs/USER_GUIDE.md) - 종합 사용자 가이드
  - Quick Start, 핵심 개념, 고급 기능
  - 실전 예제 (User Profile, API Response, Product Catalog)
  - 성능 튜닝, 모니터링, 트러블슈팅

- 🔗 [**SPRING_INTEGRATION.md**](docs/SPRING_INTEGRATION.md) - Spring 통합 가이드
  - CacheManager 설정, @Cacheable 사용법
  - Auto-Configuration (YAML/Properties)
  - Actuator 통합, 4가지 실전 예제

- 📖 [**API_REFERENCE.md**](docs/API_REFERENCE.md) - 전체 API 레퍼런스
  - 모든 클래스/인터페이스/Enum 상세 설명
  - 메서드 시그니처, 파라미터, 반환값
  - 5가지 사용 예시

- 🏗️ [**ARCHITECTURE.md**](docs/ARCHITECTURE.md) - 시스템 아키텍처
  - 모듈 구조, 레이어 아키텍처
  - 5가지 디자인 패턴, 확장 포인트
  - 스레드 안전성, 메모리 관리, 성능 최적화

---

## 🔥 핵심 기능 상세

### 1. ReferenceType (메모리 관리)

메모리 압박에 대응하는 3가지 참조 타입:

```java
// STRONG: 명시적 제거 전까지 유지 (기본값)
SBCacheMap<String, Config> configCache = SBCacheMap.<String, Config>builder()
    .timeoutSec(3600)
    .referenceType(ReferenceType.STRONG)
    .build();

// SOFT: 메모리 부족 시 GC가 회수 (대용량 캐시)
SBCacheMap<String, byte[]> imageCache = SBCacheMap.<String, byte[]>builder()
    .timeoutSec(3600)
    .referenceType(ReferenceType.SOFT)  // OOM 방지
    .build();

// WEAK: 다음 GC에서 회수 (임시 캐시)
SBCacheMap<String, Report> tempCache = SBCacheMap.<String, Report>builder()
    .timeoutSec(60)
    .referenceType(ReferenceType.WEAK)  // 메모리 절약
    .build();
```

### 2. EvictionPolicy (축출 정책)

MaxSize 초과 시 어떤 항목을 제거할지 결정:

```java
// LRU: 가장 최근에 사용되지 않은 항목 제거 (기본값)
SBCacheMap<Long, User> lruCache = SBCacheMap.<Long, User>builder()
    .maxSize(10000)
    .evictionPolicy(EvictionPolicy.LRU)
    .build();

// LFU: 가장 적게 사용된 항목 제거
SBCacheMap<String, Product> lfuCache = SBCacheMap.<String, Product>builder()
    .maxSize(5000)
    .evictionPolicy(EvictionPolicy.LFU)  // 인기도 기반
    .build();

// FIFO: 가장 먼저 추가된 항목 제거
// RANDOM: 무작위 항목 제거
// TTL: 가장 오래된 항목 제거
```

### 3. LoadStrategy (로딩 전략)

캐시 미스 시 로딩 방식:

```java
// SYNC: 동기 로딩 (기본값)
SBCacheMap<String, Data> syncCache = SBCacheMap.<String, Data>builder()
    .loader(dataLoader)
    .loadStrategy(LoadStrategy.SYNC)
    .build();

// ASYNC: 비동기 로딩 (Thundering Herd 방지)
SBCacheMap<String, Report> asyncCache = SBCacheMap.<String, Report>builder()
    .loader(reportLoader)
    .loadStrategy(LoadStrategy.ASYNC)  // 중복 로딩 방지
    .build();

// ASYNC 동작:
// Thread 1: miss → load → return
// Thread 2: miss → wait → (Thread 1 완료) → return
// Thread 3: miss → wait → (Thread 1 완료) → return
// 결과: 1번만 로딩 (효율적)
```

### 4. WriteStrategy (쓰기 전략)

캐시와 백엔드 저장소 동기화:

```java
// WRITE_THROUGH: 동기 쓰기 (즉시 영속화)
SBCacheMap<Long, User> writeThrough = SBCacheMap.<Long, User>builder()
    .loader(userLoader)
    .writer(userWriter)
    .writeStrategy(WriteStrategy.WRITE_THROUGH)
    .build();

writeThrough.put(123L, user);  // 캐시 + DB 동시 쓰기

// WRITE_BEHIND: 비동기 쓰기 (성능 우선) + 재시도 로직
SBCacheMap<String, Session> writeBehind = SBCacheMap.<String, Session>builder()
    .loader(sessionLoader)
    .writer(sessionWriter)
    .writeStrategy(WriteStrategy.WRITE_BEHIND)  // 배치 처리
    .writeBehindBatchSize(100)             // 100개씩 배치
    .writeBehindIntervalSeconds(5)         // 5초마다 플러시
    .writeBehindMaxRetries(3)              // 실패 시 최대 3회 재시도
    .writeBehindRetryDelayMs(1000)         // 재시도 간격 1초
    .build();

writeBehind.put("session1", session);  // 캐시에만 쓰고 즉시 반환
// 백그라운드에서 나중에 DB 반영 (실패 시 자동 재시도)
```

### 5. RefreshStrategy (갱신 전략)

캐시 항목 갱신 방식:

```java
// ON_MISS: 미스 시에만 갱신 (기본값)
SBCacheMap<String, Config> onMiss = SBCacheMap.<String, Config>builder()
    .loader(configLoader)
    .refreshStrategy(RefreshStrategy.ON_MISS)
    .build();

// REFRESH_AHEAD: 미리 갱신 (항상 신선한 데이터)
SBCacheMap<String, StockPrice> refreshAhead = SBCacheMap.<String, StockPrice>builder()
    .loader(stockPriceLoader)
    .timeoutSec(60)  // 1분 TTL
    .refreshStrategy(RefreshStrategy.REFRESH_AHEAD)  // 30초 후 백그라운드 갱신
    .build();

// REFRESH_AHEAD 동작:
// T=0: cache.get(key) → miss → load → return
// T=30: cache.get(key) → hit (즉시 반환) + 백그라운드 갱신 시작
// T=60: cache.get(key) → hit (이미 갱신됨, 즉시 반환)
```

---

## 🔌 Spring Framework 통합

### @Cacheable 어노테이션

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
                .evictionPolicy(EvictionPolicy.LRU)
                .referenceType(ReferenceType.SOFT)
                .enableMetrics(true)
                .build());
    }
}

@Service
public class UserService {

    @Cacheable(value = "users", key = "#id")
    public User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
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

### Spring Boot Auto-Configuration

**application.yml**:
```yaml
sb:
  cache:
    enabled: true
    caches:
      users:
        timeout-sec: 300
        max-size: 1000
        eviction-policy: LRU
        reference-type: SOFT
        enable-metrics: true
      products:
        timeout-sec: 600
        max-size: 5000
        eviction-policy: LFU
        reference-type: STRONG
```

---

## 📊 모니터링 & Actuator

### Health Indicator

**GET /actuator/health**:
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
        }
      }
    }
  }
}
```

### Metrics

```java
// 통계 조회
double hitRate = cache.getHitRate();
double missRate = cache.getMissRate();
long loadCount = cache.getLoadCount();
double avgLoadTime = cache.getAverageLoadTime();

System.out.println("Hit Rate: " + (hitRate * 100) + "%");
System.out.println("Average Load Time: " + avgLoadTime + "ms");
```

---

## 🏗️ 모듈 구조

```
sb-cached-collection/
├── cache-core/              # 핵심 인터페이스 및 전략
│   ├── strategy/            # ReferenceType, EvictionPolicy, LoadStrategy, etc.
│   ├── loader/              # SBCacheMapLoader, SBCacheListLoader
│   ├── writer/              # SBCacheMapWriter
│   └── exception/           # SBCacheLoadFailException, SBCacheWriteFailException
│
├── cache-collection/        # 캐시 구현체
│   ├── map/                 # SBCacheMap (메인 구현)
│   ├── storage/             # ReferenceBasedStorage
│   └── strategy/            # LRU, LFU, FIFO, RANDOM, TTL 구현
│
├── cache-loader-jdbc/       # JDBC Loader
├── cache-loader-file/       # File Loader
├── cache-metrics/           # 통계 및 모니터링
└── cache-spring/            # Spring Framework 통합
    ├── SBCacheManager       # Spring CacheManager 구현
    ├── SBCache              # Spring Cache 구현
    └── boot/                # Auto-Configuration
```

**의존성 그래프**:
```
cache-core (독립)
    ↑
    ├── cache-collection
    ├── cache-loader-*
    └── cache-metrics
            ↑
        cache-spring
```

---

## 🚀 성능 최적화

### 1. ConcurrentHashMap

- Lock-free 읽기 (높은 동시성)
- 세그먼트 기반 잠금 (쓰기 분산)
- O(1) 평균 시간 복잡도

### 2. ASYNC LoadStrategy (Thundering Herd 방지)

**SYNC (중복 로딩)**:
```
Thread 1: load (300ms)
Thread 2: load (300ms)
Thread 3: load (300ms)
총 DB 쿼리: 3회
```

**ASYNC (한 번만 로딩)**:
```
Thread 1: load (300ms)
Thread 2: wait → return
Thread 3: wait → return
총 DB 쿼리: 1회
```

### 3. Write-Behind 배치 처리

| 전략 | 단일 쓰기 | 100개 쓰기 | DB 부하 |
|------|----------|-----------|---------|
| WRITE_THROUGH | 10ms | 1000ms | 높음 |
| **WRITE_BEHIND** | **1ms** | **100ms** | **낮음** |

### 4. Refresh-Ahead 사전 갱신

- 사용자는 항상 캐시 히트 경험
- 백그라운드에서 갱신 → 응답 시간 단축

---

## 🧪 테스트

```bash
# 전체 테스트 실행
mvn test

# 특정 모듈 테스트
cd cache-collection && mvn test

# 테스트 결과
Tests run: 148, Failures: 0, Errors: 0, Skipped: 1
```

**테스트 커버리지**:
- SBCacheMap: 45개 테스트
- ReferenceType: 7개 테스트
- EvictionPolicy: 8개 테스트
- Spring Integration: 48개 테스트

---

## 📜 최신 개선사항

### Phase 10-A: JDBC/File Loader 구현 (2025-01)
- ✅ JDBCLoader: DataSource 기반 데이터 로딩
- ✅ FileLoader: 파일 시스템 기반 데이터 로딩

### Phase 10-B: EvictionPolicy 구현 (2025-01)
- ✅ 5가지 축출 정책: LRU, LFU, FIFO, RANDOM, TTL
- ✅ Strategy 패턴으로 확장 가능
- ✅ maxSize 초과 시 자동 축출

### Phase 10-C: ReferenceType 지원 (2025-01)
- ✅ STRONG, SOFT, WEAK 참조 타입
- ✅ ReferenceBasedStorage 구현
- ✅ ReferenceQueue를 통한 자동 정리
- ✅ GC와 협력하여 메모리 관리

### Phase 11-A: Write-Through/Write-Behind (이미 완료)
- ✅ 동기/비동기 쓰기 전략
- ✅ 백엔드 저장소 동기화
- ✅ 배치 처리 지원

### Phase 11-B: Refresh-Ahead (이미 완료)
- ✅ TTL 50% 도달 시 백그라운드 갱신
- ✅ 항상 신선한 데이터 제공
- ✅ 사용자 경험 향상

### Phase 12-A/B: Spring Cache 통합 (이미 완료)
- ✅ SBCacheManager, SBCache 구현
- ✅ Auto-Configuration 지원
- ✅ YAML/Properties 설정

### Phase 13-A/B/C: 모니터링 기능 (이미 완료)
- ✅ CacheMetrics: Hit Rate, Load Time
- ✅ Actuator Health Indicator
- ✅ Prometheus/Micrometer 통합

---

## 🔧 빌드

```bash
# 전체 빌드
mvn clean install

# 특정 모듈 빌드
cd cache-collection && mvn clean install

# 테스트 스킵
mvn clean install -DskipTests
```

---

## 📖 참고 자료

- [Google Guava Cache](https://github.com/google/guava/wiki/CachesExplained)
- [Caffeine](https://github.com/ben-manes/caffeine)
- [Spring Framework Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)
- [Java Concurrency in Practice](https://jcip.net/)

---

## 📝 라이선스

Apache License 2.0 - 상세 내용은 [LICENSE](LICENSE) 파일을 참조하세요.

---

## 🤝 기여

기여를 환영합니다! Issue 제보 및 Pull Request를 자유롭게 제출해주세요.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'feat: Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📧 연락처

- GitHub Issues: [sb-cached-collection/issues](https://github.com/scriptonbasestar/sb-cached-collection/issues)
- Email: archmagece@gmail.com

---

**Made with ❤️ by ScriptonBaseStar Team**
