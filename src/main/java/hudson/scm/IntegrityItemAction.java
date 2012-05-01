package hudson.scm;

import java.io.IOException;
import java.io.StringWriter;

import hudson.tasks.Publisher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;

import org.kohsuke.stapler.StaplerRequest;

import net.sf.json.JSONObject;

import com.mks.api.Command;
import com.mks.api.MultiValue;
import com.mks.api.Option;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;
import com.mks.api.response.WorkItemIterator;
import com.mks.api.util.Base64;

public class IntegrityItemAction extends Notifier
{
	private String hostName;
	private int port;
	private boolean secure;
    private String userName;
    private String password;
	private String queryDefinition;
	private String stateField;
	private String successValue;
	private String failureValue;
	private String logField;
	
	@Extension
	public static final IntegrityItemDescriptorImpl ITEM_DESCRIPTOR = new IntegrityItemDescriptorImpl();

    /**
     * Returns the host name of the Integrity (Workflow) Server
     * @return
     */
    public String getHostName()
    {
    	return hostName;
    }
    
    /**
     * Returns the port of the Integrity (Workflow) Server
     * @return
     */    
    public int getPort()
    {
    	return port;
    }

    /**
     * Returns true/false depending on secure sockets are enabled
     * @return
     */        
    public boolean getSecure()
    {
    	return secure;
    }

    /**
     * Returns the User connecting to the Integrity (Workflow) Server
     * @return
     */    
    public String getUserName()
    {
    	return userName;
    }
    
    /**
     * Returns the clear password of the user connecting to the Integrity (Workflow) Server
     * @return
     */        
    public String getPassword()
    {
    	return (password != null && password.length() > 0 ? Base64.decode(password) : password);
    }
	
	/**
	 * Returns the query definition expression
	 * @return Query Definition
	 */
	public String getQueryDefinition()
	{
		return queryDefinition;
	}

	/**
	 * Returns the status/state field for the "build" item
	 * @return
	 */
	public String getStateField()
	{
		return stateField;
	}
	
	/**
	 * Returns the success value that will be set when the build is a success
	 * @return
	 */
	public String getSuccessValue()
	{
		return successValue;
	}
	
	/**
	 * Returns the failure value that will be set when the build has failed
	 * @return
	 */
	public String getFailureValue()
	{
		return failureValue;
	}
	
	/**
	 * Returns the log field associated with the "build" item
	 * @return
	 */
	public String getLogField()
	{
		return logField;
	}
	
    /**
     * Sets the host name of the Integrity (Workflow) Server
     * @param hostName
     */
    public void setHostName(String hostName)
    {
    	this.hostName = hostName;
    }

    /**
     * Sets the port of the Integrity (Workflow) Server
     * @param port
     */    
    public void setPort(int port)
    {
    	this.port = port;
    }

    /**
     * Toggles whether or not secure sockets are enabled
     * @param secure
     */        
    public void setSecure(boolean secure)
    {
    	this.secure = secure;
    }

    /**
     * Sets the User connecting to the Integrity (Workflow) Server
     * @param userName
     */
    public void setUserName(String userName)
    {
    	this.userName = userName;
    }
    
    /**
     * Sets the encrypted Password of the user connecting to the Integrity (Workflow) Server
     * @param password
     */        
    public void setPassword(String password)
    {
    	this.password = Base64.encode(password);
    }
	
	/**
	 * Sets the query definition expression to obtain the build item
	 * @param queryDefinition Query Definition Expression
	 */
	public void setQueryDefinition(String queryDefinition)
	{
		this.queryDefinition = queryDefinition;
	}

	/**
	 * Sets the status/state field for the "build" item
	 * @param stateField Status/State field
	 */
	public void setStateField(String stateField)
	{
		this.stateField = stateField;
	}
	
	/**
	 * Sets the success value that will be used when the build is a success
	 * @param successValue Value to be set when the build is a success
	 */
	public void setSuccessValue(String successValue)
	{
		this.successValue = successValue;
	}
	
	/**
	 * Sets the failure value that will be set when the build has failed
	 * @param failureValue Value to be set when the build has failed
	 */
	public void setFailureValue(String failureValue)
	{
		this.failureValue = failureValue;
	}
	
