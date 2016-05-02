package com.netflix.hystrix.dashboard.eureka;

import java.util.ArrayList;
import java.util.List;

import com.netflix.hystrix.dashboard.eureka.pojos.Application;
import com.netflix.hystrix.dashboard.eureka.pojos.Applications;

import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.simplexml.SimpleXmlConverterFactory;

public class EurekaServiceInfo {
	
	public Applications retrieveApps() {
		try{
			Retrofit retrofit = new Retrofit.
					Builder().
					baseUrl("http://127.0.0.1:8080/eureka/").
					addConverterFactory(SimpleXmlConverterFactory.create()).
				build();

			EurekaService service = retrofit.create(EurekaService.class);
			Call<Applications> apps = service.listApps();
			return apps.execute().body();
			
		}catch(Exception e){
			throw new RuntimeException(e);
		}
	}
	
	public List<String> retrieveAllAplications(){
		
		List<String> applications = new ArrayList<String>();
		Applications apps = retrieveApps();
		if (apps==null) return applications;
		
		for(Application a: apps.getApplication()){
			applications.add(a.getName());
		}
		return applications;
	}
	
}
