
(function(window) {

	// cache the templates we use on this page as global variables (asynchronously)
	jQuery.get(getRelativePath("../components/hystrixCommand/templates/hystrixCircuit.html"), function(data) {
		hystrixTemplateCircuit = data;
	});
	jQuery.get(getRelativePath("../components/hystrixCommand/templates/hystrixCircuitContainer.html"), function(data) {
		hystrixTemplateCircuitContainer = data;
	});

	function getRelativePath(path) {
		var p = location.pathname.slice(0, location.pathname.lastIndexOf("/")+1);
		return p + path;
	}

	/**
	 * Object containing functions for displaying and updating the UI with streaming data.
	 * 
	 * Publish this externally as "HystrixCommandMonitor"
	 */
	window.HystrixCommandMonitor = function(containerId, args) {
		
		var self = this; // keep scope under control
		self.args = args;
		if(self.args == undefined) {
			self.args = {};
		}
		
		this.containerId = containerId;
		
		/**
		 * Initialization on construction
		 */
		// intialize various variables we use for visualization
		var maxXaxisForCircle="40%";
		var maxYaxisForCircle="40%";
		var maxRadiusForCircle="125";
		
		// CIRCUIT_BREAKER circle visualization settings
		self.circuitCircleRadius = d3.scale.pow().exponent(0.5).domain([0, 400]).range(["5", maxRadiusForCircle]); // requests per second per host
		self.circuitCircleYaxis = d3.scale.linear().domain([0, 400]).range(["30%", maxXaxisForCircle]);
		self.circuitCircleXaxis = d3.scale.linear().domain([0, 400]).range(["30%", maxYaxisForCircle]);
		self.circuitColorRange = d3.scale.linear().domain([10, 25, 40, 50]).range(["green", "#FFCC00", "#FF9900", "red"]);
		self.circuitErrorPercentageColorRange = d3.scale.linear().domain([0, 10, 35, 50]).range(["grey", "black", "#FF9900", "red"]);

		/**
		 * We want to keep sorting in the background since data values are always changing, so this will re-sort every X milliseconds
		 * to maintain whatever sort the user (or default) has chosen.
		 * 
		 * In other words, sorting only for adds/deletes is not sufficient as all but alphabetical sort are dynamically changing.
		 */
		setInterval(function() {
			// sort since we have added a new one
			self.sortSameAsLast();
		}, 10000);

		
		/**
		 * END of Initialization on construction
		 */
		
		/**
		 * Event listener to handle new messages from EventSource as streamed from the server.
		 */
		/* public */ self.eventSourceMessageListener = function(e) {
			var data = JSON.parse(e.data);
			if(data) {
				// check for reportingHosts (if not there, set it to 1 for singleHost vs cluster)
				if(!data.reportingHosts) {
					data.reportingHosts = 1;
				}
				
				if(data && data.type == 'HystrixCommand') {
					if (data.deleteData == 'true') {
						deleteCircuit(data.escapedName);
					} else {
						displayCircuit(data);
					}
				}
			}
		};

		/**
		 * Pre process the data before displying in the UI. 
		 * e.g   Get Averages from sums, do rate calculation etc. 
		 */
		function preProcessData(data) {
			validateData(data);
			// escape string used in jQuery & d3 selectors
			data.escapedName = data.name.replace(/([ !"#$%&'()*+,./:;<=>?@[\]^`{|}~])/g,'\\$1');
			// do math
			converAllAvg(data);
			calcRatePerSecond(data);
		}

		/**
		 * Since the stream of data can be aggregated from multiple hosts in a tiered manner
		 * the aggregation just sums everything together and provides us the denominator (reportingHosts)
		 * so we must divide by it to get an average per instance value. 
		 * 
		 * We want to do this on any numerical values where we want per instance rather than cluster-wide sum.
		 */
		function converAllAvg(data) {
			convertAvg(data, "errorPercentage", true);
			convertAvg(data, "latencyExecute_mean", false);
			convertAvg(data, "latencyTotal_mean", false);
			
			// the following will break when it becomes a compound string if the property is dynamically changed
			convertAvg(data, "propertyValue_metricsRollingStatisticalWindowInMilliseconds", false);
		}
		
		function convertAvg(data, key, decimal) {
			if (decimal) {
				data[key] = getInstanceAverage(data[key], data["reportingHosts"], decimal);
			} else {
				data[key] = getInstanceAverage(data[key], data["reportingHosts"], decimal);
			}
		}
		
		function getInstanceAverage(value, reportingHosts, decimal) {
			if (decimal) {
				return roundNumber(value/reportingHosts);
			} else {
				return Math.floor(value/reportingHosts);
			}
		}
		
		function calcRatePerSecond(data) {
			var numberSeconds = data["propertyValue_metricsRollingStatisticalWindowInMilliseconds"] / 1000;

			var totalRequests = data["requestCount"];
			if (totalRequests < 0) {
				totalRequests = 0;
			}
			data["ratePerSecond"] =  roundNumber(totalRequests / numberSeconds);
			data["ratePerSecondPerHost"] =  roundNumber(totalRequests / numberSeconds / data["reportingHosts"]) ;
	    }

		function validateData(data) {
			assertNotNull(data,"reportingHosts");
            assertNotNull(data,"type");
            assertNotNull(data,"name");
            assertNotNull(data,"group");
            // assertNotNull(data,"currentTime");
            assertNotNull(data,"isCircuitBreakerOpen");
            assertNotNull(data,"errorPercentage");
            assertNotNull(data,"errorCount");
            assertNotNull(data,"requestCount");
            assertNotNull(data,"rollingCountCollapsedRequests");
            assertNotNull(data,"rollingCountExceptionsThrown");
            assertNotNull(data,"rollingCountFailure");
            assertNotNull(data,"rollingCountFallbackFailure");
            assertNotNull(data,"rollingCountFallbackRejection");
            assertNotNull(data,"rollingCountFallbackSuccess");
            assertNotNull(data,"rollingCountResponsesFromCache");
            assertNotNull(data,"rollingCountSemaphoreRejected");
            assertNotNull(data,"rollingCountShortCircuited");
            assertNotNull(data,"rollingCountSuccess");
            assertNotNull(data,"rollingCountThreadPoolRejected");
            assertNotNull(data,"rollingCountTimeout");
            assertNotNull(data,"currentConcurrentExecutionCount");
            assertNotNull(data,"latencyExecute_mean");
            assertNotNull(data,"latencyExecute");
            assertNotNull(data,"latencyTotal_mean");
            assertNotNull(data,"latencyTotal");
            assertNotNull(data,"propertyValue_circuitBreakerRequestVolumeThreshold");
            assertNotNull(data,"propertyValue_circuitBreakerSleepWindowInMilliseconds");
            assertNotNull(data,"propertyValue_circuitBreakerErrorThresholdPercentage");
            assertNotNull(data,"propertyValue_circuitBreakerForceOpen");
            assertNotNull(data,"propertyValue_executionIsolationStrategy");
            assertNotNull(data,"propertyValue_executionIsolationThreadTimeoutInMilliseconds");
            assertNotNull(data,"propertyValue_executionIsolationThreadInterruptOnTimeout");
            // assertNotNull(data,"propertyValue_executionIsolationThreadPoolKeyOverride");
            assertNotNull(data,"propertyValue_executionIsolationSemaphoreMaxConcurrentRequests");
            assertNotNull(data,"propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests");
            assertNotNull(data,"propertyValue_requestCacheEnabled");
            assertNotNull(data,"propertyValue_requestLogEnabled");
            assertNotNull(data,"propertyValue_metricsRollingStatisticalWindowInMilliseconds");
		}
		
		function assertNotNull(data, key) {
			if(data[key] == undefined) {
				throw new Error("Key Missing: " + key + " for " + data.name);
			}
		}

		/**
		 * Method to display the CIRCUIT data
		 * 
		 * @param data
		 */
		/* private */ function displayCircuit(data) {
			
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
			// add the 'getInstanceAverage' function to the 'data' object so the HTML templates can use it
			data.getInstanceAverage = getInstanceAverage;
			
			var addNew = false;
			// check if we need to create the container
			if(!$('#CIRCUIT_' + data.escapedName).length) {
				// args for display
				if(self.args.includeDetailIcon != undefined && self.args.includeDetailIcon) {
					data.includeDetailIcon = true;
				}else {
					data.includeDetailIcon = false;
				}
				
				// it doesn't exist so add it
				var html = tmpl(hystrixTemplateCircuitContainer, data);
				// remove the loading thing first
				$('#' + containerId + ' span.loading').remove();
				// now create the new data and add it
				$('#' + containerId + '').append(html);
				
				// add the default sparkline graph
				d3.selectAll('#graph_CIRCUIT_' + data.escapedName + ' svg').append("svg:path");
				
				// remember this is new so we can trigger a sort after setting data
				addNew = true;
			}
			
			
			// now update/insert the data
			$('#CIRCUIT_' + data.escapedName + ' div.monitor_data').html(tmpl(hystrixTemplateCircuit, data));
			
			var ratePerSecond = data.ratePerSecond;
			var ratePerSecondPerHost = data.ratePerSecondPerHost;
			var ratePerSecondPerHostDisplay = ratePerSecondPerHost;
			var errorThenVolume = (data.errorPercentage * 100000000) +  ratePerSecond;
			
			// set the rates on the div element so it's available for sorting
			$('#CIRCUIT_' + data.escapedName).attr('rate_value', ratePerSecond);
			$('#CIRCUIT_' + data.escapedName).attr('error_then_volume', errorThenVolume);
			
			// update errorPercentage color on page
			$('#CIRCUIT_' + data.escapedName + ' a.errorPercentage').css('color', self.circuitErrorPercentageColorRange(data.errorPercentage));
			
			updateCircle('circuit', '#CIRCUIT_' + data.escapedName + ' circle', ratePerSecondPerHostDisplay, data.errorPercentage);
			
			if(data.graphValues) {
				// we have a set of values to initialize with
				updateSparkline('circuit', '#CIRCUIT_' + data.escapedName + ' path', data.graphValues);
			} else {
				updateSparkline('circuit', '#CIRCUIT_' + data.escapedName + ' path', ratePerSecond);
			}

			if(addNew) {
				// sort since we added a new circuit
				self.sortSameAsLast();
			}
		}
		
		/* round a number to X digits: num => the number to round, dec => the number of decimals */
		/* private */ function roundNumber(num) {
			var dec=1;
			var result = Math.round(num*Math.pow(10,dec))/Math.pow(10,dec);
			var resultAsString = result.toString();
			if(resultAsString.indexOf('.') == -1) {
				resultAsString = resultAsString + '.0';
			}
			return resultAsString;
		};
		
		
		
		
		/* private */ function updateCircle(variablePrefix, cssTarget, rate, errorPercentage) {
			var newXaxisForCircle = self[variablePrefix + 'CircleXaxis'](rate);
			if(parseInt(newXaxisForCircle) > parseInt(maxXaxisForCircle)) {
				newXaxisForCircle = maxXaxisForCircle;
			}
			var newYaxisForCircle = self[variablePrefix + 'CircleYaxis'](rate);
			if(parseInt(newYaxisForCircle) > parseInt(maxYaxisForCircle)) {
				newYaxisForCircle = maxYaxisForCircle;
			}
			var newRadiusForCircle = self[variablePrefix + 'CircleRadius'](rate);
			if(parseInt(newRadiusForCircle) > parseInt(maxRadiusForCircle)) {
				newRadiusForCircle = maxRadiusForCircle;
			}
			
			d3.selectAll(cssTarget)
				.transition()
				.duration(400)
				.attr("cy", newYaxisForCircle)
				.attr("cx", newXaxisForCircle)
				.attr("r", newRadiusForCircle)
				.style("fill", self[variablePrefix + 'ColorRange'](errorPercentage));
		}
		
		/* private */ function updateSparkline(variablePrefix, cssTarget, newDataPoint) {
				var currentTimeMilliseconds = new Date().getTime();
				var data = self[variablePrefix + cssTarget + '_data'];
				if(typeof data == 'undefined') {
					// else it's new
					if(typeof newDataPoint == 'object') {
						// we received an array of values, so initialize with it
						data = newDataPoint;
					} else {
						// v: VALUE, t: TIME_IN_MILLISECONDS
						data = [{"v":parseFloat(newDataPoint),"t":currentTimeMilliseconds}];
					}
					self[variablePrefix + cssTarget + '_data'] = data;
				} else {
					if(typeof newDataPoint == 'object') {
						/* if an array is passed in we'll replace the cached one */					
						data = newDataPoint;
					} else {
						// else we just add to the existing one
						data.push({"v":parseFloat(newDataPoint),"t":currentTimeMilliseconds});
					}
				}
				
				while(data.length > 200) { // 400 should be plenty for the 2 minutes we have the scale set to below even with a very low update latency
					// remove data so we don't keep increasing forever 
					data.shift();
				}	
				
				if(data.length == 1 && data[0].v == 0) {
					//console.log("we have a single 0 so skipping");
					// don't show if we have a single 0
					return;
				}
				
				if(data.length > 1 && data[0].v == 0 && data[1].v != 0) {
					//console.log("we have a leading 0 so removing it");
					// get rid of a leading 0 if the following number is not a 0
					data.shift();
				} 
				
				var xScale = d3.time.scale().domain([new Date(currentTimeMilliseconds-(60*1000*2)), new Date(currentTimeMilliseconds)]).range([0, 140]);
				
				var yMin = d3.min(data, function(d) { return d.v; });
				var yMax = d3.max(data, function(d) { return d.v; });
				var yScale = d3.scale.linear().domain([yMin, yMax]).nice().range([60, 0]); // y goes DOWN, so 60 is the "lowest"
				
				sparkline = d3.svg.line()
				// assign the X function to plot our line as we wish
				.x(function(d,i) { 
					// return the X coordinate where we want to plot this datapoint based on the time
					return xScale(new Date(d.t));
				})
				.y(function(d) {
					return yScale(d.v);
				})
				.interpolate("basis");
				
				d3.selectAll(cssTarget).attr("d", sparkline(data));
		}
		
		/* private */ function deleteCircuit(circuitName) {
			$('#CIRCUIT_' + circuitName).remove();
		}
		
	};

	// public methods for sorting
	HystrixCommandMonitor.prototype.sortByVolume = function() {
		var direction = "desc";
		if(this.sortedBy == 'rate_desc') {
			direction = 'asc';
		}
		this.sortByVolumeInDirection(direction);
	};

	HystrixCommandMonitor.prototype.sortByVolumeInDirection = function(direction) {
		this.sortedBy = 'rate_' + direction;
		$('#' + this.containerId + ' div.monitor').tsort({order: direction, attr: 'rate_value'});
	};

	HystrixCommandMonitor.prototype.sortAlphabetically = function() {
		var direction = "asc";
		if(this.sortedBy == 'alph_asc') {
			direction = 'desc';
		}
		this.sortAlphabeticalInDirection(direction);
	};

	HystrixCommandMonitor.prototype.sortAlphabeticalInDirection = function(direction) {
		this.sortedBy = 'alph_' + direction;
		$('#' + this.containerId + ' div.monitor').tsort("p.name", {order: direction});
	};

	
	HystrixCommandMonitor.prototype.sortByError = function() {
		var direction = "desc";
		if(this.sortedBy == 'error_desc') {
			direction = 'asc';
		}
		this.sortByErrorInDirection(direction);
	};

	HystrixCommandMonitor.prototype.sortByErrorInDirection = function(direction) {
		this.sortedBy = 'error_' + direction;
		$('#' + this.containerId + ' div.monitor').tsort(".errorPercentage .value", {order: direction});
	};
	
	HystrixCommandMonitor.prototype.sortByErrorThenVolume = function() {
		var direction = "desc";
		if(this.sortedBy == 'error_then_volume_desc') {
			direction = 'asc';
		}
		this.sortByErrorThenVolumeInDirection(direction);
	};
	
	HystrixCommandMonitor.prototype.sortByErrorThenVolumeInDirection = function(direction) {
		this.sortedBy = 'error_then_volume_' + direction;
		$('#' + this.containerId + ' div.monitor').tsort({order: direction, attr: 'error_then_volume'});
	};

	HystrixCommandMonitor.prototype.sortByLatency90 = function() {
		var direction = "desc";
		if(this.sortedBy == 'lat90_desc') {
			direction = 'asc';
		}
		this.sortedBy = 'lat90_' + direction;
		this.sortByMetricInDirection(direction, ".latency90 .value");
	};

	HystrixCommandMonitor.prototype.sortByLatency99 = function() {
		var direction = "desc";
		if(this.sortedBy == 'lat99_desc') {
			direction = 'asc';
		}
		this.sortedBy = 'lat99_' + direction;
		this.sortByMetricInDirection(direction, ".latency99 .value");
	};

	HystrixCommandMonitor.prototype.sortByLatency995 = function() {
		var direction = "desc";
		if(this.sortedBy == 'lat995_desc') {
			direction = 'asc';
		}
		this.sortedBy = 'lat995_' + direction;
		this.sortByMetricInDirection(direction, ".latency995 .value");
	};

	HystrixCommandMonitor.prototype.sortByLatencyMean = function() {
		var direction = "desc";
		if(this.sortedBy == 'latMean_desc') {
			direction = 'asc';
		}
		this.sortedBy = 'latMean_' + direction;
		this.sortByMetricInDirection(direction, ".latencyMean .value");
	};

	HystrixCommandMonitor.prototype.sortByLatencyMedian = function() {
		var direction = "desc";
		if(this.sortedBy == 'latMedian_desc') {
			direction = 'asc';
		}
		this.sortedBy = 'latMedian_' + direction;
		this.sortByMetricInDirection(direction, ".latencyMedian .value");
	};

	HystrixCommandMonitor.prototype.sortByMetricInDirection = function(direction, metric) {
		$('#' + this.containerId + ' div.monitor').tsort(metric, {order: direction});
	};

	// this method is for when new divs are added to cause the elements to be sorted to whatever the user last chose
	HystrixCommandMonitor.prototype.sortSameAsLast = function() {
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
		} else if(this.sortedBy == 'error_then_volume_asc') {
			this.sortByErrorThenVolumeInDirection('asc');
		} else if(this.sortedBy == 'error_then_volume_desc') {
			this.sortByErrorThenVolumeInDirection('desc');	
		} else if(this.sortedBy == 'lat90_asc') {
			this.sortByMetricInDirection('asc', '.latency90 .value');
		} else if(this.sortedBy == 'lat90_desc') {
			this.sortByMetricInDirection('desc', '.latency90 .value');
		} else if(this.sortedBy == 'lat99_asc') {
			this.sortByMetricInDirection('asc', '.latency99 .value');
		} else if(this.sortedBy == 'lat99_desc') {
			this.sortByMetricInDirection('desc', '.latency99 .value');
		} else if(this.sortedBy == 'lat995_asc') {
			this.sortByMetricInDirection('asc', '.latency995 .value');
		} else if(this.sortedBy == 'lat995_desc') {
			this.sortByMetricInDirection('desc', '.latency995 .value');
		} else if(this.sortedBy == 'latMean_asc') {
			this.sortByMetricInDirection('asc', '.latencyMean .value');
		} else if(this.sortedBy == 'latMean_desc') {
			this.sortByMetricInDirection('desc', '.latencyMean .value');
		} else if(this.sortedBy == 'latMedian_asc') {
			this.sortByMetricInDirection('asc', '.latencyMedian .value');
		} else if(this.sortedBy == 'latMedian_desc') {
			this.sortByMetricInDirection('desc', '.latencyMedian .value');
		}  
	};

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
})(window);


