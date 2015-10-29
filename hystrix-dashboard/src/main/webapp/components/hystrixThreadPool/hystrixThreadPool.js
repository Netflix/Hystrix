
(function(window) {

	// cache the templates we use on this page as global variables (asynchronously)
	jQuery.get(getRelativePath("../components/hystrixThreadPool/templates/hystrixThreadPool.html"), function(data) {
		htmlTemplate = data;
	});
	jQuery.get(getRelativePath("../components/hystrixThreadPool/templates/hystrixThreadPoolContainer.html"), function(data) {
		htmlTemplateContainer = data;
	});

	function getRelativePath(path) {
		var p = location.pathname.slice(0, location.pathname.lastIndexOf("/")+1);
		return p + path;
	}

	/**
	 * Object containing functions for displaying and updating the UI with streaming data.
	 * 
	 * Publish this externally as "HystrixThreadPoolMonitor"
	 */
	window.HystrixThreadPoolMonitor = function(index, containerId) {
		
		var self = this; // keep scope under control
		
		this.index = index;
		this.containerId = containerId;
		
		/**
		 * Initialization on construction
		 */
		// intialize various variables we use for visualization
		var maxXaxisForCircle="40%";
		var maxYaxisForCircle="40%";
		var maxRadiusForCircle="125";
		var maxDomain = 2000;
		
		self.circleRadius = d3.scale.pow().exponent(0.5).domain([0, maxDomain]).range(["5", maxRadiusForCircle]); // requests per second per host
		self.circleYaxis = d3.scale.linear().domain([0, maxDomain]).range(["30%", maxXaxisForCircle]);
		self.circleXaxis = d3.scale.linear().domain([0, maxDomain]).range(["30%", maxYaxisForCircle]);
		self.colorRange = d3.scale.linear().domain([10, 25, 40, 50]).range(["green", "#FFCC00", "#FF9900", "red"]);
		self.errorPercentageColorRange = d3.scale.linear().domain([0, 10, 35, 50]).range(["grey", "black", "#FF9900", "red"]);

		/**
		 * We want to keep sorting in the background since data values are always changing, so this will re-sort every X milliseconds
		 * to maintain whatever sort the user (or default) has chosen.
		 * 
		 * In other words, sorting only for adds/deletes is not sufficient as all but alphabetical sort are dynamically changing.
		 */
		setInterval(function() {
			// sort since we have added a new one
			self.sortSameAsLast();
		}, 1000)
		
		/**
		 * END of Initialization on construction
		 */
		
		/**
		 * Event listener to handle new messages from EventSource as streamed from the server.
		 */
		/* public */ self.eventSourceMessageListener = function(e) {
			var data = JSON.parse(e.data);
			if(data) {
				data.index = self.index;
				// check for reportingHosts (if not there, set it to 1 for singleHost vs cluster)
				if(!data.reportingHosts) {
					data.reportingHosts = 1;
				}
				
				if(data && data.type == 'HystrixThreadPool') {
					if (data.deleteData == 'true') {
						deleteThreadPool(data.escapedName);
					} else {
						displayThreadPool(data);
					}
				}
			}
		}
		
		/**
		 * Pre process the data before displying in the UI. 
		 * e.g   Get Averages from sums, do rate calculation etc. 
		 */
		function preProcessData(data) {
			validateData(data);
			// escape string used in jQuery & d3 selectors
			data.escapedName = data.name.replace(/([ !"#$%&'()*+,./:;<=>?@[\]^`{|}~])/g,'\\$1') + '_' + data.index;
			// do math
			converAllAvg(data);
			calcRatePerSecond(data);
		}
		
		function converAllAvg(data) {
			convertAvg(data, "propertyValue_queueSizeRejectionThreshold", false);
			
			// the following will break when it becomes a compound string if the property is dynamically changed
			convertAvg(data, "propertyValue_metricsRollingStatisticalWindowInMilliseconds", false);
		}
		
		function convertAvg(data, key, decimal) {
			if (decimal) {
				data[key] = roundNumber(data[key]/data["reportingHosts"]);
			} else {
				data[key] = Math.floor(data[key]/data["reportingHosts"]);
			}
		}
		
		function calcRatePerSecond(data) {
			var numberSeconds = data["propertyValue_metricsRollingStatisticalWindowInMilliseconds"] / 1000;

			var totalThreadsExecuted = data["rollingCountThreadsExecuted"];
			if (totalThreadsExecuted < 0) {
				totalThreadsExecuted = 0;
			}
			data["ratePerSecond"] =  roundNumber(totalThreadsExecuted / numberSeconds);
			data["ratePerSecondPerHost"] =  roundNumber(totalThreadsExecuted / numberSeconds / data["reportingHosts"]);
	    }

		function validateData(data) {
			
			assertNotNull(data,"type");
			assertNotNull(data,"name");
			// assertNotNull(data,"currentTime");
			assertNotNull(data,"currentActiveCount");
			assertNotNull(data,"currentCompletedTaskCount");
			assertNotNull(data,"currentCorePoolSize");
			assertNotNull(data,"currentLargestPoolSize");
			assertNotNull(data,"currentMaximumPoolSize");
			assertNotNull(data,"currentPoolSize");
			assertNotNull(data,"currentQueueSize");
			assertNotNull(data,"currentTaskCount");
			assertNotNull(data,"rollingCountThreadsExecuted");
			assertNotNull(data,"rollingMaxActiveThreads");
			assertNotNull(data,"reportingHosts");

            assertNotNull(data,"propertyValue_queueSizeRejectionThreshold");
			assertNotNull(data,"propertyValue_metricsRollingStatisticalWindowInMilliseconds");
		}
		
		function assertNotNull(data, key) {
			if(data[key] == undefined) {
				if (key == "dependencyOwner") {
					data["dependencyOwner"] = data.name;
				} else {
					throw new Error("Key Missing: " + key + " for " + data.name)
				}
			}
		}

		/**
		 * Method to display the THREAD_POOL data
		 * 
		 * @param data
		 */
		/* private */ function displayThreadPool(data) {
			
			try {
				preProcessData(data);
			} catch (err) {
				log("Failed preProcessData: " + err.message);
				return;
			}
			
			// add the 'addCommas' function to the 'data' object so the HTML templates can use it
			data.addCommas = addCommas;
			// add the 'roundNumber' function to the 'data' object so the HTML templates can use it
			data.roundNumber = roundNumber;
			
			var addNew = false;
			// check if we need to create the container
			if(!$('#THREAD_POOL_' + data.escapedName).length) {
				// it doesn't exist so add it
				var html = tmpl(htmlTemplateContainer, data);
				// remove the loading thing first
				$('#' + containerId + ' span.loading').remove();
				// get the current last column and remove the 'last' class from it
				$('#' + containerId + ' div.last').removeClass('last');
				// now create the new data and add it
				$('#' + containerId + '').append(html);
				// add the 'last' class to the column we just added
				$('#' + containerId + ' div.monitor').last().addClass('last');
				
				// add the default sparkline graph
				d3.selectAll('#graph_THREAD_POOL_' + data.escapedName + ' svg').append("svg:path");
				
				// remember this is new so we can trigger a sort after setting data
				addNew = true;
			}
			
			// set the rate on the div element so it's available for sorting
			$('#THREAD_POOL_' + data.escapedName).attr('rate_value', data.ratePerSecondPerHost);
			
			// now update/insert the data
			$('#THREAD_POOL_' + data.escapedName + ' div.monitor_data').html(tmpl(htmlTemplate, data));

			// set variables for circle visualization
			var rate = data.ratePerSecondPerHost;
			// we will treat each item in queue as 1% of an error visualization
			// ie. 5 threads in queue per instance == 5% error percentage
			var errorPercentage = data.currentQueueSize / data.reportingHosts; 
			
			updateCircle('#THREAD_POOL_' + data.escapedName + ' circle', rate, errorPercentage);
			
			if(addNew) {
				// sort since we added a new circuit
				self.sortSameAsLast();
			}
		}
		
		/* round a number to X digits: num => the number to round, dec => the number of decimals */
		/* private */ function roundNumber(num) {
			var dec=1; // we are hardcoding to support only 1 decimal so that our padding logic at the end is simple
			var result = Math.round(num*Math.pow(10,dec))/Math.pow(10,dec);
			var resultAsString = result.toString();
			if(resultAsString.indexOf('.') == -1) {
				resultAsString = resultAsString + '.';
				for(var i=0; i<dec; i++) {
					resultAsString = resultAsString + '0';
				}
			}
			return resultAsString;
		};
		
		
		/* private */ function updateCircle(cssTarget, rate, errorPercentage) {
			var newXaxisForCircle = self.circleXaxis(rate);
			if(parseInt(newXaxisForCircle) > parseInt(maxXaxisForCircle)) {
				newXaxisForCircle = maxXaxisForCircle;
			}
			var newYaxisForCircle = self.circleYaxis(rate);
			if(parseInt(newYaxisForCircle) > parseInt(maxYaxisForCircle)) {
				newYaxisForCircle = maxYaxisForCircle;
			}
			var newRadiusForCircle = self.circleRadius(rate);
			if(parseInt(newRadiusForCircle) > parseInt(maxRadiusForCircle)) {
				newRadiusForCircle = maxRadiusForCircle;
			}
			
			d3.selectAll(cssTarget)
				.transition()
				.duration(400)
				.attr("cy", newYaxisForCircle)
				.attr("cx", newXaxisForCircle)
				.attr("r", newRadiusForCircle)
				.style("fill", self.colorRange(errorPercentage));
		}
		
		/* private */ function deleteThreadPool(poolName) {
			$('#THREAD_POOL_' + poolName).remove();
		}
		
	}

	// public methods for sorting
	HystrixThreadPoolMonitor.prototype.sortByVolume = function() {
		var direction = "desc";
		if(this.sortedBy == 'rate_desc') {
			direction = 'asc';
		}
		this.sortByVolumeInDirection(direction);
	}

	HystrixThreadPoolMonitor.prototype.sortByVolumeInDirection = function(direction) {
		this.sortedBy = 'rate_' + direction;
		$('#' + this.containerId + ' div.monitor').tsort({order: direction, attr: 'rate_value'});
	}

	HystrixThreadPoolMonitor.prototype.sortAlphabetically = function() {
		var direction = "asc";
		if(this.sortedBy == 'alph_asc') {
			direction = 'desc';
		}
		this.sortAlphabeticalInDirection(direction);
	}

	HystrixThreadPoolMonitor.prototype.sortAlphabeticalInDirection = function(direction) {
		this.sortedBy = 'alph_' + direction;
		$('#' + this.containerId + ' div.monitor').tsort("p.name", {order: direction});
	}

	HystrixThreadPoolMonitor.prototype.sortByMetricInDirection = function(direction, metric) {
		$('#' + this.containerId + ' div.monitor').tsort(metric, {order: direction});
	}

	// this method is for when new divs are added to cause the elements to be sorted to whatever the user last chose
	HystrixThreadPoolMonitor.prototype.sortSameAsLast = function() {
		if(this.sortedBy == 'alph_asc') {
			this.sortAlphabeticalInDirection('asc');
		} else if(this.sortedBy == 'alph_desc') {
			this.sortAlphabeticalInDirection('desc');
		} else if(this.sortedBy == 'rate_asc') {
			this.sortByVolumeInDirection('asc');
		} else if(this.sortedBy == 'rate_desc') {
			this.sortByVolumeInDirection('desc');
		} else if(this.sortedBy == 'error_asc') {
			this.sortByErrorInDirection('asc');
		} else if(this.sortedBy == 'error_desc') {
			this.sortByErrorInDirection('desc');
		} else if(this.sortedBy == 'lat90_asc') {
			this.sortByMetricInDirection('asc', 'p90');
		} else if(this.sortedBy == 'lat90_desc') {
			this.sortByMetricInDirection('desc', 'p90');
		} else if(this.sortedBy == 'lat99_asc') {
			this.sortByMetricInDirection('asc', 'p99');
		} else if(this.sortedBy == 'lat99_desc') {
			this.sortByMetricInDirection('desc', 'p99');
		} else if(this.sortedBy == 'lat995_asc') {
			this.sortByMetricInDirection('asc', 'p995');
		} else if(this.sortedBy == 'lat995_desc') {
			this.sortByMetricInDirection('desc', 'p995');
		} else if(this.sortedBy == 'latMean_asc') {
			this.sortByMetricInDirection('asc', 'pMean');
		} else if(this.sortedBy == 'latMean_desc') {
			this.sortByMetricInDirection('desc', 'pMean');
		} else if(this.sortedBy == 'latMedian_asc') {
			this.sortByMetricInDirection('asc', 'pMedian');
		} else if(this.sortedBy == 'latMedian_desc') {
			this.sortByMetricInDirection('desc', 'pMedian');
		}  
	}

	// default sort type and direction
	this.sortedBy = 'alph_asc';


	// a temporary home for the logger until we become more sophisticated
	function log(message) {
		console.log(message);
	};

	function addCommas(nStr){
	  nStr += '';
	  if(nStr.length <=3) {
		  return nStr; //shortcut if we don't need commas
	  }
	  x = nStr.split('.');
	  x1 = x[0];
	  x2 = x.length > 1 ? '.' + x[1] : '';
	  var rgx = /(\d+)(\d{3})/;
	  while (rgx.test(x1)) {
	    x1 = x1.replace(rgx, '$1' + ',' + '$2');
	  }
	  return x1 + x2;
	}
})(window)


