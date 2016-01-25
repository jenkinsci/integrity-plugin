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
public class ViewProjectCommand extends BasicAPICommand
{
    protected ViewProjectCommand(IntegrityConfigurable serverConfig)
    {
	super(serverConfig);
	cmd = new Command(Command.SI, VIEW_PROJECT_COMMAND);
	
	runCommandWithInterim = true;
	
	// Initialize defaults
	cmd.addOption(new APIOption(IAPIOption.RECURSE));
    }
}
