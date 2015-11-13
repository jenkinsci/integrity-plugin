// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.
package hudson.scm;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.export.Exported;

import hudson.scm.IntegrityCheckpointAction.IntegrityCheckpointDescriptorImpl;
import hudson.scm.IntegritySCM.DescriptorImpl;
import hudson.scm.browsers.IntegrityWebUI;
import hudson.util.Secret;
import jenkins.model.Jenkins;

/**
 * @author Author: asen
 * @version $Revision: $
 */
public abstract class AbstractIntegritySCM extends SCM implements Serializable
{
    private static final long serialVersionUID = 7559894846609712683L;
    
    protected static final Logger LOGGER = Logger.getLogger(IntegritySCM.class.getName());
    protected static final Map<String, IntegrityCMProject> projects = new ConcurrentHashMap<String, IntegrityCMProject>();
    public static final String NL = System.getProperty("line.separator");
    public static final String FS = System.getProperty("file.separator");
    public static final int MIN_PORT_VALUE = 1;
    public static final int MAX_PORT_VALUE = 65535;
    public static final int DEFAULT_THREAD_POOL_SIZE = 5;
    public static final SimpleDateFormat SDF = new SimpleDateFormat(
	    "MMM dd, yyyy h:mm:ss a");
    protected final String ciServerURL = (null == Jenkins.getInstance()
	    .getRootUrl() ? "" : Jenkins.getInstance().getRootUrl());
    protected String integrityURL;
    protected IntegrityRepositoryBrowser browser;
    protected String serverConfig;
    protected String userName;
    protected Secret password;
    protected String configPath;
    protected String includeList;
    protected String excludeList;
    protected String checkpointLabel;
    protected String configurationName;
    protected boolean cleanCopy;
    protected boolean skipAuthorInfo = true;
    protected String lineTerminator = "native";
    protected boolean restoreTimestamp = true;
    protected boolean checkpointBeforeBuild = false;
    protected String alternateWorkspace;
    protected boolean fetchChangedWorkspaceFiles = false;
    protected boolean deleteNonMembers = false;
    protected int checkoutThreadPoolSize = DEFAULT_THREAD_POOL_SIZE;

    public AbstractIntegritySCM()
    {
	super();
    }

    @Override
    @Exported
    public IntegrityRepositoryBrowser getBrowser()
    {
	return browser == null ? new IntegrityWebUI(null) : browser;
    }

    /**
     * Returns the simple server configuration name
     * 
     * @return
     */
    public String getServerConfig()
    {
	return serverConfig;
    }

    /**
     * Returns the project specific User connecting to the Integrity Server
     * 
     * @return
     */
    public String getUserName()
    {
	return this.userName;
    }

    /**
     * Returns the project specific encrypted password of the user connecting to
     * the Integrity Server
     * 
     * @return
     */
    public String getPassword()
    {
	return this.password.getEncryptedValue();
    }

    /**
     * Returns the project specific Secret password of the user connecting to
     * the Integrity Server
     * 
     * @return
     */
    public Secret getSecretPassword()
    {
	return this.password;
    }

    /**
     * Returns the Project or Configuration Path for a Integrity Source Project
     * 
     * @return
     */
    public String getConfigPath()
    {
	return configPath;
    }

    /**
     * Returns the files that will be excluded
     * 
     * @return
     */
    public String getIncludeList()
    {
	return includeList;
    }

    /**
     * Returns the files that will be included
     * 
     * @return
     */
    public String getExcludeList()
    {
	return excludeList;
    }

    /**
     * Returns true/false depending on whether or not the workspace is required
     * to be cleaned
     * 
     * @return
     */
    public boolean getCleanCopy()
    {
	return cleanCopy;
    }

    /**
     * Returns the line terminator to apply when obtaining files from the
     * Integrity Server
     * 
     * @return
     */
    public String getLineTerminator()
    {
	return lineTerminator;
    }

    /**
     * Returns true/false depending on whether or not the restore timestamp
     * option is in effect
     * 
     * @return
     */
    public boolean getRestoreTimestamp()
    {
	return restoreTimestamp;
    }

