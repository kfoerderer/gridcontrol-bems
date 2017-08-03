package de.fzi.osh.wamp;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import rx.Scheduler;
import rx.schedulers.Schedulers;
import ws.wamp.jawampa.ApplicationError;
import ws.wamp.jawampa.SubscriptionFlags;
import ws.wamp.jawampa.WampClient;
import ws.wamp.jawampa.WampClientBuilder;
import ws.wamp.jawampa.WampSerialization;
import ws.wamp.jawampa.transport.netty.NettyWampClientConnectorProvider;
import ws.wamp.jawampa.transport.netty.NettyWampConnectionConfig;

/**
 * Represents a connection to a WAMP router. 
 * 
 * Publications on topics will be queued and dispatched later.
 * 
 * @author K. Foerderer
 *
 */
public class Connection implements Runnable{

	/**
	 * Event data for event queue.
	 * 
	 * @author K. Foerderer
	 *
	 * @param <P>
	 */
	private static class Event<P> {
		Action1<P> onEvent;
		P parameters;
	}
	
	static {
		WampSerialization.Json.getObjectMapper().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
	}
	
	private static Logger log = Logger.getLogger(Connection.class.getName());
	
	private String url;
	private String realm;
	private String name;	
	private int maxFramePayloadLength;
	
	private Action1<WampClient.ConnectedState> onOpen;
	private Action1<WampClient.DisconnectedState> onClose;
	
	private BlockingQueue<Event<?>> eventQueue; 
    
	private Thread eventThread;
	
    // Jawampa
	protected WampClient wamp;
	protected transient WampClient.State state;
	
	// rx
	private ExecutorService executor = Executors.newSingleThreadExecutor();
    protected Scheduler rxScheduler = Schedulers.from(executor);
	
	// Jackson
	private static ObjectMapper mapper;
	
	/**
	 * Constructor. Sets up the wamp parameters.
	 * 
	 * @param url Router url.
	 * @param realm Connection realm.
	 * @param name Name of this connection (for debugging).
	 * @throws Exception 
	 */
	public Connection(String url, String realm, int maxFramePayloadLength, String name) {
		this.url = url;
		this.realm = realm;
		this.name = name;
		this.maxFramePayloadLength = maxFramePayloadLength;
		
		eventQueue = new LinkedBlockingQueue<Event<?>>();
		
		mapper = new ObjectMapper();
	}
	
	/**
	 * Opens a connection to the WAMP router. 
	 * @throws Exception 
	 */
	public void open() throws Exception {
		if(null != wamp) {
			close();
		}
		
        WampClientBuilder builder = new WampClientBuilder();
        NettyWampConnectionConfig.Builder configBuilder = new NettyWampConnectionConfig.Builder();
		try {
			builder.withConnectorProvider(new NettyWampClientConnectorProvider())
					.withUri(url).withRealm(realm)
					.withInfiniteReconnects()
					.withConnectionConfiguration(configBuilder.withMaxFramePayloadLength(maxFramePayloadLength).build())
					.withCloseOnErrors(false).withReconnectInterval(5, TimeUnit.SECONDS);
			wamp = builder.build();
		} catch (Exception e) {
			log.severe(e.toString());
			Exception exception = new Exception("Setting up jawampa failed.", e);
			throw exception;
		}		
		
        // setup status changed callback
        wamp.statusChanged()
        .observeOn(rxScheduler)
        .subscribe( state -> {
        	log.info("Changed state to '" + state + "' for '" + name + "'.");
        	try {
	        	if(state instanceof WampClient.ConnectedState) {
	        		if(null != Connection.this.onOpen) {
	        			try {
	        				Connection.this.onOpen.call((WampClient.ConnectedState)state);
	        			} catch(Exception e) {
	        			}
	        		}
	        		if(null == eventThread) {
		        		// start event handling
		        		eventThread = new Thread(this, "de.fzi.osh.wamp.connection.eventThread:" + name);
		        		eventThread.start();
	        		}
	        	} else if(state instanceof WampClient.DisconnectedState) {
	        		Throwable reason = ((WampClient.DisconnectedState) state).disconnectReason();
	        		if(null != reason) {
	        			log.info(reason.toString() + " : " + reason.getMessage());
	        		}
        			// call onClose only of not disconnected in prior state
	        		if(	null != Connection.this.onClose && 
	        			(Connection.this.state instanceof WampClient.ConnectingState || 
	        			Connection.this.state instanceof WampClient.ConnectedState)) {
	        			
	        			Connection.this.onClose.call((WampClient.DisconnectedState)state);
	        		}
	        	}
        	} finally {
        		Connection.this.state = state;
        	}
        });
		
        // connect
        wamp.open();
	}
	
