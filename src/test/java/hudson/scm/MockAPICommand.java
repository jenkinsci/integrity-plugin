// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm;

import java.util.HashMap;

import com.mks.api.Command;

import hudson.scm.api.command.BasicAPICommand;

/**
 *
 * @author Author: asen
 * @version $Revision: $
 */
public class MockAPICommand extends BasicAPICommand
{
    
    public MockAPICommand(final IntegrityConfigurable serverConfig, String command)
    {
	super(serverConfig);
	cmd = new Command(Command.SI, command);
	commandHelperObjects = new HashMap<String, Object>();
    }
	
}
