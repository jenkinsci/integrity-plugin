// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm.api.command;

import java.util.Map;

import com.mks.api.Command;
import com.mks.api.Option;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;

import hudson.AbortException;
import hudson.scm.IntegrityConfigurable;
import hudson.scm.api.APISession;
import hudson.scm.api.ISession;
import hudson.scm.api.option.IAPIOption;
import hudson.util.StreamTaskListener;

/**
 * All Jenkins Integrity API Commands have to extend this class in order to execute Integrity API calls using the default method
 *
 * @author Author: asen
 * @version $Revision: $
 */
public abstract class BasicAPICommand implements IAPICommand
{
    protected Command cmd;
    protected Map<String, Object> commandHelperObjects;
    
    protected Response res;
    protected boolean runCommandWithInterim = false;
    private static IntegrityConfigurable icSettings;
    
    
    /* (non-Javadoc)
     * @see hudson.scm.api.APICommand#execute(hudson.scm.api.APISession)
     */
    @Override
    public Response execute(ISession api) throws APICommandException
    {
	if(null == cmd)
	    throw new APICommandException("Integration API Command cannot be null");
	
	try {
	    
	    doPreAction();
	    
	    if(runCommandWithInterim)
		res = api.runCommandWithInterim(cmd);
	    else
		res = api.runCommand(cmd);
	    
	    if(null != res){
        	    int resCode = res.getExitCode();
        	    
        	    if(resCode == 0){
        		// execute post action only on success response
        		doPostAction();
        	    }
	    }
	    
	} catch (APIException e) {
	    throw new APICommandException(e);
	} finally {
	    api.terminate();
	}
	
	return res;
    }

    /* (non-Javadoc)
     * @see hudson.scm.api.command.IAPICommand#execute()
     */
    @Override
    public Response execute() throws APICommandException, AbortException
    {
	IntegrityConfigurable coSettings = new IntegrityConfigurable("TEMP_ID", icSettings.getIpHostName(), icSettings.getIpPort(), icSettings.getHostName(), 
	icSettings.getPort(), icSettings.getSecure(), icSettings.getUserName(), icSettings.getPasswordInPlainText());

        ISession api = APISession.create(coSettings);
        
        // Ensure we've successfully created an API Session
        if( null == api )
        {
            StreamTaskListener task = StreamTaskListener.fromStdout();
            task.getLogger().println("Failed to establish an API connection to the Integrity Server!");
            throw new AbortException("Connection Failed!");
        }
        
        return execute(api);
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
    
    /* (non-Javadoc)
     * @see hudson.scm.api.command.APICommand#doPostAction()
     */
    @Override
    public void doPostAction()
    {
	// do nothing
    }

    /* (non-Javadoc)
     * @see hudson.scm.api.command.APICommand#doPreAction()
     */
    @Override
    public void doPreAction()
    {
	// do nothing
    }

    /* (non-Javadoc)
     * @see hudson.scm.api.command.APICommand#addAdditionalParameters(java.lang.String, java.lang.Object)
     */
    @Override
    public void addAdditionalParameters(String paramName, Object param)
    {
	commandHelperObjects.put(paramName, param);
    }

    public static void setIntegritySettings(IntegrityConfigurable desSettings)
    {
	icSettings = desSettings;
    }
    
}
