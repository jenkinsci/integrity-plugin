package hudson.scm;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.scm.IntegritySCM.DescriptorImpl;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;

import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.mks.api.response.APIException;
import com.mks.api.response.Response;
import com.mks.api.response.WorkItem;


public class IntegrityCheckpointAction extends Notifier implements Serializable
{
	private static final long serialVersionUID = 3344676447487492553L;
	private static final Logger LOGGER = Logger.getLogger("IntegritySCM");
	private String checkpointLabel;
	private final Log logger = LogFactory.getLog(getClass());
	private String serverConfig;
	private String configurationName;
	
	@Extension
	public static final IntegrityCheckpointDescriptorImpl CHECKPOINT_DESCRIPTOR = new IntegrityCheckpointDescriptorImpl();

	@DataBoundConstructor
	public IntegrityCheckpointAction(String serverConfig, String checkpointLabel)
	{
		setCheckpointLabel(checkpointLabel);
		setServerConfig(serverConfig);
	}
	
	/**
	 * Utility function to convert a groovy expression to a string
	 * @param env Environment containing the name/value pairs for substitution
	 * @param expression Groovy expression string
	 * @return Resolved string
	 */
	public static String evalGroovyExpression(Map<String, String> env, String expression)
	{
		Binding binding = new Binding();
		binding.setVariable("env", env);
		binding.setVariable("sys", System.getProperties());
		CompilerConfiguration config = new CompilerConfiguration();
		//config.setDebug(true);
		GroovyShell shell = new GroovyShell(binding, config);
		Object result = shell.evaluate("return \"" + expression + "\"");
		if (result == null)
		{
			return "";
		}
		else
		{
			return result.toString().trim();
		}
	}
	
	/**
	 * Checks if the given value is a valid Integrity Label.
	 * If it's invalid, this method gives you the reason as string.
	 * @param checkpointLabel The checkpoint label name
	 * @return the error message, or null if label is valid
	 */
	public static String isInvalidTag(String checkpointLabel)
	{
		if (checkpointLabel == null || checkpointLabel.length() == 0)
		{
			return "The label string is empty!";
		}

		char ch = checkpointLabel.charAt(0);
		if (!(('A' <= ch && ch <= 'Z') || ('a' <= ch && ch <= 'z')))
		{
			return "The label must start with an alpha character!";
		}

		for (char invalid : "$,.:;/\\@".toCharArray())
		{
			if (checkpointLabel.indexOf(invalid) >= 0)
			{
				return "The label may cannot contain one of the following characters: $ , . : ; / \\ @";
			}
		}

		return null;
	}	
	
	/**
	 * Returns the label pattern for the Checkpoint
	 * @return Checkpoint Label
	 */
	public String getCheckpointLabel()
	{
		if( checkpointLabel == null || checkpointLabel.length() == 0 )
		{
			return IntegrityCheckpointDescriptorImpl.defaultCheckpointLabel;
		}

		return checkpointLabel;
	}
	
	/**
	 * Sets the label for the Checkpoint
	 * @param checkpointLabel The Checkpoint Label
	 */
	public void setCheckpointLabel(String checkpointLabel)
	{
		this.checkpointLabel = checkpointLabel;
	}
	
	/**
	 * Returns the simple server configuration name
	 * @return
	 */
	public String getServerConfig() 
	{
		return serverConfig;
	}
	
	/**
	 * Sets the simple server configuration name
	 * @param serverConfig
	 */
	public void setServerConfig(String serverConfig) 
	{
		this.serverConfig = serverConfig;
	}
	
	/**
	 * Returns the build configuration name for this project
	 * @return
	 */
	public String getConfigurationName() 
	{
		return configurationName;
	}
	
	/**
	 * Sets the build configuration name for this project
	 * @param configurationName
	 */
	private void setConfigurationName(AbstractBuild<?,?> thisBuild) 
	{
		AbstractProject<?,?> thisProject = thisBuild.getProject();
		if( thisProject.getScm() instanceof IntegritySCM )
		{
			this.configurationName = ((IntegritySCM)thisProject.getScm()).getConfigurationName();
			LOGGER.fine("IntegrityCheckpointAction - Configuration Name = " + configurationName);
		}
		else
		{
			LOGGER.severe("IntegrityCheckpointAction - Configuration Name could not be initialized!");
		}
	}
	
