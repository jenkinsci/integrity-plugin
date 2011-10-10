package hudson.scm;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
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
import hudson.util.FormValidation;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;

import net.sf.json.JSONObject;

import com.mks.api.response.APIException;
import com.mks.api.response.Response;
import com.mks.api.response.WorkItem;

public class IntegrityCheckpointAction extends Notifier
{
	private String tagName;
	private final Log logger = LogFactory.getLog(getClass());
	
	@Extension
	public static final IntegrityCheckpointDescriptorImpl CHECKPOINT_DESCRIPTOR = new IntegrityCheckpointDescriptorImpl();

	/**
	 * Returns the label pattern for the Checkpoint
	 * @return Checkpoint Label
	 */
	public String getTagName()
	{
		if( tagName == null || tagName.length() == 0 )
		{
			return CHECKPOINT_DESCRIPTOR.getDefaultTagName();
		}

		return tagName;
	}
	
	/**
	 * Sets the label for the Checkpoint
	 * @param tagName The Checkpoint Label
	 */
	public void setTagName(String tagName)
	{
		this.tagName = tagName;
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
			listener.getLogger().println("Build failed!  Skipping Integrity Checkpoint step!");
			return true;
		}

		AbstractProject<?,?> rootProject = getRootProject(build.getProject());

		if( !(rootProject.getScm() instanceof IntegritySCM) )
		{
			listener.getLogger().println("Integrity Checkpoint is being executed for an invalid context!  Current SCM is " + rootProject.getScm() + "!");
			return true;
		}

		IntegritySCM scm = IntegritySCM.class.cast(rootProject.getScm());
		APISession api = scm.createAPISession();
		if( null != api )
		{
			// Evaluate the groovy tag name
			Map<String, String> env = build.getEnvironment(listener);
			String chkptLabel = CHECKPOINT_DESCRIPTOR.evalGroovyExpression(env, tagName);
			try
			{	
        		try
        		{
        			// Get information about the project
        			IntegrityCMProject siProject = scm.getIntegrityProject();
        			// Ensure this is not a build project configuration
        			if( ! siProject.isBuild() )
        			{
    					// A checkpoint wasn't done before the build, so lets checkpoint this build now...
        				listener.getLogger().println("Preparing to execute si checkpoint for " + siProject.getConfigurationPath());
        				Response res = siProject.checkpoint(api, chkptLabel);
    					logger.debug(res.getCommandString() + " returned " + res.getExitCode());        					
    					WorkItem wi = res.getWorkItem(siProject.getConfigurationPath());
    					String chkpt = wi.getResult().getField("resultant").getItem().getId();
    					listener.getLogger().println("Successfully checkpointed project " + scm.getConfigPath() + 
    												" with label '" + chkptLabel + "', new revision is " + chkpt);
        			}
        			else
        			{
        				// Check to see if the user has requested a checkpoint before the build
        				if( scm.getCheckpointBeforeBuild() )
        				{
        					// Looks like the checkpoint was done before the build, so lets apply the label now
	        				listener.getLogger().println("Preparing to execute si addprojectlabel for " + siProject.getConfigurationPath());
	        				Response res = siProject.addProjectLabel(api, chkptLabel);
	    					logger.debug(res.getCommandString() + " returned " + res.getExitCode());        					
	    					listener.getLogger().println("Successfully added label '" + chkptLabel + "' to revision " + siProject.getProjectRevision());        					
        				}
        				else
        				{
        					listener.getLogger().println("Cannot checkpoint a build project configuration: " + scm.getConfigPath() + "!");
        				}
        			}
        		}
        		catch(APIException aex)
        		{
            		logger.error("API Exception caught...");
            		ExceptionHandler eh = new ExceptionHandler(aex);
            		logger.error(eh.getMessage());
            		logger.debug(eh.getCommand() + " returned exit code " + eh.getExitCode());
            		throw new Exception(eh.getMessage());
        		}
        		finally
        		{
        			api.Terminate();
        		}
        	}
        	catch (Throwable e) 
        	{
        		e.printStackTrace(listener.fatalError(e.getMessage()));
				logger.error("Exception caught!  " + e);
				return false;
        	}
		}
		else
		{
			logger.error("An API Session could not be established!  Cannot perform checkpoint operation!");
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
		return CHECKPOINT_DESCRIPTOR;
	}
	
	/**
	 * The relationship of Descriptor and IntegrityCheckpointAction (the describable) is akin to class and object.
	 * This means the descriptor is used to create instances of the describable.
	 * Usually the Descriptor is an internal class in the IntegrityCheckpointAction class named DescriptorImpl. 
	 */
    public static class IntegrityCheckpointDescriptorImpl extends BuildStepDescriptor<Publisher> 
    {
    	private static Log desLogger = LogFactory.getLog(IntegrityCheckpointDescriptorImpl.class);
		private String defaultTagName;
    			
    	public IntegrityCheckpointDescriptorImpl()
    	{
        	// Log the construction...
    		super(IntegrityCheckpointAction.class);
			this.defaultTagName = "${env['JOB_NAME']}-${env['BUILD_NUMBER']}-${new java.text.SimpleDateFormat(\"yyyy_MM_dd\").format(new Date())}";
			load();    		
        	desLogger.debug("IntegrityCheckpointAction.IntegrityCheckpointDescriptorImpl() constructed!");        	            
    	}

		@Override
		public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException
		{
			IntegrityCheckpointAction chkptAction = new IntegrityCheckpointAction();
			chkptAction.setTagName(formData.getString("tagName"));
			desLogger.debug("IntegrityCheckpointAction.IntegrityCheckpointDescriptorImpl.newInstance() executed!");   
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
			this.defaultTagName = req.getParameter("tagName");
			save();
			desLogger.debug("IntegrityCheckpointAction.IntegrityCheckpointDescriptorImpl.configure() executed!");
			return super.configure(req, formData);
		}

		public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType)
		{
			desLogger.debug("IntegrityCheckpointAction.IntegrityCheckpointDescriptorImpl.isApplicable executed!");
			return true;
		}

		public String getDefaultTagName()
		{
			return defaultTagName;
		}

		public void setDefaultTagName(String defaultTagName)
		{
			this.defaultTagName = defaultTagName;
		}

		public FormValidation doTagNameCheck(@QueryParameter("value") final String tagName) throws IOException, ServletException
		{
			if( tagName == null || tagName.length() == 0 )
			{
				return FormValidation.error("Please specify a label for this Checkpoint!");
			}
			else
			{
				// Test to make sure the tag name is valid
				String s = null;
				try
				{
					s = evalGroovyExpression(new HashMap<String, String>(), tagName);
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

		/**
		 * Checks if the given value is a valid Integrity Label.
		 * If it's invalid, this method gives you the reason as string.
		 * @param tagName The checkpoint label name
		 * @return the error message, or null if label is valid
		 */
		private String isInvalidTag(String tagName)
		{
			if (tagName == null || tagName.length() == 0)
			{
				return "The label string is empty!";
			}

			char ch = tagName.charAt(0);
			if (!(('A' <= ch && ch <= 'Z') || ('a' <= ch && ch <= 'z')))
			{
				return "The label must start with an alpha character!";
			}

			for (char invalid : "$,.:;/\\@".toCharArray())
			{
				if (tagName.indexOf(invalid) >= 0)
				{
					return "The label may cannot contain one of the following characters: $ , . : ; / \\ @";
				}
			}

			return null;
		}
		
		public String evalGroovyExpression(Map<String, String> env, String expression)
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
    }	
}
