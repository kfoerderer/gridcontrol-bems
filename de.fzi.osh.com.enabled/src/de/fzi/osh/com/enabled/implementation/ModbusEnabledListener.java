package de.fzi.osh.com.enabled.implementation;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import com.ghgande.j2mod.modbus.ModbusCoupler;
import com.ghgande.j2mod.modbus.net.ModbusTCPListener;
import com.ghgande.j2mod.modbus.procimg.DigitalOut;
import com.ghgande.j2mod.modbus.procimg.IllegalAddressException;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleDigitalIn;
import com.ghgande.j2mod.modbus.procimg.SimpleDigitalOut;
import com.ghgande.j2mod.modbus.procimg.SimpleInputRegister;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;

import de.fzi.osh.com.enabled.DataListener;
import de.fzi.osh.com.enabled.EnabledListener;
import de.fzi.osh.com.enabled.EnabledListenerService;
import de.fzi.osh.com.enabled.configuration.ModbusEnabledListenerConfiguration;
import de.fzi.osh.core.configuration.ConfigurationService;
@Component(service=EnabledListenerService.class, immediate=true)
public class ModbusEnabledListener implements EnabledListenerService {
	
	private static Logger log = Logger.getLogger(ModbusEnabledListener.class.getName());
	
	private final static int ENABLED = 0;
	private final static int DISABLED = 0xFFFF;
	
	private ModbusProcessImage processImage;
	
	/**
	 * Implements a coil that forwards set-Events to a listener
	 * 
	 * @author Foerderer K.
	 *
	 */
	@SuppressWarnings("unused")
	private static class ForwardingCoil implements DigitalOut {
		/**
		 * Register address needed for forwarding.
		 */
		private short address;
		private boolean value;
		private DataListener listener;
		
		/**
		 * Constructor
		 * 
		 * @param address
		 */
		public ForwardingCoil(short address, DataListener listener) {
			this.address = address;
			this.listener = listener;
		}

		@Override
		public boolean isSet() {
			return value;
		}

		@Override
		public void set(boolean b) {
			value = b;
			listener.setCoil(address, b);
		}
	}
	
	/**
	 * Implements a register that forwards set-Events to a listener
	 * 
	 * @author Foerderer K.
	 *
	 */
	private static class ForwardingRegister implements Register {
		/**
		 * Register address needed for forwarding.
		 */
		private short address;
		private short value;
		private DataListener listener;
		
		/**
		 * Constructor
		 * 
		 * @param address
		 */
		public ForwardingRegister(short address, DataListener listener) {
			this.address = address;
			this.listener = listener;
		}

		@Override
		public int getValue() {
			return value;
		}

		@Override
		public int toUnsignedShort() {
			return Short.toUnsignedInt(value);
		}

		@Override
		public short toShort() {
			return value;
		}

		@Override
		public byte[] toBytes() {
			byte[] ret = new byte[2];
			ret[1] = (byte)(value & 0xff);
			ret[0] = (byte)((value >> 8) & 0xff);
			return ret;
		}

		@Override
		public void setValue(int v) {
			value = (short) v;				
			listener.setRegister(address, value);
		}

		@Override
		public void setValue(short s) {
			value = s;			
			listener.setRegister(address, value);
		}

		@Override
		public void setValue(byte[] bytes) {
			value = (short)(bytes[0] << 8 | bytes[1]);			
			listener.setRegister(address, value);
		}
	}
	
	/**
	 * Register formed of multiple output coils
	 * 
	 * @author K. Foerderer
	 *
	 */
	private static class EnabledRegisterCoil implements DigitalOut{
		
		private static int register;
		private int position;
		private EnabledListener listener;
		
		/**
		 * Sets internal register state.
		 * 
		 * @param enabled
		 */
		public static void setEnabled(boolean enabled) {
			if(enabled == true) {
				register = ENABLED;
				
			} else {
				register = DISABLED;
			}				
		}
		
		/**
		 * Constructor
		 * 
		 * @param register register to hold value
		 * @param position position of bit within the register (0 = first)
		 */
		public EnabledRegisterCoil(int position, EnabledListener listener) {
			this.position = position;
			this.listener = listener;
		}

		@Override
		public boolean isSet() {
			return ((register >> position) & 0x0001) == 1;
		}

		@Override
		public synchronized void set(boolean value) {
			int old = register;
			if(value == true) {
				register = (register | (0x0001 << position));
			} else {
				register = (register & ( (~0x0001) << position));
			}
			
			if(old != register) {
				// value changed
	
				if(configuration.debug == true) {
					log.finest("Enabled-Register: " + register);
				}
				
				if(register == ENABLED) {
					listener.enable();
				} else if(register == DISABLED) {
					listener.disable();
				}
			}			
		}
	}
	
	private static ConfigurationService configurationService;
	private static ModbusEnabledListenerConfiguration configuration;
	
	private boolean enabled = true;
	
	private SimpleRegister cycle;
	
