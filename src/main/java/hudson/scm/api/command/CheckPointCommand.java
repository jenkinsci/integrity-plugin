// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm.api.command;

import java.util.HashMap;

import org.apache.commons.lang.StringUtils;

import com.mks.api.Command;

import hudson.scm.IntegrityConfigurable;
import hudson.scm.api.option.APIOption;
import hudson.scm.api.option.IAPIOption;

/**
 *
 * @author Author: asen
 * @version $Revision: $
 */
public class CheckPointCommand extends BasicAPICommand
{
    protected CheckPointCommand(final IntegrityConfigurable serverConfig)
    {
	super(serverConfig);
	cmd = new Command(Command.SI, CHECKPOINT_COMMAND);
	commandHelperObjects = new HashMap<String, Object>();
    }
    
    @Override
    public void doPreAction()
    {
	String chkptLabel = (String) commandHelperObjects.get(IAPIOption.CHECKPOINT_LABEL);
	String checkpointDesc = (String) commandHelperObjects.get(IAPIOption.CHECKPOINT_DESCRIPTION);
	if(StringUtils.isNotBlank(chkptLabel))
	{
	    // Set the label
	    cmd.addOption(new APIOption(IAPIOption.LABEL, chkptLabel));
	}
	
	// Set the description
	if(StringUtils.isNotBlank(checkpointDesc)){
	    cmd.addOption(new APIOption(IAPIOption.DESCRIPTION, checkpointDesc));
	}
	else if(StringUtils.isNotBlank(chkptLabel)){ // Set the label instead as description
	    cmd.addOption(new APIOption(IAPIOption.DESCRIPTION, chkptLabel));
	}
	
    }
}
