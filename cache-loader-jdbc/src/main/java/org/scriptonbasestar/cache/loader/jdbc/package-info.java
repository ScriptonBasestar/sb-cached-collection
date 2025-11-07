/**
 * JDBC 기반 캐시 로더
 *
 * <p>SQL 쿼리를 통해 데이터베이스에서 데이터를 로드하는 로더를 제공합니다.</p>
 *
 * <h2>주요 클래스</h2>
 * <ul>
 *     <li>{@link org.scriptonbasestar.cache.loader.jdbc.JdbcMapLoader} - Map 캐시용 JDBC 로더</li>
 *     <li>{@link org.scriptonbasestar.cache.loader.jdbc.JdbcListLoader} - List 캐시용 JDBC 로더</li>
 * </ul>
 *
 * <h2>사용 예시 - JdbcMapLoader</h2>
 * <pre>{@code
 * // DataSource 설정
 * DataSource dataSource = ... ; // JDBC DataSource
 *
 * // SQL 쿼리 정의
 * String selectQuery = "SELECT value FROM cache_table WHERE key = ?";
 * String selectAllQuery = "SELECT key, value FROM cache_table";
 *
 * // 로더 생성
 * JdbcMapLoader<String, String> loader = new JdbcMapLoader<>(
 *     dataSource,
 *     selectQuery,
 *     selectAllQuery,
 *     rs -> rs.getString("key"),      // 키 매퍼
 *     rs -> rs.getString("value")     // 값 매퍼
 * );
 *
 * // 캐시 생성
 * SBCacheMap<String, String> cache = SBCacheMap.<String, String>builder()
 *     .loader(loader)
 *     .timeoutSec(300)
 *     .enableMetrics(true)
 *     .build();
 *
 * // 사용
 * String value = cache.get("key1"); // DB에서 자동 로드
 * }</pre>
 *
 * <h2>사용 예시 - JdbcListLoader</h2>
 * <pre>{@code
 * // DataSource 설정
 * DataSource dataSource = ... ; // JDBC DataSource
 *
 * // SQL 쿼리 정의
 * String selectQuery = "SELECT id, name, email FROM users ORDER BY created_at DESC";
 *
 * // 로더 생성
 * JdbcListLoader<User> loader = new JdbcListLoader<>(
 *     dataSource,
 *     selectQuery,
 *     rs -> new User(
 *         rs.getLong("id"),
 *         rs.getString("name"),
 *         rs.getString("email")
 *     )
 * );
 *
 * // 캐시 생성
 * SBCacheList<User> cache = SBCacheList.<User>builder()
 *     .loader(loader)
 *     .timeoutSec(60)
 *     .enableMetrics(true)
 *     .build();
 *
 * // 사용
 * List<User> users = cache.getList(); // DB에서 자동 로드
 * }</pre>
 *
 * <h2>주의사항</h2>
 * <ul>
 *     <li>JDBC 드라이버는 사용자가 직접 의존성에 추가해야 합니다</li>
 *     <li>DataSource는 Connection Pool을 사용하는 것을 권장합니다 (HikariCP, Commons DBCP 등)</li>
 *     <li>SQL 쿼리는 성능을 고려하여 인덱스를 활용하도록 작성해야 합니다</li>
 *     <li>대량 데이터 조회 시 메모리 사용량에 주의해야 합니다</li>
 * </ul>
 *
 * @since 2025-01
 * @author archmagece
 */
package org.scriptonbasestar.cache.loader.jdbc;
