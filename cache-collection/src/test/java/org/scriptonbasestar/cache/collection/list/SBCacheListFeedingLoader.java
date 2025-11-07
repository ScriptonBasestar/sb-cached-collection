package org.scriptonbasestar.cache.collection.list;


import lombok.extern.slf4j.Slf4j;
import org.scriptonbasestar.cache.core.loader.SBCacheListLoader;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author archmagece
 * @since 2016-11-07
 */
@Slf4j
public class SBCacheListFeedingLoader implements SBCacheListLoader<String> {
	private static final List<String> sampleData = new ArrayList<>();

	static {
		for (int i = 0; i < 30; i++) {
			log.debug("add item {}", i);
			sampleData.add("" + i + i + i + i + i);
		}
	}

	@Override
	public String loadOne(int index) throws SBCacheLoadFailException {
		try {
			log.debug("loadOne thread sleep 3000");
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return sampleData.get(index);
	}

	@Override
	public List loadAll() throws SBCacheLoadFailException {
		try {
			log.debug("loadAll thread sleep 3000");
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return sampleData;
	}
}
