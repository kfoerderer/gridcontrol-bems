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
	 * Meter configuration.
	 * 
	 * @author K. Foerderer
	 *
	 */
	public static enum MeterConfiguration {
		/**
		 * Standard configuration.
		 */
		Parallel(0), 
		/**
		 * Consumption meter measures consumption + production.
		 */
		ConsumptionIncludingProduction(1);
		
		private int id;
		private MeterConfiguration(int id) {
			this.id = id;
		}
		public int getValue() {
			return id;
		}
		public static MeterConfiguration fromValue(int id) {
			for(MeterConfiguration state: MeterConfiguration.values()) {
				if(state.getValue() == id) {
					return state;
				}
			}
			return MeterConfiguration.Parallel;
		}
	};
	
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
	 * Meter configuration in the building.
	 */
	public MeterConfiguration meterConfiguration = MeterConfiguration.Parallel;
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
