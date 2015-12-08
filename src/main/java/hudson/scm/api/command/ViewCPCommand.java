package hudson.scm.api.command;

import hudson.scm.IntegrityConfigurable;

import hudson.scm.api.option.APIOption;
import hudson.scm.api.option.IAPIOption;

import com.mks.api.Command;

public class ViewCPCommand extends BasicAPICommand {
	
	protected ViewCPCommand(final IntegrityConfigurable serverConfig)
    {
		super(serverConfig);
		cmd = new Command(Command.SI, VIEW_CP_COMMAND);
    }

}
