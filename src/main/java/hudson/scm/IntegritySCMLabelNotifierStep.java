package hudson.scm;

import java.io.IOException;
import java.util.logging.Logger;

import com.mks.api.Command;
import com.mks.api.Option;
import com.mks.api.response.APIException;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.Run;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import jenkins.tasks.SimpleBuildStep;

@SuppressWarnings("unchecked")
public class IntegritySCMLabelNotifierStep extends Notifier implements SimpleBuildStep
{
	private static final Logger LOGGER = Logger.getLogger("IntegritySCM");
	private final IntegrityConfigurable ciSettings;
	private final String configPath;
	private final String checkpointLabel;
	
	public IntegritySCMLabelNotifierStep(IntegrityConfigurable ciSettings, String configPath, String checkpointLabel)
	{
		this.ciSettings = ciSettings;
		this.configPath = configPath;
		this.checkpointLabel = checkpointLabel;
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
			listener.getLogger().println("Preparing to execute si addprojectlabel for " + configPath);
			try
			{
				// Assumes the checkpoint was done before the build, so lets apply the label now
				Command siAddProjectLabel = new Command(Command.SI, "addprojectlabel");
				// Set the project name
				siAddProjectLabel.addOption(new Option("project", configPath));
				// Set the label
				siAddProjectLabel.addOption(new Option("label", checkpointLabel));
				// Move the label, if a previous one was applied
				siAddProjectLabel.addOption(new Option("moveLabel"));
				api.runCommand(siAddProjectLabel);
				listener.getLogger().println("Successfully added label " + checkpointLabel);
			}
			catch (APIException aex)
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
