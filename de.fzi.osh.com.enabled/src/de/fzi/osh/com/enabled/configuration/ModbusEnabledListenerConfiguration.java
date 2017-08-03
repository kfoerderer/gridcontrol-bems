package de.fzi.osh.com.enabled.configuration;

import com.ghgande.j2mod.modbus.Modbus;

public class ModbusEnabledListenerConfiguration {
	/**
	 * Address to listen for Modbus connections
	 */
	public String address = "localhost";
	/**
	 * Port to listen for Modbus connections
	 */
	public int port = Modbus.DEFAULT_PORT;
	/**
	 * Modbus id
	 */
	public int id = 1;
	/**
	 * Number of threads for listener
	 */
	public int threads = 1;
	/**
	 * Address signal is written to
	 */
	public int signalAddress = 120;
	/**
	 * Address cycle is written to
	 */
	public int cycleAddress = 100;
	/**
	 * Modbus debug mode.
	 */
	public boolean debug = false;
}
