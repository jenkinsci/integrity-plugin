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
public class RelationshipsCommand extends BasicAPICommand
{
    
    protected RelationshipsCommand(final IntegrityConfigurable serverConfig)
    {
	super(serverConfig);
	cmd = new Command(Command.IM, RELATIONSHIPS_COMMAND);
    }
}
