package com.netflix.hystrix.dashboard.eureka;

import com.netflix.hystrix.dashboard.eureka.pojos.Applications;

import retrofit2.Call;
import retrofit2.http.GET;

public interface EurekaService {
	@GET("v2/apps/")
	Call<Applications> listApps();
}