    /**
     * Returns true/false depending on whether or not to use 'si revisioninfo'
     * to determine author information
     * 
     * @return
     */
    public boolean getSkipAuthorInfo()
    {
	return skipAuthorInfo;
    }

    /**
     * Returns true/false depending on whether or not perform a checkpoint
     * before the build
     * 
     * @return
     */
    public boolean getCheckpointBeforeBuild()
    {
	return checkpointBeforeBuild;
    }

    /**
     * Returns the label string for the checkpoint performed before the build
     * 
     * @return
     */
    public String getCheckpointLabel()
    {
	if (checkpointLabel == null || checkpointLabel.length() == 0) {
	    return IntegrityCheckpointDescriptorImpl.defaultCheckpointLabel;
	}
	return checkpointLabel;
    }

    /**
     * Returns the alternate workspace directory
     * 
     * @return
     */
    public String getAlternateWorkspace()
    {
	return alternateWorkspace;
    }

    /**
     * Returns the true/false depending on whether or not to synchronize changed
     * workspace files
     * 
     * @return
     */
    public boolean getFetchChangedWorkspaceFiles()
    {
	return fetchChangedWorkspaceFiles;
    }

    /**
     * Returns the true/false depending on whether non members should be deleted
     * before the build
     * 
     * @return
     */
    public boolean getDeleteNonMembers()
    {
	return deleteNonMembers;
    }

    /**
     * Returns the size of the thread pool for parallel checkouts
     * 
     * @return
     */
    public int getCheckoutThreadPoolSize()
    {
	return checkoutThreadPoolSize;
    }

    /**
     * Returns the configuration name for this project Required when working
     * with Multiple SCMs plug-in
     */
    public String getConfigurationName()
    {
	return configurationName;
    }

    /**
     * Sets the Integrity SCM web browser
     * 
     * @param browser
     */
    @DataBoundSetter
    public final void setBrowser(IntegrityRepositoryBrowser browser)
    {
	this.browser = browser;
    }

    /**
     * Sets the server configuration name for this project
     * 
     * @param serverConfig
     */
    public void setServerConfig(String serverConfig)
    {
	this.serverConfig = serverConfig;
	IntegrityConfigurable ic = ((DescriptorImpl) this.getDescriptor())
		.getConfiguration(serverConfig);
	integrityURL = (ic.getSecure() ? "https://" : "http://")
		+ ic.getHostName() + ":" + String.valueOf(ic.getPort());
	
    }

    /**
     * Sets the project specific User connecting to the Integrity Server
     * 
     * @return
     */
    @DataBoundSetter
    public final void setUserName(String userName)
    {
	if (null != userName && userName.length() > 0) {
	    this.userName = userName;
	}
    }

    /**
     * Sets the project specific encrypted Password of the user connecting to
     * the Integrity Server
     * 
     * @param password - The clear password
     */
    @DataBoundSetter
    public final void setPassword(String password)
    {
	if (null != password && password.length() > 0) {
	    this.password = Secret.fromString(password);
	}
    }

    /**
     * Sets the Project or Configuration Path for an Integrity Source Project
     * 
     * @return
     */
    public void setConfigPath(String configPath)
    {
	this.configPath = configPath;
    }

    /**
     * Sets the files that will be not be included
     * 
     * @return
     */
    @DataBoundSetter
    public final void setIncludeList(String includeList)
    {
	this.includeList = includeList;
    }

    /**
     * Sets the files that will be not be included
     * 
     * @return
     */
    @DataBoundSetter
    public final void setExcludeList(String excludeList)
    {
	this.excludeList = excludeList;
    }

    /**
     * Toggles whether or not the workspace is required to be cleaned
     * 
     * @return
     */
    @DataBoundSetter
    public final void setCleanCopy(boolean cleanCopy)
    {
	this.cleanCopy = cleanCopy;
    }

