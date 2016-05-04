package com.netflix.hystrix.dashboard.stream;

import org.junit.Test;

/**
 * UrlUtilsTest unit tests
 * 
 * @author diegopacheco
 *
 */
public class UrlUtilsTest {

	@Test(expected=IllegalArgumentException.class)
	public void testReadXmlInputStreamWithNull() {
		UrlUtils.readXmlInputStream(null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testReadXmlInputStreamWithBlank() {
		UrlUtils.readXmlInputStream("");
	}
	
}
