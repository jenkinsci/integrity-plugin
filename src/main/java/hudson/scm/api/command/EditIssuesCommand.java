/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/


package hudson.scm.api.command;

import com.mks.api.Command;

import hudson.scm.IntegrityConfigurable;

/**
 *
 * @author Author: asen
 * @version $Revision: $
 */
public class EditIssuesCommand extends BasicAPICommand
{
    
    protected EditIssuesCommand(final IntegrityConfigurable serverConfig)
    {
	super(serverConfig);
	cmd = new Command(Command.IM, EDIT_ISSUE_COMMAND);
    }
}
