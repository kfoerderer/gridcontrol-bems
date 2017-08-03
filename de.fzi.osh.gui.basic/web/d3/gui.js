/*
// Configuration variables passed to script
var wamp_host
	WAMP Borker address
var wamp_realm
	Realm
var wamp_meter_topic
	Topic for meter states
var wamp_soc_topic
	Topic for SOC events

var consumptionMeter
	UUID (string) of consumption meter
var productionMeters
	Array of UUIDs (string) of production meters
var batteryMeters
	Array of UUIDs (string) of battery meters
var batteries
	Array of UUIDs (string) of batteries

var flexibilityMessageBuffer
	Only display a message if deviating from schedule by a certain margin
 */
var liveFrame = 60; // seconds
/* Preprocess */
// combine meter arrays 
var meters = (consumptionMeter == '' ? [] : [consumptionMeter]).concat(productionMeters.concat(batteryMeters));

/* Set up graph */

//time stamp parser
var parseTime = d3.isoParse;

function drawHistoricalChart() {

	var svgHistorical = d3.select("#visualization_historical");
	// empty chart (when redrawing)
	svgHistorical.selectAll('*').remove();

	var	margin = {top: 20, right: 80, bottom: 30, left: 50},
	width = $("#visualization_historical").width() - margin.left - margin.right,
	height = $("#visualization_historical").height() - margin.top - margin.bottom,
	gHistorical= svgHistorical.append("g").attr("transform", "translate(" + margin.left + "," + margin.top + ")");

	// axis
	var x = d3.scaleTime().range([0, width]),
		y = d3.scaleLinear().range([height, 0]);

	// line drawing
	var line = d3.line()
	.curve(d3.curveBasis)
	.x(function(d) { return x(d.date); })
	.y(function(d) { return y(d.totalActivePower); });

	function row(d) {
		return {
			date: parseTime(d.date),
			totalActivePower: +d.totalActivePower // convert to number
		};
	}

	// initialize array for holding data
	var timeSeries = meters.map(function(uuid){return{id: uuid, values:[], color:'#FF00FF', cssClass: ''};});
	var totalSeries = {id: 'total', values: [], color:'rgb(55,74,154)', cssClass: 'series-total'};
	timeSeries.push(totalSeries);

	// retrieve data from server
	var queue = d3.queue();
	meters.forEach(function(uuid) { 
		queue.defer(function(callback){
			d3.tsv("data/" + uuid + ".tsv", row, function(error, data) {
				if (error) throw error;
				// determine color
				var color = '#FF00FF';
				var cssClass = 'series';
				if(uuid == consumptionMeter) {
					color = "rgb(168,52,65)";
					cssClass = 'series-consumption';
				} else if($.inArray(uuid, productionMeters) >= 0) {
					color = "rgb(71,127,24)";
					cssClass = 'series-production';
				} else if($.inArray(uuid, batteryMeters) >= 0) {
					color = "rgb(148,193,28)";
					cssClass = 'series-battery';
				}
				// find entry (android compatible)
				var obj = undefined;
				timeSeries.some(function(series){if(series.id == uuid){obj = series; return true;} else return false;});
				obj.values = data;
				obj.color = color;
				obj.cssClass = cssClass; // not used right now
				// finished
				callback(null);
			});
		});
	});
	// when everything has been loaded, draw graph
	queue.await(function(error) {
		if (error) throw error;

		// compute total
		meters.forEach(function(uuid) {
			// "find" (android compatible)
			var obj = {values:[]};
			timeSeries.some(function(series){if(series.id == uuid){obj = series; return true;} else return false;})
			obj.values.forEach(function(entry) {
				var result = undefined;
				totalSeries.values.some(function(total){if(total.date.valueOf() == entry.date.valueOf()) { result = total; return true} else return false; });
				if(result === undefined) {
					// not found => new entry
					totalSeries.values.push({date: entry.date, totalActivePower: entry.totalActivePower});
				} else {
					// add to entry
					result.totalActivePower += entry.totalActivePower;
				}
			});
		});

		x.domain([
			d3.min(timeSeries, function(c) { return d3.min(c.values, function(d) { return d.date; }); }),
			d3.max(timeSeries, function(c) { return d3.max(c.values, function(d) { return d.date; }); })
			]);
		y.domain([
			d3.min(timeSeries, function(c) { return d3.min(c.values, function(d) { return d.totalActivePower; }); }),
			d3.max(timeSeries, function(c) { return d3.max(c.values, function(d) { return d.totalActivePower; }); })
			]);

		gHistorical.append("g")
		.attr("class", "axis axis--x")
		.attr("transform", "translate(0," + height + ")")
		.call(d3.axisBottom(x));

		gHistorical.append("g")
		.attr("class", "axis axis--y")
		.call(d3.axisLeft(y))
		.append("text")
		.attr("transform", "rotate(-90)")
		.attr("y", 6)
		.attr("dy", "0.71em")
		.attr("fill", "#000")
		.text("Strom, W");

		var meter = gHistorical.selectAll(".series")
		.data(timeSeries)
		.enter().append("g")
		.attr("class", "series");

		meter.append("path")
		.attr("class", "line")
		.attr("d", function(d) { return line(d.values); })
		.style("stroke", function(d) { return d.color; });
	});

};

