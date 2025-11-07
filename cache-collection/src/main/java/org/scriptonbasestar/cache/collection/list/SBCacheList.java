package org.scriptonbasestar.cache.collection.list;

import lombok.extern.slf4j.Slf4j;
import org.scriptonbasestar.cache.collection.strategy.LoadStrategy;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author archmagece
 * @with sb-cache-java
 * @since 2016-11-06
 *
 * LoadStrategy.ONE 적용불가.
 * 다른 스레드에서 값을 수정하고 하면 out of index exception이 수시로 발생할듯..
 */
@Slf4j
public class SBCacheList<E> extends ArrayList<E> {

	private static final Object syncObject = new Object();
	private LocalTime updatedAt = LocalTime.now();
	private final int timeoutSec = 300;
	private ExecutorService executor = Executors.newFixedThreadPool(1);
	private final SBCacheListLoader<E> loader;
	private final LoadStrategy loadStrategy;

	public SBCacheList(SBCacheListLoader<E> loader, LoadStrategy loadStrategy) {
		super(Collections.synchronizedList(new ArrayList()));
		log.trace("SBCacheList Constructor - s");
		this.loader = loader;
		this.loadStrategy = loadStrategy;
		super.addAll(this.loader.loadAll());
		log.trace("SBCacheList Constructor - e");
	}

	public SBCacheList(List<? extends E> collection, SBCacheListLoader<E> loader, LoadStrategy loadStrategy) {
		super(Collections.synchronizedList(collection));
		log.trace("SBCacheList Constructor - s");
		this.loader = loader;
		this.loadStrategy = loadStrategy;
		super.addAll(this.loader.loadAll());
		log.trace("SBCacheList Constructor - e");
	}

	static class RunLoader<E> implements Runnable {
		SBCacheList sbCacheList;
		SBCacheListLoader<E> loader;
		public RunLoader(SBCacheList sbCacheList, SBCacheListLoader<E> loader){
			this.sbCacheList = sbCacheList;
			this.loader = loader;
		}
		@Override
		public void run() {
			synchronized (syncObject){
				sbCacheList.clear();
				sbCacheList.addAll(loader.loadAll());
			}
		}
	}

	@Override
	public E get(int index) {
		log.trace("SBCacheList get - s");
		if (updatedAt.plusSeconds(timeoutSec).isBefore(LocalTime.now())) {
			log.trace("SBCacheList get - cache time expired");
			updatedAt = LocalTime.now();

			if(loadStrategy == LoadStrategy.ONE){
				log.trace("SBCacheList get - loadOne");
				super.set(index, loader.loadOne(index));
			}else{
				log.trace("SBCacheList get - loadAll");
				executor.execute(new RunLoader(this, loader));
			}
		}
		return super.get(index);
	}
}
