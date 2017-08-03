package de.fzi.osh.core.configuration.implementation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import de.fzi.osh.core.configuration.ConfigurationService;
import de.fzi.osh.core.configuration.ConfigurationListener;

/**
 * Configuration Service using JSON files
 * 
 * @author K. Foerderer
 *
 */
@Component(service=ConfigurationService.class, immediate=true, property={"filename:String=config.json"})
public class JsonConfiguration implements ConfigurationService {
	
	private static Logger log = Logger.getLogger(JsonConfiguration.class.getName());
	
	private Map<String, String> configuration;
	private Set<ConfigurationListener> listeners;
	
	public JsonConfiguration() {
		configuration = new HashMap<String, String>();
		listeners = new HashSet<ConfigurationListener>();
	}
	
	@Activate
	public void activate(Map<String, ?> properties) {
		String filename = System.getProperty("user.dir") + "/" +(String)properties.get("filename");
		System.out.println("Loading configuration: " + filename);
		load(filename);
	}
	
	public synchronized void load(String filename) {
		Type type = new TypeToken<Map<String, Object>>(){}.getType();
		Gson gson = new Gson();
		
		String json;
		try(Scanner scanner = new Scanner(new File(filename))) {
			// load file content into a single string
			json = scanner.useDelimiter("\\Z").next();
			// parse file
			Map<String, Object> parsed = gson.fromJson(json, type);
			for(Map.Entry<String, Object> entry : parsed.entrySet()) {
				if(entry.getValue() instanceof String) {
					// entry is a string -> nothing to do
					configuration.put(entry.getKey(), (String)entry.getValue());
				} else {
					// entry is an object -> convert it to a string		
					
					// create String from LinkedTreeMap
					json = gson.toJson(entry.getValue());
					configuration.put(entry.getKey(), json);
				}				
			}
		} catch (FileNotFoundException e) {
			System.out.println("Failed loading configuration: " + e.getMessage());
		}
	}
	
	public synchronized void save(String filename) {
		Gson gson = new Gson();
		String json = gson.toJson(configuration);
		
		try(PrintWriter out = new PrintWriter(filename)){
		    out.println( json );
		} catch (FileNotFoundException e) {
			System.out.println("Failed write configuration: " + e.getMessage());
		}
	}
	
	@Override
	public synchronized String get(String name) {
		return (String)configuration.get(name); 
	}
	
	@Override
	public synchronized <T> T get(Class<T> clazz) {
		Gson gson = new Gson();
		
		// parse with class information
		String data = configuration.get(clazz.getName());
		if(null != data) {
			T dataStructure = gson.fromJson(data, clazz);					
			return dataStructure;
		}
		
		log.warning("Configuration not found for class " + clazz.getName() + ". Returning 'null'.");
		return null;
	}

	@Override
	public synchronized void set(String name, String value) {
		configuration.put(name, value);
		
		for( ConfigurationListener listener : listeners ) {
			listener.changed(name, value);
		}
	}
	
	@Override
	public synchronized <T> void set(T value, Class<T> clazz) {
		Gson gson = new Gson();
		
		String json = gson.toJson(value);
		
		configuration.put(value.getClass().getName(), json);
		
		for( ConfigurationListener listener : listeners ) {
			listener.changed(value.getClass().getName(), value, clazz);
		}
	}

	@Override
	public void addConfigurationListener(ConfigurationListener configurationListener) {
		listeners.add(configurationListener);
		
	}

	@Override
	public void removeConfigurationListener(ConfigurationListener configurationListener) {
		listeners.remove(configurationListener);
	}

}
