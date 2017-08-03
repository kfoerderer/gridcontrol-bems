package de.fzi.osh.wamp;

/**
 * Function call with 1 parameter.
 * 
 * @author K. Foerderer
 *
 * @param <T>
 */
public interface Action1<T> {
	public void call(T t);	
}
