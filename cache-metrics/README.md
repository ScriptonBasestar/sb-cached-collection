# cache-metrics - Prometheus/Micrometer Integration

Micrometer 기반의 메트릭 수집 및 Prometheus 통합 모듈입니다.

## Maven Dependency

```xml
<dependency>
    <groupId>org.scriptonbasestar.cache</groupId>
    <artifactId>cache-metrics</artifactId>
    <version>0.1.0</version>
</dependency>

<!-- Micrometer Prometheus registry (required) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
    <version>1.10.0</version>
</dependency>
```

## Quick Start

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.scriptonbasestar.cache.metrics.micrometer.MicrometerMetricsAdapter;

// 1. Prometheus Registry 생성
PrometheusMeterRegistry prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

// 2. 캐시 생성 시 MeterRegistry 통합
SBCacheMap<String, User> cache = SBCacheMap.<String, User>builder()
    .loader(userLoader)
    .timeoutSec(300)
    .enableMetrics(true)
    .build();

// 3. Micrometer 어댑터 생성 및 등록
MicrometerMetricsAdapter<String, User> adapter = new MicrometerMetricsAdapter<>(
    prometheusRegistry,
    "user_cache",
    cache
);

// 4. Prometheus 메트릭 수집
String metrics = prometheusRegistry.scrape();
System.out.println(metrics);
```

## Exported Metrics

```
# Cache hit/miss counters
cache_hits_total{cache="user_cache"} 15234
cache_misses_total{cache="user_cache"} 892

# Cache size
cache_size{cache="user_cache"} 8234
cache_max_size{cache="user_cache"} 10000

# Eviction counter
cache_evictions_total{cache="user_cache"} 45

# Load duration (seconds)
cache_load_duration_seconds{cache="user_cache",quantile="0.5"} 0.015
cache_load_duration_seconds{cache="user_cache",quantile="0.95"} 0.032
cache_load_duration_seconds{cache="user_cache",quantile="0.99"} 0.087
cache_load_duration_seconds_count{cache="user_cache"} 16126
cache_load_duration_seconds_sum{cache="user_cache"} 242.5

# Hit rate gauge
cache_hit_rate{cache="user_cache"} 0.945
```

## Spring Boot Integration

```java
@Configuration
public class MetricsConfig {

    @Bean
    public CacheManager cacheManager(MeterRegistry meterRegistry) {
        SBCacheMap<Object, Object> userCache = SBCacheMap.builder()
            .loader(userLoader)
            .timeoutSec(300)
            .enableMetrics(true)
            .build();

        // Micrometer 어댑터 등록
        new MicrometerMetricsAdapter<>(meterRegistry, "users", userCache);

        return new SBCacheManager().addCache("users", userCache);
    }
}
```

## Prometheus Scrape Endpoint

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

**Scrape URL**: `http://localhost:8080/actuator/prometheus`

## Grafana Dashboard

메트릭 시각화를 위한 권장 Grafana 쿼리:

```promql
# Hit Rate
rate(cache_hits_total[5m]) / (rate(cache_hits_total[5m]) + rate(cache_misses_total[5m]))

# Cache Size Usage
cache_size / cache_max_size

# Average Load Time (95th percentile)
histogram_quantile(0.95, rate(cache_load_duration_seconds_bucket[5m]))

# Eviction Rate
rate(cache_evictions_total[5m])
```

## API Reference

### MicrometerMetricsAdapter

```java
public class MicrometerMetricsAdapter<K, V> {
    /**
     * Micrometer 메트릭 어댑터 생성
     *
     * @param registry MeterRegistry (Prometheus, Datadog, etc.)
     * @param cacheName 캐시 이름 (태그로 사용됨)
     * @param cache SBCacheMap 인스턴스
     */
    public MicrometerMetricsAdapter(
        MeterRegistry registry,
        String cacheName,
        SBCacheMap<K, V> cache
    )
}
```

## License

Apache License 2.0
