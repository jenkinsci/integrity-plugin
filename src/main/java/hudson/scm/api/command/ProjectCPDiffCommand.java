package hudson.scm.api.command;

import hudson.scm.IntegrityConfigurable;
import hudson.scm.api.option.APIOption;
import hudson.scm.api.option.IAPIOption;

import com.mks.api.Command;

public class ProjectCPDiffCommand extends BasicAPICommand {
	
	protected ProjectCPDiffCommand(final IntegrityConfigurable serverConfig) {

		super(serverConfig);
		cmd = new Command(Command.SI, PROJECT_CPDIFF_COMMAND);
		
	}

}
