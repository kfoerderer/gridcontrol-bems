package de.fzi.osh.optimization;

import java.util.Map;

/**
 * Service for finding (optimal) solutions for problems 
 * 
 * @author K. Foerderer
 *
 */
public interface OptimizationService {
	/**
	 * Returns a mapping specifying the solver capabilities:
	 * 
	 *  Problem class -> Solution classes.
	 *  
	 *  Hence, a service could provide different types of solutions for the same problem instance.
	 * 
	 * @return
	 */
	public Map<Class<?>, Class<?>[]> getCapabilities();
	/**
	 * For quick capability checking
	 * 
	 * @param problem
	 * @param solution
	 * @return
	 */
	public boolean canSolve(Class<? extends Problem> problem, Class<? extends Solution> solution);
	/**
	 * Solves the problem and returns the 
	 * 
	 * @param problem
	 * @param problemClass
	 * @param solutionClass
	 * @return
	 */
	public<P extends Problem, S extends Solution> S solve(P problem, Class<P> problemClass, Class<S> solutionClass); 
}
