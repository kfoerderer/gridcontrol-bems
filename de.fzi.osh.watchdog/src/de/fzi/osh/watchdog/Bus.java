package de.fzi.osh.watchdog;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import de.fzi.osh.wamp.Action1;
import de.fzi.osh.wamp.Connection;
import de.fzi.osh.wamp.device.battery.BatteryState;
import de.fzi.osh.wamp.device.battery.BatteryTopics;
import de.fzi.osh.wamp.device.battery.GetBatteryStateRequest;
import de.fzi.osh.wamp.device.battery.GetBatteryStateResponse;
import de.fzi.osh.wamp.device.meter.MeterStatePublication;
import de.fzi.osh.wamp.device.meter.MeterTopics;
import de.fzi.osh.watchdog.wamp.EchoRequest;
import de.fzi.osh.watchdog.wamp.EchoResponse;
import de.fzi.osh.watchdog.wamp.WatchdogTopics;
import ws.wamp.jawampa.SubscriptionFlags;

/**
 * Connection to WAMP for crossbar and device monitoring.
 * 
 * @author Foerderer K.
 *
 */
public class Bus{

	private static Logger log = Logger.getLogger(Bus.class.getName());
	
	private WatchdogTopics watchdogTopics;
	private MeterTopics meterTopics;
	private BatteryTopics batteryTopics;
	
	private Map<UUID, Long> mostRecentMeterData;
	
	private Connection connection;
	
	public Bus(Configuration configuration) {
		connection = new Connection(configuration.wamp.url, configuration.wamp.realm, configuration.wamp.maxFramePayloadLength, "watchdog");
		
		mostRecentMeterData = new HashMap<UUID, Long>();
		
		watchdogTopics = new WatchdogTopics(configuration.wamp.topicPrefix);
		meterTopics = new MeterTopics(configuration.wamp.topicPrefix);
		batteryTopics = new BatteryTopics(configuration.wamp.topicPrefix);
		
		connection.onOpen(state -> {
			// register echo	
			connection.register(watchdogTopics.echo(), EchoRequest.class, parameters -> {
				EchoResponse response = new EchoResponse();
				response.out = parameters.in;
				return response;
			}, error -> {
			});
			
			for(UUID uuid : configuration.meters) {
				// listen for meter device updates
				connection.subscribe(meterTopics.meterState(uuid), SubscriptionFlags.Exact, MeterStatePublication.class, 
				meterState -> {
					mostRecentMeterData.put(meterState.uuid, Instant.now().getEpochSecond());
				});				
			}

		});
	}
	
	public void connect() {
		try {
			connection.open();
		} catch (Exception e) {
			log.severe("Could not connect to bus.");
			log.severe(e.toString());
			e.printStackTrace();
		}
	}

	/**
	 * Class for making call responses accessible.
	 * 
	 * @author Foerderer K.
	 *
	 */
	private static class ResponseContainer<T> implements Action1<T>{
		private T result = null;
		
		@Override
		public void call(T t) {
			result = t;
		}
		
		public T getResult() {
			return result;
		}
	}
	
	/**
	 * Executes an echo as blocking function call. Returns an empty string after 1 second without response.
	 * 
	 * @param in
	 * @return
	 */
	public String echo(String in) {
		Object lock = new Object();
		// fill in parameters
		EchoRequest request = new EchoRequest();
		request.in = in;
		ResponseContainer<EchoResponse> action = new ResponseContainer<EchoResponse>();
		// do call
		connection.call(watchdogTopics.echo(), request, EchoResponse.class, action, 
		error -> {
			synchronized (lock) {
				lock.notifyAll();
			}			
		}, () -> {
			synchronized (lock) {
				lock.notifyAll();
			}
		});
		
		synchronized (lock) {
			try {
				lock.wait(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		if(action.getResult() == null) {
			return "";
		}
		
		return action.getResult().out;
	}
	
	/**
	 * Returns most recent meter data.
	 * 
	 * @return
	 */
	public Map<UUID, Long> getMostRecentMeterData() {
		return mostRecentMeterData;
	}
	
	
	/**
	 * Retrieves battery state from the given battery. Blocks for up to 1 second and then returns null if no result has been received.
	 * 
	 * @param uuid
	 * @return
	 */
	public BatteryState getBatteryState(UUID uuid) {
		Object lock = new Object();
		// create request
		GetBatteryStateRequest request = new GetBatteryStateRequest();
		// create reponse container
		ResponseContainer<GetBatteryStateResponse> action = new ResponseContainer<GetBatteryStateResponse>();
		// do request
		connection.call(batteryTopics.getBatteryState(uuid), request, GetBatteryStateResponse.class, action, 
		error -> {
			synchronized (lock) {
				lock.notifyAll();
			}			
		}, () -> {
			synchronized (lock) {
				lock.notifyAll();
			}
		});
		
		// wait
		synchronized (lock) {
			try {
				lock.wait(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		return action.getResult();
	}
}
