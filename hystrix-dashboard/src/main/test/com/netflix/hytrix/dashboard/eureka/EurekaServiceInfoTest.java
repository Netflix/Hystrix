package com.netflix.hytrix.dashboard.eureka;

import org.junit.Assert;
import org.junit.Test;

import com.netflix.hystrix.dashboard.eureka.EurekaServiceInfo;

public class EurekaServiceInfoTest {

	@Test
	public void testRetrieveAppNames() {
		EurekaServiceInfo esi = new EurekaServiceInfo();
		Assert.assertNotNull(esi);
	}
	
	@Test
	public void TestRetrieveAllAplications() {
		EurekaServiceInfo esi = new EurekaServiceInfo();
		Object r = esi.retrieveAllAplications();
		Assert.assertNotNull(r);
		System.out.println(r);
	}
	
}
