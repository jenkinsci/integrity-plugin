package hudson.scm;

import java.io.IOException;
import java.util.logging.Logger;

import com.mks.api.response.APIException;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.api.ExceptionHandler;
import hudson.scm.api.command.AddProjectLabelCommand;
import hudson.scm.api.command.IAPICommand;
import hudson.scm.api.option.APIOption;
import hudson.scm.api.option.IAPIOption;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import jenkins.tasks.SimpleBuildStep;

@SuppressWarnings("unchecked")
public class IntegritySCMLabelNotifierStep extends Notifier implements SimpleBuildStep
{
	private static final Logger LOGGER = Logger.getLogger(IntegritySCM.class.getName());
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
	    	listener.getLogger().println("Preparing to execute si addprojectlabel for " + configPath);
        	 try
        	 {
        	     // Assumes the checkpoint was done before the build, so lets apply the label now
        	     IAPICommand command = new AddProjectLabelCommand(ciSettings);
        	     command.addOption(new APIOption(IAPIOption.PROJECT, configPath));
        	     command.addOption(new APIOption(IAPIOption.LABEL, checkpointLabel));
        		    	
        	     command.execute();
        		    	
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

	}
}
