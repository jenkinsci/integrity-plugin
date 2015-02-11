package hudson.scm;

import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Logger;

import hudson.scm.IntegritySCM.DescriptorImpl;
import hudson.tasks.Publisher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.Extension;
import hudson.Launcher;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.util.ListBoxModel;
import hudson.util.Secret;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import net.sf.json.JSONObject;

public class IntegrityCheckinAction extends Notifier implements Serializable
{
	private static final long serialVersionUID = 4647604916824363519L;
	private static final Logger LOGGER = Logger.getLogger("IntegritySCM");
	private String ciConfigPath;
	private String ciWorkspaceDir;
	private String includes;
	private String excludes;
	private String serverConfig;
	private String configurationName;
	
	@Extension
	public static final IntegrityCheckinDescriptorImpl CHECKIN_DESCRIPTOR = new IntegrityCheckinDescriptorImpl();

	@DataBoundConstructor
	public IntegrityCheckinAction(String ciConfigPath, String ciWorkspaceDir, String includes, String excludes, String serverConfig, String configurationName)
	{
		setCiConfigPath(ciConfigPath);
		setCiWorkspaceDir(ciWorkspaceDir);
		setIncludes(includes);
		setExcludes(excludes);
		setServerConfig(serverConfig);
		setConfigurationName(configurationName);

	}
	
    /**
	 * Returns the configuration path for the project to check-in artifacts after the build
	 * @return
	 */
    public String getCiConfigPath()
    {
    	return this.ciConfigPath;
    }
   
    /**
	 * Returns the workspace directory containing the check-in artifacts created as a result of the build
	 * @return
	 */
    public String getCiWorkspaceDir()
    {
    	return this.ciWorkspaceDir;
    }   

    /**
     * Returns the Ant-style includes filter for the check-in workspace folder
     * @return
     */
    public String getIncludes()
    {
    	return this.includes;
    }
    
    /**
     * Returns the Ant-style excludes filter for the check-in workspace folder
     * @return
     */
    public String getExcludes()
    {
    	return this.excludes;
    }
    
    /**
     * Returns the simple server configuration name
     * @return
     */
	public String getServerConfig() 
	{
		return this.serverConfig;
	}
	
	/**
	 * Returns the build configuration name for this project
	 * @return
	 */
	public String getConfigurationName() 
	{
		return this.configurationName;
	}
	
    /**
	 * Sets the configuration path for the project to check-in artifacts after the build
	 * @param ciConfigPath
	 */
    public void setCiConfigPath(String ciConfigPath)
    {
    	this.ciConfigPath = ciConfigPath;
    }
   
    /**
	 * Sets the workspace directory containing the check-in artifacts created as a result of the build
	 * @param ciWorkspaceDir
	 */
    public void setCiWorkspaceDir(String ciWorkspaceDir)
    {
    	this.ciWorkspaceDir = ciWorkspaceDir;
    }   
	
    /**
     * Sets the Ant-style includes filter for the check-in workspace folder
     * @param includes
     */
    public void setIncludes(String includes)
    {
    	this.includes = includes;
    }

    /**
     * Sets the Ant-style excludes filter for the check-in workspace folder
     * @param excludes
     */
    public void setExcludes(String excludes)
    {
    	this.excludes = excludes;
    }
	
    /**
     * Sets the simple server configuration name
     * @param configurationName
     */
	public void setServerConfig(String serverConfig) 
	{
		this.serverConfig = serverConfig;		
	}

	
    /**
     * Sets the build configuration name for this project
     * @param configurationName
     */
	public void setConfigurationName(String configurationName) 
	{
		this.configurationName = configurationName;		
	}

