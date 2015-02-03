package hudson.scm;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.ModelObject;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.scm.IntegrityCheckpointAction.IntegrityCheckpointDescriptorImpl;
import hudson.scm.browsers.IntegrityWebUI;
import hudson.util.FormValidation;
import hudson.util.Secret;
import hudson.util.ListBoxModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import com.mks.api.Command;
import com.mks.api.MultiValue;
import com.mks.api.Option;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;
import com.mks.api.response.WorkItem;

/**
 * This class provides an integration between Hudson/Jenkins for Continuous Builds and 
 * PTC Integrity for Configuration Management
 */
public class IntegritySCM extends SCM implements Serializable
{
	private static final long serialVersionUID = 7559894846609712683L;
	private static final Logger LOGGER = Logger.getLogger("IntegritySCM");
	private static final Map<String, IntegrityCMProject> projects = new ConcurrentHashMap<String, IntegrityCMProject>();
	public static final String NL = System.getProperty("line.separator");
	public static final String FS = System.getProperty("file.separator");
	public static final int MIN_PORT_VALUE = 1;
	public static final int MAX_PORT_VALUE = 65535;	
	public static final int DEFAULT_THREAD_POOL_SIZE = 5;
	public static final SimpleDateFormat SDF = new SimpleDateFormat("MMM dd, yyyy h:mm:ss a");	
	private String ciServerURL;
	private String integrityURL;
	private IntegrityRepositoryBrowser browser;
	private String serverConfig;
	private String configPath;
	private String includeList;
	private String excludeList;
	private String tagName;
	private String configurationName;
	private boolean cleanCopy;
	private boolean skipAuthorInfo = false;
	private String lineTerminator = "native";
	private boolean restoreTimestamp = true;
	private boolean checkpointBeforeBuild = false;
	private String alternateWorkspace;
	private boolean fetchChangedWorkspaceFiles = false;
	private boolean deleteNonMembers = false;
	private int checkoutThreadPoolSize = DEFAULT_THREAD_POOL_SIZE;

	/**
	 * Create a constructor that takes non-transient fields, and add the annotation @DataBoundConstructor to it. 
	 * Using the annotation helps the Stapler class to find which constructor that should be used when 
	 * automatically copying values from a web form to a class.
	 */
    @DataBoundConstructor
	public IntegritySCM(IntegrityRepositoryBrowser browser, String serverConfig, String configPath, String includeList, String excludeList, boolean cleanCopy, 
						String lineTerminator, boolean restoreTimestamp, boolean skipAuthorInfo, boolean checkpointBeforeBuild, String tagName, 
						String alternateWorkspace, boolean fetchChangedWorkspaceFiles, boolean deleteNonMembers, int checkoutThreadPoolSize, String configurationName)
	{
    	// Log the construction
    	LOGGER.fine("IntegritySCM constructor has been invoked!");
		// Initialize the class variables
    	this.ciServerURL = Jenkins.getInstance().getRootUrlFromRequest();
    	this.browser = browser;
    	this.serverConfig = serverConfig;
    	this.configPath = configPath;
    	this.includeList = includeList;
    	this.excludeList = excludeList;
    	this.cleanCopy = cleanCopy;
    	this.lineTerminator = lineTerminator;
    	this.restoreTimestamp = restoreTimestamp;
    	this.skipAuthorInfo = skipAuthorInfo;
    	this.checkpointBeforeBuild = checkpointBeforeBuild;
    	this.tagName = tagName;    	
    	this.alternateWorkspace = alternateWorkspace;
    	this.fetchChangedWorkspaceFiles = fetchChangedWorkspaceFiles;
    	this.deleteNonMembers = deleteNonMembers;
		this.checkoutThreadPoolSize = (checkoutThreadPoolSize > 0 ? checkoutThreadPoolSize : DEFAULT_THREAD_POOL_SIZE);
		this.configurationName = configurationName;

    	// Initialize the Integrity URL
    	initIntegrityURL();    	
    	
    	LOGGER.fine("CI Server URL: " + this.ciServerURL);
    	LOGGER.fine("URL: " + this.integrityURL);
    	LOGGER.fine("Server Configuration: " + this.serverConfig);
    	LOGGER.fine("Configuration Name: " + this.configurationName);
    	LOGGER.fine("Configuration Path: " + this.configPath);
    	LOGGER.fine("Include Filter: " + this.includeList);
    	LOGGER.fine("Exclude Filter: " + this.excludeList);
    	LOGGER.fine("Line Terminator: " + this.lineTerminator);
    	LOGGER.fine("Restore Timestamp: " + this.restoreTimestamp);
    	LOGGER.fine("Clean: " + this.cleanCopy);
    	LOGGER.fine("Skip Author Info: " + this.skipAuthorInfo);
    	LOGGER.fine("Checkpoint Before Build: " + this.checkpointBeforeBuild);
    	LOGGER.fine("Tag Name: " + this.tagName);    	
    	LOGGER.fine("Alternate Workspace Directory: " + this.alternateWorkspace);
    	LOGGER.fine("Fetch Changed Workspace Files: " + this.fetchChangedWorkspaceFiles);
    	LOGGER.fine("Delete Non Members: " + this.deleteNonMembers);
    	LOGGER.fine("Checkout Thread Pool Size: " + this.checkoutThreadPoolSize);
	}

