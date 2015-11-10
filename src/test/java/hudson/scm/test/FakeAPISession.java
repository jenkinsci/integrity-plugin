//  
//   $Id: FakeAPISession.java 1.8 2014/09/24 01:08:32IST gisraeli Exp  $
//
//   Copyright 2011 by PTC Inc. All rights reserved.
//
//   This Software is unpublished, valuable, confidential property of
//   PTC Inc.   Any use or disclosure of this Software
//   without the express written permission of PTC Inc.
//   is strictly prohibited.
//

package hudson.scm.test;

import hudson.scm.APISession;
import hudson.scm.ExceptionHandler;
import hudson.scm.IntegrityConfigurable;
import hudson.scm.ISession;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mks.api.CmdRunner;
import com.mks.api.Command;
import com.mks.api.IntegrationPoint;
import com.mks.api.IntegrationPointFactory;
import com.mks.api.Session;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;
import com.mks.api.util.APIVersion;

/**
 * Allows for testing of an APISession
 *
 * @since Red
 * @version $Revision: 1.8 $
 */
public class FakeAPISession implements ISession
{
	// Initialize our logger
	private static final Logger LOGGER = Logger.getLogger("IntegritySCM");
	
	// Store the API Version
	public static final String VERSION = "4.13";
	public static final int MAJOR_VERSION = 4;	
	public static final int MINOR_VERSION = 13;
	
	// Class variables used to create an API Session
	private String ipHostName;
	private int ipPort = 0;
	private String hostName;
	private int port;
	private String userName;
	private String password;
	
	// API Specific Objects
	private FakeIntegrationPoint ip;
	private FakeSession session;
	private FakeCmdRunner icr;
	private boolean terminated;
	private boolean secure;
	
	/**
     * Creates an authenticated API Session against the Integrity Server
     * @return An authenticated API Session
     */
	public static synchronized FakeAPISession create(IntegrityConfigurable settings)
	{
		// Attempt to open a connection to the Integrity Server
    	try
    	{
    		LOGGER.fine("Creating Integrity API Session...");
    		return new FakeAPISession(
    					settings.getIpHostName(),
    					settings.getIpPort(),
    					settings.getHostName(), 
    					settings.getPort(),
    					settings.getUserName(),
    					settings.getPasswordInPlainText(),
    					settings.getSecure()
    				);
    	}
    	catch(APIException aex)
    	{
    		LOGGER.severe("API Exception caught...");
    		ExceptionHandler eh = new ExceptionHandler(aex);
    		LOGGER.severe(eh.getMessage());
    		LOGGER.fine(eh.getCommand() + " returned exit code " + eh.getExitCode());
    		LOGGER.log(Level.SEVERE, "APIException", aex);
    		return null;
    	}				
	}
		
	/**
	 * Constructor for the API Session Object
	 * @throws APIException
	 */
	private FakeAPISession(String ipHost, int ipPortNum, 
					String host, int portNum, String user, String paswd, boolean secure) throws APIException
	{
		
		ipHostName = ipHost;
		ipPort = ipPortNum;
		hostName = host;
		port = portNum;
		userName = user;
		password = paswd;
		this.secure = secure;
		initAPI();
	}
	
	private void initAPI() throws APIException
	{
	 	// Initialize our termination flag...
        terminated = false;        
        ip = new FakeIntegrationPoint();
        session = new FakeSession();
        ip.put(FakeIntegrationPoint.COMMON_SESSION, session);        
        // Test the connection to the Integrity Server
        Command ping = new Command("api", "ping");
        FakeCmdRunner cmdRunner = new FakeCmdRunner();
        cmdRunner.setDefaultHostname(hostName);
        cmdRunner.setDefaultPort(port);
        cmdRunner.setDefaultUsername(userName);
        cmdRunner.setDefaultPassword(password);
        // Execute the connection
        FakeResponse res = new FakeResponse();
        cmdRunner.put("api.ping", res);
        //Response res = cmdRunner.execute(ping);
        LOGGER.fine(ping.getCommandName() + " returned exit code " + res.getExitCode());
        // Initialize class variables
        cmdRunner.release();
        LOGGER.fine("Successfully established connection " + userName + "@" + hostName + ":" + port); 
	}
	
