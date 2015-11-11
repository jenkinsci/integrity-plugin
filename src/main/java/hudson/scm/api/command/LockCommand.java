// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm.api.command;

import com.mks.api.Command;

/**
 *
 * @author Author: asen
 * @version $Revision: $
 */
public class LockCommand extends BasicAPICommand
{
    public LockCommand()
    {
	cmd = new Command(Command.SI, LOCK_COMMAND);
    }
}
