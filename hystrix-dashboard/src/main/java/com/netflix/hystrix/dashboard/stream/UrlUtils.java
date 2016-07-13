package com.netflix.hystrix.dashboard.stream;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Copyright 2013 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * Utility class to work with InputStreams
 * 
 * @author diegopacheco
 *
 */
public class UrlUtils {
	
	public static InputStream readXmlInputStream(String uri){
		
		if (uri==null || "".equals(uri)) throw new IllegalArgumentException("Invalid uri. URI cannot be null or blank. ");	
		
		try{
			 URL url = new URL(uri);
			 HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			 connection.setRequestMethod("GET");
			 connection.setRequestProperty("Accept", "application/xml");

			 return connection.getInputStream();

		}catch(Exception e){
			throw new RuntimeException(e);
		}
	}
	
}
