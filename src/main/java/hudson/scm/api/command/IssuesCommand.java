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
public class IssuesCommand extends BasicAPICommand
{
    protected IssuesCommand(final IntegrityConfigurable serverConfig)
    {
	super(serverConfig);
	cmd = new Command(Command.IM, ISSUES_COMMAND);
	
	// Initialize defaults
	cmd.addOption(new APIOption(IAPIOption.FIELDS, "ID"));
    }
}
