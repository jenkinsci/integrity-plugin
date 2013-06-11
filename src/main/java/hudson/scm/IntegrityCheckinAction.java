package hudson.scm;

import java.io.IOException;

import hudson.tasks.Publisher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.Extension;
import hudson.Launcher;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;

import org.kohsuke.stapler.StaplerRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sf.json.JSONObject;

public class IntegrityCheckinAction extends Notifier
{
	private String ciConfigPath;
	private String ciWorkspaceDir;
	private String includes;
	private String excludes;
	private final Log logger = LogFactory.getLog(getClass());
	
	@Extension
	public static final IntegrityCheckinDescriptorImpl CHECKIN_DESCRIPTOR = new IntegrityCheckinDescriptorImpl();

    /**
	 * Returns the configuration path for the project to check-in artifacts after the build
	 * @return
	 */
    public String getciConfigPath()
    {
		if( ciConfigPath == null || ciConfigPath.length() == 0 )
		{
			ciConfigPath = CHECKIN_DESCRIPTOR.getDefaultciConfigPath();
		}
		
    	return ciConfigPath;
    }
   
    /**
	 * Returns the workspace directory containing the check-in artifacts created as a result of the build
	 * @return
	 */
    public String getciWorkspaceDir()
    {
		if( ciWorkspaceDir == null || ciWorkspaceDir.length() == 0 )
		{
			ciWorkspaceDir = CHECKIN_DESCRIPTOR.getDefaultciWorkspaceDir();
		}
		
    	return ciWorkspaceDir;
    }   

    /**
     * Returns the Ant-style includes filter for the check-in workspace folder
     * @return
     */
    public String getIncludes()
    {
    	if( includes == null || includes.length() == 0 )
    	{
    		includes = CHECKIN_DESCRIPTOR.getDefaultIncludes();
    	}
    	
    	return includes;
    }
    
    /**
     * Returns the Ant-style excludes filter for the check-in workspace folder
     * @return
     */
    public String getExcludes()
    {
    	if( includes == null || includes.length() == 0 )
    	{
    		excludes = CHECKIN_DESCRIPTOR.getDefaultExcludes();
    	}
    	
    	return excludes;
    }
    
    /**
	 * Sets the configuration path for the project to check-in artifacts after the build
	 * @param ciConfigPath
	 */
    public void setciConfigPath(String ciConfigPath)
    {
    	this.ciConfigPath = ciConfigPath;
    }
   
    /**
	 * Sets the workspace directory containing the check-in artifacts created as a result of the build
	 * @param ciWorkspaceDir
	 */
    public void setciWorkspaceDir(String ciWorkspaceDir)
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
	 * Obtains the root project for the build
	 * @param abstractProject
	 * @return
	 */
	private AbstractProject<?,?> getRootProject(AbstractProject<?,?> abstractProject)
	{
		if (abstractProject.getParent() instanceof Hudson)
		{
			return abstractProject;
		}
		else
		{
			return getRootProject((AbstractProject<?,?>) abstractProject.getParent());
		}
	}
	
	/**
	 * Executes the actual Integrity Checkpoint operation
	 */
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException
	{
		if( ! Result.SUCCESS.equals(build.getResult()) )
		{
			logger.debug("Build failed!  Skipping Integrity Checkin step!");
			listener.getLogger().println("Build failed!  Skipping Integrity Checkin step!");
			return true;
		}

		AbstractProject<?,?> rootProject = getRootProject(build.getProject());

		if( !(rootProject.getScm() instanceof IntegritySCM) )
		{
			logger.debug("Integrity Check-in action is being executed for an invalid context!  Current SCM is " + rootProject.getScm() + "!");
			listener.getLogger().println("Integrity Check-in action is being executed for an invalid context!  Current SCM is " + rootProject.getScm() + "!");
			return true;
		}

		// Create our Integrity check-in task
        IntegrityCheckinTask ciTask = new IntegrityCheckinTask(ciConfigPath, ciWorkspaceDir, includes, excludes, build, listener);
        
        // Execute the check-in task and return the overall result
        return build.getWorkspace().act(ciTask);
	}

	/**
	 * Toggles whether or not this needs to run after build is finalized
	 */
	@Override
	public boolean needsToRunAfterFinalized()
	{
		return true;
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
    	private static Log desLogger = LogFactory.getLog(IntegrityCheckinDescriptorImpl.class);
		private String defaultciConfigPath;
		private String defaultciWorkspaceDir;
		private String defaultIncludes;
		private String defaultExcludes;
    			
    	public IntegrityCheckinDescriptorImpl()
    	{
        	// Log the construction...
    		super(IntegrityCheckinAction.class);
			this.defaultciConfigPath = "";
			this.defaultciWorkspaceDir = "";
			this.defaultIncludes = "";
			this.defaultExcludes = "";
			load();    		
        	desLogger.debug("IntegrityCheckinAction.IntegrityCheckinDescriptorImpl() constructed!");        	            
    	}

		@Override
		public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException
		{
			IntegrityCheckinAction ciAction = new IntegrityCheckinAction();
			ciAction.setciConfigPath(formData.getString("ciConfigPath"));
			ciAction.setciWorkspaceDir(formData.getString("ciWorkspaceDir"));
			ciAction.setIncludes(formData.getString("includes"));
			ciAction.setExcludes(formData.getString("excludes"));
			desLogger.debug("IntegrityCheckinAction.IntegrityCheckinDescriptorImpl.newInstance() executed!");   
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
			this.defaultciConfigPath = req.getParameter("ciConfigPath");
			this.defaultciWorkspaceDir = req.getParameter("ciWorkspaceDir");
			save();
			desLogger.debug("IntegrityCheckinAction.IntegrityCheckinDescriptorImpl.configure() executed!");
			return super.configure(req, formData);
		}

		public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType)
		{
			desLogger.debug("IntegrityCheckinAction.IntegrityCheckinDescriptorImpl.isApplicable executed!");
			return true;
		}

		public String getDefaultciConfigPath()
		{
			return defaultciConfigPath;
		}

		public String getDefaultciWorkspaceDir()
		{
			return defaultciWorkspaceDir;
		}
		
		public String getDefaultIncludes()
		{
			return defaultIncludes;
		}		

		public String getDefaultExcludes()
		{
			return defaultExcludes;
		}		
		
		public void setDefaultciConfigPath(String defaultciConfigPath)
		{
			this.defaultciConfigPath = defaultciConfigPath;
		}

		public void setDefaultciWorkspaceDir(String defaultciWorkspaceDir)
		{
			this.defaultciWorkspaceDir = defaultciWorkspaceDir;
		}
		
		public void setDefaultIncludes(String defaultIncludes)
		{
			this.defaultIncludes = defaultIncludes;
		}
		
		public void setDefaultExcludes(String defaultExcludes)
		{
			this.defaultExcludes = defaultExcludes;
		}			
    }	
}
