package hudson.scm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Map;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import hudson.util.FormValidation;

import net.sf.json.JSONObject;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory; 
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import com.mks.api.Command;
import com.mks.api.Option;
import com.mks.api.MultiValue;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;
import com.mks.api.util.Base64;

/**
 * This class provides an integration between Hudson for Continuous Builds and 
 * MKS Integrity for Configuration Management
 */
public class IntegritySCM extends SCM implements Serializable
{
	private static final long serialVersionUID = 7559894846609712683L;
	public static final String NL = System.getProperty("line.separator");
	public static final String FS = System.getProperty("file.separator");
	public static final SimpleDateFormat SDF = new SimpleDateFormat("MMM dd, yyyy h:mm:ss a");	
	private final Log logger = LogFactory.getLog(getClass()); 
	private String integrityURL;
	private IntegrityRepositoryBrowser browser;
	private String hostName;
	private int port;
	private boolean secure;
	private String configPath;
    private String userName;
    private String password;
	private boolean cleanCopy;
	private boolean skipAuthorInfo = false;
	private String lineTerminator = "native";
	private boolean restoreTimestamp = true;
	private transient IntegrityCMProject siProject; /* This will get initialized when checkout is executed */

	/**
	 * Create a constructor that takes non-transient fields, and add the annotation @DataBoundConstructor to it. 
	 * Using the annotation helps the Stapler class to find which constructor that should be used when 
	 * automatically copying values from a web form to a class.
	 */
    @DataBoundConstructor
	public IntegritySCM(IntegrityRepositoryBrowser browser, String hostName, int port, boolean secure, String configPath, 
							String userName, String password, boolean cleanCopy, String lineTerminator, 
							boolean restoreTimestamp, boolean skipAuthorInfo)
	{
    	// Log the construction
    	logger.info("IntegritySCM constructor has been invoked!");
		// Initialize the class variables
    	this.browser = browser;
    	this.hostName = hostName;
    	this.port = port;
    	this.secure = secure;
    	this.configPath = configPath;
    	this.userName = userName;
    	this.password = Base64.encode(password);
    	this.cleanCopy = cleanCopy;
    	this.lineTerminator = lineTerminator;
    	this.restoreTimestamp = restoreTimestamp;
    	this.skipAuthorInfo = skipAuthorInfo;
    	
    	// Initialize the Integrity URL
    	initIntegrityURL();

    	// Log the parameters received
    	logger.info("URL: " + this.integrityURL);
    	logger.info("Host: " + this.hostName);
    	logger.info("Port: " + this.port);
    	logger.info("User: " + this.userName);
    	logger.info("Password: " + this.password);
    	logger.info("Secure: " + this.secure);
    	logger.info("Project: " + this.configPath);
    	logger.info("Line Terminator: " + this.lineTerminator);
    	logger.info("Restore Timestamp: " + this.restoreTimestamp);
    	logger.info("Clean: " + this.cleanCopy);
    	logger.info("Skip Author Info: " + this.skipAuthorInfo);
	}

    @Override
    @Exported
    /**
     * Returns the MKS Integrity Repository Browser
     */
    public IntegrityRepositoryBrowser getBrowser() 
    {
        return browser;
    }
    
    /**
     * Returns the host name of the MKS Integrity Server
     * @return
     */
    public String getHostName()
    {
    	return hostName;
    }

    /**
     * Returns the port of the MKS Integrity Server
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
     * Returns the Project or Configuration Path for a MKS Integrity Source Project
     * @return
     */        
    public String getConfigPath()
    {
    	return configPath;
    }

    /**
     * Returns the User connecting to the MKS Integrity Server
     * @return
     */    
    public String getUserName()
    {
    	return userName;
    }
    
    /**
     * Returns the clear password of the user connecting to the MKS Integrity Server
     * @return
     */        
    public String getPassword()
    {
    	return Base64.decode(password);
    }
    
