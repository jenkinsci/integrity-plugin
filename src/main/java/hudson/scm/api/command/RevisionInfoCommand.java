/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/


package hudson.scm.api.command;

import com.mks.api.Command;

import hudson.scm.IntegrityConfigurable;

/**
 * Revision Info command for the Integrity Jenkins Plugin
 * 
 * @author Author: asen
 * @version $Revision: $
 */
public class RevisionInfoCommand extends BasicAPICommand
{
    protected RevisionInfoCommand(final IntegrityConfigurable serverConfig)
    {
	super(serverConfig);
	cmd = new Command(Command.SI, REVISION_INFO_COMMAND);
    }
    
}
