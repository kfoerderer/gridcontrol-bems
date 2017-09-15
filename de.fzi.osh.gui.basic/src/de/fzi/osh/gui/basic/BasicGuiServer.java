package de.fzi.osh.gui.basic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import de.fzi.osh.core.configuration.BaseConfiguration;
import de.fzi.osh.core.configuration.ConfigurationService;
import de.fzi.osh.data.logging.types.MeterData;
import de.fzi.osh.data.storage.timeseries.TimeSeriesStorageService;
import de.fzi.osh.data.storage.timeseries.TimeSeries;
import de.fzi.osh.gui.basic.configuration.BasicGuiConfiguration;
import de.fzi.osh.time.TimeService;
import de.fzi.osh.wamp.configuration.WampConfiguration;
import de.fzi.osh.wamp.device.battery.BatteryTopics;
import de.fzi.osh.wamp.device.meter.MeterTopics;

/**
 * Component that provides a basic user interface.
 * 
 * @author K. Foerderer
 *
 */
@Component(enabled=true, immediate=true)
public class BasicGuiServer {
	
	private static Logger log = Logger.getLogger(BasicGuiServer.class.getName());

	private static TimeService timeService;
	private static ConfigurationService configurationService;
	private static TimeSeriesStorageService timeSeriesStorageService;
	
	private static BaseConfiguration baseConfiguration;
	private static BasicGuiConfiguration guiConfiguration;
	private static WampConfiguration wampConfiguration;
	private static BatteryTopics batteryTopics;
	private static MeterTopics meterTopics;
	private Server server;	
	
	/**
	 * Data used for caching.
	 * 
	 * @author Foerderer K.
	 *
	 */
	private static class CacheEntry {
		public long timestamp;
		public String content;
	}
	
	private static Map<String, CacheEntry> cache = new HashMap<String, CacheEntry>();
	
	/**
	 * Generates the data.tsv file.
	 * 
	 * @author K. Foerderer
	 *
	 */
	@SuppressWarnings("serial")
	public static class DataTsvServlet extends HttpServlet {
		
        @Override
        protected void doGet( HttpServletRequest request,
                              HttpServletResponse response ) throws ServletException,
                                                            IOException
        {
            try {
            	// cache
            	try {
            		synchronized(cache) {
            			CacheEntry entry = cache.get(request.getPathInfo());
            			if(entry != null && entry.timestamp >= timeService.now() - 60) {
                			// respond
                            response.getWriter().println(entry.content);
                            return;
            			}
            		}
            	} catch(Exception e) {
            		log.warning("Error accessing cache:");
            		log.severe(e.toString());
            	}
            	
    			// create a set containing all meter ids
    			Set<UUID> meters = new HashSet<UUID>();
    			Collections.addAll(meters, baseConfiguration.consumptionMeterUUIDs);
    			if(baseConfiguration.productionMeterUUIDs != null) {
    				Collections.addAll(meters, baseConfiguration.productionMeterUUIDs);
    			}
    			if(baseConfiguration.batteryMeterUUIDs != null) {
    				Collections.addAll(meters, baseConfiguration.batteryMeterUUIDs);
    			}    			

    			// read UUID from URI
            	String resource = request.getPathInfo().substring(1);
            	String[] parts = resource.split("\\.");
            	
            	// respond with 404 if wrong file format is requested, UUID is malformed or UUID is unknown
            	if(parts.length != 2 || parts[1].equals("tsv") == false) {
            		response.sendError(HttpServletResponse.SC_NOT_FOUND);
            		return;
            	}            	
            	UUID uuid;
            	try {
            		uuid = UUID.fromString(parts[0]);
            	} catch(IllegalArgumentException e) {
            		response.sendError(HttpServletResponse.SC_NOT_FOUND);
            		return;
            	}  			
    			if(!meters.contains(uuid)) {
    				response.sendError(HttpServletResponse.SC_NOT_FOUND);
            		return;
    			}
            	
    			// set headers
                response.setContentType("text/tab-separated-values");
                response.setStatus(HttpServletResponse.SC_OK);

            	String content = "date\ttotalActivePower";
    			
    			// use an additional try-catch-block to ensure delivery of the file.
    			try 
    			{
    				TimeSeries series = MeterData.class.getAnnotation(TimeSeries.class);
	                	
                	ZonedDateTime end = ZonedDateTime.now();
                	ZonedDateTime start = end.minus(1, ChronoUnit.DAYS);
                	String where = "time <= " + end.toEpochSecond() + "s" + " AND time >= " + start.toEpochSecond() + "s";                	
                	List<MeterData> meter = timeSeriesStorageService.select(where, uuid + "_" + series.name(), MeterData.class , "1m");
	                	
                	//process data
                	MeterData previousData = null;
                	for (MeterData meterData : meter) {
                		if(null != previousData) {
                			// extrapolate from 1 min avg to 1 hour
                			double value = (meterData.totalActiveEnergyP - meterData.totalActiveEnergyN) - (previousData.totalActiveEnergyP - previousData.totalActiveEnergyN);
                			value *= (60*60*1000) / (meterData.time.toEpochMilli() - previousData.time.toEpochMilli());
                			value /= 100; // Wh/100 -> Wh
                			content +=  System.lineSeparator() + meterData.time.toString() + "\t" + value;
                		}
            			previousData = meterData;
					}        
    			} catch(Exception e) {
    				log.severe("Querying data failed.");
    				log.severe(e.toString());
    			}
    			
    			// respond
                response.getWriter().println(content);
                
                // write to cache
                synchronized(cache) {
	                CacheEntry entry = cache.get(request.getPathInfo());
	                if(entry == null) {
	                	entry = new CacheEntry();
	                }
	                entry.timestamp = timeService.now();
	                entry.content = content;
	                cache.put(request.getPathInfo(), entry);
                }
            } catch(Exception e) {
            	log.severe("Sending script file failed.");
            	log.severe(e.toString());
            }
        }
	}
	
