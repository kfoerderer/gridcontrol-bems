package de.fzi.osh.data.storage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for table columns
 * 
 * @author K. Foerderer
 *
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Column {
	/**
	 * Name of the column
	 * 
	 * @return
	 */
	String name();
	/**
	 * Column type declaration for SQL table creation, e.g. 'INT NOT NULL AUTO_INCREMENT'
	 * 
	 * @return
	 */
	String declaration();
	/**
	 * Is this column generated automatically?
	 * 
	 * If yes, no value is passed for insertion
	 * 
	 * @return
	 */
	boolean auto_creation() default false;
	/**
	 * Is this column updated automatically?
	 * 
	 * If yes, no value is passed for insertion
	 * 
	 * @return
	 */	
	boolean auto_update() default false;
	
	/**
	 * Type of the column
	 * @return
	 */
	Type type() default Type.SERIES;
	
	public enum Type {
		TIME,
		SERIES,
		TAG
	}
	
}


