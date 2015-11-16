
package hudson.scm;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.Run;
import hudson.scm.IntegritySCM.DescriptorImpl;
import hudson.util.ListBoxModel;
import hudson.util.Secret;

import java.util.logging.Logger;

import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;


public class IntegritySCMCheckinStep extends AbstractStepImpl
{
	private static final Logger LOGGER = Logger.getLogger(IntegritySCM.class.getName());
	
	private String serverConfig;
	private String userName;
	private Secret password;
	private String configPath;
	private String includes;
	private String excludes;
	private String itemID;
	private IntegrityConfigurable connectionSettings;
		
	public String getServerConfig() 
	{
		return this.serverConfig;
	}

	@DataBoundSetter
	public void setUserName(String userName)
	{
		this.userName = userName;
	}

	public String getUserName()
	{
		return this.userName;
	}

	@DataBoundSetter
	public void setPassword(String password)
	{
		this.password = Secret.fromString(password);
	}

	 /**
     * Returns the project specific encrypted password of the user connecting to the Integrity Server
     * @return
     */
	public String getPassword()
	{
		return this.password.getEncryptedValue();
	}

	@DataBoundSetter
	public void setConfigPath(String configPath)
	{
		this.configPath = configPath;
	}

	public String getConfigPath()
	{
		return this.configPath;
	}

	@DataBoundSetter
	public void setIncludes(String includes) 
	{
		this.includes = includes;
	}

	public String getIncludes()
	{
		return this.includes;
	}
	
	@DataBoundSetter
	public void setExcludes(String excludes)
	{
		this.excludes = excludes;
	}

	public String getExcludes()
	{
		return this.excludes;
	}	
	
	@DataBoundSetter
	public void setItemID(String itemID)
	{
		this.itemID = itemID;
	}

	public String getItemID()
	{
		return this.itemID;
	}		
	
	public IntegrityConfigurable getConnectionSettings()
	{
		IntegrityConfigurable desSettings = DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig);
		this.connectionSettings = new IntegrityConfigurable("TEMP_ID", desSettings.getIpHostName(), desSettings.getIpPort(), 
															desSettings.getHostName(), desSettings.getPort(), desSettings.getSecure(), 
															null == userName ? desSettings.getUserName() : userName,
															null == password ? desSettings.getPasswordInPlainText() : password.getPlainText());
		
		return this.connectionSettings;
	}
	
	@DataBoundConstructor
	public IntegritySCMCheckinStep(String serverConfig)
	{
		this.serverConfig = serverConfig;
		IntegrityConfigurable config = getConnectionSettings();
		this.userName = config.getUserName();
		this.password = Secret.fromString(config.getPassword());
		this.configPath = "";
		this.includes = "";
		this.excludes = "";		
		this.itemID = "";
		LOGGER.fine("IntegritySCMCheckinStep() constructed!");		
	}

	@Extension(optional = true)
	public static final class IntegritySCMCheckinDescriptorImpl extends AbstractStepDescriptorImpl 
	{

		public IntegritySCMCheckinDescriptorImpl() 
		{
			super(IntegritySCMCheckinStepExecution.class);
			
			LOGGER.fine("IntegritySCMCheckinDescriptorImpl() invoked!");			
		}

		@Override
		public String getFunctionName() 
		{
			return "sici";
		}

		@Override
		public String getDisplayName() 
		{
			return "Integrity SCM Checkin";
		}

		/**
		 * Provides a list box for users to choose from a list of Integrity Server configurations
		 * @param configuration Simple configuration name
		 * @return
		 */
		public ListBoxModel doFillServerConfigItems(@QueryParameter String serverConfig)
		{
			return DescriptorImpl.INTEGRITY_DESCRIPTOR.doFillServerConfigItems(serverConfig);
		}
	}

	public static class IntegritySCMCheckinStepExecution extends AbstractSynchronousStepExecution<Void> 
	{
		private static final long serialVersionUID = 7420144581704780618L;
		@Inject
		private transient IntegritySCMCheckinStep step;
		@StepContextParameter
		private transient Run<?, ?> run;
		@StepContextParameter
		private transient FilePath workspace;
		@StepContextParameter
		private transient TaskListener listener;
		@StepContextParameter
		private transient Launcher launcher;

		@Override
		protected Void run() throws Exception 
		{
			IntegritySCMCheckinNotifierStep notifier = new IntegritySCMCheckinNotifierStep(step.getConnectionSettings(), step.getConfigPath(), 
																					step.getIncludes(), step.getExcludes(), step.getItemID());
			notifier.perform(run, workspace, launcher, listener);
			return null;
		}
	}
}