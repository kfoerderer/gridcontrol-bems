package de.fzi.osh.wamp;

/**
 * Interface for an action that takes 1 argument and returns a result.
 * 
 * @author K. Foerderer
 *
 * @param <P> Parameter type.
 * @param <R> Return value type.
 */
public interface Action1R<P,R> {
	public R call(P p);
}
