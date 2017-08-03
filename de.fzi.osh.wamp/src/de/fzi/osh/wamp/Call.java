package de.fzi.osh.wamp;

public interface Call<P,R> {
	
	public String getUri();
		
	public R onCall(P parameters);
	
	public void onResponse(R response);
}