	/**
	 * Sets the log field associated with the "build" item
	 * @param logField Log field that is used to store the build log
	 */
	public void setLogField(String logField)
	{
		this.logField = logField;
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
     * Creates an authenticated API Session against the Integrity (Workflow) Server
     * @return An authenticated API Session
     */
    public APISession createAPISession()
    {
    	// Attempt to open a connection to the Integrity (Workflow) Server
    	try
    	{
    		Logger.debug("Creating Integrity API Session...");
    		return new APISession(null, 0, hostName, port, userName, Base64.decode(password), secure);
    	}
    	catch(APIException aex)
    	{
    		Logger.error("API Exception caught...");
    		ExceptionHandler eh = new ExceptionHandler(aex);
    		Logger.error(eh.getMessage());
    		Logger.debug(eh.getCommand() + " returned exit code " + eh.getExitCode());
    		aex.printStackTrace();
    		return null;
    	}				
    }
	
	/**
	 * Executes the actual Integrity Update Item operation
	 */
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException
	{
		AbstractProject<?,?> rootProject = getRootProject(build.getProject());
		if( !(rootProject.getScm() instanceof IntegritySCM) )
		{
			listener.getLogger().println("Integrity Item update is being executed for an invalid context!  Current SCM is " + rootProject.getScm() + "!");
			return true;
		}

		APISession api = createAPISession();
		if( null != api )
		{
			try
			{	
	        	try
	        	{
	        		// First lets find the build item
	        		String buildItemID = "";
	        		Command issues = new Command(Command.IM, "issues");
	        		issues.addOption(new Option("fields", "ID"));
	        		issues.addOption(new Option("queryDefinition", queryDefinition));
	        		Response issuesResponse = api.runCommand(issues);
	        		if( null != issuesResponse )
	        		{
	        			WorkItemIterator wit = issuesResponse.getWorkItems();
	        			if( wit.hasNext() )
	        			{
	        				buildItemID = wit.next().getField("ID").getValueAsString();
	        			}
	        			else
	        			{
	        				listener.getLogger().println("Cannot find an Integrity Build Item!  Response from executing custom query is null!");
	        				return false;
	        			}
	        		}
	        		else
	        		{
	        			listener.getLogger().println("Cannot find an Integrity Build Item!  Response from executing custom query is null!");
	        			return false;
	        		}
	        		
	        		// Setup the edit item command to update the build item with the results of the build
	        		Command editIssue = new Command(Command.IM, "editissue");
	        		// Load up the build log, if required
	        		if( null != logField && logField.length() > 0 )
	        		{
	        			StringWriter writer = new StringWriter();
	        			build.getLogText().writeHtmlTo(0, writer);
	        			writer.flush();
	        			writer.close();
	        			// Rid the log of NUL characters as it will blow up the im editissue command
	        			String log = writer.getBuffer().toString().replace((char)0, ' ');
	        			log = log.replaceAll(IntegritySCM.NL, "<br>");
	        			MultiValue mvLog = new MultiValue("=");
	        			mvLog.add(logField);
	        			mvLog.add(log);
	        			editIssue.addOption(new Option("richContentField", mvLog));
	        		}
	        		
	        		// Lets update the build item based on the success/failure of the build
	        		MultiValue mvState = new MultiValue("=");
	        		mvState.add(stateField);
	    			if( Result.SUCCESS.equals(build.getResult()) )
	    			{
	    				// Successful build update
        				listener.getLogger().println("Preparing to update item '" + buildItemID + "' with value " + stateField + " = " + successValue);
        				mvState.add(successValue);
	    			}
	    			else
	    			{
	    				// Failed build update
	    				listener.getLogger().println("Preparing to update item '" + buildItemID + "' with values " + stateField + " = " + failureValue);
	    				mvState.add(failureValue);
	    			}
	    			editIssue.addOption(new Option("field", mvState));
	        		editIssue.addSelection(buildItemID);	    			

	    			// Finally execute the edit item command
	    			Response editIssueResponse = api.runCommand(editIssue);
					Logger.debug(editIssueResponse.getCommandString() + " returned " + editIssueResponse.getExitCode());        					
					listener.getLogger().println("Updated build item '" + buildItemID + "' with build status!");	    			
	        	}
	        	catch(APIException aex)
	        	{
	            	Logger.error("API Exception caught...");
	            	ExceptionHandler eh = new ExceptionHandler(aex);
	            	Logger.error(eh.getMessage());
	            	Logger.debug(eh.getCommand() + " returned exit code " + eh.getExitCode());
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
				Logger.error("Exception caught!  " + e);
				return false;
	        }
		}
		else
		{
			Logger.error("An API Session could not be established!  Cannot update Integrity Build Item!");
			listener.getLogger().println("An API Session could not be established!  Cannot update Integrity Build Item!");
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
		return ITEM_DESCRIPTOR;
	}
	
	/**
	 * The relationship of Descriptor and IntegrityItemAction (the describable) is akin to class and object.
	 * This means the descriptor is used to create instances of the describable.
	 * Usually the Descriptor is an internal class in the IntegrityItemAction class named DescriptorImpl. 
	 */
    public static class IntegrityItemDescriptorImpl extends BuildStepDescriptor<Publisher> 
    {
    	private String defaultQueryDefinition;
    			
    	public IntegrityItemDescriptorImpl()
    	{
        	// Log the construction...
    		super(IntegrityItemAction.class);
    		// Initial variable initializations
			defaultQueryDefinition = "((field[Type] = \"Build Request\") and (field[State] = \"Approved\"))";
			load();
        	Logger.debug("IntegrityItemAction.IntegrityItemDescriptorImpl() constructed!");        	            
    	}

		@Override
		public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException
		{
			IntegrityItemAction itemAction = new IntegrityItemAction();
			itemAction.setHostName(formData.getString("hostName"));
			itemAction.setPort(formData.getInt("port"));
			itemAction.setUserName(formData.getString("userName"));
			itemAction.setPassword(formData.getString("password"));
			itemAction.setSecure(formData.getBoolean("secure"));
			itemAction.setQueryDefinition(formData.getString("queryDefinition"));
			itemAction.setStateField(formData.getString("stateField"));
			itemAction.setSuccessValue(formData.getString("successValue"));
			itemAction.setFailureValue(formData.getString("failureValue"));
			itemAction.setLogField(formData.getString("logField"));			
			Logger.debug("IntegrityItemAction.IntegrityItemDescriptorImpl.newInstance() executed!");   
			return itemAction;
		}    	
    	
		@Override    	
        public String getDisplayName() 
        {
            return "Integrity - Workflow Item";
        }

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException
		{
			defaultQueryDefinition = Util.fixEmptyAndTrim(req.getParameter("mks.queryDefinition"));
			save();
			Logger.debug("IntegrityItemAction.IntegrityItemDescriptorImpl.configure() executed!");
			return super.configure(req, formData);
		}

		public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType)
		{
			Logger.debug("IntegrityItemAction.IntegrityItemDescriptorImpl.isApplicable executed!");
			return true;
		}

	    /**
	     * By default, return the IntegrtySCM host name for the Integrity Server 
	     * @return defaultHostName
	     */
	    public String getDefaultHostName()
	    {
	    	return IntegritySCM.DescriptorImpl.INTEGRITY_DESCRIPTOR.getDefaultHostName();
	    }
	    
	    /**
	     * By default, return the IntegritySCM port for the Integrity Server
	     * @return defaultPort
	     */    
	    public int getDefaultPort()
	    {
	    	return IntegritySCM.DescriptorImpl.INTEGRITY_DESCRIPTOR.getDefaultPort();
	    }

	    /**
	     * By default, return the IntegritySCM secure setting for the Integrity Server
	     * @return defaultSecure
	     */        
	    public boolean getDefaultSecure()
	    {
	    	return IntegritySCM.DescriptorImpl.INTEGRITY_DESCRIPTOR.getDefaultSecure();
	    }

	    /**
	     * By default, return the IntegritySCM for the User connecting to the Integrity Server
	     * @return defaultUserName
	     */    
	    public String getDefaultUserName()
	    {
	    	return IntegritySCM.DescriptorImpl.INTEGRITY_DESCRIPTOR.getDefaultUserName();
	    }
	    
	    /**
	     * By default, return the IntegritySCM user's password connecting to the Integrity Server
	     * @return defaultPassword
	     */        
	    public String getDefaultPassword()
	    {
	    	return IntegritySCM.DescriptorImpl.INTEGRITY_DESCRIPTOR.getDefaultPassword();
	    }

	    /**
	     * Returns the default query definition that will be used to find the 'build' item
	     * @return defaultQueryDefinition
	     */
		public String getDefaultQueryDefinition()
		{
			return defaultQueryDefinition;
		}
    }	
}