	/**
	 * Closes the connection
	 */
	public void close() {
		wamp.close();
		wamp = null;
	}
	
	public boolean isConnected() {
		return state instanceof WampClient.ConnectedState;
	}
	
	/**
	 * Action to perform when the connection has been established.
	 * 
	 * @param action
	 */
	public void onOpen(Action1<WampClient.ConnectedState> action) {
		onOpen = action;
	}
	
	/**
	 * Action to perform when the connection has been closed.
	 * 
	 * @param action
	 */
	public void onClose(Action1<WampClient.DisconnectedState> action) {
		onClose = action;
	}
	
	
	/**
	 * Subscribe to a topic.
	 * 
	 * @param uri
	 * @param flags
	 * @param parameterClass
	 * @param onEvent
	 */
	public<P> void subscribe(String uri, SubscriptionFlags flags, Class<P> parameterClass, Action1<P> onEvent) {
		wamp.makeSubscription(uri, flags).observeOn(rxScheduler).subscribe( publication -> {
			try {
				if(publication.arguments() == null || publication.arguments().size() == 0) {
					log.warning("Received publication without parameters.");
					// nothing else to do, since there are no replies for publications.
					onEvent.call(null);
					return;
				}
				// parse parameters
				P parameters = mapper.convertValue(publication.arguments().get(0), parameterClass);
				
				// add event to queue
				Connection.Event<P> event = new Connection.Event<P>();
				event.onEvent = onEvent;
				event.parameters = parameters;
				Connection.this.eventQueue.add(event); // add and not put to ensure fast event handling
				
			} catch (Exception e) {
				log.severe("Addition to event queue failed");
				log.severe(e.toString());
			}
		}, error -> {
			log.severe("Subscription to '" + uri + "' failed.");
			log.severe(error.toString());
			// try again
			Thread subscriptionThread = new Thread(new Runnable() {
				@Override
				public void run() {
					// wait a few seconds and then try again
					try {
						Thread.sleep(10 * 1000);
					} catch (InterruptedException e) {
					}
					subscribe(uri, flags, parameterClass, onEvent);
				}
			}, "Re-Subscription on error: " + uri + ".");
			subscriptionThread.start();
		});		
	}
	
	/**
	 * Publish on a topic.
	 * 
	 * @param uri
	 * @param parameters
	 * @param onSuccess
	 * @param onError
	 * @param onCompleted
	 */
	public<P> void publish(String uri, P parameters, Action1<Long> onSuccess, Action1<Throwable> onError, Action0 onCompleted) {
		wamp.publish(uri, parameters).observeOn(rxScheduler).subscribe(
				id -> {
					try {
						if(null != onSuccess) {
							onSuccess.call(id);
						}
					} catch (Exception e) {
						log.severe("onSuccess failed");
						log.severe(e.toString());
					}
				},
				error -> {
					try {
						log.severe("Publishing failed on topic '" + uri + "'.");
						log.severe(error.getMessage());
						if(null != onError) {
							onError.call(error);
						}
					} catch (Exception e) {
						log.severe("onError failed");
						log.severe(e.toString());
					}
				},
				() -> {
					try {
						if(null != onCompleted) {
							onCompleted.call();
						}
					} catch (Exception e) {
						log.severe("onCompleted failed");
						log.severe(e.toString());
					}
				});
	}
	
