//
// configuration
//
var wamp_host = '##WampHost##';
var wamp_realm = '##WampRealm##';
var wamp_meter_topic = '##topicMeterState##';
var wamp_soc_topic = '##topicBatterySoc##';

var consumptionMeter = '##consumptionMeterUUID##';
var productionMeters = '##productionMeterUUIDs##';

var batteryMeters = '##batteryMeterUUIDs##';
var batteries = '##batteryUUIDs##';

var flexibilityMessageBuffer = '##flexibilityMessageBuffer##';

var autoMove = true;

// holds past meter data : uuid -> data[] : {time: epochSecond, value: Wh}
var meterSeries = '##meterSeries##';

//
// setup graph
//
var container = document.getElementById('visualization');

var end = new Date();
end.setMinutes(end.getMinutes() + 1);
var start = new Date();
start.setMinutes(start.getMinutes() - 9);

var dataset = new vis.DataSet();
var groups = new vis.DataSet();

groups.add({
    id: 0,
    content: 'Verbrauch',
    className: 'graph-consumption',
    options : {
        drawPoints: false,
        style: 'line',
        interpolation: false,
    }
})

var n = 1;
var productionIdOffset = n;
productionMeters.forEach( function(uuid) {
	groups.add({
	    id: n,
	    content: 'Erzeugung' + n,
	    className: 'graph-production',
	    options : {
	        stack: true,
	        drawPoints: false,
	        style: 'line',
	        interpolation: false,
	    }
	});
	n++;
});

var batteryIdOffset = n;
batteryMeters.forEach( function(uuid) {
	groups.add({
	    id: n,
	    content: 'Batterie' + (n - batteryIdOffset + 1),
	    className: 'graph-battery',
	    options : {
	    	drawPoints: false,
	        style: 'line',
	        interpolation: false,
	    }
	});
	n++;
});

var sumId = n;
groups.add({
    id: n,
    content: 'Netz',
    className: 'graph-net',
    options : {
        drawPoints: false,
        style: 'line',
        interpolation: false,
    }
})

// fill dataset with historic values
var sum = {};
for(key in meterSeries) {
	if(key == consumptionMeter) {
		meterSeries[key].forEach( function(data) {
            dataset.add({
            	x: new Date(data.time * 1000),
                y: data.value,
                group: 0
            });
            // compute sum
            if(!(data.time in sum)) {
            	sum[data.time] = 0;
            }
            sum[data.time] += data.value;
		});
	}
	var index = $.inArray(key, productionMeters); 
	if(index >= 0) {
		meterSeries[key].forEach( function(data) {
            dataset.add({
            	x: new Date(data.time * 1000),
                y: data.value,
                group: index + productionIdOffset
            });
            // compute sum
            if(!(data.time in sum)) {
            	sum[data.time] = 0;
            }
            sum[data.time] += data.value;
		});
	}
	index = $.inArray(key, batteryMeters);
	if(index >= 0) {
		meterSeries[key].forEach( function(data) {
            dataset.add({
            	x: new Date(data.time * 1000),
                y: data.value,
                group: index + batteryIdOffset
            });
            // compute sum
            if(!(data.time in sum)) {
            	sum[data.time] = 0;
            }
            sum[data.time] += data.value;
		});
	}
}
for(key in sum) {
	dataset.add({
    	x: new Date(key * 1000),
        y: sum[key],
        group: sumId
    });	
}


var options = {
    //legend: {right: {position: 'top-left'}},
    start: start,
    end: end,
    width: '100%',
    height: '100%',
    locale: 'de',
    dataAxis: {
    	left: {
    		title: {text: 'Strom in kW'},
    		format: function(value) {
    			return '' + (value / 1000).toPrecision(3);
    		}
    	}
    }
};
var graph2d = new vis.Graph2d(container, dataset, groups, options);

function moveGraph() {
    // move the window
    var end = new Date();
    end.setMinutes(end.getMinutes() + 1);
    var start = new Date();
    start.setMinutes(start.getMinutes() - 9);

    // 'discrete' moving strategy
    graph2d.setWindow(start, end, {animation: false});
}


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

