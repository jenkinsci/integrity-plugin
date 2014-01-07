package hudson.scm;

public interface IntegrityConfigurable {
    /**
     * Returns the Integration Point host name of the API Session
     * @return
     */
	public String getIntegrationPointHost();
	/**
     * Sets the Integration Point host name of the API Session
     * @return
     */
	public void setIntegrationPointHost(String host);
	/**
     * Returns the Integration Point port of the API Session
     * @return
     */    
	public int getIntegrationPointPort();
	/**
     * Sets the Integration Point port of the API Session
     * @return
     */    
	public void setIntegrationPointPort(int port);	
	/**
     * Returns the host name of the Integrity Server
     * @return
     */
	public String getHost();
	/**
     * Sets the host name of the Integrity Server
     * @return
     */
	public void setHost(String host);
	/**
     * Returns the port of the Integrity Server
     * @return
     */    
	public int getPort();
	/**
     * Sets the port of the Integrity Server
     * @return
     */  
	public void setPort(int port);
	/**
     * Returns the User connecting to the Integrity Server
     * @return
     */
	public String getUserName();
	/**
     * Sets the User connecting to the Integrity Server
     * @return
     */
	public void setUserName(String username);
	 /**
     * Returns the encrypted password of the user connecting to the Integrity Server
     * @return
     */
	public String getPassword();
	/**
     * Sets the encrypted Password of the user connecting to the Integrity Server
     * @param password - The clear password
     */
	public void setPassword(String password);
	/**
     * Returns true/false depending on secure sockets are enabled
     * @return
     */       
	public boolean getSecure();
	/**
     * Toggles whether or not secure sockets are enabled
     * @return
     */      
	public void setSecure(boolean secure);
	/**
     * Returns The Integrity Configuration Name
     * @return
     */     
	public String getConfigurationName();
	/**
     * Sets the Integrity Configuration Name
     * @param configurationName
     */
	public void setConfigurationName(String configurationName);
}