	/**
	 * This function executes a generic API/CLI Command
	 * @param cmd Integrity API Command Object representing a CLI command
	 * @return Integrity API Response Object
	 * @throws APIException
	 */
	public Response runCommand(Command cmd) throws APIException
	{
		FakeCmdRunner cmdRunner = new FakeCmdRunner();
	    cmdRunner.setDefaultHostname(hostName);
	    cmdRunner.setDefaultPort(port);
	    cmdRunner.setDefaultUsername(userName);
	    cmdRunner.setDefaultPassword(password);
        FakeResponse res = new FakeResponse();
        cmdRunner.put(cmd.getCommandName(), res);
	    LOGGER.fine(cmd.getCommandName() + " returned exit code " + res.getExitCode());	    
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
			if( !icr.isFinished() )
			{
				icr.interrupt();
			}
			icr.release();
		}
		
		icr = new FakeCmdRunner();
		icr.setDefaultHostname(hostName);
		icr.setDefaultPort(port);
		icr.setDefaultUsername(userName);
		icr.setDefaultPassword(password);		
	    //Response res = icr.executeWithInterim(cmd, false);
        FakeResponse res = new FakeResponse();
        icr.put(cmd.getCommandName(), res);   
	    LOGGER.fine("Executed " + cmd.getCommandName() + " with interim");
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
		FakeCmdRunner cmdRunner = new FakeCmdRunner();
	    cmdRunner.setDefaultHostname(hostName);
	    cmdRunner.setDefaultPort(port);
	    cmdRunner.setDefaultUsername(userName);
	    cmdRunner.setDefaultPassword(password);
	    cmdRunner.setDefaultImpersonationUser(impersonateUser);
	    //Response res = cmdRunner.execute(cmd);
        FakeResponse res = new FakeResponse();
        icr.put(cmd.getCommandName(), res);      
	    LOGGER.fine(cmd.getCommandName() + " returned exit code " + res.getExitCode());
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
		boolean cmdRunnerKilled = false;
		boolean sessionKilled = false;
		boolean intPointKilled = false;
		
		// Terminate only if not already terminated!
		if( ! terminated )
		{
			try
			{
				if( null != icr )
				{
					if( !icr.isFinished() )
					{
						icr.interrupt();
					}
					
					icr.release();
					cmdRunnerKilled = true;
				}
				else
				{
					cmdRunnerKilled = true;
				}
				
			}
			catch( APIException aex )
			{
			    LOGGER.fine("Caught API Exception when releasing Command Runner!");
			    LOGGER.log(Level.SEVERE, "APIException", aex);
			}
			catch( Exception ex )
			{
				LOGGER.fine("Caught Exception when releasing Command Runner!");
				LOGGER.log(Level.SEVERE, "Exception", ex);						
			}
			
			// Separate try-block to ensure this code is executed even it the previous try-block threw an exception
			try
			{
				if( null != session )
				{
					// force the termination of an running command
					session.release(true);
					sessionKilled = true;
				}
				else
				{
					sessionKilled = true;
				}
	
			}
			catch(APIException aex)
			{
			    LOGGER.fine("Caught API Exception when releasing session!");
			    LOGGER.log(Level.SEVERE, "APIException", aex);
			}
			catch(IOException ioe)
			{
			    LOGGER.fine("Caught IO Exception when releasing session!");
			    LOGGER.log(Level.SEVERE, "IOException", ioe);			
			}
			
			
			if( null != ip )
			{
				ip.release();
				intPointKilled = true;
			}
			else
			{
				intPointKilled = true;
			}
				
			if( cmdRunnerKilled && sessionKilled && intPointKilled )
			{
				terminated = true;
				LOGGER.fine("Successfully disconnected connection " + userName + "@" + hostName + ":" + port);
			}
			else
			{
				LOGGER.warning("Failed to disconnect connection " + userName + "@" + hostName + ":" + port);
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

