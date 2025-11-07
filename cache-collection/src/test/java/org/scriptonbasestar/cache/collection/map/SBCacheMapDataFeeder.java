package org.scriptonbasestar.cache.collection.map;

import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author archmagece
 * @CreatedAt 2016-12-07 18
 */
public class SBCacheMapDataFeeder {
	private static final Logger log = LoggerFactory.getLogger(SBCacheMapDataFeeder.class);
	private static final Map<Long,String> sampleData = new HashMap<>();
	static {
		for (int i = 0; i < 30; i++) {
			log.trace("add item {}", i);
			sampleData.put((long) i, "item"+i);
		}
	}

	public String loadOne(Long key) throws SBCacheLoadFailException {
		log.trace("loadOne thread sleep 500");
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return sampleData.get(key);
	}

	public Map<Long, String> loadAll() throws SBCacheLoadFailException {
		log.trace("loadAll thread sleep 1000");
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return sampleData;
	}
}
