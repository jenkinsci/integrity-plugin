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
public class ProjectAddCommand extends BasicAPICommand
{
    protected ProjectAddCommand(final IntegrityConfigurable serverConfig)
    {
	super(serverConfig);
	cmd = new Command(Command.SI, PROJECT_ADD_COMMAND);
	
	// Initialize defaults
	cmd.addOption(new APIOption(IAPIOption.SAVE_TIMESTAMP));
	cmd.addOption(new APIOption(IAPIOption.NO_CLOSE_CP));
	cmd.addOption(new APIOption(IAPIOption.ON_EXISTING_ARCHIVE, IAPIOption.SHARE_ARCHIVE));
    }
}
