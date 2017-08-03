package de.fzi.osh.data.storage.timeseries;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for a type enclosing time series' data.
 * 
 * @author Foerderer K.
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TimeSeries {
	
	/**
	 * The name of this time series.
	 * 
	 * @return
	 */
	String name();

	/**
	 * This annotation marks the series.
	 * 
	 * @author Foerderer K.
	 *
	 */
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Series {

	}
	
	/**
	 * This annotation marks a tag field.
	 * 
	 * @author Foerderer K.
	 *
	 */
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Tag {

	}
}
