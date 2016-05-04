package com.netflix.hystrix.dashboard.stream;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;

/**
 * Servlet that calls eureka REST api in order to get instances information. <BR>
 * You need provide a url parameter. i.e: eureka?url=http://127.0.0.1:8080/eureka/v2/apps
 * 
 * @author diegopacheco
 *
 */
public class EurekaInfoServlet extends HttpServlet {
	
	 private static final long serialVersionUID = 1L;
	 
	 protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		 
		 String uri = request.getParameter("url");
		 if (uri==null || "".equals(uri)) response.getOutputStream().write("Error. You need supply a valid eureka URL ".getBytes()); 
		 
		 try{
			 response.setContentType("application/xml");
			 response.setHeader("Content-Encoding", "gzip");
			 IOUtils.copy( UrlUtils.readXmlInputStream(uri) ,response.getOutputStream());
		 }catch(Exception e){
			 response.getOutputStream().write(("Error. You need supply a valid eureka URL. Ex: " + e + "").getBytes()); 
		 }
		 
	 }
}