connection.onopen = function (session) {

    // subscribe to a topic
    function onMeterState(args) {
        // decide which type of meter it is
        if(args[0].uuid == consumptionMeter) {
            // consumption

            dataset.add({
                x: new Date(args[0].time * 1000),
                y: args[0].totalActivePower / 10,
                group: 0
            });

            if(args[0].time > latest_consumption.time) {
                latest_consumption.time = args[0].time;
                latest_consumption.value = args[0].totalActivePower;
                
                $('.output_consumption').html(Math.round(args[0].totalActivePower / 10 / 1000 * 100) / 100 + ' kW');
            }
        }

        var index = $.inArray(args[0].uuid, productionMeters); 
        if(index >= 0) {
            // production
            dataset.add({
                x: new Date(args[0].time * 1000),
                y: args[0].totalActivePower / 10,
                group: index + productionIdOffset
            });

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

            dataset.add({
                x: new Date(args[0].time * 1000),
                y: args[0].totalActivePower / 10,
                group: index + batteryIdOffset
            });

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
        if(latest_total_production.time != 0 && latest_consumption.time != 0 && latest_total_battery_power.time != 0) {
	        var net = latest_total_production.value;
	        net += latest_total_battery_power.value;
	        net += latest_consumption.value;
	        $('.output_net_description').html(net > 0 ? 'Netzbezug' : 'Einspeisung');
	        $('.output_net').html(net > 0 ? Math.round(net / 10 /1000 * 100) / 100 + ' kW' : -Math.round(net / 10 /1000 * 100) / 100 + ' kW');
	
	        dataset.add({
	            x: new Date(args[0].time * 1000),
	            y: net / 10,
	            group: sumId
	        });
        }

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

        if(autoMove) {
            moveGraph();
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

$('.selection_panel_consumption').click(function() {
    // toggle visibility
    if($(this).hasClass('active')) {
        $(this).removeClass('active');
        graph2d.setOptions({groups:{visibility:{0:false}}});
    } else {
        $(this).addClass('active');
        graph2d.setOptions({groups:{visibility:{0:true}}});
    }
});

$('.selection_panel_production').click(function() {
    // toggle visibility
    if($(this).hasClass('active')) {
        $(this).removeClass('active');
        var n = 0;
        productionMeters.forEach(function(uuid){
        	graph2d.setOptions({groups:{visibility:{[productionIdOffset + n]:false}}});
        	n++;
        });
    } else {
        $(this).addClass('active');
        var n = 0;
        productionMeters.forEach(function(uuid){
        	graph2d.setOptions({groups:{visibility:{[productionIdOffset + n]:true}}});
        	n++;
        });
    }
});

$('.selection_panel_battery').click(function() {
    // toggle visibility
    if($(this).hasClass('active')) {
        $(this).removeClass('active');
        var n = 0;
        batteryMeters.forEach(function(uuid){
        	graph2d.setOptions({groups:{visibility:{[batteryIdOffset + n]:false}}});
        	n++;
        });
    } else {
        $(this).addClass('active');
        var n = 0;
        batteryMeters.forEach(function(uuid){
        	graph2d.setOptions({groups:{visibility:{[batteryIdOffset + n]:true}}});
        	n++;
        });
    }
});

$('.selection_panel_net').click(function() {
    // toggle visibility
    if($(this).hasClass('active')) {
        $(this).removeClass('active');
        graph2d.setOptions({groups:{visibility:{[sumId]:false}}});
    } else {
        $(this).addClass('active');
        graph2d.setOptions({groups:{visibility:{[sumId]:true}}});
    }
});

$('#visualization_options_automove').click(function() {
    // toggle automated graph movement
    autoMove = !autoMove;
    $('#visualization').css('pointer-events', (autoMove == true ? 'none' : 'all'));
    $(this).html((autoMove == true ? 'Grafik freigeben' : 'Grafik zur&uuml;cksetzen'));
    moveGraph();
});

$('#menu_item_simple').click(function() {
	$('#message').show();
	$('#visualization').hide();
	$('#visualization_options').hide();
	$('.menu_item_active').removeClass('menu_item_active');
	$(this).addClass('menu_item_active');
	$('#selection').css('pointer-events', 'none');
});

$('#menu_item_chart').click(function() {
	$('#message').hide();
	$('#visualization').show();
	$('#visualization_options').show();
	$('.menu_item_active').removeClass('menu_item_active');
	$(this).addClass('menu_item_active');
	$('#selection').css('pointer-events', 'all');
});


//
// final UI setup
//
if(batteryMeters.length == 0) {
	$('.selection_panel_battery').hide();	
}

$('#overlay_loading').hide();