	/**
	 * Registers a call.
	 * 
	 * @param uri
	 * @param parameterClass
	 * @param onCall
	 * @param onError
	 */
	public<P, R> void register(String uri, Class<P> parameterClass, Action1R<P, R> onCall, Action1<Throwable> onError) {
		wamp.registerProcedure(uri).observeOn(rxScheduler).subscribe(
				request -> {
					try {
						if(request.arguments() == null || request.arguments().size() == 0) {
							log.warning("Received call without parameters.");
							onCall.call(null);
							return;
						}
						// parse response
						P parameters = mapper.convertValue(request.arguments().get(0), parameterClass);
						
						request.reply(onCall.call(parameters));
						
					} catch (Exception e) {
						log.severe("onCall failed");
						log.severe(e.toString());
						e.printStackTrace();
						// notify caller of error
						try {
							request.replyError(ApplicationError.CANCELED);
						} catch (ApplicationError ae) {
						}
					}
				},
				error -> {
					log.severe("Registration of procedure failed.");
					log.severe(error.toString());
					if(null != onError) {
						onError.call(error);
					}
				});		
	}
	
	/**
	 * Performs a remote procedure call.
	 * 
	 * @param uri
	 * @param parameters
	 * @param responseClass
	 * @param onResponse
	 * @param onError
	 * @param onCompleted
	 */
	public<P, R> void call(String uri, P parameters, Class<R> responseClass, Action1<R> onResponse, Action1<Throwable> onError, Action0 onCompleted) {
		wamp.call(uri, parameters).observeOn(rxScheduler).subscribe(
				result -> {
					try {
						if(null != onResponse) {
							if(result.arguments() == null || result.arguments().size() == 0) {
								log.warning("Received publication without parameters.");
								onResponse.call(null);
								return;
							}
							// parse response
							R response = mapper.convertValue(result.arguments().get(0), responseClass);
							
							onResponse.call(response);
						}
					} catch (Exception e) {
						log.severe("onResponse failed");
						log.severe(e.toString());
					}
				},
				error -> {
					try {
						log.severe("RPC failed on topic '" + uri + "'.");
						log.severe("Parameters: " + parameters.toString());
						log.severe(error.getMessage());
						if(null != onError) {
							onError.call(error);
						}
					} catch (Exception e) {
						log.severe("onError failed");
						log.severe(e.toString());
					}
				},
				() -> {
					try {
						if(null != onCompleted) {
							onCompleted.call();
						}
					} catch (Exception e) {
						log.severe("onCompleted failed");
						log.severe(e.toString());
					}
				});
	}
	
	/**
	 * Handles event queue using a blocking queue.
	 * 
	 */
	@Override
	public void run() {
		while(state instanceof WampClient.ConnectedState) {
			try {
				// Get next element from queue. (Blocks execution)
				Event<?> event = eventQueue.take();
				handleEvent(event);
				/*if(Instant.now().getEpochSecond() % 60 == 0) { // DEBUG: this doesn't have to use time service
					log.finest("Event queue length: " + eventQueue.size());
				}*/
			} catch (InterruptedException e) {
				log.warning("Taking from event queue was interrupted.");
			}
		}
	}
	
	/**
	 * Helper method to deal with generic types. 
	 * 
	 * @param event
	 */
	public<P> void handleEvent(Event<P> event) {
		try {
			// actual event handling
			event.onEvent.call(event.parameters);
		} catch(Exception e) {
			log.severe("Processing event failed.");
			log.severe(e.toString());
		}
	}
	
	/**
	 * Returns router url.
	 * 
	 * @return
	 */
	public String getUrl() {
		return url;
	}
	
	/**
	 * Returns realm.
	 * 
	 * @return
	 */
	public String getRealm() {
		return realm;
	}
}