	/**
	 * Generates the gui.js file.
	 * 
	 * @author K. Foerderer
	 *
	 */
	@SuppressWarnings("serial")
	public static class GuiJsServlet extends HttpServlet {
		
        @Override
        protected void doGet( HttpServletRequest request,
                              HttpServletResponse response ) throws ServletException,
                                                            IOException
        {
            response.setContentType("application/javascript");
            response.setStatus(HttpServletResponse.SC_OK);                        
            
            try {
            	// add configuration to script
            	String config = 
            		"var wamp_host = '" + 
            				(BasicGuiServer.guiConfiguration.substituteWampHost.length() > 0 ? 
            						BasicGuiServer.guiConfiguration.substituteWampHost : 
            						BasicGuiServer.wampConfiguration.url) + "';" + System.lineSeparator() +
            		"var wamp_realm = '" + BasicGuiServer.wampConfiguration.realm + "';" + System.lineSeparator() +
            		"var wamp_meter_topic = '" + meterTopics.meterState(null) +  "';" + System.lineSeparator() +
            		"var wamp_soc_topic = '" + batteryTopics.soc(null) + "';" + System.lineSeparator() +
            		"var consumptionMeter = '" + (BasicGuiServer.baseConfiguration.consumptionMeterUUIDs.length > 0 ? BasicGuiServer.baseConfiguration.consumptionMeterUUIDs[0].toString() : "") + "';" + System.lineSeparator();
            	
            	String productionMeterUUIDs = "[";
            	if(baseConfiguration.productionMeterUUIDs != null && baseConfiguration.productionMeterUUIDs.length > 0) {
            		for(UUID uuid : BasicGuiServer.baseConfiguration.productionMeterUUIDs) {
                		productionMeterUUIDs += "'" + uuid.toString() + "',"; 
                	}
                	productionMeterUUIDs = productionMeterUUIDs.substring(0, productionMeterUUIDs.length() - 1);	
            	}
            	productionMeterUUIDs += "]";
            	config += "var productionMeters = " + productionMeterUUIDs + ";" + System.lineSeparator();
            	
            	String batteryMeterUUIDs = "[";
            	if(baseConfiguration.batteryMeterUUIDs != null && baseConfiguration.batteryMeterUUIDs.length > 0) {
	            	for(UUID uuid : BasicGuiServer.baseConfiguration.batteryMeterUUIDs) {
	            		batteryMeterUUIDs += "'" + uuid.toString() + "',"; 
	            	}
	            	batteryMeterUUIDs = batteryMeterUUIDs.substring(0, batteryMeterUUIDs.length() - 1);
            	}
            	batteryMeterUUIDs += "]";
            	config += "var batteryMeters = " + batteryMeterUUIDs + ";" + System.lineSeparator();

            	String batteryUUIDs = "[";
            	if(baseConfiguration.batteryUUIDs != null && baseConfiguration.batteryUUIDs.length > 0) {
	            	for(UUID uuid : BasicGuiServer.baseConfiguration.batteryUUIDs) {
	            		batteryUUIDs += "'" + uuid.toString() + "',"; 
	            	}
	            	batteryUUIDs = batteryUUIDs.substring(0, batteryUUIDs.length() - 1);
            	}
            	batteryUUIDs += "]";
            	config += 
        			"var batteries = " + batteryUUIDs + ";" + System.lineSeparator() +
        			"var meterConfiguration = " + baseConfiguration.meterConfiguration.getValue() + ";" + System.lineSeparator() +
        			"var flexibilityMessageBuffer = '" + BasicGuiServer.guiConfiguration.flexibilityMessageBuffer + "';" + System.lineSeparator();
            	
            	// read script
            	String script = config + System.lineSeparator() + 
            			new String(Files.readAllBytes(Paths.get(BasicGuiServer.guiConfiguration.webPath + "/gui.js")));

                response.getWriter().println(script);
            } catch(Exception e) {
            	log.severe("Sending script file failed.");
            	log.severe(e.toString());
            }
        }
	}
	
