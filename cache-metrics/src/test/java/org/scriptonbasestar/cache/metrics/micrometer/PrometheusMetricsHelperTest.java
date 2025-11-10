package org.scriptonbasestar.cache.metrics.micrometer;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.Test;
import org.scriptonbasestar.cache.collection.metrics.CacheMetrics;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * PrometheusMetricsHelper 테스트
 *
 * @author archmagece
 * @since 2025-01
 */
public class PrometheusMetricsHelperTest {

	@Test
	public void testCreatePrometheusRegistry() {
		// When
		PrometheusMeterRegistry registry = PrometheusMetricsHelper.createPrometheusRegistry();

		// Then
		assertNotNull(registry);
	}

	@Test
	public void testBindMetrics() {
		// Given
		CacheMetrics metrics = new CacheMetrics();
		PrometheusMeterRegistry registry = PrometheusMetricsHelper.createPrometheusRegistry();

		// When
		MicrometerMetricsAdapter adapter = PrometheusMetricsHelper.bindMetrics(
			metrics, registry, "users"
		);

		// Then
		assertNotNull(adapter);
		assertEquals("users", adapter.getCacheName());
		assertSame(metrics, adapter.getCacheMetrics());
	}

	@Test
	public void testScrapeMetrics() {
		// Given
		PrometheusMeterRegistry registry = PrometheusMetricsHelper.createPrometheusRegistry();
		CacheMetrics metrics = new CacheMetrics();
		MicrometerMetricsAdapter adapter = PrometheusMetricsHelper.bindMetrics(
			metrics, registry, "test-cache"
		);

		// When
		adapter.recordHit();
		adapter.recordHit();
		adapter.recordMiss();
		String prometheusFormat = PrometheusMetricsHelper.scrapeMetrics(registry);

		// Then
		assertNotNull(prometheusFormat);
		assertTrue(prometheusFormat.contains("cache_hits_total"));
		assertTrue(prometheusFormat.contains("cache_misses_total"));
		assertTrue(prometheusFormat.contains("test-cache"));
	}

	@Test
	public void testBindMultipleMetrics() {
		// Given
		PrometheusMeterRegistry registry = PrometheusMetricsHelper.createPrometheusRegistry();
		Map<String, CacheMetrics> metricsMap = new HashMap<>();
		metricsMap.put("cache1", new CacheMetrics());
		metricsMap.put("cache2", new CacheMetrics());
		metricsMap.put("cache3", new CacheMetrics());

		// When
		MicrometerMetricsAdapter[] adapters = PrometheusMetricsHelper.bindMultipleMetrics(
			metricsMap, registry
		);

		// Then
		assertEquals(3, adapters.length);

		// 각 어댑터의 캐시 이름 확인
		boolean hasCache1 = false;
		boolean hasCache2 = false;
		boolean hasCache3 = false;

		for (MicrometerMetricsAdapter adapter : adapters) {
			String name = adapter.getCacheName();
			if ("cache1".equals(name)) hasCache1 = true;
			if ("cache2".equals(name)) hasCache2 = true;
			if ("cache3".equals(name)) hasCache3 = true;
		}

		assertTrue(hasCache1);
		assertTrue(hasCache2);
		assertTrue(hasCache3);
	}

	@Test
	public void testPrometheusFormatContent() {
		// Given
		PrometheusMeterRegistry registry = PrometheusMetricsHelper.createPrometheusRegistry();
		CacheMetrics metrics = new CacheMetrics();
		MicrometerMetricsAdapter adapter = PrometheusMetricsHelper.bindMetrics(
			metrics, registry, "user-cache"
		);

		// When
		adapter.recordHit();
		adapter.recordHit();
		adapter.recordHit();
		adapter.recordMiss();
		adapter.recordLoadSuccess(1000000L);
		adapter.recordEviction(2);

		String prometheusFormat = PrometheusMetricsHelper.scrapeMetrics(registry);

		// Then
		// 히트 카운터 확인 (포맷이 다를 수 있으므로 유연하게 검증)
		assertTrue(prometheusFormat.contains("cache_hits") && prometheusFormat.contains("user-cache"));
		assertTrue(prometheusFormat.contains("3.0"));

		// 미스 카운터 확인
		assertTrue(prometheusFormat.contains("cache_misses") && prometheusFormat.contains("user-cache"));
		assertTrue(prometheusFormat.contains("1.0"));

		// 축출 카운터 확인
		assertTrue(prometheusFormat.contains("cache_evictions") && prometheusFormat.contains("user-cache"));
		assertTrue(prometheusFormat.contains("2.0"));

		// 로드 성공 카운터 확인
		assertTrue(prometheusFormat.contains("cache_loads") && prometheusFormat.contains("success"));

		// 히트율 게이지 확인
		assertTrue(prometheusFormat.contains("cache_hit_rate") && prometheusFormat.contains("user-cache"));
		assertTrue(prometheusFormat.contains("0.75"));
	}

	@Test
	public void testMultipleCachesInPrometheus() {
		// Given
		PrometheusMeterRegistry registry = PrometheusMetricsHelper.createPrometheusRegistry();

		CacheMetrics metrics1 = new CacheMetrics();
		CacheMetrics metrics2 = new CacheMetrics();

		MicrometerMetricsAdapter adapter1 = PrometheusMetricsHelper.bindMetrics(
			metrics1, registry, "users"
		);
		MicrometerMetricsAdapter adapter2 = PrometheusMetricsHelper.bindMetrics(
			metrics2, registry, "products"
		);

		// When
		adapter1.recordHit();
		adapter1.recordHit();

		adapter2.recordHit();
		adapter2.recordMiss();
		adapter2.recordMiss();

		String prometheusFormat = PrometheusMetricsHelper.scrapeMetrics(registry);

		// Then
		assertTrue(prometheusFormat.contains("cache_hits") && prometheusFormat.contains("users"));
		assertTrue(prometheusFormat.contains("cache_hits") && prometheusFormat.contains("products"));
		assertTrue(prometheusFormat.contains("cache_misses") && prometheusFormat.contains("products"));
		assertTrue(prometheusFormat.contains("2.0"));
		assertTrue(prometheusFormat.contains("1.0"));
	}

	@Test
	public void testEmptyMetricsMap() {
		// Given
		PrometheusMeterRegistry registry = PrometheusMetricsHelper.createPrometheusRegistry();
		Map<String, CacheMetrics> metricsMap = new HashMap<>();

		// When
		MicrometerMetricsAdapter[] adapters = PrometheusMetricsHelper.bindMultipleMetrics(
			metricsMap, registry
		);

		// Then
		assertEquals(0, adapters.length);
	}
}