/* Set up live graph */
var svgLive = d3.select("#visualization_live"),
margin = {top: 20, right: 80, bottom: 30, left: 50},
width = $("#visualization_live").width() - margin.left - margin.right,
height = $("#visualization_live").height() - margin.top - margin.bottom,
gLive= svgLive.append("g").attr("transform", "translate(" + margin.left + "," + margin.top + ")");

//initialize array for holding data
var timeSeries = meters.map(function(uuid){
	// determine color
	var color = '#FF00FF';
	var cssClass = 'series';
	if(uuid == consumptionMeter) {
		color = "rgb(168,52,65)";
		cssClass = 'series-consumption';
	} else if($.inArray(uuid, productionMeters) >= 0) {
		color = "rgb(71,127,24)";
		cssClass = 'series-production';
	} else if($.inArray(uuid, batteryMeters) >= 0) {
		color = "rgb(148,193,28)";
		cssClass = 'series-battery';
	}
	return{id: uuid, values:[], color:color, cssClass: cssClass};
});
var totalSeries = {id: 'total', values: [], color:'rgb(55,74,154)', cssClass: 'series-total'};
timeSeries.push(totalSeries);

//scales
var xScale = d3.scaleTime().range([0, width]),
yScale = d3.scaleLinear().range([height, 0]);

//line drawing
var line = d3.line()
.curve(d3.curveLinear)
.x(function(d) { return xScale(d.date); })
.y(function(d) { return yScale(d.totalActivePower); });

xScale.domain([new Date(Date.now()-liveFrame * 1000), new Date()]);
yScale.domain([
	d3.min(timeSeries, function(c) { return d3.min(c.values, function(d) { return d.totalActivePower; }); }),
	d3.max(timeSeries, function(c) { return d3.max(c.values, function(d) { return d.totalActivePower; }); })
	]);

// axis
var xAxis = d3.axisBottom(xScale); 
gLive.append("g")
.attr("class", "axis axis--x")
.attr("transform", "translate(0," + height + ")")
.call(xAxis);

var yAxis = d3.axisLeft(yScale);
gLive.append("g")
.attr("class", "axis axis--y")
.call(yAxis)
.append("text")
.attr("transform", "rotate(-90)")
.attr("y", 6)
.attr("dy", "0.71em")
.attr("fill", "#000")
.text("Strom, W");

var translation = -((xScale(Date.now()) - xScale(Date.now() - liveFrame * 1000))/liveFrame);
gLive.append("defs").append("clipPath")
.attr("id", "clip")
.append("rect")
.attr("x", 50)
.attr("width", width - 2*50)
.attr("height", height);

var series = gLive.selectAll('.series')
.data(timeSeries)
.enter().append("g")
.attr("class", "series")
.attr("clip-path", "url(#clip)");

var paths = series.append("path")
.style("stroke", function(d) { return d.color; })
.attr("class", "line")
.attr("d", function(d) { return line(d.values); });


