package org.scriptonbasestar.cache.core.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;

/**
 * @athor archmagece
 * @since 2017-01-17 16
 */
public class TimeCheckerUtilTest {

	@Before
	public void before(){
		Logger root = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		root.setLevel(Level.ALL);
	}

	@Test
	public void checkExpiredTest1(){
		Calendar calendar = Calendar.getInstance();
		Date now = new Date();
		//현재시간 true
		System.out.println("현재시간 start : "+calendar.getTime());
		System.out.println(calendar.getTimeInMillis());
		Assert.assertTrue(TimeCheckerUtil.checkExpired(calendar.getTime(), 10));
		System.out.println("====================");

		//5초전 true
		calendar.setTime(now);
		calendar.add(Calendar.SECOND, -5);
		System.out.println("5초전 start : "+calendar.getTime());
		System.out.println(calendar.getTimeInMillis());
		Assert.assertTrue(TimeCheckerUtil.checkExpired(calendar.getTime(), 10));
		System.out.println("====================");

		//9초전 true
		calendar.setTime(now);
		calendar.add(Calendar.SECOND, -9);
		System.out.println("9초전 start : "+calendar.getTime());
		System.out.println(calendar.getTimeInMillis());
		Assert.assertTrue(TimeCheckerUtil.checkExpired(calendar.getTime(), 10));
		System.out.println("====================");

//		실행 딜레이때문에 오류가 나기도 한다
//		//10초전 true
//		calendar.setTime(now);
//		calendar.add(Calendar.SECOND, -10);
//		calendar.add(Calendar.MILLISECOND, +2);
//		System.out.println("10초전에서 +2밀리초 start : "+calendar.getTime());
//		System.out.println(calendar.getTimeInMillis());
//		Assert.assertTrue(TimeCheckerUtil.checkExpired(calendar.getTime(), 10));
//		System.out.println("====================");
//
//		//10초전 true
//		calendar.setTime(now);
//		calendar.add(Calendar.SECOND, -10);
//		calendar.add(Calendar.MILLISECOND, +1);
//		System.out.println("10초전 start : "+calendar.getTime());
//		System.out.println(calendar.getTimeInMillis());
//		Assert.assertTrue(TimeCheckerUtil.checkExpired(calendar.getTime(), 10));
//		System.out.println("====================");

		//11초전 false
		calendar.setTime(now);
		calendar.add(Calendar.SECOND, -11);
		System.out.println(calendar.getTime());
		Assert.assertFalse(TimeCheckerUtil.checkExpired(calendar.getTime(), 10));
		System.out.println("====================");

		//20초전 false
		calendar.setTime(now);
		calendar.add(Calendar.SECOND, -20);
		System.out.println(calendar.getTime());
		Assert.assertFalse(TimeCheckerUtil.checkExpired(calendar.getTime(), 10));
		System.out.println("====================");
	}

	@Test
	public void checkExpiredTest2(){
		Calendar calendar = Calendar.getInstance();
		Date now = new Date();
		//현재시간 true
		System.out.println("현재시간 start : "+calendar.getTime());
		System.out.println(calendar.getTimeInMillis());
		Assert.assertTrue(TimeCheckerUtil.checkExpired(calendar.getTimeInMillis(), 10));
		System.out.println("====================");

		//5초전 true
		calendar.setTime(now);
		calendar.add(Calendar.SECOND, -5);
		System.out.println("5초전 start : "+calendar.getTime());
		System.out.println(calendar.getTimeInMillis());
		Assert.assertTrue(TimeCheckerUtil.checkExpired(calendar.getTimeInMillis(), 10));
		System.out.println("====================");

		//9초전 true
		calendar.setTime(now);
		calendar.add(Calendar.SECOND, -9);
		System.out.println("9초전 start : "+calendar.getTime());
		System.out.println(calendar.getTimeInMillis());
		Assert.assertTrue(TimeCheckerUtil.checkExpired(calendar.getTimeInMillis(), 10));
		System.out.println("====================");

//		실행 딜레이때문에 오류가 나기도 한다
//		//10초전 true
//		calendar.setTime(now);
//		calendar.add(Calendar.SECOND, -10);
//		calendar.add(Calendar.MILLISECOND, +2);
//		System.out.println("10초전에서 +2밀리초 start : "+calendar.getTime());
//		System.out.println(calendar.getTimeInMillis());
//		Assert.assertTrue(TimeCheckerUtil.checkExpired(calendar.getTimeInMillis(), 10));
//		System.out.println("====================");
//
//		//10초전 true
//		calendar.setTime(now);
//		calendar.add(Calendar.SECOND, -10);
//		calendar.add(Calendar.MILLISECOND, +1);
//		System.out.println("10초전 start : "+calendar.getTime());
//		System.out.println(calendar.getTimeInMillis());
//		Assert.assertTrue(TimeCheckerUtil.checkExpired(calendar.getTimeInMillis(), 10));
//		System.out.println("====================");

		//11초전 false
		calendar.setTime(now);
		calendar.add(Calendar.SECOND, -11);
		System.out.println(calendar.getTime());
		Assert.assertFalse(TimeCheckerUtil.checkExpired(calendar.getTimeInMillis(), 10));
		System.out.println("====================");

		//20초전 false
		calendar.setTime(now);
		calendar.add(Calendar.SECOND, -20);
		System.out.println(calendar.getTime());
		Assert.assertFalse(TimeCheckerUtil.checkExpired(calendar.getTimeInMillis(), 10));
		System.out.println("====================");
	}
}
