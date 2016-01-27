/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/


package hudson.scm.api.command;

import com.mks.api.Command;

import hudson.scm.IntegrityConfigurable;
import hudson.scm.api.option.APIOption;
import hudson.scm.api.option.IAPIOption;

/**
 *
 * @author Author: asen
 * @version $Revision: $
 */
public class AddProjectLabelCommand extends BasicAPICommand
{
    protected AddProjectLabelCommand(final IntegrityConfigurable serverConfig)
    {
	super(serverConfig);
	cmd = new Command(Command.SI, ADD_PROJECT_LABEL_COMMAND);
	
	// Initialize defaults
	// Move the label, if a previous one was applied
	cmd.addOption(new APIOption(IAPIOption.MOVE_LABEL));
    }
}