function redraw() {
	// compute total
	var total = 0;
	var missingData = false;
	var latestDate = undefined;
	meters.forEach(function(uuid) { 
		var series = undefined;
		timeSeries.some(function(entry){if(entry.id == uuid) {series = entry; return true;} else return false;});
		if(series !== undefined) {
			if(series.values.length == 0) {
				missingData = true;
			} else {
				if(latestDate === undefined || series.values[series.values.length-1].date > latestDate) {
					latestDate = series.values[series.values.length-1].date;
				}
				total += series.values[series.values.length-1].totalActivePower;
			}
		}
	});
	if(missingData == false) {
		totalSeries.values.push({date: latestDate, totalActivePower: total});
		if(totalSeries.values.length > liveFrame) {
			totalSeries.values.shift();
		}	
	}
	
	// readraw path
	paths.attr("d", function(d) { return line(d.values); })
	
	xScale.domain([new Date(Date.now()-liveFrame * 1000), new Date()]);
	yScale.domain([
		d3.min(timeSeries, function(c) { return d3.min(c.values, function(d) { return d.totalActivePower; }); }),
		d3.max(timeSeries, function(c) { return d3.max(c.values, function(d) { return d.totalActivePower; }); })
		]);
	
	d3.select('#visualization_live .axis--x').transition().duration(1000).ease(d3.easeLinear).call(xAxis);
	d3.select('#visualization_live .axis--y').transition().duration(1000).ease(d3.easeLinear).call(yAxis);
	
	var n = series.size();
	paths.attr("transform", null)
	.transition()
	.duration(1000)
	.ease(d3.easeLinear)
	.attr("transform", "translate(" + translation + ")")
	.on("end", function() {n--; if(n<=0){redraw();}});
};

redraw();

//
// setup autobahn
//
var connection = new autobahn.Connection({
         url: wamp_host,
         realm: wamp_realm
      });


var latest_consumption = {time: 0, value: 0};
var latest_production = {};
var latest_total_production = {time: 0, value: 0};
var latest_battery_power = {};
var latest_total_battery_power = {time: 0, value: 0};
var latest_battery_soc = {};
var latest_average_battery_soc = {time: 0, value: 0};
var battery_capacity = {}
var total_battery_capacity = 0;
var initial = true;

