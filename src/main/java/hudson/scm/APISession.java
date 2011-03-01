package hudson.scm;

import com.mks.api.CmdRunner;
import com.mks.api.Command;
import com.mks.api.IntegrationPoint;
import com.mks.api.IntegrationPointFactory;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;
import com.mks.api.Session;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This class represents the Integration Point to a server.  
 * It also contains a Session object
 */
public class APISession
{
	// Store the API Version
	public static final String VERSION = IntegrationPointFactory.getAPIVersion().substring(0, 
											IntegrationPointFactory.getAPIVersion().indexOf(' '));
	public static final int MAJOR_VERSION = Integer.parseInt(VERSION.substring(0, VERSION.indexOf('.')));	
	public static final int MINOR_VERSION = Integer.parseInt(VERSION.substring(VERSION.indexOf('.')+1, VERSION.length()));
	
	// Logs all API work...
	private final Log logger = LogFactory.getLog(getClass());
	
	// Class variables used to create an API Session
	private String hostName;
	private int port;
	private String userName;
	private String password;
	
	// API Specific Objects
	private IntegrationPoint ip;
	private Session session;
	private boolean terminated;
	
	/**
	 * Constructor for the API Session Object
	 * @throws APIException
	 */
	public APISession(String host, int portNum, String user, String paswd, boolean secure) throws APIException
	{
		// Initialize our termination flag...
		terminated = false;
		// Create a Server Integration Point
		ip = IntegrationPointFactory.getInstance().createIntegrationPoint(host, portNum, secure, MAJOR_VERSION, MINOR_VERSION);
		// Create the Session
		session = ip.createSession(user, paswd);
		// Test the connection to the MKS Integrity Server
		Command ping = new Command("api", "ping");
	    CmdRunner cmdRunner = session.createCmdRunner();
	    cmdRunner.setDefaultHostname(host);
	    cmdRunner.setDefaultPort(portNum);
	    cmdRunner.setDefaultUsername(user);
	    cmdRunner.setDefaultPassword(paswd);
	    // Execute the connection
		Response res = runCommand(ping);
		logger.info(res.getCommandString() + " returned exit code " + res.getExitCode());
		// Initialize class variables
		hostName = host;
		port = portNum;
		userName = user;
		password = paswd;
		cmdRunner.release();
		logger.info("Successfully established connection " + userName + "@" + hostName + ":" + port);
	}
	
	/**
	 * This function executes a generic API/CLI Command
	 * @param cmd MKS API Command Object representing a CLI command
	 * @return MKS API Response Object
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
	    logger.debug(res.getCommandString() + " returned exit code " + res.getExitCode());	    
	    cmdRunner.release();
	    return res;
	}

	/**
	 * This function executes a generic API/CLI Command impersonating another user
	 * @param cmd MKS API Command Object representing a CLI command
	 * @param impersonateUser The user to impersonate
	 * @return MKS API Response Object
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
	    logger.debug(res.getCommandString() + " returned exit code " + res.getExitCode());
	    cmdRunner.release();
	    return res;
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
				if( null != session )
				{
					session.release();
				}
	
				if( null != ip )
				{
					ip.release();
				}
				terminated = true;
				logger.info("Successfully disconnected connection " + userName + "@" + hostName + ":" + port);
			}
			catch(APIException aex)
			{
			    logger.debug("Caught API Exception when releasing session!");
			    aex.printStackTrace();
			}
			catch(IOException ioe)
			{
			    logger.debug("Caught IO Exception when releasing session!");
			    ioe.printStackTrace();			
			}
		}
	}
	
	/**
	 * Returns the MKS Integrity Hostname for this APISession
	 * @return
	 */
	public String getHostName() 
	{
		return hostName;
	}

	/**
	 * Returns the MKS Integrity Port for this APISession
	 * @return
	 */
	public String getPort()
	{
		return String.valueOf(port);
	}
	
	/**
	 * Returns the MKS Integrity User for this APISession
	 * @return
	 */
	public String getUserName()
	{
		return userName;
	}
}
