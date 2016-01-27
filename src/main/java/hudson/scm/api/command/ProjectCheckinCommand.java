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
public class ProjectCheckinCommand extends BasicAPICommand
{
    protected ProjectCheckinCommand(final IntegrityConfigurable serverConfig)
    {
	super(serverConfig);
	cmd = new Command(Command.SI, PROJECT_CHECKIN_COMMAND);
	
	// Initialize defaults
	cmd.addOption(new APIOption(IAPIOption.SAVE_TIMESTAMP));
	cmd.addOption(new APIOption(IAPIOption.NO_CLOSE_CP));
	cmd.addOption(new APIOption(IAPIOption.NO_DIFFERENT_NAMES));
	cmd.addOption(new APIOption(IAPIOption.BRANCH_VARIANT));
	cmd.addOption(new APIOption(IAPIOption.NO_CHECKIN_UNCHANGED));
    }
}