connection.onopen = function (session) {

    // subscribe to a topic
    function onMeterState(args) {

    	// panels
    	
        // decide which type of meter it is
        if(args[0].uuid == consumptionMeter) {
            // consumption
            if(args[0].time > latest_consumption.time) {
                latest_consumption.time = args[0].time;
                latest_consumption.value = args[0].totalActivePower;                
                $('.output_consumption').html(Math.round(args[0].totalActivePower / 10 / 1000 * 100) / 100 + ' kW');
            }
        }

        var index = $.inArray(args[0].uuid, productionMeters); 
        if(index >= 0) {
            // production
            if(!(args[0].uuid in latest_production)) {
                latest_production[args[0].uuid] = {time: 0, value: 0};
            }
            if(latest_production[args[0].uuid].time < args[0].time) {
                latest_production[args[0].uuid].time = args[0].time;
                latest_production[args[0].uuid].value = args[0].totalActivePower;

                if(productionMeters.length > 1) {
                    var power = Math.round(args[0].totalActivePower / 10 / 1000 * 100) / 100;
                    $('.output_production_' + $.inArray(args[0].uuid, productionMeters)).html(-power + ' kW');
                }
                var sum = 0;                
                for(key in latest_production) {
                    sum += latest_production[key].value;
                }
                $('.output_production_sum').html(-Math.round(sum / 10 /1000 * 100) / 100 + ' kW');                
                latest_total_production.time = args[0].time;
                latest_total_production.value = sum;
            }
        }

        index = $.inArray(args[0].uuid, batteryMeters);
        if(index >= 0) {
            // battery
            if(!(args[0].uuid in latest_battery_power)) {
                latest_battery_power[args[0].uuid] = {time: 0, value: 0};
            }
            if(args[0].time > latest_battery_power[args[0].uuid].time) {
                latest_battery_power[args[0].uuid].time = args[0].time;
                latest_battery_power[args[0].uuid].value = args[0].totalActivePower;
                
                var sum = 0;                
                for(key in latest_battery_power) {
                    sum += latest_battery_power[key].value;
                }
                var power = Math.round(sum / 10 / 1000 * 100) / 100;
                var batteryStateMessage = 'inaktiv';
                if(power < 0) {
                	batteryStateMessage = 'entl&auml;dt ' + -power + ' kW';
                } else if(power > 0) {
                	batteryStateMessage = 'l&auml;dt ' + power + ' kW';
                }
                $('.output_battery_power').html(batteryStateMessage);
                
                latest_total_battery_power.time = args[0].time;
                latest_total_battery_power.value = sum;                
            }
        } 
        
        // update net
        if((productionMeters.length > 0 && latest_total_production.time != 0 || productionMeters.length == 0) && 
        	latest_consumption.time != 0 && 
        	(batteryMeters.length > 0 && latest_total_battery_power.time != 0 || batteryMeters.length == 0)) {
	        var net = latest_total_production.value;
	        net += latest_total_battery_power.value;
	        net += latest_consumption.value;
	        $('.output_net_description').html(net > 0 ? 'Netzbezug' : 'Einspeisung');
	        $('.output_net').html(net > 0 ? Math.round(net / 10 /1000 * 100) / 100 + ' kW' : -Math.round(net / 10 /1000 * 100) / 100 + ' kW');
        }
    	
    	// chart
    	
    	// find entry
    	var obj = undefined;
    	timeSeries.some(function(series){if(series.id == args[0].uuid) {obj = series; return true;} else return false;});
    	if(obj !== undefined) {
    		obj.values.push({date: new Date(args[0].time*1000), totalActivePower: args[0].totalActivePower / 10});
        	if(obj.values.length > liveFrame) {
        		obj.values.shift();    	
        	}
    	}    	

    	if(batteryMeters.length > 0) {	    	
		    // flexibility message
	    	if(		latest_total_battery_power.value > 0 && 
	    			latest_total_production.value < 0 && 
	    			latest_total_production.value + latest_consumption.value + latest_battery_power.value < 0) {
	    		// more production than consumption and battery is loading
	    		$('#flexibility_status').html('Strom aus erneuerbaren Energien wird gespeichert.');
	    		$('#flexibility_status_image').attr('src', 'images/renewables-white.png');
	    		$('#flexibility_status_emote').attr('src', 'images/grin.png');
	    	} else if(	latest_total_battery_power.value < 0 &&
	    				latest_total_production.value + latest_consumption.value > 0
	    			) {
	    		// battery provides energy for household
	    		$('#flexibility_status').html('Ihre Batterie stellt Ihnen aktuell Strom zur verf&uuml;gung.');
	    		$('#flexibility_status_image').attr('src', 'images/money-white.png');
	    		$('#flexibility_status_emote').attr('src', 'images/happy.png');
	    	} else if(Math.abs(latest_total_battery_power.value / 10) > flexibilityMessageBuffer){
	    		$('#flexibility_status').html('Ihr Haushalt tr&auml;gt aktuell aktiv zur Netzstabilit&auml;t bei.');
	    		$('#flexibility_status_image').attr('src', 'images/infrastructure-white.png');
	    		$('#flexibility_status_emote').attr('src', 'images/smile.png');
	        } else {
	        	$('#flexibility_status').html('');
	    		$('#flexibility_status_image').attr('src', '');
	    		$('#flexibility_status_emote').attr('src', '');
	        }
    	}
    }
    session.subscribe(wamp_meter_topic, onMeterState, { match: 'wildcard' });

    // handles battery soc publication
    function onSoc(args) {
        if(!(args[0].uuid in latest_battery_soc)){
           latest_battery_soc[args[0].uuid] = {time: 0, value: 0}; 
        }

        if(latest_battery_soc[args[0].uuid].time < args[0].time) {
            latest_battery_soc[args[0].uuid].time = args[0].time;
            latest_battery_soc[args[0].uuid].value = args[0].effectiveSoc;

            // compute average
            var sum = 0;
            var n = 0;
            for(key in latest_battery_soc) {
                sum += latest_battery_soc[key].value;
                n++;
            }
            latest_average_battery_soc.time = args[0].time;
            latest_average_battery_soc.value = sum / n;

            $('.output_battery_soc').html(Math.round(latest_average_battery_soc.value * total_battery_capacity /100 /1000 * 100) / 100 + ' kW' +
                                        ', ' + latest_average_battery_soc.value + ' %');
        }
    }
    session.subscribe(wamp_soc_topic, onSoc, { match: 'wildcard' });

    // initialize battery data    
    // determine capacity
    total_battery_capacity = 0;
    batteries.forEach( function(uuid) {
        // osh.$UUID.battery.state.get
        session.call('osh.' + uuid + '.battery.state.get', [{}]).then( function(result) {
            battery_capacity[uuid] = result.effectiveCapacity;
            total_battery_capacity += result.effectiveCapacity;

            if(!(uuid in latest_battery_soc)){
                latest_battery_soc[uuid] = {time: 0, value: 0}; 
            }
            latest_battery_soc[uuid].time = 0;
            latest_battery_soc[uuid].value = result.effectiveStateOfCharge;

            // compute average
            var sum = 0;
            var n = 0;
            for(key in latest_battery_soc) {
                sum += latest_battery_soc[key].value;
                n++;
            }
            latest_average_battery_soc.time = 0;
            latest_average_battery_soc.value = sum / n;

            $('.output_battery_soc').html(Math.round(latest_average_battery_soc.value * total_battery_capacity /100 /1000 * 100) / 100 + ' kWh' +
                                        ', ' + latest_average_battery_soc.value + ' %');
        });
    });        
};