	public List<EnabledListener> listeners;
	public List<DataListener> dataListeners;
	
	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public void addListener(EnabledListener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeListener(EnabledListener listener) {
		listeners.remove(listener);
	}
	
	@Reference(
			name = "ConfigurationService",
			service = ConfigurationService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.DYNAMIC,
			unbind = "unbindConfigurationService"
		)	
	protected synchronized void bindConfigurationService(ConfigurationService configurationService) {
		ModbusEnabledListener.configurationService = configurationService;
	}
	protected synchronized void unbindConfigurationService(ConfigurationService configurationService) {
		ModbusEnabledListener.configurationService = null;
	}

	@Activate
	protected synchronized void activate() throws Exception {
		configuration = configurationService.get(ModbusEnabledListenerConfiguration.class);
		
		if(configuration.debug == true) {
			System.setProperty("com.ghgande.modbus.debug", "true");	
		}
		
		listeners = new ArrayList<EnabledListener>();
		dataListeners = new ArrayList<DataListener>();
		
		ModbusTCPListener listener = null;
		processImage = new ModbusProcessImage();
		
		EnabledListener enabledListener = new EnabledListener() {
			@Override
			public void enable() {
				if(enabled == false) {
					log.info("enabling");
					enabled = true;
					// call listeners
					for(EnabledListener listener : listeners) {
						listener.enable();
					}
				}
			}						
			@Override
			public void disable() {
				if(enabled == true) {
					log.info("disabling");
					enabled = false;
					// call listeners
					for(EnabledListener listener : listeners) {
						listener.disable();
					}
				}
			}
		};
		
		// set up the digital output coils to write into a single value
		for(int i = 0; i < 16; i++) {
			EnabledRegisterCoil coil = new EnabledRegisterCoil(i, enabledListener);
			processImage.setDigitalOut(configuration.signalAddress + i, coil);
		}		 
		// cycle information not used. Set up a register nevertheless
		cycle = new SimpleRegister(0);
		processImage.setRegister(configuration.cycleAddress, cycle);
		
		// battery commands from rems
		DataListener dataListener = new DataListener() {
			
			@Override
			public void setRegister(short address, short value) {
				// pass data
				for(DataListener listener : dataListeners) {
					listener.setRegister(address, value);
				}
			}
			
			@Override
			public void setCoil(short address, boolean value) {
				// pass data
				for(DataListener listener : dataListeners) {
					listener.setCoil(address, value);
				}
			}
		};
		processImage.setRegister(31, new ForwardingRegister((short) 31, dataListener));
		processImage.setRegister(32, new ForwardingRegister((short) 32, dataListener));
		processImage.setRegister(243, new ForwardingRegister((short) 243, dataListener));
		
		// set up modbus
		ModbusCoupler.getReference().setProcessImage(processImage);
		ModbusCoupler.getReference().setMaster(false);
		ModbusCoupler.getReference().setUnitID(configuration.id);
		
		// start listening for connections
		listener = new ModbusTCPListener(configuration.threads);
		listener.setAddress(InetAddress.getByName(configuration.address));
		listener.setPort(configuration.port);
		listener.listen();
		
		log.info("Listening for \"enabled\"-Signal on " + configuration.address + ":" + configuration.port);
	}

	@Deactivate
	protected synchronized void deactivate() throws Exception {
	}

	@Override
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		EnabledRegisterCoil.setEnabled(enabled);
		
		// set coils
		for(int i = 0; i < 16; i++) {
			// coil reflects "disable"
			processImage.getDigitalOut(configuration.signalAddress + i).set(!enabled);
		}
	}

	@Override
	public void publishData(Map<Short, Boolean> readOnlyCoils, Map<Short, Boolean> coils, Map<Short, Short> readOnlyRegisters, Map<Short, Short> registers) {
		if(readOnlyCoils != null) {
			// set all coils
			for(Map.Entry<Short, Boolean> coil : readOnlyCoils.entrySet()) {
				SimpleDigitalIn in;
				try {
					in = (SimpleDigitalIn) processImage.getDigitalIn(coil.getKey());
					// set value
					in.set(coil.getValue());			
				} catch(IllegalAddressException e)  {
					// register has not been created yet
					in = new SimpleDigitalIn(coil.getValue());
					processImage.setDigitalIn(coil.getKey(), in);
				} 
			}
		}
		if(coils != null) {
			// set all coils
			for(Map.Entry<Short, Boolean> coil : coils.entrySet()) {
				DigitalOut out;
				try {
					// prevent overriding enabled signal
					if(coil.getKey() >= configuration.signalAddress && coil.getKey() <= configuration.signalAddress + 16){
						out = processImage.getDigitalOut(coil.getKey());
						// set value
						out.set(coil.getValue());			
					}
				} catch(IllegalAddressException e) {
					// register has not been created yet
					out = new SimpleDigitalOut(coil.getValue());
					processImage.setDigitalOut(coil.getKey(), out);
				}
			}
		}
		if(readOnlyRegisters != null) {
			// set all registers
			for(Map.Entry<Short, Short> register : readOnlyRegisters.entrySet()) {
				SimpleInputRegister reg;
				try {
					reg = (SimpleInputRegister) processImage.getInputRegister(register.getKey());
					// set value
					reg.setValue(register.getValue());
				} catch(IllegalAddressException e) {
					// register has not been created yet
					reg = new SimpleInputRegister(register.getValue());
					processImage.setInputRegister(register.getKey(), reg);
				}
			}
		}
		if(registers != null) {
			// set all registers
			for(Map.Entry<Short, Short> register : registers.entrySet()) {
				Register reg;
				try {
					// prevent overriding cycle
					if(register.getKey() != configuration.cycleAddress){
						reg = processImage.getRegister(register.getKey());
						// set value
						reg.setValue(register.getValue());
					}
				} catch(IllegalAddressException e) {
					// register has not been created yet
					reg = new SimpleRegister(register.getValue());
					processImage.setRegister(register.getKey(), reg);
				}  
			}
		}
	}

	@Override
	public void addDataListener(DataListener listener) {
		dataListeners.add(listener);
	}

	@Override
	public void removeDataListener(DataListener listener) {
		dataListeners.remove(listener);
	}	
}