    /**
     * Returns true/false depending on whether or not the workspace is required to be cleaned
     * @return
     */        
    public boolean getCleanCopy()
    {
    	return cleanCopy; 
    }

    /**
     * Returns the line terminator to apply when obtaining files from the MKS Integrity Server
     * @return
     */        
    public String getLineTerminator()
    {
    	return lineTerminator; 
    }

    /**
     * Returns true/false depending on whether or not the restore timestamp option is in effect
     * @return
     */        
    public boolean getRestoreTimestamp()
    {
    	return restoreTimestamp; 
    }
    
    /**
     * Returns true/false depending on whether or not to use 'si revisioninfo' to determine author information
     * @return
     */        
    public boolean getSkipAuthorInfo()
    {
    	return skipAuthorInfo; 
    }    

    /**
     * Sets the host name of the MKS Integrity Server
     * @return
     */
    public void setHostName(String hostName)
    {
    	this.hostName = hostName;
    	initIntegrityURL();
    }

    /**
     * Sets the port of the MKS Integrity Server
     * @return
     */    
    public void setPort(int port)
    {
    	this.port = port;
    	initIntegrityURL();
    }

    /**
     * Toggles whether or not secure sockets are enabled
     * @return
     */        
    public void setSecure(boolean secure)
    {
    	this.secure = secure;
    	initIntegrityURL();
    }

    /**
     * Sets the Project or Configuration Path for a MKS Integrity Source Project
     * @return
     */        
    public void setConfigPath(String configPath)
    {
    	this.configPath = configPath;
    }

    /**
     * Sets the User connecting to the MKS Integrity Server
     * @return
     */    
    public void setUserName(String userName)
    {
    	this.userName = userName;
    }
    
    /**
     * Sets the encrypted Password of the user connecting to the MKS Integrity Server
     * @return
     */        
    public void setPassword(String password)
    {
    	this.password = Base64.encode(password);;
    }
    
    /**
     * Toggles whether or not the workspace is required to be cleaned
     * @return
     */        
    public void setCleanCopy(boolean cleanCopy)
    {
    	this.cleanCopy = cleanCopy; 
    }

    /**
     * Sets the line terminator to apply when obtaining files from the MKS Integrity Server
     * @return
     */        
    public void getLineTerminator(String lineTerminator)
    {
    	this.lineTerminator = lineTerminator; 
    }

    /**
     * Toggles whether or not to restore the timestamp for individual files
     * @return
     */        
    public void setRestoreTimestamp(boolean restoreTimestamp)
    {
    	this.restoreTimestamp = restoreTimestamp; 
    }

    /**
     * Toggles whether or not to use 'si revisioninfo' to determine author information
     * @return
     */        
    public void setSkipAuthorInfo(boolean skipAuthorInfo)
    {
    	this.skipAuthorInfo = skipAuthorInfo; 
    }
    
    /**
     * Provides a mechanism to update the Integrity URL, based on updates
     * to the hostName/port/secure variables
     */
    private void initIntegrityURL()
    {
    	// Initialize the Integrity URL
		if( secure )
		{
			integrityURL = "https://" + hostName + ":" + String.valueOf(port); 
		}
		else
		{
			integrityURL = "http://" + hostName + ":" + String.valueOf(port);
		}
    }
    
    /**
     * Creates an authenticated API Session against the MKS Integrity Server
     * @return An authenticated API Session
     */
    public APISession createAPISession()
    {
    	// Attempt to open a connection to the MKS Integrity Server
    	try
    	{
    		logger.info("Creating MKS API Session...");
    		return new APISession(hostName, port, userName, Base64.decode(password), secure);
    	}
    	catch(APIException aex)
    	{
    		logger.error("API Exception caught...");
    		ExceptionHandler eh = new ExceptionHandler(aex);
    		logger.error(eh.getMessage());
    		logger.info(eh.getCommand() + " returned exit code " + eh.getExitCode());
    		aex.printStackTrace();
    		return null;
    	}				
    }

