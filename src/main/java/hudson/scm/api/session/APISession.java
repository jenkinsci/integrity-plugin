/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/
package hudson.scm.api.session;

import com.mks.api.*;
import com.mks.api.fedsso.SSOSession;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;
import com.mks.api.util.APIVersion;
import com.mks.api.fedsso.SSOCmdRunner;

import hudson.scm.AuthenticationType;
import hudson.scm.IntegrityConfigurable;
import hudson.scm.IntegritySCM;
import hudson.scm.OAuth2ClientCredentials;
import hudson.scm.api.ExceptionHandler;
import hudson.security.ACL;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;

/**
 * This class represents an Integration Point to a server. It also contains a
 * Session object
 */
public class APISession implements ISession {
	// Initialize our logger
	private static final Logger LOGGER = Logger.getLogger(IntegritySCM.class.getSimpleName());
	// Store the API Version
	public static final String VERSION = "4.16";
	public static final int MAJOR_VERSION = 4;
	public static final int MINOR_VERSION = 16;
	private static final String RETURNED_EXIT_CODE = " returned exit code ";
	private static final String API_EXCEPTION = "APIException";
	public static final String PLUGIN_VERSION_PREFIX = "Jenkin_Plugin_";
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
	private boolean isLocalIntegration;
	private static Session localSession;
	private static IntegrationPoint localip;
	// For SSO
	private AuthenticationType authType;
	//private String accessToken;
	private IntegrityConfigurable settings;
	private boolean usingSSOSession; // to track if SSO session is being used (createNamedSession vs createSession)
	private SSOSession ssoSession;
	// For SSO Token Lifecycle Management
	private long ssoTokenCreationTime = 0;
	private long ssoTokenExpirationTime = 0;
	private static final long TOKEN_EXPIRATION_TTL = 3600000; // 1 hour (3600000 milliseconds)
	private static final long TOKEN_REFRESH_BUFFER = 60000; // Refresh 1 minute (60000 milliseconds) before expiry

	/**
	 * Creates an authenticated API Session against the Integrity Server
	 *
	 * @return An authenticated API Session
	 */
	public static synchronized ISession create(IntegrityConfigurable settings) {
		// Attempt to open a connection to the Integrity Server
		try {
			return createOrThrow(settings);
		} catch (APIException aex) {
			ExceptionHandler eh = new ExceptionHandler(aex);
			LOGGER.severe(eh.getMessage());
			LOGGER.fine(eh.getCommand() + RETURNED_EXIT_CODE + eh.getExitCode());
			LOGGER.log(Level.SEVERE, API_EXCEPTION, aex);
			return null;
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Failed to create API session: " + e.getMessage(), e);
			return null;
		}
	}
	public static synchronized ISession createOrThrow(IntegrityConfigurable settings) throws Exception {
			AuthenticationType authType = settings.getAuthType();
			if (authType == AuthenticationType.OAUTH) {
					LOGGER.fine("Using OAuth authentication for user: " + settings.getUserName());
			} else {
				LOGGER.fine("Creating PTC RV&S API Session for :" + settings.getUserName());
			}
			//Log the username being used for debugging purposes
			LOGGER.fine("APISession: creating session with username: " + settings.getUserName());
		try {
			return new APISession(settings.getIpHostName(), settings.getIpPort(), settings.getHostName(),
					settings.getPort(), settings.getUserName(), settings.getPasswordInPlainText(), settings.getSecure(),
					false, settings);
		} catch (Exception ex) {
			// Log full exception chain at SEVERE so it always appears in Jenkins logs
			StringBuilder chain = new StringBuilder("APISession creation error: ");
			Throwable t = ex;
			int depth = 0;
			while (t != null && depth < 10) {
				String msg = t.getMessage();
				chain.append("[").append(t.getClass().getSimpleName()).append("] ")
				     .append(msg != null ? msg : "(no message)");
				t = (t.getCause() != t) ? t.getCause() : null;
				if (t != null) chain.append(" -> ");
				depth++;
			}
			LOGGER.log(Level.SEVERE, chain.toString(), ex);
			throw ex;
		}
	}

