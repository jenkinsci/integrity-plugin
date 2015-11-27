// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

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
