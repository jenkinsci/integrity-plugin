/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/


package hudson.scm;

import hudson.scm.api.command.CommandFactory;
import hudson.scm.api.command.IAPICommand;

/**
 *
 * @author Author: asen
 * @version $Revision: $
 */
public class MockCommandFactory extends CommandFactory
{
    public static IAPICommand createCommand(final String commandName, IntegrityConfigurable integrityConfig){
	  return new MockAPICommand(integrityConfig, commandName);  
    }
}
