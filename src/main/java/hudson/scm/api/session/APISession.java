/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/
package hudson.scm.api.session;

import com.mks.api.*;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;
import hudson.scm.IntegrityConfigurable;
import hudson.scm.IntegritySCM;
import hudson.scm.api.ExceptionHandler;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class represents an Integration Point to a server. It also contains a Session object
 */
public class APISession implements ISession
{
  // Initialize our logger
  private static final Logger LOGGER = Logger
                  .getLogger(IntegritySCM.class.getSimpleName());
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

  /**
   * Creates an authenticated API Session against the Integrity Server
   *
   * @return An authenticated API Session
   */
  public static synchronized ISession create(IntegrityConfigurable settings)
  {
    // Attempt to open a connection to the Integrity Server
    try {
      LOGGER.fine(
                      "Creating Integrity API Session for :" +
                                      settings.getUserName() +
                                      settings.getSecure());
      return new APISession(settings.getIpHostName(), settings.getIpPort(),
                      settings.getHostName(),
                      settings.getPort(), settings.getUserName(),
                      settings.getPasswordInPlainText(),
                      settings.getSecure(), false);
    } catch (APIException aex) {
      ExceptionHandler eh = new ExceptionHandler(aex);
      LOGGER.severe(eh.getMessage());
      LOGGER.fine(eh.getCommand() + RETURNED_EXIT_CODE + eh.getExitCode());
      LOGGER.log(Level.SEVERE, API_EXCEPTION, aex);
      return null;
    }
  }

  /**
   * Creates a local integration point
   *
   * @param settings
   * @return
   */
  public static synchronized ISession createLocalIntegrationPoint(
                  IntegrityConfigurable settings)
  {
    try {
      LOGGER.fine(
                      "Creating Integrity API Session for :" +
                                      settings.getUserName() +
                                      settings.getSecure());
      return new APISession(settings.getIpHostName(), settings.getIpPort(),
                      settings.getHostName(),
                      settings.getPort(), settings.getUserName(),
                      settings.getPasswordInPlainText(),
                      settings.getSecure(), true);
    } catch (APIException aex) {
      ExceptionHandler eh = new ExceptionHandler(aex);
      LOGGER.severe(eh.getMessage());
      LOGGER.fine(eh.getCommand() + RETURNED_EXIT_CODE + eh.getExitCode());
      LOGGER.log(Level.SEVERE, API_EXCEPTION, aex);
      return null;
    }
  }

