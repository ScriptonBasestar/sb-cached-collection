package org.scriptonbasestar.cache.collection.storage;

import org.scriptonbasestar.cache.core.strategy.ReferenceType;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reference 기반 캐시 저장소
 * <p>
 * STRONG, SOFT, WEAK 참조 타입을 지원하는 저장소입니다.
 * GC와 협력하여 메모리 압박 시 자동으로 항목을 회수할 수 있습니다.
 * </p>
 *
 * <h3>특징:</h3>
 * <ul>
 *   <li>Thread-safe: ConcurrentHashMap 기반</li>
 *   <li>ReferenceQueue를 통한 자동 정리</li>
 *   <li>투명한 참조 처리 (사용자는 일반 Map처럼 사용)</li>
 * </ul>
 *
 * @param <K> 키 타입
 * @param <V> 값 타입
 * @author archmagece
 * @since 2025-01 (Phase 10-C)
 */
public class ReferenceBasedStorage<K, V> {

	private final ReferenceType referenceType;
	private final ConcurrentHashMap<K, Object> storage;  // Object는 V 또는 Reference<V>
	private final ReferenceQueue<V> referenceQueue;
	private final ConcurrentHashMap<Reference<V>, K> reverseMap;  // Reference -> Key 매핑

	/**
	 * ReferenceBasedStorage 생성자
	 *
	 * @param referenceType 참조 타입
	 */
	public ReferenceBasedStorage(ReferenceType referenceType) {
		this.referenceType = referenceType;
		this.storage = new ConcurrentHashMap<>();

		if (referenceType.isGcManaged()) {
			this.referenceQueue = new ReferenceQueue<>();
			this.reverseMap = new ConcurrentHashMap<>();
		} else {
			this.referenceQueue = null;
			this.reverseMap = null;
		}
	}

	/**
	 * 값을 저장합니다.
	 *
	 * @param key 키
	 * @param value 값
	 * @return 이전 값 (없으면 null)
	 */
	public V put(K key, V value) {
		if (value == null) {
			return remove(key);
		}

		// GC에 의해 회수된 항목 정리
		processQueue();

		Object wrappedValue = wrapValue(key, value);
		Object oldWrapped = storage.put(key, wrappedValue);

		return unwrapValue(oldWrapped);
	}

	/**
	 * 값을 조회합니다.
	 *
	 * @param key 키
	 * @return 값 (없거나 GC에 회수되었으면 null)
	 */
	public V get(K key) {
		// GC에 의해 회수된 항목 정리
		processQueue();

		Object wrapped = storage.get(key);
		if (wrapped == null) {
			return null;
		}

		V value = unwrapValue(wrapped);

		// Reference가 GC에 의해 회수되었으면 storage에서도 제거
		if (value == null && referenceType.isGcManaged()) {
			storage.remove(key);
		}

		return value;
	}

	/**
	 * 값을 제거합니다.
	 *
	 * @param key 키
	 * @return 제거된 값 (없으면 null)
	 */
	public V remove(K key) {
		Object wrapped = storage.remove(key);
		if (wrapped == null) {
			return null;
		}

		V value = unwrapValue(wrapped);

		// reverseMap에서도 제거
		if (wrapped instanceof Reference && reverseMap != null) {
			@SuppressWarnings("unchecked")
			Reference<V> ref = (Reference<V>) wrapped;
			reverseMap.remove(ref);
		}

		return value;
	}

	/**
	 * 모든 항목을 제거합니다.
	 */
	public void clear() {
		storage.clear();
		if (reverseMap != null) {
			reverseMap.clear();
		}
	}

	/**
	 * 저장소의 크기를 반환합니다.
	 *
	 * @return 항목 수
	 */
	public int size() {
		// GC에 의해 회수된 항목 정리
		processQueue();
		return storage.size();
	}

	/**
	 * 키 집합을 반환합니다.
	 *
	 * @return 키 집합
	 */
	public Set<K> keySet() {
		// GC에 의해 회수된 항목 정리
		processQueue();
		return storage.keySet();
	}

	/**
	 * 여러 항목을 한 번에 저장합니다.
	 *
	 * @param map 저장할 항목들
	 */
	public void putAll(Map<? extends K, ? extends V> map) {
		if (map == null) {
			return;
		}
		processQueue();
		for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
			put(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * 저장소의 내용을 일반 Map으로 변환합니다.
	 * GC에 의해 회수된 항목은 포함되지 않습니다.
	 *
	 * @return 현재 저장소의 스냅샷 Map
	 */
	public Map<K, V> toMap() {
		processQueue();
		Map<K, V> result = new HashMap<>();
		for (K key : storage.keySet()) {
			V value = get(key);
			if (value != null) {
				result.put(key, value);
			}
		}
		return result;
	}

	/**
	 * 값을 Reference로 감쌉니다.
	 *
	 * @param key 키
	 * @param value 값
	 * @return 감싼 객체 (STRONG이면 값 자체, 그 외는 Reference)
	 */
	private Object wrapValue(K key, V value) {
		switch (referenceType) {
			case SOFT:
				SoftReference<V> softRef = new SoftReference<>(value, referenceQueue);
				reverseMap.put(softRef, key);
				return softRef;
			case WEAK:
				WeakReference<V> weakRef = new WeakReference<>(value, referenceQueue);
				reverseMap.put(weakRef, key);
				return weakRef;
			case STRONG:
			default:
				return value;
		}
	}

	/**
	 * Reference에서 실제 값을 추출합니다.
	 *
	 * @param wrapped 감싼 객체
	 * @return 실제 값
	 */
	@SuppressWarnings("unchecked")
	private V unwrapValue(Object wrapped) {
		if (wrapped == null) {
			return null;
		}

		if (wrapped instanceof Reference) {
			Reference<V> ref = (Reference<V>) wrapped;
			return ref.get();
		}

		return (V) wrapped;
	}

	/**
	 * GC에 의해 회수된 Reference를 정리합니다.
	 */
	private void processQueue() {
		if (referenceQueue == null) {
			return;
		}

		Reference<? extends V> ref;
		while ((ref = referenceQueue.poll()) != null) {
			@SuppressWarnings("unchecked")
			Reference<V> typedRef = (Reference<V>) ref;

			K key = reverseMap.remove(typedRef);
			if (key != null) {
				storage.remove(key);
			}
		}
	}

	/**
	 * 현재 참조 타입을 반환합니다.
	 *
	 * @return 참조 타입
	 */
	public ReferenceType getReferenceType() {
		return referenceType;
	}
}
