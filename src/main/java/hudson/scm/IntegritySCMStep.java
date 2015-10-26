package hudson.scm;

import java.util.logging.Logger;

import hudson.Extension;
import hudson.scm.IntegritySCM.DescriptorImpl;
import hudson.util.ListBoxModel;
import hudson.util.Secret;

import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class IntegritySCMStep extends SCMStep
{
	private static final long serialVersionUID = 1432177094555038869L;
	private static final Logger LOGGER = Logger.getLogger("IntegritySCM");
	
	private String serverConfig;
	private String userName;
	private Secret password;
	private String configPath;
	private String includeList;
	private String excludeList;
	private boolean cleanCopy;
	private boolean checkpointBeforeBuild;
	
	@DataBoundConstructor
	public IntegritySCMStep(String serverConfig) 
	{
		this.serverConfig = serverConfig;
		this.userName = DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig).getUserName();
		this.password = DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig).getSecretPassword();
		this.includeList = "";
		this.excludeList = "";
		this.cleanCopy = false;
		this.checkpointBeforeBuild = true;
	}

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
	
	 /**
     * Returns the project specific Secret password of the user connecting to the Integrity Server
     * @return
     */
	public Secret getSecretPassword()
	{
		return this.password;
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
	public void setIncludeList(String includeList) 
	{
		this.includeList = includeList;
	}

	public String getIncludeList() 
	{
		return this.includeList;
	}

	@DataBoundSetter
	public void setExcludeList(String excludeList) 
	{
		this.excludeList = excludeList;
	}

	public String getExcludeList()
	{
		return this.excludeList;
	}

	@DataBoundSetter
	public void setCleanCopy(boolean cleanCopy) 
	{
		this.cleanCopy = cleanCopy;
	}

	public boolean getCleanCopy()
	{
		return this.cleanCopy;
	}
	
	@DataBoundSetter
	public void setCheckpointBeforeBuild(boolean checkpointBeforeBuild) 
	{
		this.checkpointBeforeBuild = checkpointBeforeBuild;
	}

	public boolean getCheckpointBeforeBuild()
	{
		return this.checkpointBeforeBuild;
	}
	
	@Override
	protected SCM createSCM()
	{
		LOGGER.fine("IntegritySCMStep.createSCM() invoked!");

		return new IntegritySCM(serverConfig, userName, password, configPath, includeList, excludeList, cleanCopy, checkpointBeforeBuild);
	}

	@Extension(optional = true)
	public static final class IntegritySCMStepDescriptorImpl extends SCMStepDescriptor 
	{

		public IntegritySCMStepDescriptorImpl() 
		{
            // Log the construction...
        	LOGGER.fine("IntegritySCMStepDescriptorImpl() constructed!");			
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
		
		@Override
		public String getFunctionName() 
		{
			return "sico";
		}

		@Override
		public String getDisplayName() 
		{
			return "Integrity SCM Checkout";
		}
	}	
}