	/**
	 * Applies a project label to a project or subproject
	 * @param api Integrity API Session wrapper
	 * @param listener Jenkins build listener
	 * @param siProject IntegrityCMProject object
	 * @param fullConfigPath Integrity project configuration path
	 * @param projectName Integrity project/subproject name
	 * @param revision Integrity project/subproject revision
	 * @param chkptLabel Checkpoint label string
	 * @throws APIException
	 */
	private void applyProjectLabel(APISession api, BuildListener listener, IntegrityCMProject siProject, String fullConfigPath, String projectName, String revision, String chkptLabel) throws APIException 
	{
		// Looks like the checkpoint was done before the build, so lets apply the label now
		listener.getLogger().println("Preparing to execute si addprojectlabel for " + fullConfigPath);
		listener.getLogger().println(" (" + projectName + ", " +  revision + ")");
		Response res = siProject.addProjectLabel(api, chkptLabel, projectName, revision);
		logger.debug(res.getCommandString() + " returned " + res.getExitCode());        					
		listener.getLogger().println("Successfully added label '" + chkptLabel + "' to revision " + revision);        					
		
	}
	
	/**
	 * Gets the project specific user/password for this build
	 * @param thisBuild Jenkins AbstractBuild
	 * @return
	 */
	private IntegrityConfigurable getProjectSettings(AbstractBuild<?,?> thisBuild) 
	{
		IntegrityConfigurable desSettings = DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig);
		IntegrityConfigurable ciSettings = new IntegrityConfigurable("TEMP_ID", desSettings.getIpHostName(), desSettings.getIpPort(), 
																		desSettings.getHostName(), desSettings.getPort(), desSettings.getSecure(), "", "");		
		AbstractProject<?,?> thisProject = thisBuild.getProject();
		if( thisProject.getScm() instanceof IntegritySCM )
		{
			String userName = ((IntegritySCM)thisProject.getScm()).getUserName();
			ciSettings.setUserName(userName);
			LOGGER.fine("IntegrityCheckpointAction - Project Userame = " + userName);
			
			Secret password = ((IntegritySCM)thisProject.getScm()).getSecretPassword();
			ciSettings.setPassword(password.getEncryptedValue());
			LOGGER.fine("IntegrityCheckpointAction - Project User password = " + password.getEncryptedValue());
		}
		else
		{
			LOGGER.severe("IntegrityCheckpointAction - Failed to initialize project specific connection settings!");
			return desSettings;
		}
		
