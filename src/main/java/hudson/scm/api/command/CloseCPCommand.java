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
public class CloseCPCommand extends BasicAPICommand
{
    protected CloseCPCommand(final IntegrityConfigurable serverConfig)
    {
	super(serverConfig);
	cmd = new Command(Command.SI, CLOSE_CP_COMMAND);
	
	// Initialize defaults
	cmd.addOption(new APIOption(IAPIOption.RELEASE_LOCKS));
    }
}