connection.open();

//
// UI setup
//
 
// prepare production display
if(productionMeters.length > 1) {
	var content = '';
	for(i = 0; i < productionMeters.length; i++) {
		content += '<div class="text-small output_production_' + i + '"></div>';
	}
	content += '<div class="selection_panel_line"></div><div class="text-extralarge output_production_sum"></div>';
	$('.selection_panel_production_content').html(content);
} else {
	$('.selection_panel_production_content').html('<div class="selection_panel_spacer"></div><div class="text-extralarge output_production_sum"></div>');
}

$('#menu_item_simple').click(function() {
	$('#message').show();
	$('#visualization').hide();
	$('#visualization_options').hide();
	$('.menu_item_active').removeClass('menu_item_active');
	$(this).addClass('menu_item_active');
	$('#visualization_live').hide();
	$('#visualization_historical').hide();
});

$('#menu_item_chart').click(function() {
	$('#message').hide();
	$('#visualization').show();
	$('#visualization_options').show();
	$('.menu_item_active').removeClass('menu_item_active');
	$(this).addClass('menu_item_active');
	if($('#visualization_button_live').prop('disabled') == true){
		$('#visualization_live').show();
	} else {
		$('#visualization_historical').show();	
	}
});

$('#visualization_button_past').click(function() {
	$('#visualization_button_past').prop('disabled', 'true');
	$('#visualization_button_live').prop('disabled', '');
	$('#visualization_live').hide();
	drawHistoricalChart();
	$('#visualization_historical').show();
});

$('#visualization_button_live').click(function() {
	$('#visualization_button_past').prop('disabled', '');
	$('#visualization_button_live').prop('disabled', 'true');
	$('#visualization_historical').hide();
	$('#visualization_live').show();
});


//
// final UI setup
//
var panels = 3;
if(batteryMeters.length == 0) {
	$('.selection_panel_battery').hide();	

	$('#flexibility_status_image').attr('src', 'images/infrastructure-white.png');
	$('#flexibility_status_emote').attr('src', 'images/smile.png');
	$('#flexibility_status').html('Ihr Haushalt hilft durch Prognosen die Nutzung des Quartierspeichers zu verbessern');
	panels--;
}
if(productionMeters.length == 0) {
	$('.selection_panel_production').hide();
	panels--;
}
if(consumptionMeter == '') {
	$('.selection_panel_consumption').hide();
	panels--;
}
if(panels <= 1) {
	$('.selection_panel_net').hide();
}

$('#overlay_loading').hide();