	@Activate
	protected synchronized void activate() throws Exception {
		log.info("Starting server.");		
		// load configuration
		baseConfiguration = configurationService.get(BaseConfiguration.class);
		guiConfiguration = configurationService.get(BasicGuiConfiguration.class);
		wampConfiguration = configurationService.get(WampConfiguration.class);
		
		batteryTopics = new BatteryTopics(wampConfiguration.topicPrefix);
		meterTopics = new MeterTopics(wampConfiguration.topicPrefix);
		
		// start server
		server = new Server(guiConfiguration.port);
		
		// setup servlet
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.setResourceBase(guiConfiguration.webPath);
        context.setInitParameter("cacheControl","max-age=0,public");
        context.setInitParameter("pathInfoOnly", "true");
        server.setHandler(context);

        context.addServlet(DefaultServlet.class, "/images/*");
        context.addServlet(DefaultServlet.class, "/libs/*");
        context.addServlet(DefaultServlet.class, "/style.css");
        context.addServlet(DefaultServlet.class, "/favicon.ico");
        context.addServlet(DefaultServlet.class, "/manifest.json");
        context.addServlet(GuiJsServlet.class, "/gui.js");
        context.addServlet(DataTsvServlet.class, "/data/*");
        context.addServlet(DefaultServlet.class, "/");
        
		
		server.start();
		log.info("Started server.");
	}

	@Deactivate
	protected synchronized void deactivate() throws Exception {
		log.info("Shutting down.");
		server.stop();
	}
	
	@Reference(
			name = "TimeService",
			service = TimeService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindTimeService"
		)	
	protected synchronized void bindTimeService(TimeService timeService) {
		BasicGuiServer.timeService = timeService;
	}
	protected synchronized void unbindTimeService(TimeService timeService) {
		BasicGuiServer.timeService = null;
	}
	
	@Reference(
			name = "ConfigurationService",
			service = ConfigurationService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindConfigurationService"
		)	
	protected synchronized void bindConfigurationService(ConfigurationService configurationService) {
		BasicGuiServer.configurationService = configurationService;
	}
	protected synchronized void unbindConfigurationService(ConfigurationService configurationService) {
		BasicGuiServer.configurationService = null;
	}	
	
	@Reference(
			name = "TimeSeriesStorageService",
			service = TimeSeriesStorageService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindTimeSeriesStorageService"
		)	
	protected synchronized void bindTimeSeriesStorageService(TimeSeriesStorageService dataStorageService) {
		BasicGuiServer.timeSeriesStorageService = dataStorageService;
	}
	protected synchronized void unbindTimeSeriesStorageService(TimeSeriesStorageService dataStorageService) {
		BasicGuiServer.timeSeriesStorageService = null;
	}
}