  /**
   * Constructor for the API Session Object
   *
   * @throws APIException
   */
  private APISession(String ipHost, int ipPortNum, String host, int portNum,
                  String user,
                  String paswd, boolean secure, boolean isLocalIntegration)
                  throws APIException
  {
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
    }else
      initAPI();
  }

  private void initAPI() throws APIException
  {
    // Initialize our termination flag...
    terminated = false;
    // Create a Server Integration Point to a client or the target server itself
    if (null != ipHostName && ipHostName.length() > 0 && ipPort > 0) {
      // Connect via the client, using "client as server"
      ip = IntegrationPointFactory.getInstance()
                      .createIntegrationPoint(ipHostName, ipPort, secure,
                                      MAJOR_VERSION, MINOR_VERSION);
    } else {
      // Directly to the server...
      ip = IntegrationPointFactory.getInstance()
                      .createIntegrationPoint(hostName, port, secure,
                                      MAJOR_VERSION, MINOR_VERSION);
    }
    // Create the Session
    String implementationVersion = getClass().getPackage().getImplementationVersion();
    session = ip.createNamedSession(PLUGIN_VERSION_PREFIX + implementationVersion, null, userName, password);
    session.setTimeout(300000); // 15 Minutes
    session.setAutoReconnect(true);
  }

  /**
   * Initialize the Local integration point
   *
   * @throws APIException
   */
  private static void initLocalAPI() throws APIException
  {
    // Initialize our termination flag...
    if(localSession == null) {
      if (localip == null) {
        localip = IntegrationPointFactory.getInstance()
                        .createLocalIntegrationPoint(MAJOR_VERSION,
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
  public void ping() throws APIException
  {
    // Test the connection to the Integrity Server
    LOGGER.log(Level.FINE,
                    "Pinging server :" + userName + "@" + hostName + ":" +
                                    port);
    Command ping = new Command("api", "ping");
    CmdRunner cmdRunner;
    if(isLocalIntegration)
      cmdRunner = localSession.createCmdRunner();
    else
      cmdRunner = session.createCmdRunner();
    cmdRunner.setDefaultHostname(hostName);
    cmdRunner.setDefaultPort(port);
    cmdRunner.setDefaultUsername(userName);
    cmdRunner.setDefaultPassword(password);
    // Execute the connection
    Response res = cmdRunner.execute(ping);
    LOGGER.log(Level.FINEST, res.getCommandString() + RETURNED_EXIT_CODE +
                    res.getExitCode());
    // Initialize class variables
    cmdRunner.release();
    LOGGER.log(Level.FINE,
                    "Successfully pinged connection " + userName + "@" +
                                    hostName + ":" + port);
  }

  /**
   * This function executes a generic API/CLI Command
   *
   * @param cmd Integrity API Command Object representing a CLI command
   * @return Integrity API Response Object
   * @throws APIException
   */
  public Response runCommand(Command cmd) throws APIException
  {
    CmdRunner cmdRunner;
    if(isLocalIntegration)
      cmdRunner = localSession.createCmdRunner();
    else
      cmdRunner = session.createCmdRunner();
    cmdRunner.setDefaultHostname(hostName);
    cmdRunner.setDefaultPort(port);
    cmdRunner.setDefaultUsername(userName);
    cmdRunner.setDefaultPassword(password);
    Response res = cmdRunner.execute(cmd);
    LOGGER.fine(res.getCommandString() + RETURNED_EXIT_CODE +
                    res.getExitCode());
    cmdRunner.release();
    return res;
  }

  /**
   * This function executes a generic API/CLI Command with interim
   *
   * @param cmd Integrity API Command Object representing a CLI command
   * @return Integrity API Response Object
   * @throws APIException
   */
  public Response runCommandWithInterim(Command cmd) throws APIException
  {
    // Terminate the previous command runner, if applicable
    if (null != icr) {
      if (!icr.isFinished()) {
        icr.interrupt();
      }
      icr.release();
    }
    if(isLocalIntegration)
      icr = localSession.createCmdRunner();
    else
      icr = session.createCmdRunner();
    icr.setDefaultHostname(hostName);
    icr.setDefaultPort(port);
    icr.setDefaultUsername(userName);
    icr.setDefaultPassword(password);
    Response res = icr.executeWithInterim(cmd, false);
    LOGGER.fine("Executed " + res.getCommandString() + " with interim");
    return res;
  }

  /**
   * This function executes a generic API/CLI Command impersonating another user
   *
   * @param cmd             Integrity API Command Object representing a CLI command
   * @param impersonateUser The user to impersonate
   * @return Integrity API Response Object
   * @throws APIException
   */
  public Response runCommandAs(Command cmd, String impersonateUser)
                  throws APIException
  {
    CmdRunner cmdRunner;
    if(isLocalIntegration)
      cmdRunner = localSession.createCmdRunner();
    else
      cmdRunner = session.createCmdRunner();
    cmdRunner.setDefaultHostname(hostName);
    cmdRunner.setDefaultPort(port);
    cmdRunner.setDefaultUsername(userName);
    cmdRunner.setDefaultPassword(password);
    cmdRunner.setDefaultImpersonationUser(impersonateUser);
    Response res = cmdRunner.execute(cmd);
    LOGGER.fine(res.getCommandString() + RETURNED_EXIT_CODE +
                    res.getExitCode());
    cmdRunner.release();
    return res;
  }

  @Override
  public void refreshAPISession() throws APIException
  {
    terminate();
    initAPI();
    ping();
  }

  /**
   * Terminate the API Session and Integration Point
   */
  @Override
  public boolean terminate()
  {
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
      // Separate try-block to ensure this code is executed even it the previous try-block threw an
      // exception
      try {
        if (null != session) {
          // disconnect any users explicitly
          Command cmd = new Command(Command.IM, "disconnect");
          CmdRunner cmdRunner = session.createCmdRunner();
          cmdRunner.setDefaultHostname(hostName);
          cmdRunner.setDefaultPort(port);
          cmdRunner.setDefaultUsername(userName);
          cmdRunner.setDefaultPassword(password);
          Response res = cmdRunner.execute(cmd);
          cmdRunner.release();
         // force the termination of an running command
         session.release(false);
         sessionKilled = true;
        } else { sessionKilled = true; }
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
        LOGGER
                        .fine("Successfully disconnected connection " +
                                        userName + "@" + hostName + ":" +
                                        port);
      } else {
        LOGGER.warning("Failed to disconnect connection " + userName + "@" +
                        hostName + ":" + port);
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
  public String getUserName()
  {
    return userName;
  }

  @Override
  public String toString()
  {
    StringBuilder builder = new StringBuilder();
    builder.append("\n Session Host :" + this.hostName + "  ");
    builder.append("Session Port :" + this.port + " ");
    builder.append("Session User :" + this.userName + " ");
    return builder.toString();
  }

  @Override
  public void close()
  {
    // do nothing. This is used for LC session termination.
  }

  @Override
  public void checkifAlive() throws APIException
  {
    try {
      this.ping();
    } catch (Exception e) {
      LOGGER.warning("[LocalClient] Exception while pinging session :"+e.getMessage());
      initLocalAPI();
    }
  }
}
