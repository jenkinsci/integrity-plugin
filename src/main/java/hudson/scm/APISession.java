package hudson.scm;

import com.mks.api.CmdRunner;
import com.mks.api.Command;
import com.mks.api.IntegrationPoint;
import com.mks.api.IntegrationPointFactory;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;
import com.mks.api.util.Base64;
import com.mks.api.Session;

import java.io.IOException;

/**
 * This class represents an Integration Point to a server.  
 * It also contains a Session object
 */
public class APISession
{
	// Store the API Version
	public static final String VERSION = "4.11";
	public static final int MAJOR_VERSION = 4;	
	public static final int MINOR_VERSION = 11;
	
	// Encrypted password prefix
	public static final String ENC_PREFIX = "__PTC__";
	
	// Class variables used to create an API Session
	private String ipHostName;
	private int ipPort = 0;
	private String hostName;
	private int port;
	private String userName;
	private String password;
	
	// API Specific Objects
	private IntegrationPoint ip;
	private Session session;
	private CmdRunner icr;	
	private boolean terminated;
	private boolean secure;
	
	/**
     * Creates an authenticated API Session against the Integrity Server
     * @return An authenticated API Session
     */
	public static APISession create(IntegrityConfigurable settings)
	{
		// Attempt to open a connection to the Integrity Server
    	try
    	{
    		Logger.debug("Creating Integrity API Session...");
    		return new APISession(
    					settings.getIntegrationPointHost(),
    					settings.getIntegrationPointPort(),
    					settings.getHost(), 
    					settings.getPort(),
    					settings.getUserName(),
    					settings.getPassword(),
    					settings.getSecure()
    				);
    	}
    	catch(APIException aex)
    	{
    		Logger.error("API Exception caught...");
    		ExceptionHandler eh = new ExceptionHandler(aex);
    		Logger.error(eh.getMessage());
    		Logger.debug(eh.getCommand() + " returned exit code " + eh.getExitCode());
    		aex.printStackTrace();
    		return null;
    	}				
	}
	
	/**
	 * Constructor for the API Session Object
	 * @throws APIException
	 */
	private APISession(String ipHost, int ipPortNum, 
					String host, int portNum, String user, String paswd, boolean secure) throws APIException
	{
		
		ipHostName = ipHost;
		ipPort = ipPortNum;
		hostName = host;
		port = portNum;
		userName = user;
		password = Base64.decode(paswd.substring(ENC_PREFIX.length()));
		this.secure = secure;
		initAPI();
	}
	
	
	
	private void initAPI() throws APIException
	{
	 	// Initialize our termination flag...
        terminated = false;
        // Create a Server Integration Point to a client or the target server itself
        if( null != ipHostName && ipHostName.length() > 0 && ipPort > 0 )
        {
            // Connect via the client, using "client as server"
            ip = IntegrationPointFactory.getInstance().createIntegrationPoint(ipHostName, ipPort, secure, MAJOR_VERSION, MINOR_VERSION);
        }
        else
        {
            // Directly to the server...
            ip = IntegrationPointFactory.getInstance().createIntegrationPoint(hostName, port, secure, MAJOR_VERSION, MINOR_VERSION);
        }
        // Create the Session
        session = ip.createSession(userName, password);
        // Test the connection to the Integrity Server
        Command ping = new Command("api", "ping");
        CmdRunner cmdRunner = session.createCmdRunner();
        cmdRunner.setDefaultHostname(hostName);
        cmdRunner.setDefaultPort(port);
        cmdRunner.setDefaultUsername(userName);
        cmdRunner.setDefaultPassword(password);
        // Execute the connection
        Response res = cmdRunner.execute(ping);
        Logger.debug(res.getCommandString() + " returned exit code " + res.getExitCode());
        // Initialize class variables
        cmdRunner.release();
        Logger.debug("Successfully established connection " + userName + "@" + hostName + ":" + port); 
	}
	
