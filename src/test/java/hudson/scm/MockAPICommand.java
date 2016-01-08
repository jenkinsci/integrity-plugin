/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/


package hudson.scm;

import java.util.HashMap;

import com.mks.api.Command;

import hudson.scm.api.command.BasicAPICommand;

/**
 *
 * @author Author: asen
 * @version $Revision: $
 */
public class MockAPICommand extends BasicAPICommand
{
    
    public MockAPICommand(final IntegrityConfigurable serverConfig, String command)
    {
	super(serverConfig);
	cmd = new Command(Command.SI, command);
	commandHelperObjects = new HashMap<String, Object>();
    }
	
}
