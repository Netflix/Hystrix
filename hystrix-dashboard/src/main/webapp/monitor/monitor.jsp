<!doctype html>
<html lang="en">
<head>
	<meta charset="utf-8" />
	<title>Hystrix Monitor</title>
	<meta name="viewport" content="width=device-width, initial-scale=1.0" />

	<!-- Setup base for everything -->
	<link rel="stylesheet" type="text/css" href="../css/global.css" />
	
	<!-- Our custom CSS -->
	<link rel="stylesheet" type="text/css" href="./monitor.css" />

	<!-- d3 -->
	<script type="text/javascript" src="../js/d3.v2.min.js"></script>
	
	<!-- Javascript to monitor and display -->
	<script src="//ajax.googleapis.com/ajax/libs/jquery/1.7.2/jquery.min.js" type="text/javascript"></script>
	<script type="text/javascript" src="../js/jquery.tinysort.min.js"></script>
	<script type="text/javascript" src="../js/tmpl.js"></script>
	
	<!-- HystrixCommand -->
	<script type="text/javascript" src="../components/hystrixCommand/hystrixCommand.js"></script>
	<link rel="stylesheet" type="text/css" href="../components/hystrixCommand/hystrixCommand.css" />
	
	<!-- HystrixThreadPool -->
	<script type="text/javascript" src="../components/hystrixThreadPool/hystrixThreadPool.js"></script>
	<link rel="stylesheet" type="text/css" href="../components/hystrixThreadPool/hystrixThreadPool.css" />

</head>
<body>
	<div id="header">
		<h2><span id="title_name"></span></h2>
	</div>

	<div class="container">
		<div class="row">
			<div class="menubar">
				<div class="title">
				Circuit
				</div>
				<div class="menu_actions">
					Sort: 
					<a href="javascript://" onclick="hystrixMonitor.sortByErrorThenVolume();">Error then Volume</a> |
					<a href="javascript://" onclick="hystrixMonitor.sortAlphabetically();">Alphabetical</a> | 
					<a href="javascript://" onclick="hystrixMonitor.sortByVolume();">Volume</a> | 
					<a href="javascript://" onclick="hystrixMonitor.sortByError();">Error</a> | 
					<a href="javascript://" onclick="hystrixMonitor.sortByLatencyMean();">Mean</a> | 
					<a href="javascript://" onclick="hystrixMonitor.sortByLatencyMedian();">Median</a> | 
					<a href="javascript://" onclick="hystrixMonitor.sortByLatency90();">90</a> | 
					<a href="javascript://" onclick="hystrixMonitor.sortByLatency99();">99</a> | 
					<a href="javascript://" onclick="hystrixMonitor.sortByLatency995();">99.5</a> 
				</div>
				<div class="menu_legend">
					<span class="success">Success</span> | <span class="latent">Latent</span> | <span class="shortCircuited">Short-Circuited</span> | <span class="timeout">Timeout</span> | <span class="rejected">Rejected</span> | <span class="failure">Failure</span> | <span class="errorPercentage">Error %</span>
				</div>
			</div>
		</div>
		<div id="dependencies" class="row dependencies"><span class="loading">Loading ...</span></div>
		
		<div class="spacer"></div>
		<div class="spacer"></div>
		
		<div class="row">
			<div class="menubar">
				<div class="title">
				Thread Pools
				</div>
				<div class="menu_actions">
					Sort: <a href="javascript://" onclick="dependencyThreadPoolMonitor.sortAlphabetically();">Alphabetical</a> | 
					<a href="javascript://" onclick="dependencyThreadPoolMonitor.sortByVolume();">Volume</a> | 
				</div>
			</div>
		</div>
		<div id="dependencyThreadPools" class="row dependencyThreadPools"><span class="loading">Loading ...</span></div>
	</div>



<script>
		/**
		 * Queue up the monitor to start once the page has finished loading.
		 * 
		 * This is an inline script and expects to execute once on page load.
		 */ 
		 
		 // commands
		var hystrixMonitor = new HystrixCommandMonitor('dependencies', {includeDetailIcon:false});
		
		var stream = getUrlVars()["stream"];
		
		if(stream != undefined) {
			if(getUrlVars()["delay"] != undefined) {
				stream = stream + "&delay=" + getUrlVars()["delay"];
			}
			
			var commandStream = "../proxy.stream?origin=" + stream;
			var poolStream = "../proxy.stream?origin=" + stream;
			
			if(getUrlVars()["title"] != undefined) {
				$('#title_name').html("Hystrix Stream: " + decodeURIComponent(getUrlVars()["title"]))
			} else {
				$('#title_name').html("Hystrix Stream: " + decodeURIComponent(stream))
			}
		}
				
		$(window).load(function() { // within load with a setTimeout to prevent the infinite spinner
			setTimeout(function() {
				if(commandStream == undefined) {
						console.log("commandStream is undefined")
						$("#dependencies .loading").html("The 'stream' argument was not provided.");
						$("#dependencies .loading").addClass("failed");
				} else {
					// sort by error+volume by default
					hystrixMonitor.sortByErrorThenVolume();
					
					// start the EventSource which will open a streaming connection to the server
					var source = new EventSource(commandStream);
					
					// add the listener that will process incoming events
					source.addEventListener('message', hystrixMonitor.eventSourceMessageListener, false);

					//	source.addEventListener('open', function(e) {
					//		console.console.log(">>> opened connection, phase: " + e.eventPhase);
					//	    // Connection was opened.
					//	}, false);

					source.addEventListener('error', function(e) {
					  if (e.eventPhase == EventSource.CLOSED) {
					    // Connection was closed.
						  console.log("Connection was closed on error: " + e);
					  } else {
						  console.log("Error occurred while streaming: " + e);
					  }
					}, false);
				}
			},0);
		});
		
		// thread pool
		var dependencyThreadPoolMonitor = new HystrixThreadPoolMonitor('dependencyThreadPools');

		$(window).load(function() { // within load with a setTimeout to prevent the infinite spinner
			setTimeout(function() {
				if(poolStream == undefined) {
						console.log("poolStream is undefined")
						$("#dependencyThreadPools .loading").html("The 'stream' argument was not provided.");
						$("#dependencyThreadPools .loading").addClass("failed");
				} else {
					dependencyThreadPoolMonitor.sortByVolume();
					
					// start the EventSource which will open a streaming connection to the server
					var source = new EventSource(poolStream);
					
					// add the listener that will process incoming events
					source.addEventListener('message', dependencyThreadPoolMonitor.eventSourceMessageListener, false);
	
					//	source.addEventListener('open', function(e) {
					//		console.console.log(">>> opened connection, phase: " + e.eventPhase);
					//	    // Connection was opened.
					//	}, false);
	
					source.addEventListener('error', function(e) {
					  if (e.eventPhase == EventSource.CLOSED) {
					    // Connection was closed.
						  console.log("Connection was closed on error: " + e);
					  } else {
						  console.log("Error occurred while streaming: " + e);
					  }
					}, false);
				}
			},0);
		});
		
		//Read a page's GET URL variables and return them as an associative array.
		// from: http://jquery-howto.blogspot.com/2009/09/get-url-parameters-values-with-jquery.html
		function getUrlVars()
		{
		    var vars = [], hash;
		    var hashes = window.location.href.slice(window.location.href.indexOf('?') + 1).split('&');
		    for(var i = 0; i < hashes.length; i++)
		    {
		        hash = hashes[i].split('=');
		        vars.push(hash[0]);
		        vars[hash[0]] = hash[1];
		    }
		    return vars;
		}
		
	</script>


</body>
</html>