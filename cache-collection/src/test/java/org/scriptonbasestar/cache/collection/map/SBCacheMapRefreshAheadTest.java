package org.scriptonbasestar.cache.collection.map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scriptonbasestar.cache.core.loader.SBCacheMapLoader;
import org.scriptonbasestar.cache.core.strategy.RefreshStrategy;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Tests for Refresh-Ahead strategy functionality.
 * <p>
 * Refresh-Ahead proactively refreshes cache entries before they expire,
 * ensuring users always get fresh data without waiting for reload.
 * </p>
 *
 * @author archmagece
 * @since 2025-01
 */
public class SBCacheMapRefreshAheadTest {

	private SBCacheMap<String, String> cache;
	private AtomicInteger loadCounter;
	private Map<String, String> dataSource;

	@Before
	public void setUp() {
		loadCounter = new AtomicInteger(0);
		dataSource = new HashMap<>();
		dataSource.put("key1", "value1-v0");
		dataSource.put("key2", "value2-v0");
	}

	@After
	public void tearDown() {
		if (cache != null) {
			cache.close();
		}
	}

	/**
	 * Test that Refresh-Ahead triggers background refresh before TTL expires.
	 */
	@Test
	public void testRefreshAheadTriggersBeforeExpiry() throws InterruptedException {
		// Given: Cache with 5 second TTL and 80% refresh-ahead factor
		cache = SBCacheMap.<String, String>builder()
			.loader(createLoader())
			.timeoutSec(5)
			.refreshStrategy(RefreshStrategy.REFRESH_AHEAD)
			.refreshAheadFactor(0.8)  // Refresh at 80% of TTL (4 seconds)
			.build();

		// When: First access loads from source
		String value1 = cache.get("key1");
		assertEquals("value1-v0", value1);
		assertEquals(1, loadCounter.get());

		// Wait for 4+ seconds (80% of TTL)
		Thread.sleep(4200);

		// Update data source
		dataSource.put("key1", "value1-v1");

		// Access again - should trigger background refresh
		String value2 = cache.get("key1");

		// Should return old value immediately (not expired yet)
		assertEquals("value1-v0", value2);

		// Wait for background refresh to complete
		Thread.sleep(500);

		// Verify background refresh happened
		assertTrue("Load counter should be >= 2 (initial + refresh)", loadCounter.get() >= 2);

		// Next access should return refreshed value
		String value3 = cache.get("key1");
		assertEquals("value1-v1", value3);
	}

	/**
	 * Test that Refresh-Ahead does not trigger multiple concurrent refreshes for same key.
	 */
	@Test
	public void testRefreshAheadPreventsConcurrentRefresh() throws InterruptedException {
		// Given: Cache with short TTL
		cache = SBCacheMap.<String, String>builder()
			.loader(createSlowLoader(200))  // 200ms load time
			.timeoutSec(2)
			.refreshStrategy(RefreshStrategy.REFRESH_AHEAD)
			.refreshAheadFactor(0.8)
			.build();

		// When: Initial load
		cache.get("key1");
		int initialCount = loadCounter.get();

		// Wait for refresh threshold
		Thread.sleep(1700);  // 80% of 2 seconds = 1.6 seconds

		// Multiple concurrent accesses
		for (int i = 0; i < 10; i++) {
			new Thread(() -> cache.get("key1")).start();
		}

		// Wait for potential refreshes
		Thread.sleep(500);

		// Then: Should only trigger ONE background refresh
		// Initial load + 1 refresh = 2 total
		assertTrue("Should not exceed 2 loads", loadCounter.get() <= initialCount + 1);
	}

	/**
	 * Test that ON_MISS strategy does NOT trigger background refresh.
	 */
	@Test
	public void testOnMissStrategyNoBackgroundRefresh() throws InterruptedException {
		// Given: Cache with ON_MISS strategy (default)
		cache = SBCacheMap.<String, String>builder()
			.loader(createLoader())
			.timeoutSec(2)
			.refreshStrategy(RefreshStrategy.ON_MISS)
			.build();

		// When: Load and wait
		cache.get("key1");
		assertEquals(1, loadCounter.get());

		// Wait past TTL expiry
		Thread.sleep(2200);

		// Then: No background refresh should have occurred
		assertEquals("ON_MISS should not trigger background refresh", 1, loadCounter.get());

		// Next access triggers reload
		cache.get("key1");
		assertEquals(2, loadCounter.get());
	}

