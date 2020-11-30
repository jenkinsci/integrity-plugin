/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/


package hudson.scm.api.command;

import com.mks.api.Command;
import com.mks.api.Option;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;
import hudson.AbortException;
import hudson.scm.IntegrityConfigurable;
import hudson.scm.IntegritySCM;
import hudson.scm.api.option.IAPIOption;
import hudson.scm.api.session.ISession;
import hudson.scm.api.session.ISessionPool;
import org.apache.commons.pool2.KeyedObjectPool;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * All Jenkins Integrity API Commands have to extend this class in order to execute Integrity API
 * calls using the default method
 *
 * @author Author: asen
 * @version $Revision: $
 */
public abstract class BasicAPICommand implements IAPICommand
{
  protected static final Logger LOGGER = Logger.getLogger(IntegritySCM.class.getSimpleName());
  protected Command cmd;
  protected Map<String, Object> commandHelperObjects;

  protected Response res;
  protected boolean runCommandWithInterim = false;
  protected final IntegrityConfigurable serverConfig;
  private ISession api = null;
  private static final String SESSION_INVALID_FAILED = "Failed to invalidate Session Pool Object :";
  private static final String SESSION_NOT_ESTABLISHED_TO = "An Windchill RV&S API Session could not be established to ";
  private static final String CANNOT_PERFORM = "!  Cannot perform ";
  private static final String OPERATION = " operation : ";
  private final KeyedObjectPool<IntegrityConfigurable, ISession> pool;

  /**
   * Constructor initialized with serverconfig id for commands to fire to a particular Integrity
   * server
   * 
   * @param serverConfig
   */
  public BasicAPICommand(IntegrityConfigurable serverConfig)
  {
    this.pool = ISessionPool.getInstance().getPool();
    this.serverConfig = serverConfig;
  }

  public BasicAPICommand()
  {
    this(null);
  }

  /*
   * (non-Javadoc)
   * 
   * @see hudson.scm.api.APICommand#execute(hudson.scm.api.APISession)
   */
  @Override
  public Response execute(ISession api) throws APIException
  {
    if (null == cmd)
    {
      LOGGER.log(Level.SEVERE, "Integration API Command cannot be null");
      throw new APIException("Integration API Command cannot be null");
    }

    doPreAction();

    if (runCommandWithInterim)
    {
      LOGGER.log(Level.FINE, "Running API command with interim: " + cmd.getCommandName());
      res = api.runCommandWithInterim(cmd);
    } else
    {
      LOGGER.log(Level.FINE, "Running API command: " + cmd.getCommandName());
      res = api.runCommand(cmd);
    }

    if (null != res && !runCommandWithInterim)
    {
      int resCode = res.getExitCode();

      if (resCode == 0)
      {
        LOGGER.log(Level.FINE,
            "Response for API command: " + cmd.getCommandName() + " : " + resCode);
        // execute post action only on success response
        doPostAction();
      }
    }

    return res;
  }

  /*
   * (non-Javadoc)
   * 
   * @see hudson.scm.api.command.IAPICommand#execute()
   */
  @Override
  public Response execute() throws APIException, AbortException
  {
    if (null == serverConfig)
    {
      LOGGER.severe("Unable to get Server configuration for " + cmd.getCommandName()
          + " operation. Server config is null");
      throw new AbortException(
          "Unable to get Server configuration for " + cmd.getCommandName() + " operation");
    }

    try
    {
      LOGGER.log(Level.FINEST, "Borrowing Session Object from Pool :" + serverConfig.getName()
          + ", for running API command : " + cmd.getCommandName());

      api = pool.borrowObject(serverConfig);
      res = execute(api);

    } catch (NoSuchElementException | IllegalStateException e)
    {
      try
      {
        if (api != null)
          pool.invalidateObject(serverConfig, api);
      } catch (Exception e1)
      {
        LOGGER.log(Level.SEVERE,SESSION_INVALID_FAILED + serverConfig.getName(), e1);
      }
      LOGGER.log(Level.SEVERE,
          SESSION_NOT_ESTABLISHED_TO + serverConfig.getHostName() + ":"
              + serverConfig.getPort() + CANNOT_PERFORM + cmd.getCommandName()
              + OPERATION + e.getMessage(),
          e);
      throw new AbortException(SESSION_NOT_ESTABLISHED_TO
          + serverConfig.getHostName() + ":" + serverConfig.getPort() +
                      CANNOT_PERFORM
          + cmd.getCommandName() + OPERATION + e.getMessage());
    } catch (APIException aex)
    {
      try
      {
        if (api != null)
          pool.invalidateObject(serverConfig, api);
      } catch (Exception e1)
      {
        LOGGER.log(Level.SEVERE,
                        SESSION_INVALID_FAILED + serverConfig.getName(), e1);
      }
      // Do Nothing. Rethrow
      throw aex;
    } catch (Exception e)
    {
      try
      {
        if (api != null)
          pool.invalidateObject(serverConfig, api);
      } catch (Exception e1)
      {
        LOGGER.log(Level.SEVERE,
                        SESSION_INVALID_FAILED + serverConfig.getName(), e1);
      }
      LOGGER.log(Level.SEVERE,
          SESSION_NOT_ESTABLISHED_TO + serverConfig.getName() + ":"
              + serverConfig.getPort() + CANNOT_PERFORM + cmd.getCommandName()
              + OPERATION + e.getMessage(),
          e);
      throw new AbortException(SESSION_NOT_ESTABLISHED_TO
          + serverConfig.getHostName() + ":" + serverConfig.getPort() +
                      CANNOT_PERFORM
          + cmd.getCommandName() + OPERATION + e.getMessage());
    } finally
    {
      try
      {
        if (null != api && !runCommandWithInterim)
        {
          LOGGER.log(Level.FINEST,
              "Returning session object back to pool :" + serverConfig.getName());
          api.terminate();
          pool.invalidateObject(serverConfig, api);
          //pool.returnObject(serverConfig, api);
        }
      } catch (Exception e)
      {
        LOGGER.log(Level.SEVERE,
            "Failed to return Session back to Session Pool :" + serverConfig.getName(), e);
      }
    }

    return res;
  }

  @Override
  public void addOption(IAPIOption option)
  {
    cmd.addOption((Option) option);
  }

  @Override
  public void addSelection(String param)
  {
    cmd.addSelection(param);
  }

  /*
   * (non-Javadoc)
   * 
   * @see hudson.scm.api.command.APICommand#doPostAction()
   */
  @Override
  public void doPostAction()
  {
    // NOOP
  }

  /*
   * (non-Javadoc)
   * 
   * @see hudson.scm.api.command.APICommand#doPreAction()
   */
  @Override
  public void doPreAction()
  {
    // NOOP
  }

  /*
   * (non-Javadoc)
   * 
   * @see hudson.scm.api.command.APICommand#addAdditionalParameters(java.lang.String,
   * java.lang.Object)
   */
  @Override
  public void addAdditionalParameters(String paramName, Object param)
  {
    commandHelperObjects.put(paramName, param);
  }

  /*
   * (non-Javadoc)
   * 
   * @see hudson.scm.api.command.IAPICommand#terminateAPI()
   */
  @Override
  public void terminateAPI() throws Exception
  {
    if (runCommandWithInterim && api != null)
    {
      LOGGER.log(Level.FINEST,
          "Terminating API Session for WITH_INTERIM command :" + api.toString());
      api.terminate();
      ISessionPool.getInstance().getPool().invalidateObject(serverConfig, api);
    }
  }
}
