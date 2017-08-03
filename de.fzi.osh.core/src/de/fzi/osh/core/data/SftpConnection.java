package de.fzi.osh.core.data;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Scanner;
import java.util.logging.Logger;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;

/**
 * Class for performing sftp actions
 * 
 * @author K. Foerderer
 *
 */
public class SftpConnection {
	
	private Logger log = Logger.getLogger(SftpConnection.class.getName());
	
	private String host;
	private short port;
	private String user;
	private String password;
	
	private JSch jSch = new JSch();
	private Session session;
	private ChannelSftp sftp;
	
	/**
	 * Constructor
	 * 
	 * @param host
	 * @param port
	 * @param user
	 * @param password
	 */
	public SftpConnection(String host, short port, String user, String password) {
		this.host = host;
		this.port = port;
		this.user = user;
		this.password = password;
	}

	/**
	 * Establish server connection.
	 * 
	 * @return <i>true</i> on success.
	 */
	public boolean connect() {
		if( (null != session && session.isConnected()) || 
			(null != sftp && sftp.isConnected())) {
			log.warning("Allready connected! Disconnecting and reestablishing connection.");
			disconnect();
		}			
		
		try {
			// configure session
			session = jSch.getSession(user, host, port);
			session.setPassword(password);
			
			// TODO: [security] how to not deactivate host key checking?
			session.setConfig("StrictHostKeyChecking", "no");
				
			// connect to server
			session.connect();
		} catch (Exception e) {
			log.severe("Could not connect to '" +  host + ":" + port + "' with user '" + user + "'.");
			log.severe(e.getMessage());
			return false;
		}		
		
		try {
			// open sftp channel
			sftp = (ChannelSftp) session.openChannel("sftp");
					
			sftp.connect();			
		} catch (Exception e) {
			log.severe("Could not open sftp channel.");
			log.severe(e.getMessage());
			session.disconnect();
			return false;
		}		
		
		return true;
	}
	
	public boolean isConnected() {
		return (null != session && session.isConnected() && null != sftp && sftp.isConnected());
	}
	
	/**
	 * Disconnect from server
	 */
	public void disconnect() {
		if(null != session) {
			session.disconnect();
			session = null;
		}
		if(null != sftp) {
			sftp.disconnect();
			sftp = null;
		}		
	}
	
	/**
	 * Writes data to a file;
	 * 
	 * @param file
	 * @param data
	 * @return <i>true</i> on success.
	 */
	public boolean write(String file, String data){
		try {
			InputStream stream = new ByteArrayInputStream(data.getBytes("UTF-8"));
			sftp.put(stream, file);	
		} catch(Exception e) {
			return false;
		}
		return true;
	}
	
	/**
	 * Reads the contents of a file
	 * 
	 * @param file
	 * @return <i>null</i> on failure. Else, file content. 
	 */
	public String read(String file){
		try {
			String content;
			InputStream stream = sftp.get(file);
			try(Scanner scanner = new Scanner(stream)) {
				scanner.useDelimiter("\\A");
				content = scanner.hasNext() ? scanner.next() : "";
			}		
			return content;
		} catch(Exception e) {
			return null;
		}
	}
	
	/**
	 * Renames a file
	 * 
	 * @param from
	 * @param to
	 * @return <i>true</i> on success.
	 */
	public boolean rename(String from, String to) {
		try {
			sftp.rename(from, to);			
			return true;
		} catch(Exception e) {
			return false;
		}
	}
	
	/**
	 * Changes the working directory.
	 * 
	 * @param directory
	 * @return
	 */
	public boolean changeDirectory(String directory) {
		try {
			sftp.cd(directory);
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	/**
	 * Creates a directory
	 * 
	 * @param directory
	 * @return
	 */
	public boolean makeDirectory(String directory) {
		try {
			sftp.mkdir(directory);
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	/**
	 * Returns the attributes for a file or folder. If the object does not exist <i>null</i> is returned.
	 * 
	 * @param path
	 * @return
	 */
	public SftpATTRS getAttributes(String path) {
		SftpATTRS attributes;		
		try {
			// retrieve attributes
			attributes = sftp.stat(path);
		} catch(Exception e) {
			attributes = null;
		}		
		return attributes;
	}
}
