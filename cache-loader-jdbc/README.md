# cache-loader-jdbc - JDBC/JPA Data Source Loader

JDBC 및 JPA를 통한 데이터베이스 캐시 로더 구현입니다.

## Maven Dependency

```xml
<dependency>
    <groupId>org.scriptonbasestar.cache</groupId>
    <artifactId>cache-loader-jdbc</artifactId>
    <version>0.1.0</version>
</dependency>

<!-- Spring JDBC (if using JdbcTemplate) -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-jdbc</artifactId>
    <version>5.3.30</version>
</dependency>
```

## Quick Start

### JdbcMapLoader - Single Entity

```java
import org.springframework.jdbc.core.JdbcTemplate;
import org.scriptonbasestar.cache.loader.jdbc.JdbcMapLoader;

// 1. JdbcTemplate 준비
DataSource dataSource = // ... your datasource
JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

// 2. RowMapper 정의
RowMapper<User> userRowMapper = (rs, rowNum) -> {
    User user = new User();
    user.setId(rs.getLong("id"));
    user.setName(rs.getString("name"));
    user.setEmail(rs.getString("email"));
    return user;
};

// 3. JdbcMapLoader 생성
JdbcMapLoader<Long, User> jdbcLoader = new JdbcMapLoader<>(
    jdbcTemplate,
    userRowMapper,
    "SELECT * FROM users WHERE id = ?",  // loadOne SQL
    "SELECT * FROM users"                // loadAll SQL
);

// 4. 캐시 생성
SBCacheMap<Long, User> userCache = SBCacheMap.<Long, User>builder()
    .loader(jdbcLoader)
    .timeoutSec(300)
    .maxSize(1000)
    .build();

// 5. 사용
User user = userCache.get(123L);  // SELECT * FROM users WHERE id = 123
```

### JdbcMapLoader - Parameterized Query

```java
// 복잡한 쿼리 예시
JdbcMapLoader<Long, UserProfile> profileLoader = new JdbcMapLoader<>(
    jdbcTemplate,
    profileRowMapper,
    "SELECT u.*, p.* FROM users u " +
    "JOIN profiles p ON u.id = p.user_id " +
    "WHERE u.id = ?",
    "SELECT u.*, p.* FROM users u " +
    "JOIN profiles p ON u.id = p.user_id"
);
```

### JdbcListLoader - List Data

```java
import org.scriptonbasestar.cache.loader.jdbc.JdbcListLoader;

// 리스트 데이터 로더
JdbcListLoader<Order> orderListLoader = new JdbcListLoader<>(
    jdbcTemplate,
    orderRowMapper,
    "SELECT * FROM orders WHERE status = 'PENDING' ORDER BY created_at DESC"
);

SBCacheList<Order> pendingOrders = new SBCacheList<>(orderListLoader, 60);
List<Order> orders = pendingOrders.getAll();  // DB에서 로드
```

## Advanced Usage

### With Write-Through Strategy

```java
// Writer 정의
SBCacheMapWriter<Long, User> userWriter = new SBCacheMapWriter<>() {
    @Override
    public void write(Long key, User value) {
        jdbcTemplate.update(
            "INSERT INTO users (id, name, email) VALUES (?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE name = ?, email = ?",
            key, value.getName(), value.getEmail(),
            value.getName(), value.getEmail()
        );
    }

    @Override
    public void writeAll(Map<Long, User> entries) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO users (id, name, email) VALUES (?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE name = ?, email = ?",
            entries.entrySet().stream().map(e -> new Object[]{
                e.getKey(),
                e.getValue().getName(),
                e.getValue().getEmail(),
                e.getValue().getName(),
                e.getValue().getEmail()
            }).toArray(Object[][]::new)
        );
    }

    @Override
    public void delete(Long key) {
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", key);
    }

    @Override
    public void deleteAll(Collection<Long> keys) {
        jdbcTemplate.batchUpdate(
            "DELETE FROM users WHERE id = ?",
            keys.stream().map(k -> new Object[]{k}).toArray(Object[][]::new)
        );
    }
};

// 캐시 생성 (Read-Through + Write-Through)
SBCacheMap<Long, User> cache = SBCacheMap.<Long, User>builder()
    .loader(jdbcLoader)
    .writer(userWriter)
    .writeStrategy(WriteStrategy.WRITE_THROUGH)
    .timeoutSec(300)
    .build();

// 자동으로 DB 동기화
cache.put(123L, newUser);  // INSERT or UPDATE
cache.remove(123L);        // DELETE
```

