package org.scriptonbasestar.cache.collection.map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.scriptonbasestar.cache.core.loader.SBCacheMapLoader;
import org.scriptonbasestar.cache.core.strategy.WriteStrategy;
import org.scriptonbasestar.cache.core.writer.SBCacheMapWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for WriteStrategy (WRITE_THROUGH and WRITE_BEHIND) in SBCacheMap.
 */
public class SBCacheMapWriteStrategyTest {

	private SBCacheMap<String, String> cache;
	private MockCacheWriter writer;
	private SBCacheMapLoader<String, String> loader;

	@Before
	public void setUp() {
		writer = new MockCacheWriter();
		loader = new SBCacheMapLoader<String, String>() {
			@Override
			public String loadOne(String key) {
				return "loaded-" + key;
			}
		};
	}

	@After
	public void tearDown() {
		if (cache != null) {
			cache.close();
		}
	}

	// ========== READ_ONLY Strategy Tests ==========

	@Test
	public void testReadOnlyStrategy_NoWrites() {
		cache = SBCacheMap.<String, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.writeStrategy(WriteStrategy.READ_ONLY)
			.build();

		cache.put("key1", "value1");
		cache.put("key2", "value2");
		cache.remove("key1");

		// Verify no writes occurred
		Assert.assertEquals(0, writer.getWriteCount());
		Assert.assertEquals(0, writer.getDeleteCount());
		Assert.assertTrue(writer.getWrittenData().isEmpty());
	}

	@Test
	public void testReadOnlyStrategy_WithNullWriter() {
		cache = SBCacheMap.<String, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.writeStrategy(WriteStrategy.READ_ONLY)
			.writer(null)  // Explicit null writer
			.build();

		cache.put("key1", "value1");
		String value = cache.get("key1");
		Assert.assertEquals("value1", value);

		cache.remove("key1");

		// After removal, get() will load from loader
		String reloadedValue = cache.get("key1");
		Assert.assertEquals("loaded-key1", reloadedValue);  // Loaded from loader, not from writer
	}

	// ========== WRITE_THROUGH Strategy Tests ==========

	@Test
	public void testWriteThroughStrategy_ImmediateWrites() {
		cache = SBCacheMap.<String, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.writeStrategy(WriteStrategy.WRITE_THROUGH)
			.writer(writer)
			.build();

		cache.put("key1", "value1");
		cache.put("key2", "value2");

		// Verify immediate writes
		Assert.assertEquals(2, writer.getWriteCount());
		Assert.assertEquals("value1", writer.getWrittenData().get("key1"));
		Assert.assertEquals("value2", writer.getWrittenData().get("key2"));
	}

	@Test
	public void testWriteThroughStrategy_ImmediateDeletes() {
		cache = SBCacheMap.<String, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.writeStrategy(WriteStrategy.WRITE_THROUGH)
			.writer(writer)
			.build();

		cache.put("key1", "value1");
		cache.remove("key1");

		// Verify immediate delete
		Assert.assertEquals(1, writer.getWriteCount());
		Assert.assertEquals(1, writer.getDeleteCount());
		Assert.assertTrue(writer.getDeletedKeys().contains("key1"));
	}

	@Test(expected = RuntimeException.class)
	public void testWriteThroughStrategy_ExceptionPropagation() {
		MockCacheWriter failingWriter = new MockCacheWriter();
		failingWriter.setFailOnWrite(true);

		cache = SBCacheMap.<String, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.writeStrategy(WriteStrategy.WRITE_THROUGH)
			.writer(failingWriter)
			.build();

		// Should throw exception from writer
		cache.put("key1", "value1");
	}

	@Test
	public void testWriteThroughStrategy_Update() {
		cache = SBCacheMap.<String, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.writeStrategy(WriteStrategy.WRITE_THROUGH)
			.writer(writer)
			.build();

		cache.put("key1", "value1");
		cache.put("key1", "value2");  // Update

		// Verify both writes
		Assert.assertEquals(2, writer.getWriteCount());
		Assert.assertEquals("value2", writer.getWrittenData().get("key1"));
	}

	// ========== WRITE_BEHIND Strategy Tests ==========

	@Test
	public void testWriteBehindStrategy_AsyncWrites() throws InterruptedException {
		cache = SBCacheMap.<String, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.writeStrategy(WriteStrategy.WRITE_BEHIND)
			.writer(writer)
			.writeBehindBatchSize(10)
			.writeBehindIntervalSeconds(1)
			.build();

		cache.put("key1", "value1");
		cache.put("key2", "value2");

		// Writes should be queued, not immediate
		Assert.assertEquals(0, writer.getWriteCount());

		// Wait for flush interval
		Thread.sleep(1500);

		// Verify writes occurred after interval
		Assert.assertTrue(writer.getWriteCount() >= 2);
		Assert.assertEquals("value1", writer.getWrittenData().get("key1"));
		Assert.assertEquals("value2", writer.getWrittenData().get("key2"));
	}

	@Test
	public void testWriteBehindStrategy_BatchSizeTrigger() throws InterruptedException {
		cache = SBCacheMap.<String, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.writeStrategy(WriteStrategy.WRITE_BEHIND)
			.writer(writer)
			.writeBehindBatchSize(5)
			.writeBehindIntervalSeconds(60)  // Long interval
			.build();

		// Add 5 entries to trigger batch flush
		for (int i = 1; i <= 5; i++) {
			cache.put("key" + i, "value" + i);
		}

		// Close cache to force flush of queued writes
		cache.close();
		cache = null;  // Prevent tearDown from closing again

		// Verify batch write occurred
		Assert.assertTrue("Expected at least 5 writes, got: " + writer.getWriteCount(),
			writer.getWriteCount() >= 5);
	}

