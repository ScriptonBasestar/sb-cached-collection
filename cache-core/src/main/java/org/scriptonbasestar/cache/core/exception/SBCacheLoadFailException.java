package org.scriptonbasestar.cache.core.exception;

/**
 * @author archmagece
 * @with sb-cache-java
 * @since 2016-11-06
 *
 */
public class SBCacheLoadFailException extends RuntimeException {

	public SBCacheLoadFailException() {
		super();
	}

	public SBCacheLoadFailException(String message) {
		super(message);
	}

	public SBCacheLoadFailException(String message, Throwable cause) {
		super(message, cause);
	}

	public SBCacheLoadFailException(Throwable cause) {
		super(cause);
	}
}
