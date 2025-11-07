package org.scriptonbasestar.cache.core.loader;

import org.scriptonbasestar.cache.core.exception.SBCacheLoadFailException;

import java.util.List;

/**
 * @author archmagece
 * @with sb-cache-java
 * @since 2016-11-06
 *
 */
public interface SBCacheListLoader<E> {
	E loadOne(int index) throws SBCacheLoadFailException;
	List<E> loadAll() throws SBCacheLoadFailException;
}
