package hudson.scm;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.scm.browsers.IntegrityWebUI;
import hudson.util.FormValidation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jenkins.model.Jenkins;

import net.sf.json.JSONObject;

import org.apache.commons.codec.digest.DigestUtils;
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
import com.mks.api.util.Base64;

/**
 * This class provides an integration between Hudson/Jenkins for Continuous Builds and 
 * PTC Integrity for Configuration Management
 */
public class IntegritySCM extends SCM implements Serializable, IntegrityConfigurable
{
	private static final long serialVersionUID = 7559894846609712683L;
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
	private String host;
	private String integrationPointHost;
	private int integrationPointPort = 0;
	private int port;
	private boolean secure;
	private String configPath;
	private String includeList;
	private String excludeList;
	private String tagName;
	private String userName;
	private String password;
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
	public IntegritySCM(IntegrityRepositoryBrowser browser, String host, int port, boolean secure, String configPath, String includeList, String excludeList,
							String userName, String password, String integrationPointHost, int integrationPointPort, boolean cleanCopy,
							String lineTerminator, boolean restoreTimestamp, boolean skipAuthorInfo, boolean checkpointBeforeBuild, String tagName,
							String alternateWorkspace, boolean fetchChangedWorkspaceFiles, boolean deleteNonMembers, int checkoutThreadPoolSize, String configurationName)
	{
    	// Log the construction
    	Logger.debug("IntegritySCM constructor has been invoked!");
		// Initialize the class variables
    	this.ciServerURL = Jenkins.getInstance().getRootUrlFromRequest();
    	this.browser = browser;
    	setIntegrationPointHost(integrationPointHost);
    	setHost(host);
    	setIntegrationPointPort(integrationPointPort);
    	setPort(port);
    	setSecure(secure);
    	setUserName(userName);
    	setPassword(password);
    	setConfigurationName(configurationName);
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

    	// Initialize the Integrity URL
    	initIntegrityURL();

    	// Log the parameters received
    	Logger.debug("CI Server URL: " + this.ciServerURL);
    	Logger.debug("URL: " + this.integrityURL);
    	Logger.debug("IP Host: " + getIntegrationPointHost());
    	Logger.debug("Host: " + getHost());
    	Logger.debug("IP Port: " + getIntegrationPointPort());
    	Logger.debug("Port: " + getPort());
    	Logger.debug("Configuration Name: " + this.configurationName);
    	Logger.debug("Configuration Path: " + this.configPath);
    	Logger.debug("Include Filter: " + this.includeList);
    	Logger.debug("Exclude Filter: " + this.excludeList);
    	Logger.debug("User: " + getUserName());
    	Logger.debug("Password: " + DigestUtils.md5Hex(getPassword()));
    	Logger.debug("Secure: " + getSecure());
    	Logger.debug("Line Terminator: " + this.lineTerminator);
    	Logger.debug("Restore Timestamp: " + this.restoreTimestamp);
    	Logger.debug("Clean: " + this.cleanCopy);
    	Logger.debug("Skip Author Info: " + this.skipAuthorInfo);
    	Logger.debug("Checkpoint Before Build: " + this.checkpointBeforeBuild);
    	Logger.debug("Tag Name: " + this.tagName);    	
    	Logger.debug("Alternate Workspace Directory: " + this.alternateWorkspace);
    	Logger.debug("Fetch Changed Workspace Files: " + this.fetchChangedWorkspaceFiles);
    	Logger.debug("Delete Non Members: " + this.deleteNonMembers);
    	Logger.debug("Checkout Thread Pool Size: " + this.checkoutThreadPoolSize);
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
    
    public String getHost()
    {
    	return host;
    }

    public String getIntegrationPointHost()
    {
    	return this.integrationPointHost;
    }
    
    public int getPort()
    {
    	return port;
    }
    
    public int getIntegrationPointPort()
    {
    	return this.integrationPointPort;
    }
    
    public boolean getSecure()
    {
    	return secure;
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

    public String getUserName()
    {
    	return userName;
    }
    
    public String getPassword()
    {
    	return APISession.ENC_PREFIX + password;
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
	
    public void setHost(String host)
    {
    	this.host = host;
    	initIntegrityURL();
    }

    public void setIntegrationPointHost(String host)
    {
    	this.integrationPointHost = host;
    }
    
    public void setPort(int port)
    {
    	this.port = port;
    	initIntegrityURL();
    }

    public void setIntegrationPointPort(int port)
    {
    	this.integrationPointPort = port;
    }
    
    public void setSecure(boolean secure)
    {
    	this.secure = secure;
    	initIntegrityURL();
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

    public void setUserName(String userName)
    {
    	this.userName = userName;
    }
    
    public void setPassword(String password)
    {
    	if( password.indexOf(APISession.ENC_PREFIX) == 0 )
    	{
    		this.password = Base64.encode(Base64.decode(password.substring(APISession.ENC_PREFIX.length())));
    	}
    	else
    	{
    		this.password = Base64.encode(password);
    	}
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
		if( getSecure() )
		{
			integrityURL = "https://" + getHost() + ":" + String.valueOf(getPort()); 
		}
		else
		{
			integrityURL = "http://" + getHost() + ":" + String.valueOf(getPort());
		}
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
		Logger.debug("buildEnvVars() invoked...!");		
		env.put("MKSSI_PROJECT", IntegrityCheckpointAction.evalGroovyExpression(env, configPath));
		env.put("MKSSI_HOST", getHost());
		env.put("MKSSI_PORT", String.valueOf(getPort()));
		env.put("MKSSI_USER", getUserName());

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
		Logger.debug("calcRevisionsFromBuild() invoked...!");
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
		Logger.debug("Preparing to execute si projectinfo for " + resolvedConfigPath);
		Response infoRes = api.runCommand(siProjectInfoCmd);
		Logger.debug(infoRes.getCommandString() + " returned " + infoRes.getExitCode());
		// Initialize our siProject class variable
		IntegrityCMProject siProject = new IntegrityCMProject(infoRes.getWorkItems().next(), projectDB, getConfigurationName());
		// Set the project options
		siProject.setLineTerminator(lineTerminator);
		siProject.setRestoreTimestamp(restoreTimestamp);
		siProject.setSkipAuthorInfo(skipAuthorInfo);
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
	
		Logger.debug("Preparing to execute si viewproject for " + siProject.getConfigurationPath());
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
		Logger.debug("Start execution of checkout() routine...!");
		
		// Re-evaluate the config path to resolve any groovy expressions...
		String resolvedConfigPath = IntegrityCheckpointAction.evalGroovyExpression(build.getEnvironment(listener), configPath);
		
		// Provide links to the Change and Build logs for easy access from Integrity
		listener.getLogger().println("Change Log: " + ciServerURL + build.getUrl() + "changes");
		listener.getLogger().println("Build Log: " + ciServerURL + build.getUrl() + "console");
		
		// Lets start with creating an authenticated Integrity API Session for various parts of this operation...
		APISession api = APISession.create(this);	
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
    				Logger.debug(res.getCommandString() + " returned " + res.getExitCode());        					
					WorkItem wi = res.getWorkItem(siProject.getConfigurationPath());
					String chkpt = wi.getResult().getField("resultant").getItem().getId();
					listener.getLogger().println("Successfully executed pre-build checkpoint for project " + 
													siProject.getConfigurationPath() + ", new revision is " + chkpt);
					// Update the siProject to use the new checkpoint as the basis for this build
					Command siProjectInfoCmd = new Command(Command.SI, "projectinfo");
					siProjectInfoCmd.addOption(new Option("project", siProject.getProjectName()));	
					siProjectInfoCmd.addOption(new Option("projectRevision", chkpt));
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
	            	Logger.debug("Found previous project state in build " + previousBuild.getNumber());
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
				Logger.debug("Cannot construct project state for any of the pevious builds!");
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
													restoreTimestamp, true, fetchChangedWorkspaceFiles,checkoutThreadPoolSize, listener, this);
			}
			else 
			{
				// Otherwise, update the workspace in accordance with the user's cleanCopy option				
				coTask = new IntegrityCheckoutTask(projectMembersList, dirList, alternateWorkspace, lineTerminator, 
													restoreTimestamp, cleanCopy, fetchChangedWorkspaceFiles, checkoutThreadPoolSize, listener, this);
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
	    	Logger.error("API Exception caught...");
    		listener.getLogger().println("An API Exception was caught!"); 
    		ExceptionHandler eh = new ExceptionHandler(aex);
    		Logger.error(eh.getMessage());
    		listener.getLogger().println(eh.getMessage());
    		Logger.debug(eh.getCommand() + " returned exit code " + eh.getExitCode());
    		listener.getLogger().println(eh.getCommand() + " returned exit code " + eh.getExitCode());
    		Logger.fatal(aex);
    		return false;
	    }
		catch(SQLException sqlex)
		{
	    	Logger.error("SQL Exception caught...");
    		listener.getLogger().println("A SQL Exception was caught!"); 
    		listener.getLogger().println(sqlex.getMessage());
    		Logger.fatal(sqlex);
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
		Logger.debug("compareRemoteRevisionWith() invoked...!");
        IntegrityRevisionState baseline;
        IntegrityCMProject siProject = getIntegrityProject();
        // Lets get the baseline from our last build
        if( _baseline instanceof IntegrityRevisionState )
        {
        	baseline = (IntegrityRevisionState)_baseline;
        	// Get the baseline that contains the last build
        	AbstractBuild<?,?> lastBuild = project.getLastBuild();
        	if( null == lastBuild )
        	{
        		// We've got no previous builds, build now!
        		Logger.debug("No prior successful builds found!  Advise to build now!");
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
        			APISession api = APISession.create(this);	
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
	        		    	Logger.error("API Exception caught...");
	        	    		listener.getLogger().println("An API Exception was caught!"); 
	        	    		ExceptionHandler eh = new ExceptionHandler(aex);
	        	    		Logger.error(eh.getMessage());
	        	    		listener.getLogger().println(eh.getMessage());
	        	    		Logger.debug(eh.getCommand() + " returned exit code " + eh.getExitCode());
	        	    		listener.getLogger().println(eh.getCommand() + " returned exit code " + eh.getExitCode());
	        	    		aex.printStackTrace();
	        	    		return PollingResult.NO_CHANGES;
	        		    }
	        			catch(SQLException sqlex)
	        			{
	        		    	Logger.error("SQL Exception caught...");
	        	    		listener.getLogger().println("A SQL Exception was caught!"); 
	        	    		listener.getLogger().println(sqlex.getMessage());
	        	    		Logger.fatal(sqlex);
	        	    		return PollingResult.NO_CHANGES;		
	        			}
	        		    finally
	        		    {
	        				api.Terminate();
	        				siProject.closeProjectDB();
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
        			Logger.debug("No prior Integrity Project state can be found!  Advice to build now!");
        			return PollingResult.BUILD_NOW;
        		}
        	}
        }
        else
        {
        	// This must be an error, no changes to report
        	Logger.error("This method was called with the wrong SCMRevisionState class!");
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
		Logger.debug("createChangeLogParser() invoked...!");
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
		Logger.debug("IntegritySCM.getDescriptor() invoked...!");		
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
    	@Extension
    	public static final DescriptorImpl INTEGRITY_DESCRIPTOR = new DescriptorImpl();
    	private String defaultHostName;
    	private String defaultIPHostName;    	
    	private int defaultPort;
    	private int defaultIPPort;    	    	
    	private boolean defaultSecure;
        private String defaultUserName;
        private String defaultPassword;
        private int defaultCheckoutThreadPoolSize = IntegritySCM.DEFAULT_THREAD_POOL_SIZE;
        private String defaultTagName;
		
        protected DescriptorImpl() 
        {
        	super(IntegritySCM.class, IntegrityWebUI.class);
    		defaultHostName = Util.getHostName();
    		defaultIPHostName = "";    		
    		defaultPort = 7001;
    		defaultIPPort = 0;
    		defaultSecure = false;
    		defaultUserName = "";
    		defaultPassword = "";
    		defaultTagName = "${env['JOB_NAME']}-${env['BUILD_NUMBER']}-${new java.text.SimpleDateFormat(\"yyyy_MM_dd\").format(new Date())}";
            load();

            // Initialize our derby environment
            DerbyUtils.setDerbySystemDir(Jenkins.getInstance().getRootDir());
            DerbyUtils.loadDerbyDriver();
            
            // Log the construction...
        	Logger.debug("IntegritySCM DescriptorImpl() constructed!");
        }
        
        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException 
        {
        	IntegritySCM scm = (IntegritySCM) super.newInstance(req, formData);
        	scm.browser = RepositoryBrowsers.createInstance(IntegrityWebUI.class, req, formData, "browser");
        	if (scm.browser == null)
        	{
        		scm.browser = new IntegrityWebUI(null);
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
			return "Integrity - CM";
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
        	Logger.debug("Request to configure IntegritySCM (SCMDescriptor) invoked...");
			
        	Logger.debug("mks.defaultHostName = " + req.getParameter("mks.defaultHostName"));
        	defaultHostName = Util.fixEmptyAndTrim(req.getParameter("mks.defaultHostName"));
        	Logger.debug("defaultHostName = " + defaultHostName);
			
        	Logger.debug("mks.defaultIPHostName = " + req.getParameter("mks.defaultIPHostName"));
			defaultIPHostName = Util.fixEmptyAndTrim(req.getParameter("mks.defaultIPHostName"));
			Logger.debug("defaultIPHostName = " + defaultIPHostName);
			
			Logger.debug("mks.defaultPort = " + req.getParameter("mks.defaultPort"));
			defaultPort = Integer.parseInt(Util.fixNull(req.getParameter("mks.defaultPort")));
			Logger.debug("defaultPort = " + defaultPort);
			
			Logger.debug("mks.defaultIPPort = " + req.getParameter("mks.defaultIPPort"));
			defaultIPPort = Integer.parseInt(Util.fixNull(req.getParameter("mks.defaultIPPort")));
			Logger.debug("defaultIPPort = " + defaultIPPort);
			
			Logger.debug("mks.defaultSecure = " + req.getParameter("mks.defaultSecure"));
			defaultSecure = "on".equalsIgnoreCase(Util.fixEmptyAndTrim(req.getParameter("mks.defaultSecure"))) ? true : false;
			Logger.debug("defaultSecure = " + defaultSecure);
			
			Logger.debug("mks.defaultUserName = " + req.getParameter("mks.defaultUserName"));
			defaultUserName = Util.fixEmptyAndTrim(req.getParameter("mks.defaultUserName"));
			Logger.debug("defaultUserName = " + defaultUserName);
			
			setDefaultPassword(Util.fixEmptyAndTrim(req.getParameter("mks.defaultPassword")));
			Logger.debug("defaultPassword = " + DigestUtils.md5Hex(defaultPassword));
			
			save();
            return true;
        }
		
	    /**
	     * Returns the default host name for the Integrity Server 
	     * @return defaultHostName
	     */
	    public String getDefaultHostName()
	    {
	    	return defaultHostName;
	    }

	    /**
	     * Returns the default Integration Point host name 
	     * @return defaultIPHostName
	     */
	    public String getDefaultIPHostName()
	    {
	    	return defaultIPHostName;
	    }
	    
	    /**
	     * Returns the default port for the Integrity Server
	     * @return defaultPort
	     */    
	    public int getDefaultPort()
	    {
	    	return defaultPort;
	    }

	    /**
	     * Returns the default Integration Point port
	     * @return defaultIPPort
	     */    
	    public int getDefaultIPPort()
	    {
	    	return defaultIPPort;
	    }
	    
	    /**
	     * Returns the default secure setting for the Integrity Server
	     * @return defaultSecure
	     */        
	    public boolean getDefaultSecure()
	    {
	    	return defaultSecure;
	    }

	    /**
	     * Returns the default User connecting to the Integrity Server
	     * @return defaultUserName
	     */    
	    public String getDefaultUserName()
	    {
	    	return defaultUserName;
	    }
	    
	    /**
	     * Returns the default user's encrypted password connecting to the Integrity Server
	     * @return defaultPassword
	     */        
	    public String getDefaultPassword()
	    {
	    	return APISession.ENC_PREFIX + defaultPassword;
	    }
	    
	    /**
	     * Return the default checkout thread pool size
	     * @return
	     */
	    public int getDefaultCheckoutThreadPoolSize()
		{
	        return defaultCheckoutThreadPoolSize;
	    }
	    
	    /**
	     * Returns the default checkpoint label groovy script
	     * @return
	     */
		public String getDefaultTagName()
		{
			return defaultTagName;
		}
	    
	    /**
	     * Sets the default host name for the Integrity Server
	     * @param defaultHostName
	     */
	    public void setDefaultHostName(String defaultHostName)
	    {
	    	this.defaultHostName = defaultHostName;
	    }

	    /**
	     * Sets the default host name for the Integration Point
	     * @param defaultIPHostName
	     */
	    public void setDefaultIPHostName(String defaultIPHostName)
	    {
	    	this.defaultIPHostName = defaultIPHostName;
	    }
	    
	    /**
	     * Sets the default port for the Integrity Server
	     * @param defaultPort
	     */    
	    public void setDefaultPort(int defaultPort)
	    {
	    	this.defaultPort = defaultPort;
	    }

	    /**
	     * Sets the default port for the Integration Point
	     * @param defaultIPPort
	     */    
	    public void setDefaultIPPort(int defaultIPPort)
	    {
	    	this.defaultIPPort = defaultIPPort;
	    }
	    
	    /**
	     * Toggles whether or not secure sockets are enabled
	     * @param defaultSecure
	     */        
	    public void setDefaultSecure(boolean defaultSecure)
	    {
	    	this.defaultSecure = defaultSecure;
	    }

	    /**
	     * Sets the default User connecting to the Integrity Server
	     * @param defaultUserName
	     */    
	    public void setDefaultUserName(String defaultUserName)
	    {
	    	this.defaultUserName = defaultUserName;
	    }
	    
	    /**
	     * Sets the encrypted Password of the default user connecting to the Integrity Server
	     * @param defaultPassword
	     */        
	    public void setDefaultPassword(String defaultPassword)
	    {
	    	if( defaultPassword.indexOf(APISession.ENC_PREFIX) == 0 )
	    	{
	    		this.defaultPassword = Base64.encode(Base64.decode(defaultPassword.substring(APISession.ENC_PREFIX.length())));
	    	}
	    	else
	    	{
	    		this.defaultPassword = Base64.encode(defaultPassword);
	    	}	    	
	    }
	    
	    /**
         * Sets the default checkout thread pool size
         * @return
         */
        public void setDefaultCheckoutThreadPoolSize(int defaultCheckoutThreadPoolSize)
		{
            this.defaultCheckoutThreadPoolSize = defaultCheckoutThreadPoolSize;
        }
		
	    /**
	     * Validates that the port number is numeric and within a valid range 
	     * @param value Integer value for Port or IP Port
	     * @return
	     */
		public FormValidation doValidPortCheck(@QueryParameter String value)
		{
			// The field mks.port and mks.ipport will be validated through the checkUrl. 
			// When the user has entered some information and moves the focus away from field,
			// Hudson/Jenkins will call DescriptorImpl.doValidPortCheck to validate that data entered.
			try
			{
				int intValue = Integer.parseInt(value);
				// Adding plus 1 to the min value in case the default is left unchanged
				if( (intValue+1) < MIN_PORT_VALUE || intValue > MAX_PORT_VALUE )
				{
					return FormValidation.error("Value must be between " + MIN_PORT_VALUE + " and " + MAX_PORT_VALUE + "!");
				}
			}
			catch(NumberFormatException nfe)
			{
				return FormValidation.error("Value must be numeric!");
			}
			
			// Validation was successful if we got here, so we'll return all good!
		    return FormValidation.ok();
		}
		
		/**
		 * Validates that the thread pool size is numeric and within a valid range
		 * @param value Integer value for Thread Pool Size
		 * @return
		 */
		public FormValidation doValidCheckoutThreadPoolSizeCheck(@QueryParameter String value)
        {
            // The field mks.checkoutThreadPoolSize will be validated through the checkUrl. 
            // When the user has entered some information and moves the focus away from field,
            // Hudson/Jenkins will call DescriptorImpl.doValidCheckoutThreadPoolSizeCheck to validate that data entered.
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