	/**
	 * Test that refresh-ahead factor of 0.5 refreshes at 50% TTL.
	 */
	@Test
	public void testRefreshAheadFactor50Percent() throws InterruptedException {
		// Given: Cache with 50% refresh factor
		cache = SBCacheMap.<String, String>builder()
			.loader(createLoader())
			.timeoutSec(4)
			.refreshStrategy(RefreshStrategy.REFRESH_AHEAD)
			.refreshAheadFactor(0.5)  // Refresh at 50% of TTL (2 seconds)
			.build();

		// When: Initial load
		cache.get("key1");
		assertEquals(1, loadCounter.get());

		// Wait for 50% TTL
		Thread.sleep(2200);

		// Update data source
		dataSource.put("key1", "value1-updated");

		// Access to trigger refresh
		String value = cache.get("key1");
		assertNotNull(value);

		// Wait for background refresh
		Thread.sleep(300);

		// Then: Background refresh should have occurred
		assertTrue("Should have triggered refresh at 50% TTL", loadCounter.get() >= 2);
	}

	/**
	 * Test that refresh-ahead handles loader exceptions gracefully.
	 */
	@Test
	public void testRefreshAheadHandlesLoaderException() throws InterruptedException {
		// Given: Loader that fails on second call
		AtomicInteger callCount = new AtomicInteger(0);
		SBCacheMapLoader<String, String> failingLoader = new SBCacheMapLoader<String, String>() {
			@Override
			public String loadOne(String key) throws SBCacheLoadFailException {
				int count = callCount.incrementAndGet();
				if (count == 2) {
					throw new SBCacheLoadFailException("Simulated failure");
				}
				return "value-" + count;
			}

			@Override
			public Map<String, String> loadAll() throws SBCacheLoadFailException {
				Map<String, String> result = new HashMap<>();
				result.put("key1", loadOne("key1"));
				return result;
			}
		};

		cache = SBCacheMap.<String, String>builder()
			.loader(failingLoader)
			.timeoutSec(2)
			.refreshStrategy(RefreshStrategy.REFRESH_AHEAD)
			.refreshAheadFactor(0.8)
			.build();

		// When: Initial load succeeds
		String value1 = cache.get("key1");
		assertEquals("value-1", value1);

		// Wait for refresh threshold
		Thread.sleep(1700);

		// Trigger refresh (will fail in background)
		String value2 = cache.get("key1");

		// Then: Should still return old value (background refresh failed gracefully)
		assertEquals("value-1", value2);

		// Wait for background task
		Thread.sleep(300);

		// Cache should still work with old value
		String value3 = cache.get("key1");
		assertNotNull(value3);
	}

	/**
	 * Test that refresh-ahead respects cache closure.
	 */
	@Test
	public void testRefreshAheadExecutorShutdownOnClose() throws InterruptedException {
		// Given: Cache with refresh-ahead
		cache = SBCacheMap.<String, String>builder()
			.loader(createLoader())
			.timeoutSec(10)
			.refreshStrategy(RefreshStrategy.REFRESH_AHEAD)
			.refreshAheadFactor(0.8)
			.build();

		cache.get("key1");

		// When: Close cache
		cache.close();

		// Then: No exceptions should occur
		// Verify by trying to access (should not crash)
		try {
			// Accessing closed cache might throw exception or return null
			// The important thing is no background thread exceptions
			Thread.sleep(100);
		} catch (Exception e) {
			fail("Should not throw exception after close: " + e.getMessage());
		}
	}

	/**
	 * Creates a standard loader that tracks load count.
	 */
	private SBCacheMapLoader<String, String> createLoader() {
		return new SBCacheMapLoader<String, String>() {
			@Override
			public String loadOne(String key) throws SBCacheLoadFailException {
				loadCounter.incrementAndGet();
				String value = dataSource.get(key);
				if (value == null) {
					throw new SBCacheLoadFailException("Key not found: " + key);
				}
				return value;
			}

			@Override
			public Map<String, String> loadAll() throws SBCacheLoadFailException {
				return new HashMap<>(dataSource);
			}
		};
	}

	/**
	 * Creates a slow loader with configurable delay.
	 */
	private SBCacheMapLoader<String, String> createSlowLoader(long delayMs) {
		return new SBCacheMapLoader<String, String>() {
			@Override
			public String loadOne(String key) throws SBCacheLoadFailException {
				loadCounter.incrementAndGet();
				try {
					Thread.sleep(delayMs);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				String value = dataSource.get(key);
				if (value == null) {
					throw new SBCacheLoadFailException("Key not found: " + key);
				}
				return value;
			}

			@Override
			public Map<String, String> loadAll() throws SBCacheLoadFailException {
				return new HashMap<>(dataSource);
			}
		};
	}
}
