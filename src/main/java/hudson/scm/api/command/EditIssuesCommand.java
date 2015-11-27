// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

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