### With Spring Transaction

```java
@Service
@Transactional
public class UserCacheService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SBCacheMap<Long, User> userCache;

    @PostConstruct
    public void init() {
        JdbcMapLoader<Long, User> loader = new JdbcMapLoader<>(
            jdbcTemplate,
            userRowMapper,
            "SELECT * FROM users WHERE id = ?",
            "SELECT * FROM users"
        );

        this.userCache = SBCacheMap.<Long, User>builder()
            .loader(loader)
            .timeoutSec(300)
            .maxSize(10000)
            .build();
    }

    public User getUser(Long id) {
        return userCache.get(id);  // 트랜잭션 내에서 실행
    }
}
```

## Best Practices

### 1. Connection Pool 설정

```properties
# HikariCP (권장)
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
```

### 2. 쿼리 최적화

```java
// ❌ N+1 쿼리 문제
JdbcMapLoader<Long, User> badLoader = new JdbcMapLoader<>(
    jdbcTemplate,
    userRowMapper,
    "SELECT * FROM users WHERE id = ?",  // 각 키마다 실행
    "SELECT * FROM users"
);

// ✅ JOIN으로 해결
JdbcMapLoader<Long, UserWithProfile> goodLoader = new JdbcMapLoader<>(
    jdbcTemplate,
    userWithProfileRowMapper,
    "SELECT u.*, p.* FROM users u LEFT JOIN profiles p ON u.id = p.user_id WHERE u.id = ?",
    "SELECT u.*, p.* FROM users u LEFT JOIN profiles p ON u.id = p.user_id"
);
```

### 3. 인덱스 활용

```sql
-- loadOne 쿼리의 WHERE 절에 사용되는 컬럼에 인덱스 필수
CREATE INDEX idx_users_id ON users(id);
CREATE INDEX idx_orders_status ON orders(status);
```

## API Reference

### JdbcMapLoader

```java
public class JdbcMapLoader<K, V> implements SBCacheMapLoader<K, V> {
    /**
     * JDBC 기반 Map 로더 생성
     *
     * @param jdbcTemplate Spring JdbcTemplate
     * @param rowMapper RowMapper (ResultSet → Entity 변환)
     * @param selectOneSql loadOne 쿼리 (? placeholder 사용)
     * @param selectAllSql loadAll 쿼리
     */
    public JdbcMapLoader(
        JdbcTemplate jdbcTemplate,
        RowMapper<V> rowMapper,
        String selectOneSql,
        String selectAllSql
    )
}
```

### JdbcListLoader

```java
public class JdbcListLoader<T> implements SBCacheListLoader<T> {
    /**
     * JDBC 기반 List 로더 생성
     *
     * @param jdbcTemplate Spring JdbcTemplate
     * @param rowMapper RowMapper (ResultSet → Entity 변환)
     * @param selectAllSql loadAll 쿼리
     */
    public JdbcListLoader(
        JdbcTemplate jdbcTemplate,
        RowMapper<T> rowMapper,
        String selectAllSql
    )
}
```

## Performance Tips

1. **Connection Pool**: 최소 10개 이상 설정
2. **Query Optimization**: EXPLAIN PLAN으로 쿼리 분석
3. **Index**: WHERE, JOIN 절에 사용되는 컬럼 인덱싱
4. **Batch Size**: Write-Behind 사용 시 배치 크기 조정 (100~1000)
5. **TTL**: 데이터 변경 빈도에 따라 적절한 TTL 설정

## License

Apache License 2.0
