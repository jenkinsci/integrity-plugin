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
public class SubmitCPCommand extends BasicAPICommand
{
    protected SubmitCPCommand(final IntegrityConfigurable serverConfig)
    {
	super(serverConfig);
	cmd = new Command(Command.SI, SUBMIT_CP_COMMAND);
	
	// Initialize defaults
	cmd.addOption(new APIOption(IAPIOption.CLOSE_CP));
	cmd.addOption(new APIOption(IAPIOption.COMMIT));
    }
}
