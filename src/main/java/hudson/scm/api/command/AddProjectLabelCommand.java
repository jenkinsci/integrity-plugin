// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm.api.command;

import com.mks.api.Command;

import hudson.scm.api.option.APIOption;
import hudson.scm.api.option.IAPIOption;

/**
 *
 * @author Author: asen
 * @version $Revision: $
 */
public class AddProjectLabelCommand extends BasicAPICommand
{
    public AddProjectLabelCommand()
    {
	cmd = new Command(Command.SI, ADD_PROJECT_LABEL_COMMAND);
	
	// Initialize defaults
	// Move the label, if a previous one was applied
	cmd.addOption(new APIOption(IAPIOption.MOVE_LABEL));
    }
}