    @Override
    @Exported
    /**
     * Returns the Integrity Repository Browser
     */
    public IntegrityRepositoryBrowser getBrowser() 
    {
        return browser == null ? new IntegrityWebUI(null) : browser;
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
     * Returns the Project or Configuration Path for a Integrity Source Project
     * @return
     */        
    public String getConfigPath()
    {
    	return configPath;
    }
    
    /**
     * Returns the files that will be excluded
     * @return
     */        
    public String getIncludeList()
    {
    	return includeList;
    }
    
    /**
     * Returns the files that will be included
     * @return
     */        
    public String getExcludeList()
    {
    	return excludeList;
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
     * Returns the line terminator to apply when obtaining files from the Integrity Server
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
     * Returns true/false depending on whether or not perform a checkpoint before the build
     * @return
     */
    public boolean getCheckpointBeforeBuild()
    {
    	return checkpointBeforeBuild;
    }
    
    /**
     * Returns the label string for the checkpoint performed before the build
     * @return
     */
    public String getTagName()
    {
		if( tagName == null || tagName.length() == 0 )
		{
			return IntegrityCheckpointDescriptorImpl.defaultTagName;
		}    	
    	return tagName;
    }
    
    /**
     * Returns the alternate workspace directory
     * @return
     */
    public String getAlternateWorkspace()
    {
    	return alternateWorkspace;
    }

    /**
     * Returns the true/false depending on whether or not to synchronize changed workspace files
     * @return
     */
    public boolean getFetchChangedWorkspaceFiles()
    {
    	return fetchChangedWorkspaceFiles;
    }
    
    /**
     * Returns the true/false depending on whether non members should be deleted before the build
     * @return
     */
    public boolean getDeleteNonMembers()
	{
        return deleteNonMembers;
	}
	
     /**
	 * Returns the size of the thread pool for parallel checkouts
     * @return
     */
    public int getCheckoutThreadPoolSize()
	{
        return checkoutThreadPoolSize;
    }

    /**
     * Returns the configuration name for this project
     * Required when working with Multiple SCMs plug-in
     */
	public String getConfigurationName() 
	{
		return configurationName;
	}
	
	/**
	 * Sets the server configuration name for this project
	 * @param serverConfig
	 */
    public void setServerConfig(String serverConfig)
    {
    	this.serverConfig = serverConfig;
    	IntegrityConfigurable ic = ((DescriptorImpl)this.getDescriptor()).getConfiguration(serverConfig);
    	integrityURL = (ic.getSecure() ? "https://" : "http://") + ic.getHostName() + ":" + String.valueOf(ic.getPort());     	
    }

    /**
     * Sets the Project or Configuration Path for an Integrity Source Project
     * @return
     */        
    public void setConfigPath(String configPath)
    {
    	this.configPath = configPath;
    }
    
    /**
     * Sets the files that will be not be included
     * @return
     */        
    public void setIncludeList(String includeList)
    {
    	this.includeList = includeList;
    }

    /**
     * Sets the files that will be not be included
     * @return
     */        
    public void setExcludeList(String excludeList)
    {
    	this.excludeList = excludeList;
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
     * Sets the line terminator to apply when obtaining files from the Integrity Server
     * @return
     */        
    public void setLineTerminator(String lineTerminator)
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
     * Toggles whether or not a checkpoint should be performed before the build
     * @param checkpointBeforeBuild
     */
    public void setCheckpointBeforeBuild(boolean checkpointBeforeBuild)
    {
    	this.checkpointBeforeBuild = checkpointBeforeBuild;
    }
    
    /**
     * Sets the label string for the checkpoint performed before the build
     * @param tagName
     */
    public void setTagName(String tagName)
    {
    	this.tagName = tagName;
    }
    
    /**
     * Sets an alternate workspace for the checkout directory
     * @param alternateWorkspace
     */
    public void setAlternateWorkspace(String alternateWorkspace)
    {
    	this.alternateWorkspace = alternateWorkspace;
    }

    /**
     * Toggles whether or not changed workspace files should be synchronized
     * @param fetchChangedWorkspaceFiles
     */
    public void setFetchChangedWorkspaceFiles(boolean fetchChangedWorkspaceFiles)
    {
    	this.fetchChangedWorkspaceFiles = fetchChangedWorkspaceFiles;
    }
    
    /**
     * Toggles whether or not non members should be deleted
     * @param deleteNonMembers
     */
    public void setDeleteNonMembers(boolean deleteNonMembers)
    {
        this.deleteNonMembers = deleteNonMembers;
	}
	
     /** 
	 * Sets the thread pool size of parallel checkout threads
     * @param checkoutThreadPoolSize
     */
    public void setCheckoutThreadPoolSize(int checkoutThreadPoolSize)
	{
        this.checkoutThreadPoolSize = checkoutThreadPoolSize;
    }
    
    /**
     * Sets the configuration name for this project
     * @param configurationName Name for this project configuration
     */
	public void setConfigurationName(String configurationName) 
	{
		this.configurationName = configurationName;
	}
    
    /**
     * Provides a mechanism to update the Integrity URL, based on updates
     * to the hostName/port/secure variables
     */
    private void initIntegrityURL()
    {
    	// Initialize the Integrity URL
    	IntegrityConfigurable ic = ((DescriptorImpl)this.getDescriptor()).getConfiguration(serverConfig);
    	integrityURL = (ic.getSecure() ? "https://" : "http://") + ic.getHostName() + ":" + String.valueOf(ic.getPort()); 
    }

    /**
     * Returns the Integrity Configuration Management Project
     * @return
     */
    public IntegrityCMProject getIntegrityProject()
    {
    	return findProject(configurationName);
    }
    
    public static IntegrityCMProject findProject(String configurationName)
    {
    	return hasProject(configurationName) ? projects.get(configurationName) : null;
    }
    
    public static boolean hasProject(String configurationName)
    {
    	return projects.containsKey(configurationName);
    }
	/**
	 * Adds Integrity CM Project info to the build variables  
	 */
	@Override 
	public void buildEnvVars(AbstractBuild<?, ?> build, Map<String, String> env)
	{ 
		super.buildEnvVars(build, env);
		LOGGER.fine("buildEnvVars() invoked...!");		
    	IntegrityConfigurable ic = ((DescriptorImpl)this.getDescriptor()).getConfiguration(serverConfig);		
		env.put("MKSSI_PROJECT", IntegrityCheckpointAction.evalGroovyExpression(env, configPath));
		env.put("MKSSI_HOST", ic.getHostName());
		env.put("MKSSI_PORT", String.valueOf(ic.getPort()));
		env.put("MKSSI_USER", ic.getUserName());

		// Populate with information about the most recent checkpoint
		IntegrityCMProject siProject = getIntegrityProject();
		if( null != siProject && siProject.isBuild() )
		{
			env.put("MKSSI_BUILD", getIntegrityProject().getProjectRevision());
		}
	}
	
	/**
	 * Overridden calcRevisionsFromBuild function
	 * Returns the current project configuration which can be used to difference any future configurations
	 * @see hudson.scm.SCM#calcRevisionsFromBuild(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.TaskListener)
	 */
	@Override
	public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener) throws IOException, InterruptedException 
	{
		// Log the call for debug purposes
		LOGGER.fine("calcRevisionsFromBuild() invoked...!");
		return new IntegrityRevisionState(build.getRootDir());
	}

	/**
	 * Primes the Integrity Project metadata information
	 * @param api Integrity API Session
	 * @return response Integrity API Response
	 * @throws APIException
	 */
	private Response initializeCMProject(APISession api, File projectDB, String resolvedConfigPath) throws APIException
	{
		// Get the project information for this project
		Command siProjectInfoCmd = new Command(Command.SI, "projectinfo");
		siProjectInfoCmd.addOption(new Option("project", resolvedConfigPath));	
		LOGGER.fine("Preparing to execute si projectinfo for " + resolvedConfigPath);
		Response infoRes = api.runCommand(siProjectInfoCmd);
		LOGGER.fine(infoRes.getCommandString() + " returned " + infoRes.getExitCode());
		// Initialize our siProject class variable
		IntegrityCMProject siProject = new IntegrityCMProject(infoRes.getWorkItems().next(), projectDB, getConfigurationName());
		// Set the project options
		siProject.setLineTerminator(lineTerminator);
		siProject.setRestoreTimestamp(restoreTimestamp);
		siProject.setSkipAuthorInfo(skipAuthorInfo);
		siProject.setCheckpointBeforeBuild(checkpointBeforeBuild);
		projects.put(configurationName, siProject);
		return infoRes;
	}

	/**
	 * Utility function to parse the include/exclude filter
	 * @param siViewProjectCmd API Command for the 'si viewproject' command
	 * @return
	 */
	private void applyMemberFilters(Command siViewProjectCmd)
	{
		// Checking if our include list has any entries
		if( null != includeList && includeList.length() > 0 )
		{ 
			StringBuilder filterString = new StringBuilder();
			String[] filterTokens = includeList.split(",|;");
			// prepare a OR combination of include filters (all in one filter, separated by comma if needed)
			for( int i = 0; i < filterTokens.length; i++ )
			{ 
				filterString.append(i > 0 ? "," : "");
				filterString.append("file:");
				filterString.append(filterTokens[i]);
			}
			siViewProjectCmd.addOption(new Option("filter", filterString.toString()));
		}
	 
		// Checking if our exclude list has any entries
		if( null != excludeList && excludeList.length() > 0 )
		{ 
			String[] filterTokens = excludeList.split(",|;");
			// prepare a AND combination of exclude filters (one filter each filter)
			for( int i = 0; i < filterTokens.length; i++ )
			{ 
				if (filterTokens[i]!= null)
				{
					siViewProjectCmd.addOption(new Option("filter", "!file:"+filterTokens[i]));
				}
			}                              
		}
	}
	
	/**
	 * Primes the Integrity Project Member metadata information
	 * @param api Integrity API Session
	 * @return response Integrity API Response
	 * @throws APIException
	 * @throws SQLException 
	 */
	private Response initializeCMProjectMembers(APISession api) throws APIException, SQLException
	{
		IntegrityCMProject siProject = getIntegrityProject();
		// Lets parse this project
		Command siViewProjectCmd = new Command(Command.SI, "viewproject");
		siViewProjectCmd.addOption(new Option("recurse"));
		siViewProjectCmd.addOption(new Option("project", siProject.getConfigurationPath()));
		MultiValue mvFields = new MultiValue(",");
		mvFields.add("name");
		mvFields.add("context");
		mvFields.add("cpid");		
		mvFields.add("memberrev");
		mvFields.add("membertimestamp");
		mvFields.add("memberdescription");
		mvFields.add("type");
		siViewProjectCmd.addOption(new Option("fields", mvFields));
			
		// Apply our include/exclude filters
        applyMemberFilters(siViewProjectCmd);
	
		LOGGER.fine("Preparing to execute si viewproject for " + siProject.getConfigurationPath());
		Response viewRes = api.runCommandWithInterim(siViewProjectCmd);
		siProject.parseProject(viewRes.getWorkItems());
		return viewRes;
	}
	
    /**
     * Toggles whether or not a workspace is required for polling
     * Since, we're using a Server Integration Point in the Integrity API, 
     * we do not require a workspace.
     */
    @Override
    public boolean requiresWorkspaceForPolling() 
    {
        return false;
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
		LOGGER.fine("Start execution of checkout() routine...!");
		
		// Re-evaluate the config path to resolve any groovy expressions...
		String resolvedConfigPath = IntegrityCheckpointAction.evalGroovyExpression(build.getEnvironment(listener), configPath);
		
		// Provide links to the Change and Build logs for easy access from Integrity
		listener.getLogger().println("Change Log: " + ciServerURL + build.getUrl() + "changes");
		listener.getLogger().println("Build Log: " + ciServerURL + build.getUrl() + "console");
		
		// Lets start with creating an authenticated Integrity API Session for various parts of this operation...
		APISession api = APISession.create(((DescriptorImpl)this.getDescriptor()).getConfiguration(serverConfig));	
		// Ensure we've successfully created an API Session
		if( null == api )
		{
			listener.getLogger().println("Failed to establish an API connection to the Integrity Server!");
			return false;
		}
		// Lets also open the change log file for writing...
		// Override file.encoding property so that we write as UTF-8 and do not have problems with special characters
		PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(changeLogFile),"UTF-8"));
		try
		{
			// Next, load up the information for this Integrity Project's configuration
			listener.getLogger().println("Preparing to execute si projectinfo for " + resolvedConfigPath);
			initializeCMProject(api, build.getRootDir(), resolvedConfigPath);
			IntegrityCMProject siProject = getIntegrityProject();
			// Check to see we need to checkpoint before the build
			if( checkpointBeforeBuild )
			{
				// Make sure we don't have a build project configuration
				if( ! siProject.isBuild() )
				{
					// Execute a pre-build checkpoint...
    				listener.getLogger().println("Preparing to execute pre-build si checkpoint for " + siProject.getConfigurationPath());
    				Response res = siProject.checkpoint(api, IntegrityCheckpointAction.evalGroovyExpression(build.getEnvironment(listener), tagName));
    				LOGGER.fine(res.getCommandString() + " returned " + res.getExitCode());        					
					WorkItem wi = res.getWorkItem(siProject.getConfigurationPath());
					String chkpt = wi.getResult().getField("resultant").getItem().getId();
					listener.getLogger().println("Successfully executed pre-build checkpoint for project " + 
													siProject.getConfigurationPath() + ", new revision is " + chkpt);
					// Update the siProject to use the new checkpoint as the basis for this build
					Command siProjectInfoCmd = new Command(Command.SI, "projectinfo");
					siProjectInfoCmd.addOption(new Option("project", siProject.getConfigurationPath() + "#forceJump=#b=" + chkpt));	

					Response infoRes = api.runCommand(siProjectInfoCmd);
					siProject.initializeProject(infoRes.getWorkItems().next(), getConfigurationName());
				}
				else
				{
					listener.getLogger().println("Cannot perform a pre-build checkpoint for build project configuration!");
				}
			}
			listener.getLogger().println("Preparing to execute si viewproject for " + siProject.getConfigurationPath());
			initializeCMProjectMembers(api);
					
	    	// Now, we need to find the project state from the previous build.
			AbstractBuild<?,?> previousBuild = build.getPreviousBuild();
	        while( null != previousBuild )
	        {
	        	// Go back through each previous build to find a useful project state
	        	File prevProjectDB = DerbyUtils.getIntegrityCMProjectDB(previousBuild, getConfigurationName());
	            if( prevProjectDB.isDirectory() ) 
	            {
	            	LOGGER.fine("Found previous project state in build " + previousBuild.getNumber());
	                break;
	            }
				
	            previousBuild = previousBuild.getPreviousBuild();	            
	        }
	        
	        // Load up the project state for this previous build...
			File prevProjectDB = DerbyUtils.getIntegrityCMProjectDB(previousBuild, getConfigurationName());
			// Now that we've loaded the object, lets make sure it is an IntegrityCMProject!
			if( null != prevProjectDB && prevProjectDB.isDirectory())
			{
				// Compare this project with the old 
				siProject.compareBaseline(prevProjectDB.getParentFile().getParentFile(), api);		
			}
			else
			{
	            // Not sure what object we've loaded, but its no IntegrityCMProject!
				LOGGER.fine("Cannot construct project state for any of the pevious builds!");
				// Prime the author information for the current build as this could be the first build
				if( ! skipAuthorInfo ){ siProject.primeAuthorInformation(api); }
			}
			
	        // After all that insane interrogation, we have the current Project state that is
	        // correctly initialized and either compared against its baseline or is a fresh baseline itself
	        // Now, lets figure out how to populate the workspace...
			List<Hashtable<CM_PROJECT, Object>> projectMembersList = siProject.viewProject();
			List<String> dirList = siProject.getDirList();
			IntegrityCheckoutTask coTask = null;
			if( null == prevProjectDB )
			{ 
				// If we we were not able to establish the previous project state, 
				// then always do full checkout.  cleanCopy = true
				coTask = new IntegrityCheckoutTask(projectMembersList, dirList, alternateWorkspace, lineTerminator, 
													restoreTimestamp, true, fetchChangedWorkspaceFiles,checkoutThreadPoolSize, listener, 
													((DescriptorImpl)this.getDescriptor()).getConfiguration(serverConfig));
			}
			else 
			{
				// Otherwise, update the workspace in accordance with the user's cleanCopy option				
				coTask = new IntegrityCheckoutTask(projectMembersList, dirList, alternateWorkspace, lineTerminator, 
													restoreTimestamp, cleanCopy, fetchChangedWorkspaceFiles, checkoutThreadPoolSize, listener, 
													((DescriptorImpl)this.getDescriptor()).getConfiguration(serverConfig));
			}
			
			// Execute the IntegrityCheckoutTask.invoke() method to do the actual synchronization...
			if( workspace.act(coTask) )
			{ 
				// Now that the workspace is updated, lets save the current project state for future comparisons
				listener.getLogger().println("Saving current Integrity Project configuration...");
				if( fetchChangedWorkspaceFiles ){ siProject.updateChecksum(coTask.getChecksumUpdates()); }
				// Write out the change log file, which will be used by the parser to report the updates
				listener.getLogger().println("Writing build change log...");
				writer.println(siProject.getChangeLog(String.valueOf(build.getNumber()), projectMembersList));				
				listener.getLogger().println("Change log successfully generated: " + changeLogFile.getAbsolutePath());
				// Delete non-members in this workspace, if appropriate...
				if( deleteNonMembers )
				{
				    IntegrityDeleteNonMembersTask deleteNonMembers = new IntegrityDeleteNonMembersTask(build, listener, alternateWorkspace, getIntegrityProject());
				    if( ! workspace.act(deleteNonMembers) )
					{
				        return false;
				    }
				}
			}
			else
			{
				// Checkout failed!  Returning false...
				return false;
			}
		}
	    catch(APIException aex)
	    {
	    	LOGGER.severe("API Exception caught...");
    		listener.getLogger().println("An API Exception was caught!"); 
    		ExceptionHandler eh = new ExceptionHandler(aex);
    		LOGGER.severe(eh.getMessage());
    		listener.getLogger().println(eh.getMessage());
    		LOGGER.fine(eh.getCommand() + " returned exit code " + eh.getExitCode());
    		listener.getLogger().println(eh.getCommand() + " returned exit code " + eh.getExitCode());
    		
    		return false;
	    }
		catch(SQLException sqlex)
		{
	    	LOGGER.severe("SQL Exception caught...");
    		listener.getLogger().println("A SQL Exception was caught!"); 
    		listener.getLogger().println(sqlex.getMessage());
    		LOGGER.log(Level.SEVERE, "SQLException", sqlex);
    		return false;			
		}
	    finally
	    {
	        if( writer != null )
			{
	            writer.close();
	        }
	    	if( getIntegrityProject() != null )
			{
	    		getIntegrityProject().closeProjectDB();
	    	}
	    	api.Terminate();
	    }

	    //If we got here, everything is good on the checkout...
	    return true;
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
		LOGGER.fine("compareRemoteRevisionWith() invoked...!");
        IntegrityRevisionState baseline;
        IntegrityCMProject siProject = null;
        // Lets get the baseline from our last build
        if( _baseline instanceof IntegrityRevisionState )
        {
        	baseline = (IntegrityRevisionState)_baseline;
        	// Get the baseline that contains the last build
        	AbstractBuild<?,?> lastBuild = project.getLastBuild();
        	if( null == lastBuild )
        	{
        		// We've got no previous builds, build now!
        		LOGGER.fine("No prior successful builds found!  Advise to build now!");
        		return PollingResult.BUILD_NOW;
        	}
        	else
        	{
        		// Lets trying to get the baseline associated with the last build
        		baseline = (IntegrityRevisionState)calcRevisionsFromBuild(lastBuild, launcher, listener);
        		if( null != baseline && null != baseline.getProjectDB() )
        		{
        			// Next, load up the information for the current Integrity Project
        			// Lets start with creating an authenticated Integrity API Session for various parts of this operation...
        			APISession api = APISession.create(((DescriptorImpl)this.getDescriptor()).getConfiguration(serverConfig));	
        			if( null != api )
        			{
	        			try
	        			{
	        				// Re-evaluate the config path to resolve any groovy expressions...
	        				String resolvedConfigPath = IntegrityCheckpointAction.evalGroovyExpression(project.getCharacteristicEnvVars(), configPath);
	        				listener.getLogger().println("Preparing to execute si projectinfo for " + resolvedConfigPath);
	        				initializeCMProject(api, new File(lastBuild.getRootDir(), "PollingResult"), resolvedConfigPath);
	        				listener.getLogger().println("Preparing to execute si viewproject for " + resolvedConfigPath);
	        				initializeCMProjectMembers(api);
	        				// Get the current project information after all the cache priming...
	        				siProject = getIntegrityProject();
	        				// Obtain the details on the old project configuration
	            			File projectDB = baseline.getProjectDB();
	        				// Compare this project with the old project 
	        				int changeCount = siProject.compareBaseline(projectDB, api);		
	        				// Finally decide whether or not we need to build again
	        				if( changeCount > 0 )
	        				{
	        					listener.getLogger().println("Project contains changes a total of " + changeCount + " changes!");
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
	        		    	LOGGER.severe("API Exception caught...");
	        	    		listener.getLogger().println("An API Exception was caught!"); 
	        	    		ExceptionHandler eh = new ExceptionHandler(aex);
	        	    		LOGGER.severe(eh.getMessage());
	        	    		listener.getLogger().println(eh.getMessage());
	        	    		LOGGER.fine(eh.getCommand() + " returned exit code " + eh.getExitCode());
	        	    		listener.getLogger().println(eh.getCommand() + " returned exit code " + eh.getExitCode());
	        	    		aex.printStackTrace();
	        	    		return PollingResult.NO_CHANGES;
	        		    }
	        			catch(SQLException sqlex)
	        			{
	        		    	LOGGER.severe("SQL Exception caught...");
	        	    		listener.getLogger().println("A SQL Exception was caught!"); 
	        	    		listener.getLogger().println(sqlex.getMessage());
	        	    		LOGGER.log(Level.SEVERE, "SQLException", sqlex);
	        	    		return PollingResult.NO_CHANGES;		
	        			}
	        		    finally
	        		    {
	        				api.Terminate();
	        				if( null != siProject )
	        				{
	        					siProject.closeProjectDB();	
	        				}
	        		    }
        			}
        			else
        			{
        				listener.getLogger().println("Failed to establish an API connection to the Integrity Server!");
        				return PollingResult.NO_CHANGES;
        			}        			
        		}
        		else
        		{
        			// Can't construct a previous project state, lets build now!
        			LOGGER.fine("No prior Integrity Project state can be found!  Advice to build now!");
        			return PollingResult.BUILD_NOW;
        		}
        	}
        }
        else
        {
        	// This must be an error, no changes to report
        	LOGGER.severe("This method was called with the wrong SCMRevisionState class!");
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
		LOGGER.fine("createChangeLogParser() invoked...!");
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
		LOGGER.fine("IntegritySCM.getDescriptor() invoked...!");		
	    return DescriptorImpl.INTEGRITY_DESCRIPTOR;
	}

	/**
	 * The relationship of Descriptor and SCM (the describable) is akin to class and object.
	 * This means the descriptor is used to create instances of the describable.
	 * Usually the Descriptor is an internal class in the SCM class named DescriptorImpl. 
	 * The Descriptor should also contain the global configuration options as fields, 
	 * just like the SCM class contains the configurations options for a job.
	 */
    public static class DescriptorImpl extends SCMDescriptor<IntegritySCM> implements ModelObject 
    {    	
    	@Extension
    	public static final DescriptorImpl INTEGRITY_DESCRIPTOR = new DescriptorImpl();
    	private List<IntegrityConfigurable> configurations;
		
        protected DescriptorImpl() 
        {
        	super(IntegritySCM.class, IntegrityWebUI.class);
        	configurations = new ArrayList<IntegrityConfigurable>();
            load();

            // Initialize our derby environment
            DerbyUtils.setDerbySystemDir(Jenkins.getInstance().getRootDir());
            DerbyUtils.loadDerbyDriver();
            
            // Log the construction...
        	LOGGER.fine("IntegritySCM DescriptorImpl() constructed!");
        }
        
        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException 
        {
        	LOGGER.fine("newInstance() on IntegritySCM (SCMDescriptor) invoked...");
        	IntegritySCM scm = (IntegritySCM) super.newInstance(req, formData);
        	scm.browser = RepositoryBrowsers.createInstance(IntegrityWebUI.class, req, formData, "browser");
        	if (scm.browser == null)
        	{
        		scm.browser = new IntegrityWebUI(null);
        	}
        	
            // The field configurationName will be validated to ensure it is unique 
            // When the user has entered some information in the Configuration Name field and moves the focus away from field,
            // Jenkins will call DescriptorImpl.doUniqueConfigurationNameCheck to ensure uniqueness.
        	String thisProjectName = Util.fixEmptyAndTrim(req.getParameter("name"));
        	LOGGER.fine("The current project is " + thisProjectName);
			for( AbstractProject<?, ?> project : Jenkins.getInstance().getItems(AbstractProject.class) )
			{
				if( project.getScm() instanceof IntegritySCM )
				{
					if( !project.getName().equals(thisProjectName) )
					{
						LOGGER.fine("Looking for duplicate configuration names - Project = " + project.getUrl());
						if( ((IntegritySCM)project.getScm()).getConfigurationName().equalsIgnoreCase(scm.getConfigurationName()) )
						{
							throw new FormException("Configuration Name '" + scm.getConfigurationName() + "' is not unique!", "configurationName");
						}
					}
				}
			}

            return scm;
        }
        
        /**
         * Returns the name of the SCM, this is the name that will show up next to 
         * CVS, Subversion, etc. when configuring a job.
         */
		@Override
		public String getDisplayName() 
		{
			return "Integrity";
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
        	LOGGER.fine("Request to configure IntegritySCM (SCMDescriptor) invoked...");
        	this.configurations = req.bindJSONToList(IntegrityConfigurable.class, formData.get("serverConfig"));
			save();
            return true;
        }

        /**
         * Returns the default groovy expression for the checkpoint label
         * @return
         */
        public String getTagName()
        {
        	return IntegrityCheckpointDescriptorImpl.defaultTagName;
        }
        
        /**
         * Returns the default thread pool size for a new project
         * @return
         */
        public int getCheckoutThreadPoolSize()
        {
        	return DEFAULT_THREAD_POOL_SIZE;
        }
                
		/**
		 * Returns the list of Integrity Server connections. 
		 * @return A list of IntegrityConfigurable objects.
		 */
		public List<IntegrityConfigurable> getConfigurations()
		{
			if( null == this.configurations )
			{
				this.configurations = new ArrayList<IntegrityConfigurable>();
			}
			
			return this.configurations;
		}

		/**
		 * Sets the list of Integrity Server connections. 
		 * @param configurations A list of IntegrityConfigurable objects.
		 */
		public void setConfigurations(List<IntegrityConfigurable> configurations)
		{
			this.configurations = configurations;
		}        

		/**
		 * Return the IntegrityConfigurable object for the specified simple name
		 * @param name
		 * @return
		 */
		public IntegrityConfigurable getConfiguration(String name)
		{
			for(IntegrityConfigurable configuration : this.configurations )
			{
				if( configuration.getName().equals(name) ) 
				{ 
					return configuration;
				}
			}
			
			return null;
		}
			
		/**
		 * Provides a list box for users to choose from a list of Integrity Server configurations
		 * @param configuration Simple configuration name
		 * @return
		 */
		public ListBoxModel doFillServerConfigItems(@QueryParameter String serverConfig)
		{
			ListBoxModel listBox = new ListBoxModel();
			
			if( null != this.configurations && this.configurations.size() > 0 )
			{			
				for( IntegrityConfigurable config : this.configurations )
				{
					listBox.add(config.getName());
				}				
			}
			return listBox;
		}		
					    	    
        public FormValidation doTestConnection(@QueryParameter("serverConfig.hostName") final String hostName,
                								@QueryParameter("serverConfig.port") final int port,
                								@QueryParameter("serverConfig.userName") final String userName,
                								@QueryParameter("serverConfig.password") final String password,
                								@QueryParameter("serverConfig.secure") final boolean secure,
                								@QueryParameter("serverConfig.ipHostName") final String ipHostName,
                								@QueryParameter("serverConfig.ipPort") final int ipPort) throws IOException, ServletException 
		{
        	LOGGER.fine("Testing Integrity API Connection...");
        	LOGGER.fine("hostName: " + hostName);
        	LOGGER.fine("port: " + port);
        	LOGGER.fine("userName: " + userName);
        	LOGGER.fine("password: " + Secret.fromString(password).getEncryptedValue());
        	LOGGER.fine("secure: " + secure);
        	LOGGER.fine("ipHostName: " + ipHostName);
        	LOGGER.fine("ipPort: " + ipPort);
        	
			IntegrityConfigurable ic = new IntegrityConfigurable(ipHostName, ipPort, hostName, port, secure, userName, password);
			APISession api = APISession.create(ic);
			if( null != api )
			{
				api.Terminate();
				return FormValidation.ok("Connection successful!");
			}
			else
			{
				return FormValidation.error("Failed to establish connection!");
			}
		}
        
		/**
		 * Validates that the thread pool size is numeric and within a valid range
		 * @param value Integer value for Thread Pool Size
		 * @return
		 */
		public FormValidation doValidCheckoutThreadPoolSizeCheck(@QueryParameter String value)
        {
            // The field checkoutThreadPoolSize will be validated through the checkUrl. 
            // When the user has entered some information and moves the focus away from field,
            // Jenkins will call DescriptorImpl.doValidCheckoutThreadPoolSizeCheck to validate that data entered.
            try
            {
                int intValue = Integer.parseInt(value);
                if(intValue < 1 || intValue > 10)
				{
                    return FormValidation.error("Thread pool size must be between 1 an 10");
                }
            }
            catch(NumberFormatException nfe)
            {
                return FormValidation.error("Value must be numeric!");
            }
            
            // Validation was successful if we got here, so we'll return all good!
            return FormValidation.ok();
        }        		
    }
}
