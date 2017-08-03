package de.fzi.osh.com.enabled.implementation;

import java.util.HashMap;
import java.util.Map;

import com.ghgande.j2mod.modbus.procimg.DigitalIn;
import com.ghgande.j2mod.modbus.procimg.DigitalOut;
import com.ghgande.j2mod.modbus.procimg.FIFO;
import com.ghgande.j2mod.modbus.procimg.File;
import com.ghgande.j2mod.modbus.procimg.IllegalAddressException;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.ProcessImage;
import com.ghgande.j2mod.modbus.procimg.Register;

/**
 * Process image implementation. 
 * 
 * @author K. Foerderer
 *
 */
public class ModbusProcessImage implements ProcessImage {
	
	private Map<Integer, DigitalIn> digitalInMap;
	private Map<Integer, DigitalOut> digitalOutMap;
	private Map<Integer, InputRegister> inRegisterMap;
	private Map<Integer, Register> outRegisterMap;
	
	public ModbusProcessImage() {
		digitalInMap = new HashMap<Integer, DigitalIn>();
		digitalOutMap = new HashMap<Integer, DigitalOut>();
		inRegisterMap = new HashMap<Integer, InputRegister>();
		outRegisterMap = new HashMap<Integer, Register>();
	}

	@Override
	public DigitalIn getDigitalIn(int ref) throws IllegalAddressException {
		DigitalIn in = digitalInMap.get(ref);
		if(in == null) {
			throw new IllegalAddressException();
		}
		return in;
	}

	@Override
	public int getDigitalInCount() {
		return digitalInMap.size();
	}

	@Override
	public DigitalIn[] getDigitalInRange(int offset, int count) throws IllegalAddressException {
		DigitalIn[] array = new DigitalIn[count];
		
		for(int i = 0; i < count; i++) {
			array[i] = digitalInMap.get(offset + i);				
		}
		
		return array;
	}
	
	public void setDigitalIn(int ref, DigitalIn in) {
		digitalInMap.put(ref, in);
	}
	
	public void removeDigitalIn(int ref) {
		digitalInMap.remove(ref);
	}

	@Override
	public DigitalOut getDigitalOut(int ref) throws IllegalAddressException {
		DigitalOut out = digitalOutMap.get(ref);
		if(out == null) {
			throw new IllegalAddressException();
		}
		return out;
	}

	@Override
	public int getDigitalOutCount() {
		return digitalOutMap.size();
	}

	@Override
	public DigitalOut[] getDigitalOutRange(int offset, int count) throws IllegalAddressException {
		DigitalOut[] array = new DigitalOut[count];
		
		for(int i = 0; i < count; i++) {
			array[i] = digitalOutMap.get(offset + i);				
		}
		
		return array;
	}
	
	public void setDigitalOut(int ref, DigitalOut out) {
		digitalOutMap.put(ref, out);
	}
	
	public void removeDigitalOut(int ref) {
		digitalOutMap.remove(ref);
	}

	@Override
	public InputRegister getInputRegister(int ref) throws IllegalAddressException {
		InputRegister in = inRegisterMap.get(ref);
		if(in == null) {
			throw new IllegalAddressException();
		}
		return in;
	}

	@Override
	public int getInputRegisterCount() {
		return inRegisterMap.size();
	}

	@Override
	public InputRegister[] getInputRegisterRange(int offset, int count) throws IllegalAddressException {
		InputRegister[] array = new InputRegister[count];
		
		for(int i = 0; i < count; i++) {
			array[i] = inRegisterMap.get(offset + i);				
		}
		
		return array;
	}
	
	public void setInputRegister(int ref, InputRegister in) {
		inRegisterMap.put(ref, in);
	}
	
	public void removeInputRegister(int ref) {
		inRegisterMap.remove(ref);
	}

	@Override
	public Register getRegister(int ref) throws IllegalAddressException {
		Register out = outRegisterMap.get(ref);
		if(out == null) {
			throw new IllegalAddressException();
		}
		return out;
	}

	@Override
	public int getRegisterCount() {
		return outRegisterMap.size();
	}

	@Override
	public Register[] getRegisterRange(int offset, int count) throws IllegalAddressException {
		Register[] array = new Register[count];
		
		for(int i = 0; i < count; i++) {
			array[i] = outRegisterMap.get(offset + i);				
		}
		
		return array;
	}
	
	public void setRegister(int ref, Register in) {
		outRegisterMap.put(ref, in);
	}
	
	public void removeRegister(int ref) {
		outRegisterMap.remove(ref);
	}

	@Override
	public FIFO getFIFO(int arg0) throws IllegalAddressException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FIFO getFIFOByAddress(int arg0) throws IllegalAddressException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getFIFOCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public File getFile(int arg0) throws IllegalAddressException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public File getFileByNumber(int arg0) throws IllegalAddressException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getFileCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getUnitID() {
		// TODO Auto-generated method stub
		return 0;
	}
}