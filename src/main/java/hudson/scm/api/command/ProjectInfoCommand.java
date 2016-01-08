/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/


package hudson.scm.api.command;

import com.mks.api.Command;

import hudson.scm.IntegrityConfigurable;

/**
 * Project Info command for the Integrity Jenkins Plugin
 * @author Author: asen
 * @version $Revision: $
 */
public class ProjectInfoCommand extends BasicAPICommand
{
    
    protected ProjectInfoCommand(final IntegrityConfigurable serverConfig)
    {
	super(serverConfig);
	cmd = new Command(Command.SI, PROJECT_INFO_COMMAND);
    }

}
