// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm.api.command;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

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
  private IntegrityConfigurable serverConfig;

  /**
   * Constructor initialized with serverconfig id for commands to fire to a particular Integrity
   * server
   * 
   * @param serverConfig
   */
  public BasicAPICommand(IntegrityConfigurable serverConfig)
  {
    this.serverConfig = serverConfig;
  }

  public BasicAPICommand()
  {
    // Do Nothing
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
      throw new APIException("Integration API Command cannot be null");

    doPreAction();

    if (runCommandWithInterim)
      res = api.runCommandWithInterim(cmd);
    else
      res = api.runCommand(cmd);

    if (null != res && !runCommandWithInterim)
    {
      int resCode = res.getExitCode();

      if (resCode == 0)
      {
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

    ISession api = null;
    Response res;
    try
    {
      api = ISessionPool.getInstance().getPool().borrowObject(serverConfig);
      res = execute(api);

      if (null != api)
        ISessionPool.getInstance().getPool().returnObject(serverConfig, api);

    } catch (NoSuchElementException e)
    {
      LOGGER.log(Level.SEVERE,
          "An Integrity API Session could not be established to " + serverConfig.getHostName() + ":"
              + serverConfig.getPort() + "!  Cannot perform " + cmd.getCommandName()
              + " operation : " + e.getMessage(),
          e);
      throw new AbortException("An Integrity API Session could not be established to "
          + serverConfig.getHostName() + ":" + serverConfig.getPort() + "!  Cannot perform "
          + cmd.getCommandName() + " operation : " + e.getMessage());
    } catch (IllegalStateException e)
    {
      LOGGER.log(Level.SEVERE,
          "An Integrity API Session could not be established to " + serverConfig.getHostName() + ":"
              + serverConfig.getPort() + "!  Cannot perform " + cmd.getCommandName()
              + " operation : " + e.getMessage(),
          e);
      throw new AbortException("An Integrity API Session could not be established to "
          + serverConfig.getHostName() + ":" + serverConfig.getPort() + "!  Cannot perform "
          + cmd.getCommandName() + " operation : " + e.getMessage());
    } catch (Exception e)
    {
      LOGGER.log(Level.SEVERE,
          "An Integrity API Session could not be established to " + serverConfig.getHostName() + ":"
              + serverConfig.getPort() + "!  Cannot perform " + cmd.getCommandName()
              + " operation : " + e.getMessage(),
          e);
      throw new AbortException("An Integrity API Session could not be established to "
          + serverConfig.getHostName() + ":" + serverConfig.getPort() + "!  Cannot perform "
          + cmd.getCommandName() + " operation : " + e.getMessage());
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
    // do nothing
  }

  /*
   * (non-Javadoc)
   * 
   * @see hudson.scm.api.command.APICommand#doPreAction()
   */
  @Override
  public void doPreAction()
  {
    // do nothing
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

}