	/**
	 * Creates a local integration point
	 *
	 * @param settings
	 * @return
	 */
	public static synchronized ISession createLocalIntegrationPoint(IntegrityConfigurable settings) {
		try {
			LOGGER.fine("Creating PTC RV&S API Session for :" + settings.getUserName());
			return new APISession(settings.getIpHostName(), settings.getIpPort(), settings.getHostName(),
					settings.getPort(), settings.getUserName(), settings.getPasswordInPlainText(), settings.getSecure(),
					true, settings);
		} catch (APIException aex) {
			ExceptionHandler eh = new ExceptionHandler(aex);
			LOGGER.severe(eh.getMessage());
			LOGGER.fine(eh.getCommand() + RETURNED_EXIT_CODE + eh.getExitCode());
			LOGGER.log(Level.SEVERE, API_EXCEPTION, aex);
			return null;
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Failed to create Local API session: " + e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Constructor for the API Session Object
	 *
	 * @throws APIException
	 */
	private APISession(String ipHost, int ipPortNum, String host, int portNum, String user, String paswd,
			boolean secure, boolean isLocalIntegration, IntegrityConfigurable settings) throws APIException, Exception {
		this.settings = settings;
		this.authType = settings.getAuthType();
		ipHostName = ipHost;
		ipPort = ipPortNum;
		hostName = host;
		port = portNum;
		userName = user;
		password = paswd;
		this.secure = secure;
		this.isLocalIntegration = isLocalIntegration;
		if (isLocalIntegration) {
			initLocalAPI();
		} else
			initAPI();
	}

	private void initAPI() throws APIException {
		// Initialize our termination flag...
		terminated = false;
		// Create a Server Integration Point to a client or the target server itself
		if (null != ipHostName && ipHostName.length() > 0 && ipPort > 0) {
			// Connect via the client, using "client as server"
			ip = IntegrationPointFactory.getInstance().createIntegrationPoint(ipHostName, ipPort, secure, MAJOR_VERSION,
					MINOR_VERSION);
		}else if(authType == AuthenticationType.OAUTH) {
			// Directly to the server...
			ip = IntegrationPointFactory.getInstance().createIntegrationPoint(hostName, port, true, MAJOR_VERSION,
					MINOR_VERSION);	
		}else {
			// Directly to the server...
			ip = IntegrationPointFactory.getInstance().createIntegrationPoint(hostName, port, secure, MAJOR_VERSION,
					MINOR_VERSION);
		}
		// Create the Session
		String implementationVersion = getClass().getPackage().getImplementationVersion();
		boolean useSSOSession = false;
		if (authType == AuthenticationType.OAUTH) {
			// When authType is OAUTH, we should always use SSO session
			// Check if server has SSO enabled, but don't fall back to basic auth if it doesn't
			try {
				if (ip.isServerSSOEnabled()) {
					useSSOSession = true;	
					LOGGER.fine("Server SSO is enabled. Creating OAuth session for user: " + userName);
				} else {
					LOGGER.fine("Server SSO check returned false, but proceeding with OAuth session creation since authType is OAUTH");
					useSSOSession = true;
				}
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, "Exception checking if server SSO is enabled: " + e.getMessage(), e);
				LOGGER.fine("Proceeding with OAuth session creation for user: " + userName);
				useSSOSession = true;
			}
		}
		if (useSSOSession) {
			try {
				OAuth2ClientCredentials oauthCred = findOAuthCredentialById(settings.getSsoCredentialId());
				if (oauthCred == null) {
					throw new APIException("No OAuth2ClientCredentials found for ID: " + settings.getSsoCredentialId());
				}
				String ClientId = oauthCred.getClientId();
				String ClientSecret = oauthCred.getClientSecret().getPlainText();
				String TokenEndpoint = oauthCred.getTokenEndpoint();
				String Scope = oauthCred.getOAuthScope();
				LOGGER.fine("Creating SSO session for user: " + userName);
				// Create SSO session
				ssoSession = ip.createNamedSSOSession(new APIVersion(5,1), PLUGIN_VERSION_PREFIX + implementationVersion, ClientId,
						ClientSecret, Scope, TokenEndpoint);
				usingSSOSession = true;
				
				// Track token creation and expiration times for refresh management
				ssoTokenCreationTime = System.currentTimeMillis();
				ssoTokenExpirationTime = ssoTokenCreationTime + TOKEN_EXPIRATION_TTL;
				LOGGER.log(Level.FINE, "Successfully created SSO session for user: " + userName + 
						". Token will expire at: " + new java.util.Date(ssoTokenExpirationTime));
			} catch (APIException aex) {
				LOGGER.log(Level.SEVERE, "Failed to create SSO session for user: " + userName, aex);
				throw aex;
			}
		} else {
			LOGGER.log(Level.FINE, "APISession: checking if username is coming or not and which session it uses in case of non sso: " + userName);
			session = ip.createNamedSession(PLUGIN_VERSION_PREFIX + implementationVersion, null, userName, password);
			session.setTimeout(300000); // 15 Minutes
			session.setAutoReconnect(true);
		}
	}

	public OAuth2ClientCredentials findOAuthCredentialById(String credentialsId) {
		return CredentialsMatchers.firstOrNull(
				CredentialsProvider.lookupCredentialsInItemGroup(OAuth2ClientCredentials.class, // Ask for the specific
																								// class directly
						Jenkins.get(), ACL.SYSTEM2, Collections.emptyList()),
				CredentialsMatchers.withId(credentialsId));
	}

	/**
	 * Validates the SSO token and refreshes it if it's about to expire
	 * @throws APIException if token refresh fails
	 */
	private void validateAndRefreshSSOTokenIfNeeded() throws APIException {
		if (!usingSSOSession || ssoSession == null) {
			return; // Not using SSO, nothing to do
		}
		
		long currentTime = System.currentTimeMillis();
		long timeUntilExpiry = ssoTokenExpirationTime - currentTime;
		
		// If token will expire within buffer (1 minute), refresh it now
		if (timeUntilExpiry <= TOKEN_REFRESH_BUFFER) {
			LOGGER.log(Level.WARNING, 
					"SSO token for user: " + userName + " will expire in " + 
					(timeUntilExpiry / 1000) + " seconds. Refreshing token now...");
			
			try {
				refreshSSOToken();
			} catch (APIException aex) {
				LOGGER.log(Level.SEVERE, 
						"Failed to refresh SSO token for user: " + userName + 
						". Command execution may fail. Error: " + aex.getMessage(), aex);
				// Don't throw - let the command execute and fail if necessary
				// This allows the error handler to catch and retry
			}
		}
	}

	/**
	 * Refreshes the SSO token by creating a new SSO session
	 * @throws APIException if token refresh fails
	 */
	private void refreshSSOToken() throws APIException {
		LOGGER.log(Level.FINE, "Attempting to refresh SSO token for user: " + userName);
		
		try {
			// Get OAuth credentials
			OAuth2ClientCredentials oauthCred = findOAuthCredentialById(settings.getSsoCredentialId());
			if (oauthCred == null) {
				throw new APIException("No OAuth2ClientCredentials found for ID: " + 
									 settings.getSsoCredentialId());
			}
			
			String clientId = oauthCred.getClientId();
			String clientSecret = oauthCred.getClientSecret().getPlainText();
			String tokenEndpoint = oauthCred.getTokenEndpoint();
			String scope = oauthCred.getOAuthScope();
			String implementationVersion = getClass().getPackage().getImplementationVersion();
			
			// Release old SSO session
			if (ssoSession != null) {
				try {
					// SSOSession will be recreated with a fresh token, so we don't need to explicitly release
					LOGGER.log(Level.FINE, "Preparing to replace old SSO session with new one");
				} catch (Exception e) {
					LOGGER.log(Level.FINE, "Exception while preparing SSO session: " + e.getMessage());
				}
			}
			
			// Create new SSO session with fresh token
			ssoSession = ip.createNamedSSOSession(new APIVersion(5,1), PLUGIN_VERSION_PREFIX + implementationVersion, 
													clientId, clientSecret, scope, tokenEndpoint);
			
			// Update token expiration time
			ssoTokenCreationTime = System.currentTimeMillis();
			ssoTokenExpirationTime = ssoTokenCreationTime + TOKEN_EXPIRATION_TTL;
			
			LOGGER.log(Level.FINE, "Successfully refreshed SSO token for user: " + userName + 
									   ". New token expires at: " + new java.util.Date(ssoTokenExpirationTime));
			
		} catch (APIException aex) {
			LOGGER.log(Level.SEVERE, 
					"Failed to refresh SSO token for user: " + userName + 
					". Error: " + aex.getMessage(), aex);
			throw aex;
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, 
					"Unexpected error refreshing SSO token for user: " + userName + 
					". Error: " + e.getMessage(), e);
			throw new APIException("Failed to refresh SSO token: " + e.getMessage());
		}
	}