	/**
	 * Gets the project specific user/password for this build
	 * @param thisBuild Jenkins AbstractBuild
	 * @return
	 */
	private IntegrityConfigurable getProjectSettings(AbstractBuild<?,?> thisBuild) 
	{
		IntegrityConfigurable desSettings = DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig);
		IntegrityConfigurable ciSettings = new IntegrityConfigurable("TEMP_ID", desSettings.getIpHostName(), desSettings.getIpPort(), desSettings.getHostName(), 
																		desSettings.getPort(), desSettings.getSecure(), "", "");		
		AbstractProject<?,?> thisProject = thisBuild.getProject();
		if( thisProject.getScm() instanceof IntegritySCM )
		{
			String userName = ((IntegritySCM)thisProject.getScm()).getUserName();
			ciSettings.setUserName(userName);
			LOGGER.fine("IntegrityCheckinAction - Project Userame = " + userName);
			
			Secret password = ((IntegritySCM)thisProject.getScm()).getSecretPassword();
			ciSettings.setPassword(password.getEncryptedValue());
			LOGGER.fine("IntegrityCheckinAction - Project User password = " + password.getEncryptedValue());
		}
		else
		{
			LOGGER.severe("IntegrityCheckinAction - Failed to initialize project specific connection settings!");
			return desSettings;
		}
		
		return ciSettings;
	}
	
	/**
	 * Executes the actual Integrity Checkpoint operation
	 */
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException
	{
		if( ! Result.SUCCESS.equals(build.getResult()) )
		{
			LOGGER.fine("Build failed!  Skipping Integrity Checkin step!");
			listener.getLogger().println("Build failed!  Skipping Integrity Checkin step!");
			return true;
		}

		// Create our Integrity check-in task
        IntegrityCheckinTask ciTask = new IntegrityCheckinTask(ciConfigPath, ciWorkspaceDir, includes, excludes, build, listener, getProjectSettings(build));
        
        // Execute the check-in task and return the overall result
        return build.getWorkspace().act(ciTask);
	}

	/**
	 * Toggles whether or not this needs to run after build is finalized
	 * Returning false, so that a check-in failure will cause a failed build
	 */
	@Override
	public boolean needsToRunAfterFinalized()
	{
		return false;
	}

	/**
	 * Returns the build step we're monitoring
	 */
	public BuildStepMonitor getRequiredMonitorService()
	{
		return BuildStepMonitor.BUILD;
	}

	/**
	 * Return the instance of DescriptorImpl object for this class
	 */
	@Override
	public BuildStepDescriptor<Publisher> getDescriptor()
	{
		return CHECKIN_DESCRIPTOR;
	}
	
	/**
	 * The relationship of Descriptor and IntegrityCheckpointAction (the describable) is akin to class and object.
	 * This means the descriptor is used to create instances of the describable.
	 * Usually the Descriptor is an internal class in the IntegrityCheckpointAction class named DescriptorImpl. 
	 */
    public static class IntegrityCheckinDescriptorImpl extends BuildStepDescriptor<Publisher> 
    {
    	public IntegrityCheckinDescriptorImpl()
    	{
        	// Log the construction...
    		super(IntegrityCheckinAction.class);
			load();    		
        	LOGGER.fine("IntegrityCheckinAction.IntegrityCheckinDescriptorImpl() constructed!");        	            
    	}

		@Override
		public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException
		{
			IntegrityCheckinAction ciAction = (IntegrityCheckinAction) super.newInstance(req, formData);
			LOGGER.fine("IntegrityCheckinAction.IntegrityCheckinDescriptorImpl.newInstance() executed!");   
			return ciAction;
		}    	
    	
		@Override    	
        public String getDisplayName() 
        {
            return "Integrity - CM Checkin";
        }

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException
		{
			save();
			LOGGER.fine("IntegrityCheckinAction.IntegrityCheckinDescriptorImpl.configure() executed!");
			return super.configure(req, formData);
		}

		public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType)
		{
			LOGGER.fine("IntegrityCheckinAction.IntegrityCheckinDescriptorImpl.isApplicable executed!");
			return true;
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
}
