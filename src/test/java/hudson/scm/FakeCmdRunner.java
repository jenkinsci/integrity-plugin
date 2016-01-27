/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/

package hudson.scm;

import com.mks.api.CmdRunner;
import com.mks.api.Command;
import com.mks.api.Session;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;

/**
 * Allows for testing classes that use a command runner
 *
 * @since Red
 * @version $Revision: 1.7 $
 */
public class FakeCmdRunner extends FakeObject implements CmdRunner
{
    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#getSession()
     */
    public Session getSession()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#getInvocationID()
     */
    public String getInvocationID()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#setInvocationID(java.lang.String)
     */
    public void setInvocationID(String invocationID)
    {
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#interrupt()
     */
    public void interrupt() throws APIException
    {
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#setDefaultImpersonationUser(java.lang.String)
     */
    public void setDefaultImpersonationUser(String impUser)
    {
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#getDefaultImpersonationUser()
     */
    public String getDefaultImpersonationUser()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#getDefaultHostname()
     */
    public String getDefaultHostname()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#getDefaultPort()
     */
    public int getDefaultPort()
    {
	return 0;
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#setDefaultHostname(java.lang.String)
     */
    public void setDefaultHostname(String host)
    {
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#setDefaultPort(int)
     */
    public void setDefaultPort(int port)
    {
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#getDefaultUsername()
     */
    public String getDefaultUsername()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#getDefaultPassword()
     */
    public String getDefaultPassword()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#setDefaultUsername(java.lang.String)
     */
    public void setDefaultUsername(String user)
    {
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#setDefaultPassword(java.lang.String)
     */
    public void setDefaultPassword(String pass)
    {
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#execute(java.lang.String[])
     */
    public Response execute(String[] args) throws APIException
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#execute(com.mks.api.Command)
     */
    public Response execute(Command cmd) throws APIException
    {
	put(cmd.getCommandName(),cmd);
	Response r = (Response) getValueByKey( cmd.getApp() + "." +  cmd.getCommandName() );
	if ( r != null && r.getAPIException() != null )
	    throw r.getAPIException();
	return r;
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#executeWithInterim(java.lang.String[], boolean)
     */
    public Response executeWithInterim(String[] args, boolean enableCache)
	    throws APIException
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#executeWithInterim(com.mks.api.Command, boolean)
     */
    public Response executeWithInterim(Command cmd, boolean enableCache)
	    throws APIException
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#release()
     */
    public void release() throws APIException
    {
    }

    /* (non-Javadoc)
     * @see com.mks.api.CmdRunner#isFinished()
     */
    public boolean isFinished()
    {
	return false;
    }

    /*
     * (non-Javadoc)
     * @see com.mks.api.CmdRunner#execute(java.lang.String[], java.lang.String)
     */
    public Response execute(String[] args, String vendorName)
	    throws APIException
    {
	return null;
    }

    /*
     * (non-Javadoc)
     * @see com.mks.api.CmdRunner#executeWithInterim(java.lang.String[], boolean, java.lang.String)
     */
    public Response executeWithInterim(String[] args, boolean enableCache,
	    String vendorName) throws APIException
    {
	return null;
    }
}
