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
public class EditResultsCommand extends BasicAPICommand
{
    protected EditResultsCommand(final IntegrityConfigurable serverConfig)
    {
	super(serverConfig);
	cmd = new Command(Command.TM, EDIT_RESULT_COMMAND);
	
	// Initialize defaults
	cmd.addOption(new APIOption(IAPIOption.FORCE_CREATE));
    }
}
