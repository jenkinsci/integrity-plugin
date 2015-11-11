package hudson.scm;

import java.io.IOException;
import java.util.logging.Logger;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.api.APISession;
import hudson.scm.api.ExceptionHandler;
import hudson.scm.api.command.APICommandException;
import hudson.scm.api.command.CheckPointCommand;
import hudson.scm.api.command.IAPICommand;
import hudson.scm.api.option.APIOption;
import hudson.scm.api.option.IAPIOption;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import jenkins.tasks.SimpleBuildStep;

@SuppressWarnings("unchecked")
public class IntegritySCMChkptNotifierStep extends Notifier implements SimpleBuildStep
{
	private static final Logger LOGGER = Logger.getLogger("IntegritySCM");
	private final IntegrityConfigurable ciSettings;
	private final String configPath;
	private final String checkpointLabel;
	private final String checkpointDesc;
	
	public IntegritySCMChkptNotifierStep(IntegrityConfigurable ciSettings, String configPath, String checkpointLabel, String checkpointDesc)
	{
		this.ciSettings = ciSettings;
		this.configPath = configPath;
		this.checkpointLabel = checkpointLabel;
		this.checkpointDesc = checkpointDesc;
	}
	
	public BuildStepMonitor getRequiredMonitorService()
	{
		return BuildStepMonitor.NONE;
	}

	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException
	{
		APISession api = APISession.create(ciSettings);
		if( null != api )
		{
			listener.getLogger().println("Preparing to execute si checkpoint for project " + configPath);
			try
			{
				// Construct the checkpoint command
				IAPICommand command = new CheckPointCommand();
				command.addOption(new APIOption(IAPIOption.PROJECT, configPath));
				command.addAdditionalParameters(IAPIOption.CHECKPOINT_LABEL, checkpointLabel);
				command.addAdditionalParameters(IAPIOption.CHECKPOINT_DESCRIPTION, checkpointDesc);
				
				command.execute(api);
				
				listener.getLogger().println("Successfully checkpointed project " + configPath);
			}
			catch (APICommandException aex)
			{
                		LOGGER.severe("API Exception caught...");
                		ExceptionHandler eh = new ExceptionHandler(aex);
                		aex.printStackTrace(listener.fatalError(eh.getMessage()));
                		LOGGER.severe(eh.getMessage());
                		LOGGER.fine(eh.getCommand() + " returned exit code " + eh.getExitCode());
			}
			finally
			{
				api.Terminate();
			}
		}
		else
		{
			listener.getLogger().println("Failed to establish connection with Integrity!");
		}
	}
}
