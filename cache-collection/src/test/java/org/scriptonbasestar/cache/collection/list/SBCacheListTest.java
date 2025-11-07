package org.scriptonbasestar.cache.collection.list;

import org.junit.Test;
import org.scriptonbasestar.cache.core.strategy.LoadStrategy;
import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;

import java.util.List;

/**
 * @author archmagece
 * @since 2016-11-07
 */
public class SBCacheListTest {

	@Test
	public void testLoad(){
		final SBCacheListFeedingLoader dataFeed = new SBCacheListFeedingLoader();

		System.out.println(dataFeed.loadOne(0));
//		System.out.println(dataFeed.loadAll());
		System.out.println(dataFeed.loadOne(0));
//		System.out.println(dataFeed.loadAll());
		System.out.println(dataFeed.loadOne(0));
//		System.out.println(dataFeed.loadAll());
	}

	@Test
	public void testLoadCache(){
		final SBCacheListFeedingLoader dataFeed = new SBCacheListFeedingLoader();
		SBCacheList<String> cacheData = new SBCacheList<>(new SBCacheListLoader<String>(){
			@Override
			public String loadOne(int index) throws SBCacheLoadFailException {
				return dataFeed.loadOne(index);
			}
			@Override
			public List<String> loadAll() throws SBCacheLoadFailException {
				return dataFeed.loadAll();
			}
		}, LoadStrategy.ALL);

		System.out.println(cacheData.get(0));
		System.out.println(cacheData);
		System.out.println(cacheData.get(0));
		System.out.println(cacheData);
		System.out.println(cacheData.get(0));
		System.out.println(cacheData);
	}
}
