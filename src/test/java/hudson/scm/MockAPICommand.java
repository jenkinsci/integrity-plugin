// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm;

import java.util.HashMap;

import com.mks.api.Command;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;

import hudson.AbortException;
import hudson.scm.api.ISession;
import hudson.scm.api.command.BasicAPICommand;

/**
 *
 * @author Author: asen
 * @version $Revision: $
 */
public class MockAPICommand extends BasicAPICommand
{
    
    Command cmd;
    IntegrityConfigurable configObj;
	
    public MockAPICommand(final IntegrityConfigurable serverConfig, String command)
    {
	super(serverConfig);
	cmd = new Command(Command.SI, command);
	commandHelperObjects = new HashMap<String, Object>();
    }
	

    /* (non-Javadoc)
     * @see hudson.scm.api.command.IAPICommand#execute()
     */
    @Override
    public Response execute() throws APIException, AbortException
    {
	configObj= new IntegrityConfigurable("server1", "ppumsv-ipdc16d.ptcnet.ptc.com", 7001, "ppumsv-ipdc16d.ptcnet.ptc.com", 7001, false, "developer", "password");
	ISession api = FakeAPISession.create(configObj);
	return super.execute(api);
    }

}
