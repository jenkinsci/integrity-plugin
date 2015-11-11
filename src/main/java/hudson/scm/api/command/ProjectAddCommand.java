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
public class ProjectAddCommand extends BasicAPICommand
{
    public ProjectAddCommand()
    {
	cmd = new Command(Command.SI, PROJECT_ADD_COMMAND);
	
	// Initialize defaults
	cmd.addOption(new APIOption(IAPIOption.SAVE_TIMESTAMP));
	cmd.addOption(new APIOption(IAPIOption.NO_CLOSE_CP));
	cmd.addOption(new APIOption(IAPIOption.ON_EXISTING_ARCHIVE, IAPIOption.SHARE_ARCHIVE));
    }
}
