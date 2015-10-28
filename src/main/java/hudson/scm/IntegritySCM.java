package hudson.scm;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.ModelObject;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.Run;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.sql.ConnectionPoolDataSource;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
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
	private final String ciServerURL = (null == Jenkins.getInstance().getRootUrl() ? "" : Jenkins.getInstance().getRootUrl());
	private String integrityURL;
	private IntegrityRepositoryBrowser browser;
	private String serverConfig;
	private String userName;
	private Secret password;
	private String configPath;
	private String includeList;
	private String excludeList;
	private String checkpointLabel;
	private String configurationName;
	private boolean cleanCopy;
	private boolean skipAuthorInfo = true;
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
	@Deprecated
	public IntegritySCM(IntegrityRepositoryBrowser browser, String serverConfig, String userName, String password, String configPath, 
						String includeList, String excludeList, boolean cleanCopy, String lineTerminator, boolean restoreTimestamp, 
						boolean skipAuthorInfo, boolean checkpointBeforeBuild, String checkpointLabel, String alternateWorkspace, 
						boolean fetchChangedWorkspaceFiles, boolean deleteNonMembers, int checkoutThreadPoolSize, String configurationName)
	{
    	// Log the construction
    	LOGGER.fine("IntegritySCM constructor (deprecated) has been invoked!");
		// Initialize the class variables
    	this.browser = browser;
    	this.serverConfig = serverConfig;
    	if( null != userName && userName.length() > 0 )
    	{
    		this.userName = userName;
    	}
    	else
    	{
    		this.userName = DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig).getUserName();
    	}
    	if( null != password && password.length() > 0 )
    	{
    		this.password = Secret.fromString(password);
    	}
    	else
    	{
    		this.password = DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig).getSecretPassword(); 
    	}
    	
    	this.configPath = configPath;
    	this.includeList = includeList;
    	this.excludeList = excludeList;
    	this.cleanCopy = cleanCopy;
    	this.lineTerminator = lineTerminator;
    	this.restoreTimestamp = restoreTimestamp;
    	this.skipAuthorInfo = skipAuthorInfo;
    	this.checkpointBeforeBuild = checkpointBeforeBuild;
    	this.checkpointLabel = checkpointLabel;    	
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
    	LOGGER.fine("Project User: " + this.userName);
    	LOGGER.fine("Project User Password: " + this.password);
    	LOGGER.fine("Configuration Name: " + this.configurationName);
    	LOGGER.fine("Configuration Path: " + this.configPath);
    	LOGGER.fine("Include Filter: " + this.includeList);
    	LOGGER.fine("Exclude Filter: " + this.excludeList);
    	LOGGER.fine("Line Terminator: " + this.lineTerminator);
    	LOGGER.fine("Restore Timestamp: " + this.restoreTimestamp);
    	LOGGER.fine("Clean: " + this.cleanCopy);
    	LOGGER.fine("Skip Author Info: " + this.skipAuthorInfo);
    	LOGGER.fine("Checkpoint Before Build: " + this.checkpointBeforeBuild);
    	LOGGER.fine("Tag Name: " + this.checkpointLabel);    	
    	LOGGER.fine("Alternate Workspace Directory: " + this.alternateWorkspace);
    	LOGGER.fine("Fetch Changed Workspace Files: " + this.fetchChangedWorkspaceFiles);
    	LOGGER.fine("Delete Non Members: " + this.deleteNonMembers);
    	LOGGER.fine("Checkout Thread Pool Size: " + this.checkoutThreadPoolSize);
	}

    @DataBoundConstructor
	public IntegritySCM(String serverConfig, String configPath, String configurationName)
	{
    	// Log the construction
    	LOGGER.fine("IntegritySCM constructor has been invoked!");
		// Initialize the class variables
    	this.serverConfig = serverConfig;
    	IntegrityConfigurable desSettings = DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig);
    	this.userName = desSettings.getUserName();
    	this.password = desSettings.getSecretPassword();     	
    	this.configPath = configPath;
    	this.includeList = "";
    	this.excludeList = "";
    	this.cleanCopy = false;
    	this.lineTerminator = "native";
    	this.restoreTimestamp = true;
    	this.skipAuthorInfo = true;
    	this.checkpointBeforeBuild = true;
    	this.checkpointLabel = "";    	
    	this.alternateWorkspace = "";
    	this.fetchChangedWorkspaceFiles = true;
    	this.deleteNonMembers = true;
		this.checkoutThreadPoolSize = DEFAULT_THREAD_POOL_SIZE;
		this.configurationName = configurationName;

    	// Initialize the Integrity URL
    	initIntegrityURL();    	
    	
    	LOGGER.fine("IntegritySCM constructed!");
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
     * Returns the project specific User connecting to the Integrity Server
     * @return
     */
	public String getUserName()
	{
		return this.userName;
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
    public String getCheckpointLabel()
    {
		if( checkpointLabel == null || checkpointLabel.length() == 0 )
		{
			return IntegrityCheckpointDescriptorImpl.defaultCheckpointLabel;
		}    	
    	return checkpointLabel;
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
	 * Sets the Integrity SCM web browser
	 * @param browser
	 */
	 @DataBoundSetter 
	 public final void setBrowser(IntegrityRepositoryBrowser browser)
	 {
		 this.browser = browser;
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
     * Sets the project specific User connecting to the Integrity Server
     * @return
     */
    @DataBoundSetter 
    public final void setUserName(String userName)
	{
    	if( null != userName && userName.length() > 0 )
    	{
    		this.userName = userName;
    	}
	}
	 
	/**
     * Sets the project specific encrypted Password of the user connecting to the Integrity Server
     * @param password - The clear password
     */
    @DataBoundSetter 
    public final void setPassword(String password)
	{
    	if( null != password && password.length() > 0 )
    	{
    		this.password = Secret.fromString(password);
    	}
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
    @DataBoundSetter 
    public final void setIncludeList(String includeList)
    {
    	this.includeList = includeList;
    }

    /**
     * Sets the files that will be not be included
     * @return
     */        
    @DataBoundSetter 
    public final void setExcludeList(String excludeList)
    {
    	this.excludeList = excludeList;
    }

    /**
     * Toggles whether or not the workspace is required to be cleaned
     * @return
     */        
    @DataBoundSetter 
    public final void setCleanCopy(boolean cleanCopy)
    {
    	this.cleanCopy = cleanCopy; 
    }

    /**
     * Sets the line terminator to apply when obtaining files from the Integrity Server
     * @return
     */        
    @DataBoundSetter 
    public final void setLineTerminator(String lineTerminator)
    {
    	this.lineTerminator = lineTerminator; 
    }

    /**
     * Toggles whether or not to restore the timestamp for individual files
     * @return
     */        
    @DataBoundSetter 
    public final void setRestoreTimestamp(boolean restoreTimestamp)
    {
    	this.restoreTimestamp = restoreTimestamp; 
    }

    /**
     * Toggles whether or not to use 'si revisioninfo' to determine author information
     * @return
     */        
    @DataBoundSetter 
    public final void setSkipAuthorInfo(boolean skipAuthorInfo)
    {
    	this.skipAuthorInfo = skipAuthorInfo; 
    }
    
    /**
     * Toggles whether or not a checkpoint should be performed before the build
     * @param checkpointBeforeBuild
     */
    @DataBoundSetter 
    public final void setCheckpointBeforeBuild(boolean checkpointBeforeBuild)
    {
    	this.checkpointBeforeBuild = checkpointBeforeBuild;
    }
    
    /**
     * Sets the label string for the checkpoint performed before the build
     * @param checkpointLabel
     */
    @DataBoundSetter 
    public final void setCheckpointLabel(String checkpointLabel)
    {
    	this.checkpointLabel = checkpointLabel;
    }
    
    /**
     * Sets an alternate workspace for the checkout directory
     * @param alternateWorkspace
     */
    @DataBoundSetter 
    public final void setAlternateWorkspace(String alternateWorkspace)
    {
    	this.alternateWorkspace = alternateWorkspace;
    }

    /**
     * Toggles whether or not changed workspace files should be synchronized
     * @param fetchChangedWorkspaceFiles
     */
    @DataBoundSetter 
    public final void setFetchChangedWorkspaceFiles(boolean fetchChangedWorkspaceFiles)
    {
    	this.fetchChangedWorkspaceFiles = fetchChangedWorkspaceFiles;
    }
    
    /**
     * Toggles whether or not non members should be deleted
     * @param deleteNonMembers
     */
    @DataBoundSetter 
    public final void setDeleteNonMembers(boolean deleteNonMembers)
    {
        this.deleteNonMembers = deleteNonMembers;
	}
	
     /** 
	 * Sets the thread pool size of parallel checkout threads
     * @param checkoutThreadPoolSize
     */
    @DataBoundSetter 
    public final void setCheckoutThreadPoolSize(int checkoutThreadPoolSize)
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

		env.put("MKSSI_HOST", ic.getHostName());
		env.put("MKSSI_PORT", String.valueOf(ic.getPort()));
		env.put("MKSSI_USER", userName);

		// Populate with information about the most recent checkpoint
		IntegrityCMProject siProject = getIntegrityProject();
		if( null != siProject && siProject.isBuild() )
		{
	    	env.put("MKSSI_PROJECT", siProject.getConfigurationPath());			
			env.put("MKSSI_BUILD", siProject.getProjectRevision());
		}
	}
	
	/**
	 * Overridden calcRevisionsFromBuild function
	 * Returns the current project configuration which can be used to difference any future configurations
	 */
	@Override
	public SCMRevisionState calcRevisionsFromBuild(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException 
	{
		// Log the call for debug purposes
		LOGGER.fine("calcRevisionsFromBuild() invoked...!");
		// Get the project cache table name for this build
		String projectCacheTable = null;
		Job<?, ?> job = run.getParent();
		String jobName = job.getName();
		
		try
		{
			projectCacheTable = DerbyUtils.getProjectCache(((DescriptorImpl)this.getDescriptor()).getDataSource(), 
																jobName, configurationName, run.getNumber());
		}
		catch (SQLException sqlex)
		{
	    	LOGGER.severe("SQL Exception caught...");
    		listener.getLogger().println("A SQL Exception was caught!"); 
    		listener.getLogger().println(sqlex.getMessage());
    		LOGGER.log(Level.SEVERE, "SQLException", sqlex);
		}				
		return new IntegrityRevisionState(jobName, configurationName, projectCacheTable);
	}

	/**
	 * Primes the Integrity Project metadata information
	 * @param api Integrity API Session
	 * @return response Integrity API Response
	 * @throws APIException
	 */
	private Response initializeCMProject(APISession api, String projectCacheTable, String resolvedConfigPath) throws APIException
	{
		// Get the project information for this project
		Command siProjectInfoCmd = new Command(Command.SI, "projectinfo");
		siProjectInfoCmd.addOption(new Option("project", resolvedConfigPath));	
		LOGGER.fine("Preparing to execute si projectinfo for " + resolvedConfigPath);
		Response infoRes = api.runCommand(siProjectInfoCmd);
		LOGGER.fine(infoRes.getCommandString() + " returned " + infoRes.getExitCode());
		// Initialize our siProject class variable
		IntegrityCMProject siProject = new IntegrityCMProject(infoRes.getWorkItems().next(), projectCacheTable);
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
		DerbyUtils.parseProject(siProject, viewRes.getWorkItems());
		return viewRes;
	}
	
	/**
	 * Toggles whether or not the Integrity SCM plugin can be used for polling
	 */
	@Override
	public boolean supportsPolling() 
	{
		return true;
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
	 */
	@Override
	public void checkout(Run<?, ?> run, Launcher launcher, FilePath workspace, 
							TaskListener listener, File changeLogFile,
							SCMRevisionState baseline) throws IOException, InterruptedException 
	{
		// Log the invocation... 
		LOGGER.fine("Start execution of checkout() routine...!");
		
		// Re-evaluate the config path to resolve any groovy expressions...
		String resolvedConfigPath = IntegrityCheckpointAction.evalGroovyExpression(run.getEnvironment(listener), configPath);
		
		// Provide links to the Change and Build logs for easy access from Integrity
		listener.getLogger().println("Change Log: " + ciServerURL + run.getUrl() + "changes");
		listener.getLogger().println("Build Log: " + ciServerURL + run.getUrl() + "console");
		
		// Lets start with creating an authenticated Integrity API Session for various parts of this operation...
		IntegrityConfigurable desSettings = DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig);
		IntegrityConfigurable coSettings = new IntegrityConfigurable("TEMP_ID", desSettings.getIpHostName(), desSettings.getIpPort(), desSettings.getHostName(), 
																		desSettings.getPort(), desSettings.getSecure(), userName, password.getPlainText());				
		APISession api = APISession.create(coSettings);
		
		// Ensure we've successfully created an API Session
		if( null == api )
		{
			listener.getLogger().println("Failed to establish an API connection to the Integrity Server!");
			throw new AbortException("Connection Failed!");
		}
		// Lets also open the change log file for writing...
		// Override file.encoding property so that we write as UTF-8 and do not have problems with special characters
		PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(changeLogFile),"UTF-8"));
		try
		{
			// Register the project cache for this build
			Job<?, ?> job = run.getParent();
			String projectCacheTable = DerbyUtils.registerProjectCache(((DescriptorImpl)this.getDescriptor()).getDataSource(), 
																		job.getName(), configurationName, run.getNumber());			
			
			// Next, load up the information for this Integrity Project's configuration
			listener.getLogger().println("Preparing to execute si projectinfo for " + resolvedConfigPath);
			initializeCMProject(api, projectCacheTable, resolvedConfigPath);
			IntegrityCMProject siProject = getIntegrityProject();
			// Check to see we need to checkpoint before the build
			if( checkpointBeforeBuild )
			{
				// Make sure we don't have a build project configuration
				if( ! siProject.isBuild() )
				{
					// Execute a pre-build checkpoint...
    				listener.getLogger().println("Preparing to execute pre-build si checkpoint for " + siProject.getConfigurationPath());
    				Response res = siProject.checkpoint(api, IntegrityCheckpointAction.evalGroovyExpression(run.getEnvironment(listener), checkpointLabel));
    				LOGGER.fine(res.getCommandString() + " returned " + res.getExitCode());        					
					WorkItem wi = res.getWorkItem(siProject.getConfigurationPath());
					String chkpt = wi.getResult().getField("resultant").getItem().getId();
					listener.getLogger().println("Successfully executed pre-build checkpoint for project " + 
													siProject.getConfigurationPath() + ", new revision is " + chkpt);
					// Update the siProject to use the new checkpoint as the basis for this build
					Command siProjectInfoCmd = new Command(Command.SI, "projectinfo");
					siProjectInfoCmd.addOption(new Option("project", siProject.getConfigurationPath() + "#forceJump=#b=" + chkpt));	

					Response infoRes = api.runCommand(siProjectInfoCmd);
					siProject.initializeProject(infoRes.getWorkItems().next());
				}
				else
				{
					listener.getLogger().println("Cannot perform a pre-build checkpoint for build project configuration!");
				}
			}
			listener.getLogger().println("Preparing to execute si viewproject for " + siProject.getConfigurationPath());
			initializeCMProjectMembers(api);
					
	    	// Now, we need to find the project state from the previous build.
			String prevProjectCache = null;
	        if( null != baseline && baseline instanceof IntegrityRevisionState )
	        {
	        	IntegrityRevisionState irs = (IntegrityRevisionState)baseline;
	        	prevProjectCache = irs.getProjectCache();

	            if( null != prevProjectCache && prevProjectCache.length() > 0 ) 
	            {
	            	// Compare the current project with the old revision state
	            	LOGGER.fine("Found previous project state " + prevProjectCache);
	            	DerbyUtils.compareBaseline(prevProjectCache, projectCacheTable, skipAuthorInfo, api);
	            }
			}
			else
			{
	            // We don't have the previous Integrity Revision State!
				LOGGER.fine("Cannot construct previous Integrity Revision State!");
				// Prime the author information for the current build as this could be the first build
				if( ! skipAuthorInfo ){ DerbyUtils.primeAuthorInformation(projectCacheTable, api); }
			}
			
	        // After all that insane interrogation, we have the current Project state that is
	        // correctly initialized and either compared against its baseline or is a fresh baseline itself
	        // Now, lets figure out how to populate the workspace...
			List<Hashtable<CM_PROJECT, Object>> projectMembersList = DerbyUtils.viewProject(projectCacheTable);
			List<String> dirList = DerbyUtils.getDirList(projectCacheTable);
			String resolvedAltWkspace = IntegrityCheckpointAction.evalGroovyExpression(run.getEnvironment(listener), alternateWorkspace);
			// If we we were not able to establish the previous project state, then always do full checkout.  cleanCopy = true
			// Otherwise, update the workspace in accordance with the user's cleanCopy option
			IntegrityCheckoutTask coTask = new IntegrityCheckoutTask(projectMembersList, dirList, resolvedAltWkspace, lineTerminator, restoreTimestamp,
																	((null == prevProjectCache || prevProjectCache.length() == 0) ? true : cleanCopy), 
																	fetchChangedWorkspaceFiles,checkoutThreadPoolSize, listener, coSettings);
			
			// Execute the IntegrityCheckoutTask.invoke() method to do the actual synchronization...
			if( workspace.act(coTask) )
			{ 
				// Now that the workspace is updated, lets save the current project state for future comparisons
				listener.getLogger().println("Saving current Integrity Project configuration...");
				if( fetchChangedWorkspaceFiles ){ DerbyUtils.updateChecksum(projectCacheTable, coTask.getChecksumUpdates()); }
				// Write out the change log file, which will be used by the parser to report the updates
				listener.getLogger().println("Writing build change log...");
				writer.println(siProject.getChangeLog(String.valueOf(run.getNumber()), projectMembersList));				
				listener.getLogger().println("Change log successfully generated: " + changeLogFile.getAbsolutePath());
				// Delete non-members in this workspace, if appropriate...
				if( deleteNonMembers )
				{
					
				    IntegrityDeleteNonMembersTask deleteNonMembers = new IntegrityDeleteNonMembersTask(listener, resolvedAltWkspace, projectMembersList, dirList);
				    if( ! workspace.act(deleteNonMembers) )
					{
				        throw new AbortException("Failed to delete non-members!");
				    }
				}
			}
			else
			{
				// Checkout failed!  Returning false...
				throw new AbortException("Failed to synchronize workspace!");
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
    		
    		throw new AbortException("Caught Integrity APIException!");
	    }
		catch(SQLException sqlex)
		{
	    	LOGGER.severe("SQL Exception caught...");
    		listener.getLogger().println("A SQL Exception was caught!"); 
    		listener.getLogger().println(sqlex.getMessage());
    		LOGGER.log(Level.SEVERE, "SQLException", sqlex);
    		
    		throw new AbortException("Caught Derby SQLException!");
		}
	    finally
	    {
	        if( writer != null )
			{
	            writer.close();
	        }
	    	api.Terminate();
	    }

		// Log the completion... 
		LOGGER.fine("Completed execution of checkout() routine...!");
	}
	


	/**
	 * Overridden compareRemoteRevisionWith function
	 * Loads up the previous project configuration and compares 
	 * that against the current to determine if the project has changed
	 */
	@Override
	public PollingResult compareRemoteRevisionWith(Job<?, ?> job, Launcher launcher, FilePath workspace,
													TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException	
	{
		// Log the call for now...
		LOGGER.fine("compareRemoteRevisionWith() invoked...!");

        // Lets get the baseline from our last build
        if( null != baseline && baseline instanceof IntegrityRevisionState )
        {
        	IntegrityRevisionState irs = (IntegrityRevisionState) baseline;
	        String prevProjectCache = irs.getProjectCache();
	        if( null != prevProjectCache && prevProjectCache.length() > 0 ) 
	        {
	        	// Compare the current project with the old revision state
	            LOGGER.fine("Found previous project state " + prevProjectCache);

				// Next, load up the information for the current Integrity Project
				// Lets start with creating an authenticated Integrity API Session for various parts of this operation...
				IntegrityConfigurable desSettings = DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig);
				IntegrityConfigurable coSettings = new IntegrityConfigurable("TEMP_ID", desSettings.getIpHostName(), desSettings.getIpPort(), desSettings.getHostName(), 
																				desSettings.getPort(), desSettings.getSecure(), userName, password.getPlainText());		        			
				APISession api = APISession.create(coSettings);	
				if( null != api )
				{
	    			try
	    			{
	    				// Get the project cache table name
	    				String projectCacheTable = DerbyUtils.registerProjectCache(((DescriptorImpl)this.getDescriptor()).getDataSource(), job.getName(), configurationName, 0);				        				
	    				// Re-evaluate the config path to resolve any groovy expressions...
	    				String resolvedConfigPath = IntegrityCheckpointAction.evalGroovyExpression(job.getCharacteristicEnvVars(), configPath);
	    				listener.getLogger().println("Preparing to execute si projectinfo for " + resolvedConfigPath);
	    				initializeCMProject(api, projectCacheTable, resolvedConfigPath);
	    				listener.getLogger().println("Preparing to execute si viewproject for " + resolvedConfigPath);
	    				initializeCMProjectMembers(api);
	
	    				// Compare this project with the old project 
	    				int changeCount = DerbyUtils.compareBaseline(prevProjectCache, projectCacheTable, skipAuthorInfo, api);		
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
		    	// We've got no previous builds, build now!
				LOGGER.fine("No prior Integrity Project state can be found!  Advice to build now!");
	        	return PollingResult.BUILD_NOW;	        	
	        }
        }
	    else
        {
	    	// We've got no previous builds, build now!
			LOGGER.fine("No prior Integrity Project state can be found!  Advice to build now!");
        	return PollingResult.BUILD_NOW;
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
	public DescriptorImpl getDescriptor() 
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
	public static final class DescriptorImpl extends SCMDescriptor<IntegritySCM> implements ModelObject
    {
		@Extension
    	public static final DescriptorImpl INTEGRITY_DESCRIPTOR = new DescriptorImpl();
    	private ConnectionPoolDataSource dataSource;
    	private List<IntegrityConfigurable> configurations;
		
        public DescriptorImpl() 
        {
        	super(IntegritySCM.class, IntegrityWebUI.class);
        	configurations = new ArrayList<IntegrityConfigurable>();
            load();

            // Initialize our derby environment
            System.setProperty(DerbyUtils.DERBY_SYS_HOME_PROPERTY, Jenkins.getInstance().getRootDir().getAbsolutePath());;
            DerbyUtils.loadDerbyDriver();
    		LOGGER.info("Creating Integrity SCM cache db connection...");
    		dataSource = DerbyUtils.createConnectionPoolDataSource(Jenkins.getInstance().getRootDir().getAbsolutePath());
    		LOGGER.info("Creating Integrity SCM cache registry...");
    		DerbyUtils.createRegistry(dataSource);            
            
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

		@Override 
        public boolean isApplicable(@SuppressWarnings("rawtypes") Job project) 
        {
            return true;
        }
        
        /**
         * Returns the pooled connection data source for the derby db
         * @return
         */
        public ConnectionPoolDataSource getDataSource()
        {
        	return dataSource;
        }
        
        /**
         * Returns the default groovy expression for the checkpoint label
         * @return
         */
        public String getCheckpointLabel()
        {
        	return IntegrityCheckpointDescriptorImpl.defaultCheckpointLabel;
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
         * Returns a default value for the Configuration Name
         * @return
         */
        public String getConfigurationName()
        {
        	return UUID.randomUUID().toString();
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
				if( name.equals(configuration.getConfigId()) ) 
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
					listBox.add(config.getName(), config.getConfigId());
				}				
			}
			return listBox;
		}		
		
		/**
		 * A credentials validation helper
		 * @param hostName
		 * @param port
		 * @param userName
		 * @param password
		 * @param secure
		 * @param ipHostName
		 * @param ipPort
		 * @return
		 * @throws IOException
		 * @throws ServletException
		 */
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
        	
			IntegrityConfigurable ic = new IntegrityConfigurable("TEMP_ID", ipHostName, ipPort, hostName, port, secure, userName, password);
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
