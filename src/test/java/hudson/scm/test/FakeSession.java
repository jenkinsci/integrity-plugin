//  
//   $Id: FakeSession.java 1.7 2015/03/30 20:19:12IST Reid, Randall (rreid) Exp  $
//
//   Copyright 2011 by PTC Inc. All rights reserved.
//
//   This Software is unpublished, valuable, confidential property of
//   PTC Inc.   Any use or disclosure of this Software
//   without the express written permission of PTC Inc.
//   is strictly prohibited.
//

package hudson.scm.test;

import java.io.IOException;
import java.util.Iterator;

import com.mks.api.*;
import com.mks.api.response.APIException;

/**
 * Allows for testing classes that use a Session.
 *
 * @since Red
 * @version $Revision: 1.7 $
 */
public  class FakeSession extends FakeObject implements Session
{
    public static final String CREATE_CMD_RUNNER = "createCmdRunner";
    public static final String AUTO_RECONNECT = "autoReconnect";

    /* (non-Javadoc)
     * @see com.mks.api.Session#getIntegrationID()
     */
    public String getIntegrationID()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunnerCreator#createCmdRunner()
     */
    public CmdRunner createCmdRunner() throws APIException
    {
	return (CmdRunner) getValueByKey( CREATE_CMD_RUNNER );
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunnerCreator#getCmdRunners()
     */
    public Iterator<?> getCmdRunners()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunnerCreator#release()
     */
    public void release() throws IOException, APIException
    {
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunnerCreator#release(boolean)
     */
    public void release(boolean force) throws IOException, APIException
    {
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunnerCreator#setDefaultImpersonationUser(java.lang.String)
     */
    public void setDefaultImpersonationUser(String impUser)
    {
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunnerCreator#getDefaultImpersonationUser()
     */
    public String getDefaultImpersonationUser()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunnerCreator#getDefaultHostname()
     */
    public String getDefaultHostname()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunnerCreator#getDefaultPort()
     */
    public int getDefaultPort()
    {
	return 0;
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunnerCreator#setDefaultHostname(java.lang.String)
     */
    public void setDefaultHostname(String host)
    {
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunnerCreator#setDefaultPort(int)
     */
    public void setDefaultPort(int port)
    {
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunnerCreator#getDefaultUsername()
     */
    public String getDefaultUsername()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunnerCreator#getDefaultPassword()
     */
    public String getDefaultPassword()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunnerCreator#setDefaultUsername(java.lang.String)
     */
    public void setDefaultUsername(String user)
    {
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunnerCreator#setDefaultPassword(java.lang.String)
     */
    public void setDefaultPassword(String pass)
    {
    }

    /* (non-Javadoc)
     * @see com.mks.api.IntegrationVersionRequest#getAPIRequestVersion()
     */
    public VersionNumber getAPIRequestVersion()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.Session#getIntegrationPoint()
     */
    public IntegrationPoint getIntegrationPoint()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.Session#setAutoReconnect(boolean)
     */
    public void setAutoReconnect(boolean flag)
    {
	put(AUTO_RECONNECT, flag);
    }

    /* (non-Javadoc)
     * @see com.mks.api.Session#getAutoReconnect()
     */
    public boolean getAutoReconnect()
    {
	return getBooleanValueByKey(AUTO_RECONNECT);
    }

    /* (non-Javadoc)
     * @see com.mks.api.Session#getTimeout()
     */
    public int getTimeout()
    {
	return 0;
    }

    /* (non-Javadoc)
     * @see com.mks.api.Session#setTimeout(int)
     */
    public void setTimeout(int timeout)
    {
    }

    /* (non-Javadoc)
     * @see com.mks.api.Session#isCommon()
     */
    public boolean isCommon()
    {
	return false;
    }

}