	/**
	 * Initialize the Local integration point
	 *
	 * @throws APIException
	 */
	private static void initLocalAPI() throws APIException {
		// Initialize our termination flag...
		if (localSession == null) {
			if (localip == null) {
				localip = IntegrationPointFactory.getInstance().createLocalIntegrationPoint(MAJOR_VERSION,
						MINOR_VERSION);
				localip.setAutoStartIntegrityClient(true);
			}

			LOGGER.log(Level.FINEST, "[Local Client] Initializing Local Client session");
			localSession = localip.createSession();
			localSession.setAutoReconnect(true);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.scm.api.session.ISession#ping()
	 */
	@Override
	public void ping() throws APIException {
		// Test the connection to the Integrity Server
		LOGGER.log(Level.FINE, "Pinging server :" + userName + "@" + hostName + ":" + port);
		Command ping = new Command("api", "ping");
		CmdRunner cmdRunner;
		SSOCmdRunner ssoCmdRunner;
		if (AuthenticationType.OAUTH == authType && !isLocalIntegration && usingSSOSession) {
			// Validate and refresh SSO token if needed before executing command
			validateAndRefreshSSOTokenIfNeeded();
			ssoCmdRunner = ssoSession.createCmdRunner();
			// Execute the connection
			Response res = ssoCmdRunner.execute(ping);
			LOGGER.log(Level.FINEST, res.getCommandString() + RETURNED_EXIT_CODE + res.getExitCode());
			// Initialize class variables
			ssoCmdRunner.release();
			LOGGER.log(Level.FINE, "Successfully pinged SSO connection " + userName + "@" + hostName + ":" + port);
			return;
		}else if (isLocalIntegration) {
			cmdRunner = localSession.createCmdRunner();
			cmdRunner.setDefaultHostname(hostName);
	        cmdRunner.setDefaultPort(port);
	        cmdRunner.setDefaultUsername(userName);
	        cmdRunner.setDefaultPassword(password);
	        Response res = cmdRunner.execute(ping);
	        LOGGER.log(Level.FINEST, res.getCommandString() + RETURNED_EXIT_CODE + res.getExitCode());
	        cmdRunner.release();
	        LOGGER.log(Level.FINE, "Successfully pinged local connection " + userName + "@" + hostName + ":" + port);
	        return;
		}else if (session != null) {
		cmdRunner = session.createCmdRunner();
		cmdRunner.setDefaultHostname(hostName);
		cmdRunner.setDefaultPort(port);
		cmdRunner.setDefaultUsername(userName);
		cmdRunner.setDefaultPassword(password);
		// Execute the connection
		Response res = cmdRunner.execute(ping);
		LOGGER.log(Level.FINEST, res.getCommandString() + RETURNED_EXIT_CODE + res.getExitCode());
		// Initialize class variables
		cmdRunner.release();
		LOGGER.log(Level.FINE, "Successfully pinged connection " + userName + "@" + hostName + ":" + port);
		}
		else
		{
			throw new APIException("No valid session to ping for user: " + userName);
		}
	}

	/**
	 * This function executes a generic API/CLI Command
	 *
	 * @param cmd Integrity API Command Object representing a CLI command
	 * @return Integrity API Response Object
	 * @throws APIException
	 */
	public Response runCommand(Command cmd) throws APIException {
	    CmdRunner cmdRunner;
	    SSOCmdRunner ssoCmdRunner;
	    if (AuthenticationType.OAUTH == authType && !isLocalIntegration && usingSSOSession) {
	        // Validate and refresh SSO token if needed before executing command
	        validateAndRefreshSSOTokenIfNeeded();
	        ssoCmdRunner = ssoSession.createCmdRunner();
	        Response res = ssoCmdRunner.execute(cmd);
	        LOGGER.log(Level.FINEST, res.getCommandString() + RETURNED_EXIT_CODE + res.getExitCode());
	        ssoCmdRunner.release();
	        LOGGER.log(Level.FINE, "Successfully executed SSO command " + cmd.getCommandName());
	        return res;
	    } else if (isLocalIntegration) {
	        cmdRunner = localSession.createCmdRunner();
	        cmdRunner.setDefaultHostname(hostName);
	        cmdRunner.setDefaultPort(port);
	        cmdRunner.setDefaultUsername(userName);
	        cmdRunner.setDefaultPassword(password);
	        Response res = cmdRunner.execute(cmd);
	        LOGGER.log(Level.FINEST, res.getCommandString() + RETURNED_EXIT_CODE + res.getExitCode());
	        cmdRunner.release();
	        LOGGER.log(Level.FINE, "Successfully executed local command " + cmd.getCommandName());
	        return res;
	    } else if (session != null) {
	        cmdRunner = session.createCmdRunner();
	        cmdRunner.setDefaultHostname(hostName);
	        cmdRunner.setDefaultPort(port);
	        cmdRunner.setDefaultUsername(userName);
	        cmdRunner.setDefaultPassword(password);
	        Response res = cmdRunner.execute(cmd);
	        LOGGER.log(Level.FINEST, res.getCommandString() + RETURNED_EXIT_CODE + res.getExitCode());
	        cmdRunner.release();
	        LOGGER.log(Level.FINE, "Successfully executed command " + cmd.getCommandName());
	        return res;
	    } else {
	        throw new APIException("No valid session to run command for user: " + userName);
	    }
	}

	/**
	 * This function executes a generic API/CLI Command with interim
	 *
	 * @param cmd Integrity API Command Object representing a CLI command
	 * @return Integrity API Response Object
	 * @throws APIException
	 */
	public Response runCommandWithInterim(Command cmd) throws APIException {
	    if (null != icr) {
	        if (!icr.isFinished()) {
	            icr.interrupt();
	        }
	        icr.release();
	    }
	    SSOCmdRunner ssoCmdRunner;
	    if (AuthenticationType.OAUTH == authType && !isLocalIntegration && usingSSOSession) {
	        // Validate and refresh SSO token if needed before executing command
	        validateAndRefreshSSOTokenIfNeeded();
	        // Store the SSO cmd runner for later cleanup (don't release immediately to avoid CommandAlreadyRunningException)
	        ssoCmdRunner = ssoSession.createCmdRunner();
	        Response res = ssoCmdRunner.executeWithInterim(cmd, false);
	        LOGGER.log(Level.FINE, "Executed " + res.getCommandString() + " with interim (SSO)");
	        // Note: The runner is released in the cleanup path at the beginning of the next call or in the cleanup/terminate method
	        return res;
	    } else if (isLocalIntegration) {
	        icr = localSession.createCmdRunner();
	        icr.setDefaultHostname(hostName);
	        icr.setDefaultPort(port);
	        icr.setDefaultUsername(userName);
	        icr.setDefaultPassword(password);
	        Response res = icr.executeWithInterim(cmd, false);
	        LOGGER.log(Level.FINE, "Executed " + res.getCommandString() + " with interim (local)");
	        return res;
	    } else if (session != null) {
	        icr = session.createCmdRunner();
	        icr.setDefaultHostname(hostName);
	        icr.setDefaultPort(port);
	        icr.setDefaultUsername(userName);
	        icr.setDefaultPassword(password);
	        Response res = icr.executeWithInterim(cmd, false);
	        LOGGER.log(Level.FINE, "Executed " + res.getCommandString() + " with interim");
	        return res;
	    } else {
	        throw new APIException("No valid session to run command with interim for user: " + userName);
	    }
	}

	/**
	 * This function executes a generic API/CLI Command impersonating another user
	 *
	 * @param cmd             Integrity API Command Object representing a CLI
	 *                        command
	 * @param impersonateUser The user to impersonate
	 * @return Integrity API Response Object
	 * @throws APIException
	 */
	public Response runCommandAs(Command cmd, String impersonateUser) throws APIException {
	    CmdRunner cmdRunner;
	    SSOCmdRunner ssoCmdRunner;
	    if (AuthenticationType.OAUTH == authType && !isLocalIntegration && usingSSOSession) {
	        // Validate and refresh SSO token if needed before executing command
	        validateAndRefreshSSOTokenIfNeeded();
	        ssoCmdRunner = ssoSession.createCmdRunner();
	        // SSO may not support impersonation, but if it does:
	        // ssoCmdRunner.setDefaultImpersonationUser(impersonateUser);
	        Response res = ssoCmdRunner.execute(cmd);
	        LOGGER.log(Level.FINEST, res.getCommandString() + RETURNED_EXIT_CODE + res.getExitCode());
	        ssoCmdRunner.release();
	        LOGGER.log(Level.FINE, "Successfully executed SSO command as " + impersonateUser);
	        return res;
	    } else if (isLocalIntegration) {
	        cmdRunner = localSession.createCmdRunner();
	        cmdRunner.setDefaultHostname(hostName);
	        cmdRunner.setDefaultPort(port);
	        cmdRunner.setDefaultUsername(userName);
	        cmdRunner.setDefaultPassword(password);
	        cmdRunner.setDefaultImpersonationUser(impersonateUser);
	        Response res = cmdRunner.execute(cmd);
	        LOGGER.log(Level.FINEST, res.getCommandString() + RETURNED_EXIT_CODE + res.getExitCode());
	        cmdRunner.release();
	        LOGGER.log(Level.FINE, "Successfully executed local command as " + impersonateUser);
	        return res;
	    } else if (session != null) {
	        cmdRunner = session.createCmdRunner();
	        cmdRunner.setDefaultHostname(hostName);
	        cmdRunner.setDefaultPort(port);
	        cmdRunner.setDefaultUsername(userName);
	        cmdRunner.setDefaultPassword(password);
	        cmdRunner.setDefaultImpersonationUser(impersonateUser);
	        Response res = cmdRunner.execute(cmd);
	        LOGGER.log(Level.FINEST, res.getCommandString() + RETURNED_EXIT_CODE + res.getExitCode());
	        cmdRunner.release();
	        LOGGER.log(Level.FINE, "Successfully executed command as " + impersonateUser);
	        return res;
	    } else {
	        throw new APIException("No valid session to run command as user: " + impersonateUser);
	    }
	}

	@Override
	public void refreshAPISession() throws APIException {
		terminate();
		initAPI();
		ping();
	}

	/**
	 * Terminate the API Session and Integration Point
	 */
	@Override
	public boolean terminate() {
		boolean cmdRunnerKilled = false;
		boolean sessionKilled = false;
		// Terminate only if not already terminated!
		if (!terminated) {
			try {
				if (null != icr) {
					if (!icr.isFinished()) {
						icr.interrupt();
					}
					icr.release();
					cmdRunnerKilled = true;
				} else {
					cmdRunnerKilled = true;
				}
			} catch (APIException aex) {
				LOGGER.fine("Caught API Exception when releasing Command Runner!");
				LOGGER.log(Level.SEVERE, API_EXCEPTION, aex);
			} catch (Exception ex) {
				LOGGER.fine("Caught Exception when releasing Command Runner!");
				LOGGER.log(Level.SEVERE, "Exception", ex);
			}
			// Separate try-block to ensure this code is executed even it the previous
			// try-block threw an
			// exception
			try {
				if (null != session) {
					// disconnect any users explicitly
					Command cmd = new Command(Command.IM, "disconnect");
					if (AuthenticationType.OAUTH == authType && !isLocalIntegration && usingSSOSession) {
						SSOCmdRunner ssoCmdRunner = ssoSession.createCmdRunner();
						Response res = ssoCmdRunner.execute(cmd);
						if (res.getExitCode() == 0) {
							LOGGER.fine("Disconnected user " + userName + " from server " + hostName + ":" + port);

						}
						ssoCmdRunner.release();
					} else {
						CmdRunner cmdRunner = session.createCmdRunner();
						cmdRunner.setDefaultHostname(hostName);
						cmdRunner.setDefaultPort(port);
						cmdRunner.setDefaultUsername(userName);
						cmdRunner.setDefaultPassword(password);
						Response res = cmdRunner.execute(cmd);
						if (res.getExitCode() == 0) {
							LOGGER.fine("Disconnected user " + userName + " from server " + hostName + ":" + port);
						}
						cmdRunner.release();
					}

					// force the termination of an running command
					session.release(false);
					sessionKilled = true;
				} else {
					sessionKilled = true;
				}
			} catch (APIException aex) {
				LOGGER.fine("Caught API Exception when releasing session!");
				LOGGER.log(Level.SEVERE, API_EXCEPTION, aex);
			} catch (IOException ioe) {
				LOGGER.fine("Caught IO Exception when releasing session!");
				LOGGER.log(Level.SEVERE, "IOException", ioe);
			}

			if (null != ip) {
				ip.release();
				IntegrationPointFactory.getInstance().removeIntegrationPoint(ip);
			}

			if (cmdRunnerKilled && sessionKilled) {
				terminated = true;
				LOGGER.fine("Successfully disconnected connection " + userName + "@" + hostName + ":" + port);
			} else {
				LOGGER.warning("Failed to disconnect connection " + userName + "@" + hostName + ":" + port);
			}
		}
		return terminated;
	}

	/**
	 * Returns the Integrity User for this APISession
	 *
	 * @return
	 */
	@Override
	public String getUserName() {
		return userName;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("\n Session Host :" + this.hostName + "  ");
		builder.append("Session Port :" + this.port + " ");
		builder.append("Session User :" + this.userName + " ");
		return builder.toString();
	}

	@Override
	public void close() {
		// do nothing. This is used for LC session termination.
	}

	@Override
	public void checkifAlive() throws APIException {
		try {
			this.ping();
		} catch (Exception e) {
			LOGGER.warning("[LocalClient] Exception while pinging session :" + e.getMessage());
			initLocalAPI();
		}
	}
}