		return ciSettings;
	}
	
	/**
	 * Executes the actual Integrity Checkpoint operation
	 */
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException
	{
		// Set the configuration name for this build
		setConfigurationName(build);
		
		if( ! Result.SUCCESS.equals(build.getResult()) )
		{
			listener.getLogger().println("Build failed!  Skipping Integrity Checkpoint step!");
			return true;
		}
		
		APISession api = APISession.create(getProjectSettings(build));
		if( null != api )
		{
			// Evaluate the groovy tag name
			Map<String, String> env = build.getEnvironment(listener);
			String chkptLabel = IntegrityCheckpointAction.evalGroovyExpression(env, checkpointLabel);
    		try
    		{
    			// Get information about the project
    			IntegrityCMProject siProject = IntegritySCM.findProject(getConfigurationName());
    			if( null != siProject )
    			{
	    			// Ensure this is not a build project configuration
	    			if( ! siProject.isBuild() )
	    			{
						// A checkpoint wasn't done before the build, so lets checkpoint this build now...
	    				listener.getLogger().println("Preparing to execute si checkpoint for " + siProject.getConfigurationPath());
	    				Response res = siProject.checkpoint(api, chkptLabel);
						logger.debug(res.getCommandString() + " returned " + res.getExitCode());        					
						WorkItem wi = res.getWorkItem(siProject.getConfigurationPath());
						String chkpt = wi.getResult().getField("resultant").getItem().getId();
						listener.getLogger().println("Successfully checkpointed project " + siProject.getConfigurationPath() + 
													" with label '" + chkptLabel + "', new revision is " + chkpt);
	    			}
	    			else
	    			{
	    				// Check to see if the user has requested a checkpoint before the build
	    				if( siProject.getCheckpointBeforeBuild() ) 
	    				{
	    					// Attach label to 'main' project
	    					applyProjectLabel(api, listener, siProject, siProject.getConfigurationPath(), siProject.getProjectName(), siProject.getProjectRevision(), chkptLabel);
	    					
	    					// Attach label to 'subProjects'
	    					for (Hashtable<CM_PROJECT, Object> memberInfo: DerbyUtils.viewSubProjects(siProject.getProjectCacheTable())) 
	    					{
	    						String fullConfigPath = String.class.cast(memberInfo.get(CM_PROJECT.CONFIG_PATH));
	    						String projectName = String.class.cast(memberInfo.get(CM_PROJECT.NAME));
	    						String revision = String.class.cast(memberInfo.get(CM_PROJECT.REVISION));
	   							applyProjectLabel(api, listener, siProject, fullConfigPath, projectName, revision, chkptLabel);
	    					}
	    				}
	    				else
	    				{
	    					listener.getLogger().println("Cannot checkpoint a build project configuration: " + siProject.getConfigurationPath() + "!");
	    				}
	    			}
    			}
    			else
    			{
    				LOGGER.severe("Cannot find Integrity CM Project information for configuration '" + getConfigurationName() + "'");    				
					listener.getLogger().println("ERROR: Cannot find Integrity CM Project information for configuration '" + getConfigurationName() + "'!");    				
    			}
    		}
    		catch( APIException aex )
    		{
        		LOGGER.severe("API Exception caught...");
        		ExceptionHandler eh = new ExceptionHandler(aex);
        		aex.printStackTrace(listener.fatalError(eh.getMessage()));
        		LOGGER.severe(eh.getMessage());
        		LOGGER.fine(eh.getCommand() + " returned exit code " + eh.getExitCode());
        		return false;
    		}
    		catch( SQLException sqlex )
    		{
		    	LOGGER.severe("SQL Exception caught...");
	    		listener.getLogger().println("A SQL Exception was caught!"); 
	    		listener.getLogger().println(sqlex.getMessage());
	    		LOGGER.log(Level.SEVERE, "SQLException", sqlex);
	    		return false;			
    		}
    		finally
    		{
    			api.Terminate();
    		}
        		
		}
		else
		{
			LOGGER.severe("An API Session could not be established!  Cannot perform checkpoint operation!");
			listener.getLogger().println("An API Session could not be established!  Cannot perform checkpoint operation!");
			return false;
		}

		return true;
	}

	/**
	 * Toggles whether or not this needs to run after build is finalized
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
		return CHECKPOINT_DESCRIPTOR;
	}
	
	/**
	 * The relationship of Descriptor and IntegrityCheckpointAction (the describable) is akin to class and object.
	 * This means the descriptor is used to create instances of the describable.
	 * Usually the Descriptor is an internal class in the IntegrityCheckpointAction class named DescriptorImpl. 
	 */
    public static class IntegrityCheckpointDescriptorImpl extends BuildStepDescriptor<Publisher> 
    {
		public static final String defaultCheckpointLabel = "${env['JOB_NAME']}-${env['BUILD_NUMBER']}-${new java.text.SimpleDateFormat(\"yyyy_MM_dd\").format(new Date())}";

    	public IntegrityCheckpointDescriptorImpl()
    	{
        	// Log the construction...
    		super(IntegrityCheckpointAction.class); 
			load();    		
        	LOGGER.fine("IntegrityCheckpointAction.IntegrityCheckpointDescriptorImpl() constructed!");        	            
    	}

		@Override
		public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException
		{
			IntegrityCheckpointAction chkptAction = (IntegrityCheckpointAction) super.newInstance(req, formData);
			LOGGER.fine("IntegrityCheckpointAction.IntegrityCheckpointDescriptorImpl.newInstance() executed!");   
			return chkptAction;
		}    	
    	
		@Override    	
        public String getDisplayName() 
        {
            return "Integrity - CM Checkpoint";
        }

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException
		{
			save();
			LOGGER.fine("IntegrityCheckpointAction.IntegrityCheckpointDescriptorImpl.configure() executed!");
			return super.configure(req, formData);
		}

		public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType)
		{
			LOGGER.fine("IntegrityCheckpointAction.IntegrityCheckpointDescriptorImpl.isApplicable executed!");
			return true;
		}

		/**
		 * Returns the defaultCheckpointLabel for a checkpoint
		 * @return
		 */
		public String getCheckpointLabel()
		{
			return defaultCheckpointLabel;
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
		
		public FormValidation doCheckpointLabelCheck(@QueryParameter("value") final String checkpointLabel) throws IOException, ServletException
		{
			if( checkpointLabel == null || checkpointLabel.length() == 0 )
			{
				return FormValidation.error("Please specify a label for this Checkpoint!");
			}
			else
			{
				// Test to make sure the tag name is valid
				String s = null;
				try
				{
					s = evalGroovyExpression(new HashMap<String, String>(), checkpointLabel);
				}
				catch(CompilationFailedException e)
				{
					return FormValidation.error("Check if quotes, braces, or brackets are balanced. " + e.getMessage());
				}

				if( null != s )
				{
					String errorMessage = isInvalidTag(s);
					if( null != errorMessage )
					{
						return FormValidation.error(errorMessage);
					}
				}
			}
			return FormValidation.ok();
		}
    }
}