    /**
     * Sets the line terminator to apply when obtaining files from the Integrity
     * Server
     * 
     * @return
     */
    @DataBoundSetter
    public final void setLineTerminator(String lineTerminator)
    {
	this.lineTerminator = lineTerminator;
    }

    /**
     * Toggles whether or not to restore the timestamp for individual files
     * 
     * @return
     */
    @DataBoundSetter
    public final void setRestoreTimestamp(boolean restoreTimestamp)
    {
	this.restoreTimestamp = restoreTimestamp;
    }

    /**
     * Toggles whether or not to use 'si revisioninfo' to determine author
     * information
     * 
     * @return
     */
    @DataBoundSetter
    public final void setSkipAuthorInfo(boolean skipAuthorInfo)
    {
	this.skipAuthorInfo = skipAuthorInfo;
    }

    /**
     * Toggles whether or not a checkpoint should be performed before the build
     * 
     * @param checkpointBeforeBuild
     */
    @DataBoundSetter
    public final void setCheckpointBeforeBuild(boolean checkpointBeforeBuild)
    {
	this.checkpointBeforeBuild = checkpointBeforeBuild;
    }

    /**
     * Sets the label string for the checkpoint performed before the build
     * 
     * @param checkpointLabel
     */
    @DataBoundSetter
    public final void setCheckpointLabel(String checkpointLabel)
    {
	this.checkpointLabel = checkpointLabel;
    }

    /**
     * Sets an alternate workspace for the checkout directory
     * 
     * @param alternateWorkspace
     */
    @DataBoundSetter
    public final void setAlternateWorkspace(String alternateWorkspace)
    {
	this.alternateWorkspace = alternateWorkspace;
    }

    /**
     * Toggles whether or not changed workspace files should be synchronized
     * 
     * @param fetchChangedWorkspaceFiles
     */
    @DataBoundSetter
    public final void setFetchChangedWorkspaceFiles(
	    boolean fetchChangedWorkspaceFiles)
    {
	this.fetchChangedWorkspaceFiles = fetchChangedWorkspaceFiles;
    }

    /**
     * Toggles whether or not non members should be deleted
     * 
     * @param deleteNonMembers
     */
    @DataBoundSetter
    public final void setDeleteNonMembers(boolean deleteNonMembers)
    {
	this.deleteNonMembers = deleteNonMembers;
    }

    /**
     * Sets the thread pool size of parallel checkout threads
     * 
     * @param checkoutThreadPoolSize
     */
    @DataBoundSetter
    public final void setCheckoutThreadPoolSize(int checkoutThreadPoolSize)
    {
	this.checkoutThreadPoolSize = checkoutThreadPoolSize;
    }

    /**
     * Sets the configuration name for this project
     * 
     * @param configurationName Name for this project configuration
     */
    public void setConfigurationName(String configurationName)
    {
	this.configurationName = configurationName;
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
     * Toggles whether or not a workspace is required for polling Since, we're
     * using a Server Integration Point in the Integrity API, we do not require
     * a workspace.
     */
    @Override
    public boolean requiresWorkspaceForPolling()
    {
	return false;
    }

    /**
     * Overridden createChangeLogParser function Creates a custom Integrity
     * Change Log Parser, which compares two view project outputs
     * 
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
     * Returns the Integrity Configuration Management Project
     * 
     * @return
     */
    public IntegrityCMProject getIntegrityProject()
    {
	return findProject(configurationName);
    }

    /**
     * @param configurationName
     * @return
     */
    public static IntegrityCMProject findProject(String configurationName)
    {
	return hasProject(configurationName)
		? projects.get(configurationName)
		: null;
    }

    /**
     * @param configurationName
     * @return
     */
    public static boolean hasProject(String configurationName)
    {
	return projects.containsKey(configurationName);
    }

    /**
     * Returns the SCMDescriptor<?> for the SCM object. The SCMDescriptor is
     * used to create new instances of the SCM.
     */
    @Override
    public DescriptorImpl getDescriptor()
    {
	return DescriptorImpl.INTEGRITY_DESCRIPTOR;
    }
    
}