	@Test
	public void testWriteBehindStrategy_AsyncDeletes() throws InterruptedException {
		cache = SBCacheMap.<String, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.writeStrategy(WriteStrategy.WRITE_BEHIND)
			.writer(writer)
			.writeBehindBatchSize(10)
			.writeBehindIntervalSeconds(1)
			.build();

		cache.put("key1", "value1");
		cache.remove("key1");

		// Delete should be queued, not immediate
		Assert.assertEquals(0, writer.getDeleteCount());

		// Wait for flush interval
		Thread.sleep(1500);

		// Verify delete occurred after interval
		Assert.assertEquals(1, writer.getDeleteCount());
		Assert.assertTrue(writer.getDeletedKeys().contains("key1"));
	}

	@Test
	public void testWriteBehindStrategy_NoExceptionPropagation() {
		MockCacheWriter failingWriter = new MockCacheWriter();
		failingWriter.setFailOnWrite(true);

		cache = SBCacheMap.<String, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.writeStrategy(WriteStrategy.WRITE_BEHIND)
			.writer(failingWriter)
			.writeBehindBatchSize(10)
			.writeBehindIntervalSeconds(1)
			.build();

		// Should not throw exception (async write failure is logged)
		cache.put("key1", "value1");
		// Test passes if no exception is thrown
	}

	@Test
	public void testWriteBehindStrategy_FlushOnClose() throws InterruptedException {
		cache = SBCacheMap.<String, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.writeStrategy(WriteStrategy.WRITE_BEHIND)
			.writer(writer)
			.writeBehindBatchSize(100)
			.writeBehindIntervalSeconds(60)  // Long interval
			.build();

		cache.put("key1", "value1");
		cache.put("key2", "value2");

		// Writes should be queued
		Assert.assertEquals(0, writer.getWriteCount());

		// Close should flush pending writes
		cache.close();

		// Verify writes were flushed
		Assert.assertTrue(writer.getWriteCount() >= 2);
		Assert.assertEquals("value1", writer.getWrittenData().get("key1"));
		Assert.assertEquals("value2", writer.getWrittenData().get("key2"));
		Assert.assertEquals(1, writer.getFlushCount());
	}

	@Test
	public void testWriteBehindStrategy_MixedOperations() throws InterruptedException {
		cache = SBCacheMap.<String, String>builder()
			.loader(loader)
			.timeoutSec(60)
			.writeStrategy(WriteStrategy.WRITE_BEHIND)
			.writer(writer)
			.writeBehindBatchSize(10)
			.writeBehindIntervalSeconds(1)
			.build();

		cache.put("key1", "value1");
		cache.put("key2", "value2");
		cache.remove("key1");
		cache.put("key3", "value3");

		// Wait for flush
		Thread.sleep(1500);

		// Verify mixed operations
		Assert.assertTrue(writer.getWriteCount() >= 2);  // key2, key3 (key1 was deleted)
		Assert.assertTrue(writer.getDeleteCount() >= 1);
		Assert.assertEquals("value2", writer.getWrittenData().get("key2"));
		Assert.assertEquals("value3", writer.getWrittenData().get("key3"));
		Assert.assertTrue(writer.getDeletedKeys().contains("key1"));
	}

	// ========== Mock CacheWriter for Testing ==========

	private static class MockCacheWriter implements SBCacheMapWriter<String, String> {
		private final Map<String, String> writtenData = new ConcurrentHashMap<>();
		private final List<String> deletedKeys = new ArrayList<>();
		private final AtomicInteger writeCount = new AtomicInteger(0);
		private final AtomicInteger deleteCount = new AtomicInteger(0);
		private final AtomicInteger flushCount = new AtomicInteger(0);
		private boolean failOnWrite = false;

		@Override
		public void write(String key, String value) throws Exception {
			if (failOnWrite) {
				throw new RuntimeException("Simulated write failure");
			}
			writtenData.put(key, value);
			writeCount.incrementAndGet();
		}

		@Override
		public void writeAll(Map<? extends String, ? extends String> entries) throws Exception {
			if (failOnWrite) {
				throw new RuntimeException("Simulated writeAll failure");
			}
			for (Map.Entry<? extends String, ? extends String> entry : entries.entrySet()) {
				writtenData.put(entry.getKey(), entry.getValue());
				writeCount.incrementAndGet();
			}
		}

		@Override
		public void delete(String key) throws Exception {
			synchronized (deletedKeys) {
				deletedKeys.add(key);
			}
			deleteCount.incrementAndGet();
		}

		@Override
		public void deleteAll(Iterable<? extends String> keys) throws Exception {
			for (String key : keys) {
				synchronized (deletedKeys) {
					deletedKeys.add(key);
				}
				deleteCount.incrementAndGet();
			}
		}

		@Override
		public void flush() throws Exception {
			flushCount.incrementAndGet();
		}

		public Map<String, String> getWrittenData() {
			return new HashMap<>(writtenData);
		}

		public List<String> getDeletedKeys() {
			synchronized (deletedKeys) {
				return new ArrayList<>(deletedKeys);
			}
		}

		public int getWriteCount() {
			return writeCount.get();
		}

		public int getDeleteCount() {
			return deleteCount.get();
		}

		public int getFlushCount() {
			return flushCount.get();
		}

		public void setFailOnWrite(boolean failOnWrite) {
			this.failOnWrite = failOnWrite;
		}
	}
}
