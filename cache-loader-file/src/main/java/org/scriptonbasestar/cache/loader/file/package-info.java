/**
 * 파일 기반 캐시 로더
 *
 * <p>JSON 파일에서 데이터를 로드하는 캐시 로더를 제공합니다.</p>
 *
 * <h3>주요 클래스</h3>
 * <ul>
 *   <li>{@link org.scriptonbasestar.cache.loader.file.FileMapLoader} - JSON 파일에서 Map 데이터 로드</li>
 *   <li>{@link org.scriptonbasestar.cache.loader.file.FileListLoader} - JSON 파일에서 List 데이터 로드</li>
 * </ul>
 *
 * <h3>특징</h3>
 * <ul>
 *   <li>Jackson 기반 JSON 파싱</li>
 *   <li>TypeReference를 통한 제네릭 타입 지원</li>
 *   <li>파일 존재 여부 확인</li>
 *   <li>마지막 수정 시간 조회</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // Map 로더
 * FileMapLoader<String, User> mapLoader = new FileMapLoader<>(
 *     new File("users.json"),
 *     new TypeReference<Map<String, User>>() {}
 * );
 *
 * // List 로더
 * FileListLoader<Product> listLoader = new FileListLoader<>(
 *     new File("products.json"),
 *     new TypeReference<List<Product>>() {}
 * );
 * }</pre>
 *
 * @author archmagece
 * @since 2025-01
 */
package org.scriptonbasestar.cache.loader.file;
