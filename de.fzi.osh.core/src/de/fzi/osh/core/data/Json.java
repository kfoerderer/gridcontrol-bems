package de.fzi.osh.core.data;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;

/**
 * A converter for json data.
 * 
 * @author K. Foerderer
 *
 */
public class Json {

	private static Logger log = Logger.getLogger(Json.class.getName());

	/**
	 * Convert json to object
	 * 
	 * @param json
	 */
	public static<T> T parse(String json, Class<T> classOfIt) {
		Gson gson = new Gson();

		T dataStructure;
		
		// read file content and try to parse it using gson
		try {			
			dataStructure = gson.fromJson(json, classOfIt);			
		} catch(Exception e1) {
			log.log(Level.SEVERE, "Could not parse string " + json);
			log.log(Level.INFO, "Creating object with preset values.");
			
			// error occurred -> create a new instance with default parameters 
			try {
				dataStructure = classOfIt.newInstance();
			}
			catch(Exception e)
			{
				// didn't work as well -> return null
				log.log(Level.SEVERE, "Could not generate a new instance of class " + classOfIt.getName());
				dataStructure = null;
			}			
		}
		
		return dataStructure;
	}
	
	/**
	 * Load a json file
	 * 
	 * @param filename
	 */
	public static<T> T readFile(String filename, Class<T> classOfIt) {
		Gson gson = new Gson();
		
		T dataStructure = null;
		
		// read file content and try to parse it using gson
		try(Reader reader = new FileReader(filename)) {			
			dataStructure = gson.fromJson(reader, classOfIt);			
		} catch(IOException ioE) {
			log.log(Level.SEVERE, "Could not load file: " + filename);
			log.log(Level.INFO, "Creating object with preset values.");
			
			// error occurred -> create a new instance with default parameters 
			try {
				dataStructure = classOfIt.newInstance();
			}
			catch(Exception e)
			{
				// didn't work as well -> return null
				log.log(Level.SEVERE, "Could not generate a new instance of class " + classOfIt.getName());
				dataStructure = null;
			}			
		} catch(Exception e) {
			log.severe(e.toString());
		}
		
		return dataStructure;
	}
	
	/**
	 * Save configuration to a json file
	 * 
	 * @param filename
	 */
	public static<T> void writeFile(String filename, T dataStructure) {
		Gson gson = new Gson();
		String json = gson.toJson(dataStructure);
		
		try(PrintWriter out = new PrintWriter(filename)){
		    out.println( json );
		} catch (FileNotFoundException e) {
			System.out.println("Failed write configuration: " + e.toString());
		}
	}
}