    /**
     * Returns the MKS Integrity Configuration Management Project
     * @return
     */
    public IntegrityCMProject getIntegrityProject()
    {
    	return siProject;
    }
    
	/**
	 * Adds MKS Integrity CM Project info to the build variables  
	 */
	@Override 
	public void buildEnvVars(AbstractBuild<?, ?> build, Map<String, String> env)
	{ 
		super.buildEnvVars(build, env);
		logger.info("buildEnvVars() invoked...!");		
		env.put("MKSSI_PROJECT", configPath);
		env.put("MKSSI_HOST", hostName);
		env.put("MKSSI_PORT", String.valueOf(port));
		env.put("MKSSI_USER", userName);
	}
	
	/**
	 * Overridden calcRevisionsFromBuild function
	 * Returns the current project configuration which can be used to difference any future configurations
	 * @see hudson.scm.SCM#calcRevisionsFromBuild(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.TaskListener)
	 */
	@Override
	public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener) throws IOException, InterruptedException 
	{
		// Just logging the call for now
		logger.info("calcRevisionsFromBuild() invoked...!");
		Object obj = getIntegrityCMProjectState(build);
		// Now that we've loaded the object, lets make sure it is an IntegrityCMProject!
		if( obj instanceof IntegrityCMProject && null != obj )
		{
			// Cast object to an IntegrityCMProject
			IntegrityCMProject cmProject = (IntegrityCMProject) obj;
			// Returns the current Project Configuration for the requested build
			return new IntegrityRevisionState(cmProject);			
		}
		else
		{
            // Not sure what object we've loaded, but its no IntegrityCMProject!
			logger.info("Cannot construct project state for build " + build.getNumber() + "!");
			// Returns the current Project Configuration for the requested build
			return new IntegrityRevisionState(null);						
		}		
	}

	/**
	 * Primes the MKS Integrity Project metadata information
	 * @param api MKS API Session
	 * @return response MKS API Response
	 * @throws APIException
	 */
	private Response initializeCMProject(APISession api) throws APIException
	{
		// Get the project information for this project
		Command siProjectInfoCmd = new Command(Command.SI, "projectinfo");
		siProjectInfoCmd.addOption(new Option("project", configPath));	
		logger.info("Preparing to execute si projectinfo for " + configPath);
		Response infoRes = api.runCommand(siProjectInfoCmd);
		logger.info(infoRes.getCommandString() + " returned " + infoRes.getExitCode());
		// Initialize our siProject class variable
		siProject = new IntegrityCMProject(infoRes.getWorkItems().next());
		// Set the project options
		siProject.setLineTerminator(lineTerminator);
		siProject.setRestoreTimestamp(restoreTimestamp);
		siProject.setSkipAuthorInfo(skipAuthorInfo);
		return infoRes;
	}

	/**
	 * Primes the MKS Integrity Project Member metadata information
	 * @param api MKS API Session
	 * @return response MKS API Response
	 * @throws APIException
	 */
	private Response initializeCMProjectMembers(APISession api) throws APIException
	{
		// Lets parse this project
		Command siViewProjectCmd = new Command(Command.SI, "viewproject");
		siViewProjectCmd.addOption(new Option("recurse"));
		siViewProjectCmd.addOption(new Option("project", siProject.getConfigurationPath()));
		MultiValue mvFields = new MultiValue(",");
		mvFields.add("name");
		mvFields.add("context");
		mvFields.add("memberrev");
		mvFields.add("membertimestamp");
		mvFields.add("memberdescription");
		siViewProjectCmd.addOption(new Option("fields", mvFields));
		logger.info("Preparing to execute si viewproject for " + siProject.getConfigurationPath());
		Response viewRes = api.runCommand(siViewProjectCmd);
		logger.info(viewRes.getCommandString() + " returned " + viewRes.getExitCode());
		siProject.parseProject(viewRes.getWorkItems(), api);
		return viewRes;
	}
	
    /**
     * Toggles whether or not a workspace is required for polling
     * Since, we're using a Server Integration Point in the MKS API, 
     * we do not require a workspace.
     */
    @Override
    public boolean requiresWorkspaceForPolling() 
    {
        return false;
    }
    
    private Object getIntegrityCMProjectState(AbstractBuild<?,?> build) throws IOException
    {
    	// Make sure this build is not null, before processing it!
    	if( null != build )
    	{
	        // Lets make absolutely certain we've found a useful build, 
	        File viewProjectFile = getViewProjectResponseFile(build);
	        if( ! viewProjectFile.exists() )
	        {
	        	// There is no project state for this build!
	        	logger.info("Project state not found for build " + build.getNumber() + "!");
	        	return null;
	        }
	        else
	        {
	        	// We've found a build that contains the project state...  load it up!			
				logger.info("Attempting to load up project state for build " + build.getNumber() + "...");
				FileInputStream fis = new FileInputStream(viewProjectFile);
				ObjectInputStream ois = new ObjectInputStream(fis);							
				Object obj = null;
				try
				{
					// Read the serialized Project object				
					obj = ois.readObject();
					logger.info("Project state re-constructed successfully for build " + build.getNumber() + "!");		
				}
				catch( ClassNotFoundException cne )
				{
		            // Not sure what we've found, but its no good...
					logger.info("Caught Exception: " + cne.getMessage());
					logger.info("Cannot construct project state for build" + build.getNumber() + "!");		
				}
				finally
				{
					ois.close();
					fis.close();
				}
				
				return obj;
			}
    	}
    	return null;
    }
    
	/**
	 * Overridden checkout function
	 * This is the real invocation of this plugin.
	 * Currently, we will do a project info and determine the true nature of the project
	 * Subsequent to that we will run a view project command and cache the information
	 * on each member, so that we can execute project checkout commands.  This obviously
	 * eliminates the need for a sandbox and can wily nilly delete the workspace directory as needed
	 * @see hudson.scm.SCM#checkout(hudson.model.AbstractBuild, hudson.Launcher, hudson.FilePath, hudson.model.BuildListener, java.io.File)
	 */
	@Override
	public boolean checkout(AbstractBuild<?, ?> build, Launcher launcher, FilePath workspace, 
							BuildListener listener, File changeLogFile) throws IOException, InterruptedException 
	{
		// Log the invocation... 
		logger.info("Start execution of checkout() routine...!");

		// Lets start with creating an authenticated MKS API Session for various parts of this operation...
		APISession api = createAPISession();
		// Ensure we've successfully created an API Session
		if( null == api )
		{
			listener.getLogger().println("Failed to establish an API connection to the MKS Integrity Server!");
			return false;
		}
		// Lets also open the change log file for writing...
		PrintWriter writer = new PrintWriter(new FileWriter(changeLogFile));		
		try
		{
			// Next, load up the information for this MKS Integrity Project's configuration
			listener.getLogger().println("Preparing to execute si projectinfo for " + configPath);
			initializeCMProject(api);
			listener.getLogger().println("Preparing to execute si viewproject for " + configPath);
			initializeCMProjectMembers(api);
					
			// Figure out what our previous build was...
			AbstractBuild<?,?> previousBuild = build.getPreviousBuild();
	    	// Check to see if we've had a previous build...
	        if( null == previousBuild ) 
	        {
	            // Nothing worthwhile to compare against
	        	logger.info("Cannot find a previous build!");
	        }    	
	
	    	// Now, we need to find the project state from the previous build.
	        for( AbstractBuild<?,?> b = build.getPreviousBuild(); null != b; b = b.getPreviousBuild() ) 
	        {
	        	// For each previous build, lets make sure we can find a project state
	            if( getViewProjectResponseFile(b).exists()) 
	            {
	            	logger.info("Found previous project state in build " + b.getNumber());
	            	previousBuild = b;
	                break;
	            }
	        }
	        
	        // Load up the project state for this previous build...
			Object obj = getIntegrityCMProjectState(previousBuild);
			// Now that we've loaded the object, lets make sure it is an IntegrityCMProject!
			if( obj instanceof IntegrityCMProject && null != obj )
			{
				// Cast object to an IntegrityCMProject
				IntegrityCMProject oldProject = (IntegrityCMProject) obj;
				// Compare this project with the old 
				siProject.compareBaseline(oldProject);		
			}
			else
			{
	            // Not sure what object we've loaded, but its no IntegrityCMProject!
				logger.info("Cannot construct project state for any of the pevious builds!");
			}
			
	        // After all that insane interrogation, we have the current Project state that is
	        // correctly initialized and either compared against its baseline or is a fresh baseline itself
	        // Now, lets figure out how to populate the workspace...
			IntegrityCheckoutTask coTask = null;
			if( null == obj )
			{ 
				// If we we were not able to establish the previous project state, 
				// then always do full checkout.  cleanCopy = true
			    coTask = new IntegrityCheckoutTask(this, siProject, true, listener);
			}
			else 
			{
				// Otherwise, update the workspace in accordance with the user's cleanCopy option				
				coTask = new IntegrityCheckoutTask(this, siProject, cleanCopy, listener);
			}
			
			// Execute the IntegrityCheckoutTask.invoke() method to do the actual synchronization...
			if( workspace.act(coTask) )
			{ 
				// Now that the workspace is updated, lets save the current project state for future comparisons
				listener.getLogger().println("Saving current MKS Integrity Project configuration...");
				printViewProjectResponse(build, listener, siProject);
				// Write out the change log file, which will be used by the parser to report the updates
				listener.getLogger().println("Writing build change log...");
				writer.println(siProject.getChangeLog(String.valueOf(build.getNumber()), api));				
				listener.getLogger().println("Change log successfully generated: " + changeLogFile.getAbsolutePath());
			}
			else
			{
				// Checkout failed!  Returning false...
				return false;
			}
		}
	    catch(APIException aex)
	    {
    		logger.error("API Exception caught...");
    		listener.getLogger().println("An API Exception was caught!"); 
    		ExceptionHandler eh = new ExceptionHandler(aex);
    		logger.error(eh.getMessage());
    		listener.getLogger().println(eh.getMessage());
    		logger.info(eh.getCommand() + " returned exit code " + eh.getExitCode());
    		listener.getLogger().println(eh.getCommand() + " returned exit code " + eh.getExitCode());
    		aex.printStackTrace();
    		return false;
	    }
	    finally
	    {
	    	writer.close();
			api.Terminate();
	    }

	    //If we got here, everything is good on the checkout...
	    return true;
	}

    /**
     * Called from checkout to save the current state of the project for this build
     * @param build Hudson AbstractBuild
     * @param listener Hudson Build Listener
     * @param response MKS API Response
     * @throws IOException
     */
    private void printViewProjectResponse(AbstractBuild<?,?> build, BuildListener listener, IntegrityCMProject pj) throws IOException
    {
    	// For every build we will basically save our view project output, so it can be compared build over build
    	File viewProjectFile = new File(build.getRootDir(), "viewproject.dat");
    	FileOutputStream fos = new FileOutputStream(viewProjectFile);
    	ObjectOutputStream pjOut = new ObjectOutputStream(fos);
    	try
    	{
        	pjOut.writeObject(pj);    		
        	listener.getLogger().println("API Response for si viewproject successfully saved to file!");    		
    	}
    	finally
    	{
    		pjOut.close();
    		fos.close();
    	}
    	listener.getLogger().println("Successfully saved current MKS Integrity Project configuration to " + viewProjectFile.getAbsolutePath());    	
    }
    
    /**
     * Returns the MKS API Response xml file for the specified build 
     * @param build Hudson AbstractBuild Object
     * @return
     */
    public static File getViewProjectResponseFile(AbstractBuild<?,?> build) 
    {
        return new File(build.getRootDir(),"viewproject.dat");
    }
    
	/**
	 * Overridden compareRemoteRevisionWith function
	 * Loads up the previous project configuration and compares 
	 * that against the current to determine if the project has changed
	 * @see hudson.scm.SCM#compareRemoteRevisionWith(hudson.model.AbstractProject, hudson.Launcher, hudson.FilePath, hudson.model.TaskListener, hudson.scm.SCMRevisionState)
	 */
	@Override
	protected PollingResult compareRemoteRevisionWith(AbstractProject<?, ?> project, Launcher launcher, FilePath workspace,
													final TaskListener listener, SCMRevisionState _baseline) throws IOException, InterruptedException	
	{
		// Log the call for now...
		logger.info("compareRemoteRevisionWith() invoked...!");
        IntegrityRevisionState baseline;
        // Lets get the baseline from our last successful build
        if( _baseline instanceof IntegrityRevisionState )
        {
        	baseline = (IntegrityRevisionState)_baseline;
        	// Get the baseline that contains the last successful build
        	AbstractBuild<?,?> lastSuccessfulBuild = project.getLastSuccessfulBuild();
        	if( null == lastSuccessfulBuild )
        	{
        		// We've not no successful builds or may be the first one, build now!
        		logger.info("No prior successful builds found!  Advice to build now!");
        		return PollingResult.BUILD_NOW;
        	}
        	else
        	{
        		// Lets trying to get the baseline associated with the last successful build
        		baseline = (IntegrityRevisionState)calcRevisionsFromBuild(lastSuccessfulBuild, launcher, listener);
        		if( null != baseline && null != baseline.getSIProject() )
        		{
        			// Obtain the details on the old project configuration
        			IntegrityCMProject oldProject = baseline.getSIProject();
        			// Next, load up the information for the current MKS Integrity Project
        			// Lets start with creating an authenticated MKS API Session for various parts of this operation...
        			APISession api = createAPISession();
        			if( null != api )
        			{
	        			try
	        			{
	        				listener.getLogger().println("Preparing to execute si projectinfo for " + configPath);
	        				initializeCMProject(api);
	        				listener.getLogger().println("Preparing to execute si viewproject for " + configPath);
	        				initializeCMProjectMembers(api);
	        				// Compare this project with the old project 
	        				siProject.compareBaseline(oldProject);		
	        				// Finally decide whether or not we need to build again
	        				if( siProject.hasProjectChanged() )
	        				{
	        					listener.getLogger().println("Project contains changes a total of " + siProject.getChangeCount() + " changes!");
	        					return PollingResult.SIGNIFICANT;
	        				}
	        				else
	        				{
	        					listener.getLogger().println("No new changes detected in project!");        					
	        					return PollingResult.NO_CHANGES;
	        				}
	        			}
	        		    catch(APIException aex)
	        		    {
	        	    		logger.error("API Exception caught...");
	        	    		listener.getLogger().println("An API Exception was caught!"); 
	        	    		ExceptionHandler eh = new ExceptionHandler(aex);
	        	    		logger.error(eh.getMessage());
	        	    		listener.getLogger().println(eh.getMessage());
	        	    		logger.info(eh.getCommand() + " returned exit code " + eh.getExitCode());
	        	    		listener.getLogger().println(eh.getCommand() + " returned exit code " + eh.getExitCode());
	        	    		aex.printStackTrace();
	        	    		return PollingResult.NO_CHANGES;
	        		    }
	        		    finally
	        		    {
	        				api.Terminate();
	        		    }
        			}
        			else
        			{
        				listener.getLogger().println("Failed to establish an API connection to the MKS Integrity Server!");
        				return PollingResult.NO_CHANGES;
        			}        			
        		}
        		else
        		{
        			// Can't construct a previous project state, lets build now!
        			logger.info("No prior MKS Integrity Project state can be found!  Advice to build now!");
        			return PollingResult.BUILD_NOW;
        		}
        	}
        }
        else
        {
        	// This must be an error, no changes to report
        	logger.error("This method was called with the wrong SCMRevisionState class!");
        	return PollingResult.NO_CHANGES;
        }
	}
	
	/**
	 * Overridden createChangeLogParser function
	 * Creates a custom Integrity Change Log Parser, which compares two view project outputs  
	 * @see hudson.scm.SCM#createChangeLogParser()
	 */
	@Override
	public ChangeLogParser createChangeLogParser() 
	{
		// Log the call
		logger.info("createChangeLogParser() invoked...!");
		return new IntegrityChangeLogParser(integrityURL);
	}
	
	/**
	 * Returns the SCMDescriptor<?> for the SCM object. 
	 * The SCMDescriptor is used to create new instances of the SCM.
	 */
	@Override
	public SCMDescriptor<IntegritySCM> getDescriptor() 
	{
		// Log the call
		logger.info("IntegritySCM.getDescriptor() invoked...!");		
	    return DescriptorImpl.INTEGRITY_DESCRIPTOR;
	}

	/**
	 * The relationship of Descriptor and SCM (the describable) is akin to class and object.
	 * This means the descriptor is used to create instances of the describable.
	 * Usually the Descriptor is an internal class in the SCM class named DescriptorImpl. 
	 * The Descriptor should also contain the global configuration options as fields, 
	 * just like the SCM class contains the configurations options for a job.
	 */
    public static class DescriptorImpl extends SCMDescriptor<IntegritySCM> 
    {
    	private static Log desLogger = LogFactory.getLog(DescriptorImpl.class);
    	@Extension
    	public static final DescriptorImpl INTEGRITY_DESCRIPTOR = new DescriptorImpl(); 
		private String globalOptions;
		
        protected DescriptorImpl() 
        {
        	super(IntegritySCM.class, IntegrityRepositoryBrowser.class);        	
            load();
        	// Log the construction...
        	desLogger.info("IntegritySCM DescriptorImpl() constructed!");        	            
        }
        
        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException 
        {
        	IntegritySCM scm = (IntegritySCM) super.newInstance(req, formData);
            scm.browser = RepositoryBrowsers.createInstance(IntegrityRepositoryBrowser.class, req, formData, "browser");
            return scm;
        }
        
        /**
         * Returns the name of the SCM, this is the name that will show up next to 
         * CVS, Subversion, etc. when configuring a job.
         */
		@Override
		public String getDisplayName() 
		{
			return "MKS Integrity - CM";
		}
		
		/**
		 * This method is invoked when the global configuration page is submitted.
		 * In the method the data in the web form should be copied to the Descriptor's fields.
		 * To persist the fields to the global configuration XML file, the save() method must be called. 
		 * Data is defined in the global.jelly page.
		 */
        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException 
        {
        	// Log the request to configure
        	desLogger.info("Request to configure IntegritySCM invoked...");
			// This is where we can get any global settings...
			// Can't thing of any integrity globals to define, so we'll just save() and return true!
		    globalOptions = Util.fixEmpty(req.getParameter("mks.globalOptions").trim());        	
            save();
            return true;
        }
		
		/**
		 * Returns the MKS Integrity global options configured for this plugin 
		 */
		public String getGlobalOptions() 
		{
			return globalOptions;
		}

		/**
		 * Sets the MKS Integrity global options configured for this plugin 
		 */
		public void setGlobalOptions(String globalOptions) 
		{
			this.globalOptions = globalOptions;
		}

		/**
		 * The field mks.globalOptions will be validated through the checkUrl. 
		 * When the user has entered some information and moves the focus away from field,
		 * Hudson will call DescriptorImpl.doGlobalOptionsCheck to validate that data entered.
		 */
		public FormValidation doGlobalOptionsCheck(@QueryParameter String value)
		{
			// This is where we can validate the entry of the Global Options field
            // If there is an error, we return:
			//	return FormValidation.error("This is an error!");
			// If we want to throw a warning:
			//	return FormValidation.warning("This is a warning!");

			// Nothing to validate, so we'll return all good!
		    return FormValidation.ok();
		}		
    }
}
