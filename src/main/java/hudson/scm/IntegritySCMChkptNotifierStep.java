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
				Command siCheckpoint = new Command(Command.SI, "checkpoint");
				// Set the project name
				siCheckpoint.addOption(new Option("project", configPath));
				// Set the label and description if applicable
				if( null != checkpointLabel && checkpointLabel.length() > 0 )
				{
					// Set the label
					siCheckpoint.addOption(new Option("label", checkpointLabel));
				}
				
				if( null != checkpointDesc && checkpointDesc.length() > 0 )
				{
					// Set the description
					siCheckpoint.addOption(new Option("description", checkpointDesc));
				}
			
				api.runCommand(siCheckpoint);
				listener.getLogger().println("Successfully checkpointed project " + configPath);
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
