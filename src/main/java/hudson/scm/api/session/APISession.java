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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
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
  public static final String VERSION = "4.13";
  public static final int MAJOR_VERSION = 4;
  public static final int MINOR_VERSION = 13;
  private static final String RETURNED_EXIT_CODE = " returned exit code ";
  private static final String API_EXCEPTION = "APIException";
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
  private static List<ISession> unClosedSessions = Collections.synchronizedList(new ArrayList<ISession>());

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
  public static APISession createLocalIntegrationPoint(
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
    initAPI();
  }

  private void initAPI() throws APIException
  {
    // Initialize our termination flag...
    terminated = false;
    if (isLocalIntegration) {
      initLocalAPI();
    } else {
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
    }
    // Create the Session
    session = ip.createSession(userName, password);
    session.setTimeout(300000); // 5 Minutes
  }

  /**
   * Initialize the Local integration point
   *
   * @throws APIException
   */
  private void initLocalAPI() throws APIException
  {
    if (ip == null) {
      ip = IntegrationPointFactory.getInstance()
                      .createLocalIntegrationPoint(MAJOR_VERSION,
                                      MINOR_VERSION);
      ip.setAutoStartIntegrityClient(true);
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
    CmdRunner cmdRunner = session.createCmdRunner();
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
    CmdRunner cmdRunner = session.createCmdRunner();
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
    CmdRunner cmdRunner = session.createCmdRunner();
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
    boolean intPointKilled = false;
    // Terminate only if not already terminated!
    if (!terminated) {
      try {
        if (null != icr) {
          if(!isLocalIntegration) {
            if (!icr.isFinished()) {
              icr.interrupt();
            }
            icr.release();
            cmdRunnerKilled = true;
          }
          else if (icr.isFinished()){
            icr.release();
            cmdRunnerKilled = true;
          }
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
          if (!isLocalIntegration) {
            //Command cmd = new Command(Command.IM, "disconnect");
            //runCommand(cmd);
            // force the termination of an running command
            session.release(true);
            sessionKilled = true;
          } else if (cmdRunnerKilled){
            session.release(false);
            sessionKilled = true;
          }
        } else { sessionKilled = true; }
      } catch (APIException aex) {
        LOGGER.fine("Caught API Exception when releasing session!");
        LOGGER.log(Level.SEVERE, API_EXCEPTION, aex);
      } catch (IOException ioe) {
        LOGGER.fine("Caught IO Exception when releasing session!");
        LOGGER.log(Level.SEVERE, "IOException", ioe);
      }

      if(!isLocalIntegration) {
        if (null != ip) {
          ip.release();
          intPointKilled = true;
        } else {
          intPointKilled = true;
        }
      } else { intPointKilled = true; }

      if (cmdRunnerKilled && sessionKilled && intPointKilled) {
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
   * Returns the Integrity Integration Point Hostname for this APISession
   *
   * @return
   */
  public String getIPHostName()
  {
    return ipHostName;
  }

  /**
   * Returns the Integrity Integration Point Port for this APISession
   *
   * @return
   */
  public String getIPPort()
  {
    return String.valueOf(ipPort);
  }

  /**
   * Returns the Integrity Hostname for this APISession
   *
   * @return
   */
  public String getHostName()
  {
    return hostName;
  }

  /**
   * Returns the Integrity Port for this APISession
   *
   * @return
   */
  public String getPort()
  {
    return String.valueOf(port);
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
  public void close() throws IllegalStateException
  {
    synchronized (unClosedSessions) {
      unClosedSessions.add(this);
    }
  }

  public static void terminateUnclosedSessions()
  {
    synchronized (unClosedSessions) {
    Iterator i = unClosedSessions.iterator();
      while (i.hasNext()){
        ISession session = (ISession) i.next();
        try {
          boolean isTerminated = session.terminate();
          if(isTerminated) {i.remove();}
        } catch (Exception e) {
          LOGGER.warning("Error while shutting down Local API session: " +
                          e.getMessage());
        }
      }
    }
  }

}
