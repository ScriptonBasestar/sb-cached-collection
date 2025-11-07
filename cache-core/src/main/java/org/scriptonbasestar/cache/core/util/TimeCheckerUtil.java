package org.scriptonbasestar.cache.core.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * @athor archmagece
 * @since 2017-01-17 16
 */
@Slf4j
@UtilityClass
public class TimeCheckerUtil {

	/**
	 * @deprecated Use {@link #checkExpired(long, int)} with System.currentTimeMillis() instead.
	 * This method is kept for backward compatibility.
	 */
	@Deprecated
	public static boolean checkExpired(Date now, int timeoutSecond){
		return checkExpired(now.getTime(), timeoutSecond);
	}

	/**
	 * 주어진 타임스탬프가 현재 시간 기준으로 만료되지 않았는지 확인합니다.
	 *
	 * @param now 확인할 타임스탬프 (milliseconds)
	 * @param timeoutSecond 타임아웃 시간 (초)
	 * @return 만료되지 않았으면 true, 만료되었으면 false
	 */
	public static boolean checkExpired(long now, int timeoutSecond){
		long currentTimeMillis = System.currentTimeMillis();
		long expiryTime = currentTimeMillis - (timeoutSecond * 1000L);

		if(log.isTraceEnabled()){
			Instant nowInstant = Instant.ofEpochMilli(now);
			Instant expiryInstant = Instant.ofEpochMilli(expiryTime);
			log.trace("checkExpired param - now : {}, timeoutSecond : {}", nowInstant, timeoutSecond);
			log.trace("checkExpired 비교 - now : {}, 비교시간 : {}", nowInstant, expiryInstant);
		}

		return now > expiryTime;
	}

	/**
	 * Duration을 사용하는 새로운 메서드 (Java 8+ 스타일)
	 *
	 * @param timestampMillis 확인할 타임스탬프 (milliseconds)
	 * @param timeout 타임아웃 Duration
	 * @return 만료되지 않았으면 true, 만료되었으면 false
	 */
	public static boolean checkExpired(long timestampMillis, Duration timeout){
		Instant timestamp = Instant.ofEpochMilli(timestampMillis);
		Instant now = Instant.now();
		Instant expiryTime = now.minus(timeout);

		if(log.isTraceEnabled()){
			log.trace("checkExpired param - timestamp : {}, timeout : {}", timestamp, timeout);
			log.trace("checkExpired 비교 - timestamp : {}, expiryTime : {}", timestamp, expiryTime);
		}

		return timestamp.isAfter(expiryTime);
	}

}
