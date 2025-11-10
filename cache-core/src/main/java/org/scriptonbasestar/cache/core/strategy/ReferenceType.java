package org.scriptonbasestar.cache.core.strategy;

/**
 * 캐시 항목의 참조 타입을 정의합니다.
 * <p>
 * Java의 참조 타입에 따라 GC(Garbage Collector)의 동작이 달라집니다.
 * 메모리 압박 상황에서 캐시의 동작 방식을 제어할 수 있습니다.
 * </p>
 *
 * <h3>참조 타입별 특징:</h3>
 * <ul>
 *   <li><b>STRONG</b>: 일반적인 참조. GC가 절대 회수하지 않음 (기본값)</li>
 *   <li><b>SOFT</b>: 메모리 부족 시에만 GC가 회수. 대용량 캐시에 적합</li>
 *   <li><b>WEAK</b>: 다음 GC 사이클에 무조건 회수. 임시 캐시에 적합</li>
 * </ul>
 *
 * <h3>사용 예시:</h3>
 * <pre>{@code
 * // 대용량 이미지 캐시 - 메모리 압박 시 자동 정리
 * SBCacheMap<String, BufferedImage> imageCache = SBCacheMap.<String, BufferedImage>builder()
 *     .loader(imageLoader)
 *     .timeoutSec(3600)
 *     .referenceType(ReferenceType.SOFT)
 *     .build();
 *
 * // 임시 계산 결과 캐시 - GC 때마다 정리
 * SBCacheMap<String, ComputeResult> tempCache = SBCacheMap.<String, ComputeResult>builder()
 *     .referenceType(ReferenceType.WEAK)
 *     .build();
 *
 * // 일반 캐시 - 명시적으로만 제거
 * SBCacheMap<Long, User> userCache = SBCacheMap.<Long, User>builder()
 *     .referenceType(ReferenceType.STRONG)  // 기본값
 *     .build();
 * }</pre>
 *
 * <h3>Trade-offs:</h3>
 * <table border="1">
 * <tr>
 *   <th>타입</th>
 *   <th>장점</th>
 *   <th>단점</th>
 *   <th>사용 시기</th>
 * </tr>
 * <tr>
 *   <td>STRONG</td>
 *   <td>
 *     - 예측 가능한 동작<br>
 *     - 최고 히트율<br>
 *     - 명시적 제어
 *   </td>
 *   <td>
 *     - OutOfMemoryError 위험<br>
 *     - 수동 관리 필요
 *   </td>
 *   <td>
 *     - 작은 캐시<br>
 *     - 중요한 데이터<br>
 *     - 메모리 여유 있음
 *   </td>
 * </tr>
 * <tr>
 *   <td>SOFT</td>
 *   <td>
 *     - 자동 메모리 관리<br>
 *     - OOM 방지<br>
 *     - 대용량 가능
 *   </td>
 *   <td>
 *     - 히트율 불안정<br>
 *     - GC 부하 증가<br>
 *     - 예측 어려움
 *   </td>
 *   <td>
 *     - 대용량 캐시<br>
 *     - 이미지/파일<br>
 *     - Best-effort
 *   </td>
 * </tr>
 * <tr>
 *   <td>WEAK</td>
 *   <td>
 *     - 메모리 절약<br>
 *     - 빠른 GC<br>
 *     - 메모리 누수 방지
 *   </td>
 *   <td>
 *     - 매우 낮은 히트율<br>
 *     - 예측 불가<br>
 *     - 캐시 효과 미미
 *   </td>
 *   <td>
 *     - 임시 데이터<br>
 *     - 계산 결과<br>
 *     - 메모리 민감
 *   </td>
 * </tr>
 * </table>
 *
 * <h3>GC 동작 상세:</h3>
 * <ul>
 *   <li><b>STRONG</b>: {@code Object obj = new Object();} - 일반 참조</li>
 *   <li><b>SOFT</b>: {@code SoftReference<Object> ref = new SoftReference<>(obj);} - JVM이 메모리 부족 판단 시 회수</li>
 *   <li><b>WEAK</b>: {@code WeakReference<Object> ref = new WeakReference<>(obj);} - 다음 GC에서 무조건 회수</li>
 * </ul>
 *
 * <h3>주의사항:</h3>
 * <ul>
 *   <li>SOFT/WEAK는 히트율이 예측 불가능하므로 모니터링 필수</li>
 *   <li>GC 튜닝(-Xms, -Xmx, -XX:SoftRefLRUPolicyMSPerMB)이 동작에 영향</li>
 *   <li>크리티컬한 데이터는 STRONG 사용 권장</li>
 *   <li>ReferenceQueue를 사용한 정리 작업 자동화</li>
 * </ul>
 *
 * @author archmagece
 * @since 2025-01 (Phase 10-C)
 * @see java.lang.ref.Reference
 * @see java.lang.ref.SoftReference
 * @see java.lang.ref.WeakReference
 * @see java.lang.ref.ReferenceQueue
 */
public enum ReferenceType {
	/**
	 * 강한 참조 (기본값)
	 * <p>
	 * 일반적인 Java 참조입니다. GC가 절대 회수하지 않으며,
	 * 명시적으로 제거하거나 maxSize 초과 시에만 축출됩니다.
	 * </p>
	 * <p>
	 * <b>사용 시기:</b> 대부분의 경우, 예측 가능한 캐시가 필요할 때
	 * </p>
	 */
	STRONG,

	/**
	 * 약한 참조 (Soft Reference)
	 * <p>
	 * 메모리가 부족할 때만 GC가 회수하는 참조입니다.
	 * JVM은 OutOfMemoryError를 던지기 전에 모든 SoftReference를 회수합니다.
	 * </p>
	 * <p>
	 * <b>사용 시기:</b> 대용량 캐시, 메모리 압박이 예상되는 환경
	 * </p>
	 * <p>
	 * <b>예시:</b> 이미지 캐시, 파일 내용 캐시, 계산 비용이 높지만
	 * 재계산 가능한 데이터
	 * </p>
	 */
	SOFT,

	/**
	 * 매우 약한 참조 (Weak Reference)
	 * <p>
	 * 다음 GC 사이클에서 무조건 회수되는 참조입니다.
	 * 강한 참조가 없으면 즉시 회수 대상이 됩니다.
	 * </p>
	 * <p>
	 * <b>사용 시기:</b> 임시 캐시, 메모리 누수 방지가 중요할 때
	 * </p>
	 * <p>
	 * <b>예시:</b> 임시 계산 결과, 메타데이터, 이미 메모리에 있는
	 * 객체에 대한 빠른 조회용
	 * </p>
	 * <p>
	 * <b>주의:</b> 캐시 히트율이 매우 낮을 수 있음
	 * </p>
	 */
	WEAK;

	/**
	 * 참조 타입에 대한 간단한 설명을 반환합니다.
	 *
	 * @return 참조 타입 설명
	 */
	public String getDescription() {
		switch (this) {
			case STRONG:
				return "Strong reference - Never garbage collected";
			case SOFT:
				return "Soft reference - Collected when memory is low";
			case WEAK:
				return "Weak reference - Collected at next GC";
			default:
				return "Unknown reference type";
		}
	}

	/**
	 * 참조 타입이 GC에 의해 자동 회수될 수 있는지 확인합니다.
	 *
	 * @return GC에 의해 회수 가능하면 true
	 */
	public boolean isGcManaged() {
		return this == SOFT || this == WEAK;
	}
}
