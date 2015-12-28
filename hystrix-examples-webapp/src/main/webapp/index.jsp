<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>HystrixCommandDemo Execution and HystrixRequestLog [hystrix-examples-webapp]</title>
</head>
<body>
<div style="width:800px;margin:0 auto;">
	
	<center><h2>HystrixCommandDemo Execution and HystrixRequestLog<br>[hystrix-examples-webapp]</h2></center>
	<center><img width="264" height="233" src="https://raw.github.com/wiki/Netflix/Hystrix/images/hystrix-logo.png"></center>
	<p>
		The following is the output of <i>HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString()</i> after simulating the execution of several commands.
	</p>
	<p>
		The simulation code is in com.netflix.hystrix.examples.demo.HystrixCommandDemo.
	</p>
	<%@ page import="com.netflix.hystrix.examples.demo.HystrixCommandDemo, com.netflix.hystrix.HystrixRequestLog" %>
	<%
	new HystrixCommandDemo().executeSimulatedUserRequestForOrderConfirmationAndCreditCardPayment();
	%>
	<hr>
	<b><%=	HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString() %></b>
	<hr>
	<p>
		This request log is also part of the HTTP response header with key name "X-HystrixLog".
	</p>
	<p>
		You can view the realtime stream at <a href="./hystrix.stream">./hystrix.stream</a>.
	</p>
	<p>
		To see the realtime stream change over time execute the following (with correct hostname, port etc) to keep accessing the webapp while watching the stream and you will see the metrics change:
	</p>
	<pre>
	while true ; do curl "http://localhost:8989/hystrix-examples-webapp"; done
	</pre>
	<p>
	The configuration of Hystrix for this functionality is done in web.xml as follows:
	</p>
<pre>
	&lt;filter&gt;
		&lt;display-name&gt;HystrixRequestContextServletFilter&lt;/display-name&gt;
		&lt;filter-name&gt;HystrixRequestContextServletFilter&lt;/filter-name&gt;
		&lt;filter-class&gt;com.netflix.hystrix.contrib.requestservlet.HystrixRequestContextServletFilter&lt;/filter-class&gt;
	&lt;/filter&gt;
	&lt;filter-mapping&gt;
		&lt;filter-name&gt;HystrixRequestContextServletFilter&lt;/filter-name&gt;
		&lt;url-pattern&gt;/*&lt;/url-pattern&gt;
	&lt;/filter-mapping&gt;
	
	&lt;filter&gt;
		&lt;display-name&gt;HystrixRequestLogViaResponseHeaderServletFilter&lt;/display-name&gt;
		&lt;filter-name&gt;HystrixRequestLogViaResponseHeaderServletFilter&lt;/filter-name&gt;
		&lt;filter-class&gt;com.netflix.hystrix.contrib.requestservlet.HystrixRequestLogViaResponseHeaderServletFilter&lt;/filter-class&gt;
	&lt;/filter&gt;
	&lt;filter-mapping&gt;
		&lt;filter-name&gt;HystrixRequestLogViaResponseHeaderServletFilter&lt;/filter-name&gt;
		&lt;url-pattern&gt;/*&lt;/url-pattern&gt;
	&lt;/filter-mapping&gt;

	&lt;servlet&gt;
		&lt;description&gt;&lt;/description&gt;
		&lt;display-name&gt;HystrixMetricsStreamServlet&lt;/display-name&gt;
		&lt;servlet-name&gt;HystrixMetricsStreamServlet&lt;/servlet-name&gt;
		&lt;servlet-class&gt;com.netflix.hystrix.contrib.metrics.eventstream.HystrixMetricsStreamServlet&lt;/servlet-class&gt;
	&lt;/servlet&gt;

	&lt;servlet-mapping&gt;
		&lt;servlet-name&gt;HystrixMetricsStreamServlet&lt;/servlet-name&gt;
		&lt;url-pattern&gt;/hystrix.stream&lt;/url-pattern&gt;
	&lt;/servlet-mapping&gt;
</pre>

</div>
</body>
</html>