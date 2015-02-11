package hudson.scm;

import java.io.Serializable;
import java.util.UUID;

import hudson.util.Secret;

import org.kohsuke.stapler.DataBoundConstructor;

public final class IntegrityConfigurable implements Serializable 
{
	private static final long serialVersionUID = 3627193531372714191L;
	private String configId;
	private String name;
	private String ipHostName;
	private int ipPort = 0;
	private String hostName;
	private int port;
	private boolean secure;
	private String userName;
	private Secret password;

	@DataBoundConstructor
	public IntegrityConfigurable(String configId, String ipHostName, int ipPort, String hostName, int port, boolean secure, String userName, String password) 
	{
		this.configId = (null == configId || configId.length() == 0 ? UUID.randomUUID().toString() : configId);
		this.ipHostName = ipHostName;
		this.ipPort = ipPort;
		this.hostName = hostName;
		this.port = port;
		this.secure = secure;
		this.userName = userName;
		this.password = Secret.fromString(password);
		this.name = String.format("%s@%s:%d", userName, hostName, port);
	}

	/**
	 * Returns the unique id associated with this configuration
	 * @return
	 */
	public String getConfigId()
	{
		return this.configId;
	}
	
	/**
	 * Sets the unique id for this configuration
	 * @param id
	 */
	public void setConfigId(String id)
	{
		this.configId = id;
	}
	
    /**
     * Returns the Integration Point host name for the connection
     * @return
     */
	public String getIpHostName()
	{
		return this.ipHostName;
	}
		
	/**
     * Sets the Integration Point host name of the API Session
     * @return
     */
	public void setIpHostName(String ipHostName)
	{
		this.ipHostName = ipHostName;
	}
	
	/**
     * Returns the Integration Point port of the API Session
     * @return
     */    
	public int getIpPort()
	{
		return ipPort;
	}
	
	/**
     * Sets the Integration Point port of the API Session
     * @return
     */    
	public void setIpPort(int ipPort)
	{
		this.ipPort = ipPort;
	}
	
	/**
     * Returns the host name of the Integrity Server
     * @return
     */
	public String getHostName()
	{
		return this.hostName;
	}
	
	/**
     * Sets the host name of the Integrity Server
     * @return
     */
	public void setHostName(String hostName)
	{
		this.hostName = hostName;
	}
	
	/**
     * Returns the port of the Integrity Server
     * @return
     */    
	public int getPort()
	{
		return this.port;
	}
	
	/**
     * Sets the port of the Integrity Server
     * @return
     */  
	public void setPort(int port)
	{
		this.port = port;
	}
	
	/**
     * Returns the User connecting to the Integrity Server
     * @return
     */
	public String getUserName()
	{
		return this.userName;
	}
	
	/**
     * Sets the User connecting to the Integrity Server
     * @return
     */
	public void setUserName(String userName)
	{
		this.userName = userName;
	}
	
	 /**
     * Returns the encrypted password of the user connecting to the Integrity Server
     * @return
     */
	public String getPassword()
	{
		return this.password.getEncryptedValue();
	}
	
	/**
	 * Returns the Secret password in its raw form
	 * @return
	 */
	public Secret getSecretPassword()
	{
		return this.password;
	}
	
	/**
	 * Returns the password (plain text) of the user connecting to the Integrity Server 
	 * @return
	 */
	public String getPasswordInPlainText()
	{
		return password.getPlainText();
	}
	
	/**
     * Sets the encrypted Password of the user connecting to the Integrity Server
     * @param password - The clear password
     */
	public void setPassword(String password)
	{
		this.password = Secret.fromString(password);
	}
	
	/**
     * Returns true/false depending on secure sockets are enabled
     * @return
     */       
	public boolean getSecure()
	{
		return this.secure;
	}
	
	/**
     * Toggles whether or not secure sockets are enabled
     * @return
     */      
	public void setSecure(boolean secure)
	{
		this.secure = secure;
	}
	
	/**
	 * Returns the simple name for this Integrity Configuration
	 * @return
	 */
	public String getName()
	{
		this.name = String.format("%s@%s:%d", userName, hostName, port);
		return this.name;
	}
	
	/**
	 * Overridden equality function to safeguard from duplicate connections
	 */
	@Override
	public boolean equals(Object o)
	{
		if( o instanceof IntegrityConfigurable )
		{
			return ((IntegrityConfigurable)o).getHostName().equals(hostName) &&
					((IntegrityConfigurable)o).getPort() == port && 
					((IntegrityConfigurable)o).getUserName().equals(userName) &&
					((IntegrityConfigurable)o).getPasswordInPlainText().equals(password.getPlainText());
							
		}
		else
		{
			return false;
		}
	}
}
