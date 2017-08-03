package de.fzi.osh.core.configuration;

import java.util.UUID;

/**
 * Basic configuration
 * 
 * @author K. Foerderer
 *
 */
public class BaseConfiguration {
	/**
	 * uuid for this device
	 */
	public UUID uuid = UUID.randomUUID();
	/**
	 * FMS uses virtual supply points for GEMS identification.
	 */
	public String virtualSupplyPoint = "vsp";
	/**
	 * Energy market participant which uses the flexibility.
	 */
	public String energySupplier = "EMT1";
	/**
	 * UUIDs of consumption meters.
	 */
	public UUID[] consumptionMeterUUIDs;
	/**
	 * UUIDs of production meters.
	 */
	public UUID[] productionMeterUUIDs;
	/**
	 * UUIDs of battery meters.
	 */
	public UUID[] batteryMeterUUIDs;
	/**
	 * UUIDs of all batteries.
	 */
	public UUID[] batteryUUIDs;
}
