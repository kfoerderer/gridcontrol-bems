package de.fzi.osh.wamp;

import ws.wamp.jawampa.SubscriptionFlags;

public abstract class Topic<P> {

	protected Topic(String uri, SubscriptionFlags flags) {
		this.uri = uri;
		this.flags = flags;
	}
	
	public final String uri;
	
	public final SubscriptionFlags flags;
	
}