	/**
	 * This function executes a generic API/CLI Command
	 * @param cmd Integrity API Command Object representing a CLI command
	 * @return Integrity API Response Object
	 * @throws APIException
	 */
	public Response runCommand(Command cmd) throws APIException
	{
	    CmdRunner cmdRunner = session.createCmdRunner();
	    cmdRunner.setDefaultHostname(hostName);
	    cmdRunner.setDefaultPort(port);
	    cmdRunner.setDefaultUsername(userName);
	    cmdRunner.setDefaultPassword(password);
	    Response res = cmdRunner.execute(cmd);
	    Logger.debug(res.getCommandString() + " returned exit code " + res.getExitCode());	    
	    cmdRunner.release();
	    return res;
	}
	
	/**
	 * This function executes a generic API/CLI Command with interim
	 * @param cmd Integrity API Command Object representing a CLI command
	 * @return Integrity API Response Object
	 * @throws APIException
	 */
	public Response runCommandWithInterim(Command cmd) throws APIException
	{
		// Terminate the previous command runner, if applicable
		if( null != icr )
		{
			icr.interrupt();
			icr.release();
		}
		icr = session.createCmdRunner();
		icr.setDefaultHostname(hostName);
		icr.setDefaultPort(port);
		icr.setDefaultUsername(userName);
		icr.setDefaultPassword(password);
	    Response res = icr.executeWithInterim(cmd, false);
	    Logger.debug("Executed " + res.getCommandString() + " with interim");
	    return res;
	}
	
	/**
	 * This function executes a generic API/CLI Command impersonating another user
	 * @param cmd Integrity API Command Object representing a CLI command
	 * @param impersonateUser The user to impersonate
	 * @return Integrity API Response Object
	 * @throws APIException
	 */
	public Response runCommandAs(Command cmd, String impersonateUser) throws APIException
	{
	    CmdRunner cmdRunner = session.createCmdRunner();
	    cmdRunner.setDefaultHostname(hostName);
	    cmdRunner.setDefaultPort(port);
	    cmdRunner.setDefaultUsername(userName);
	    cmdRunner.setDefaultPassword(password);
	    cmdRunner.setDefaultImpersonationUser(impersonateUser);
	    Response res = cmdRunner.execute(cmd);
	    Logger.debug(res.getCommandString() + " returned exit code " + res.getExitCode());
	    cmdRunner.release();
	    return res;
	}
	
	public void refreshAPISession() throws APIException
	{
	    Terminate();
	    initAPI();
	} 
	
	/**
	 * Terminate the API Session and Integration Point
	 */
	public void Terminate()
	{
		// Terminate only if not already terminated!
		if( ! terminated )
		{
			try
			{
				if( null != icr )
				{
					icr.interrupt();
					icr.release();
				}
				
				if( null != session )
				{
					session.release();
				}
	
				if( null != ip )
				{
					ip.release();
				}
				
				terminated = true;
				Logger.debug("Successfully disconnected connection " + userName + "@" + hostName + ":" + port);
			}
			catch(APIException aex)
			{
			    Logger.debug("Caught API Exception when releasing session!");
			    aex.printStackTrace();
			}
			catch(IOException ioe)
			{
			    Logger.debug("Caught IO Exception when releasing session!");
			    ioe.printStackTrace();			
			}
		}
	}
	
	/**
	 * Returns the Integrity Integration Point Hostname for this APISession
	 * @return
	 */
	public String getIPHostName() 
	{
		return ipHostName;
	}

	/**
	 * Returns the Integrity Integration Point Port for this APISession
	 * @return
	 */
	public String getIPPort()
	{
		return String.valueOf(ipPort);
	}
	
	/**
	 * Returns the Integrity Hostname for this APISession
	 * @return
	 */
	public String getHostName() 
	{
		return hostName;
	}

	/**
	 * Returns the Integrity Port for this APISession
	 * @return
	 */
	public String getPort()
	{
		return String.valueOf(port);
	}
	
	/**
	 * Returns the Integrity User for this APISession
	 * @return
	 */
	public String getUserName()
	{
		return userName;
	}
}
