/*******************************************************************************
 * Contributors: PTC 2016
 *******************************************************************************/
package hudson.scm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import javax.servlet.ServletException;
import javax.sql.ConnectionPoolDataSource;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.mks.api.Command;
import com.mks.api.MultiValue;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;
import com.mks.api.response.WorkItem;
import com.mks.api.response.WorkItemIterator;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.IntegrityCMMember.CPInfo;
import hudson.scm.IntegrityCMMember.CPMember;
import hudson.scm.IntegrityCheckpointAction.IntegrityCheckpointDescriptorImpl;
import hudson.scm.api.APIUtils;
import hudson.scm.api.ExceptionHandler;
import hudson.scm.api.command.CommandFactory;
import hudson.scm.api.command.IAPICommand;
import hudson.scm.api.option.APIOption;
import hudson.scm.api.option.IAPIFields;
import hudson.scm.api.option.IAPIOption;
import hudson.scm.api.session.APISession;
import hudson.scm.api.session.ISession;
import hudson.scm.browsers.IntegrityWebUI;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

/**
 * This class provides an integration between Hudson/Jenkins for Continuous Builds and PTC Integrity
 * for Configuration Management
 */
public class IntegritySCM extends AbstractIntegritySCM implements Serializable
{
  /**
   * Create a constructor that takes non-transient fields, and add the
   * annotation @DataBoundConstructor to it. Using the annotation helps the Stapler class to find
   * which constructor that should be used when automatically copying values from a web form to a
   * class.
   */
  @Deprecated
  public IntegritySCM(IntegrityRepositoryBrowser browser, String serverConfig, String userName,
      String password, String configPath, String includeList, String excludeList, boolean cleanCopy,
      String lineTerminator, boolean restoreTimestamp, boolean skipAuthorInfo,
      boolean checkpointBeforeBuild, String checkpointLabel, String alternateWorkspace,
      boolean fetchChangedWorkspaceFiles, boolean deleteNonMembers, int checkoutThreadPoolSize,
      String configurationName)
  {
    super();
    // Log the construction
    LOGGER.fine("IntegritySCM constructor (deprecated) has been invoked!");
    // Initialize the class variables
    this.browser = browser;
    this.serverConfig = serverConfig;
    if (null != userName && userName.length() > 0)
    {
      this.userName = userName;
    } else
    {
      this.userName =
          DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig).getUserName();
    }
    if (null != password && password.length() > 0)
    {
      this.password = Secret.fromString(password);
    } else
    {
      this.password =
          DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig).getSecretPassword();
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
    this.checkoutThreadPoolSize =
        (checkoutThreadPoolSize > 0 ? checkoutThreadPoolSize : DEFAULT_THREAD_POOL_SIZE);
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
    super();
    // Log the construction
    LOGGER.fine("IntegritySCM constructor has been invoked!");
    // Initialize the class variables
    this.serverConfig = serverConfig;
    IntegrityConfigurable desSettings =
        DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig);
    this.userName = desSettings.getUserName();
    this.password = desSettings.getSecretPassword();
    this.configPath = configPath;
    this.includeList = "";
    this.excludeList = "";
    this.cleanCopy = false;
    this.CPBasedMode = false;
    this.lineTerminator = "native";
    this.restoreTimestamp = true;
    this.skipAuthorInfo = true;
    this.checkpointBeforeBuild = true;
    this.checkpointLabel = "";
    this.alternateWorkspace = "";
    this.fetchChangedWorkspaceFiles = true;
    this.deleteNonMembers = true;
    this.checkoutThreadPoolSize = DEFAULT_THREAD_POOL_SIZE;
    this.checkoutThreadTimeout = DEFAULT_CHECKOUT_THREAD_TIMEOUT;
    this.configurationName = configurationName;

    // Initialize the Integrity URL
    initIntegrityURL();

    LOGGER.fine("IntegritySCM constructed!");
  }

  /**
   * Provides a mechanism to update the Integrity URL, based on updates to the hostName/port/secure
   * variables
   */
  private void initIntegrityURL()
  {
    // Initialize the Integrity URL
    IntegrityConfigurable ic =
        ((DescriptorImpl) this.getDescriptor()).getConfiguration(serverConfig);
    integrityURL = (ic.getSecure() ? "https://" : "http://") + ic.getHostName() + ":"
        + String.valueOf(ic.getPort());
  }

  /**
   * Adds Integrity CM Project info to the build variables
   */
  @Override
  public void buildEnvVars(AbstractBuild<?, ?> build, Map<String, String> env)
  {
    super.buildEnvVars(build, env);
    LOGGER.fine("buildEnvVars() invoked...!");
    IntegrityConfigurable ic =
        ((DescriptorImpl) this.getDescriptor()).getConfiguration(serverConfig);

    env.put("MKSSI_HOST", ic.getHostName());
    env.put("MKSSI_PORT", String.valueOf(ic.getPort()));
    env.put("MKSSI_USER", userName);

    // Populate with information about the most recent checkpoint
    IntegrityCMProject siProject = getIntegrityProject();
    if (null != siProject && siProject.isBuild())
    {
      env.put("MKSSI_PROJECT", siProject.getConfigurationPath());
      env.put("MKSSI_BUILD", siProject.getProjectRevision());
    }
  }

  /**
   * Overridden calcRevisionsFromBuild function Returns the current project configuration which can
   * be used to difference any future configurations
   */
  @Override
  public SCMRevisionState calcRevisionsFromBuild(Run<?, ?> run, FilePath workspace,
      Launcher launcher, TaskListener listener) throws IOException, InterruptedException
  {
    // Log the call for debug purposes
    LOGGER.fine("calcRevisionsFromBuild() invoked...!");
    // Get the project cache table name for this build
    String projectCacheTable = null;
    Job<?, ?> job = run.getParent();
    String jobName = job.getName();

    try
    {
      projectCacheTable = DerbyUtils.getCachedTableFromRegistry("PROJECT_CACHE_TABLE",
          ((DescriptorImpl) this.getDescriptor()).getDataSource(), jobName, configurationName,
          run.getNumber());
    } catch (SQLException sqlex)
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
   * 
   * @param run
   * @param listener
   * @return response Integrity API Response
   * @throws InterruptedException
   * @throws IOException
   * @throws APIException
   */
  private Response initializeCMProject(EnvVars environment, String projectCacheTable)
      throws APIException, IOException, InterruptedException
  {
    // Re-evaluate the config path to resolve any groovy expressions...
    String resolvedConfigPath =
        IntegrityCheckpointAction.evalGroovyExpression(environment, configPath);

    // Get the project information for this project
    IAPICommand command = CommandFactory.createCommand(IAPICommand.PROJECT_INFO_COMMAND,
        DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig));
    command.addOption(new APIOption(IAPIOption.PROJECT, resolvedConfigPath));

    Response infoRes = command.execute();

    LOGGER.fine(infoRes.getCommandString() + " returned " + infoRes.getExitCode());
    // Initialize our siProject class variable
    IntegrityCMProject siProject =
        new IntegrityCMProject(APIUtils.getWorkItem(infoRes), projectCacheTable);
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
   * 
   * @param command API Command for the 'si viewproject' command
   * @return
   */
  private void applyMemberFilters(IAPICommand command)
  {
    // Checking if our include list has any entries
    if (null != includeList && includeList.length() > 0)
    {
      StringBuilder filterString = new StringBuilder();
      String[] filterTokens = includeList.split(",|;");
      // prepare a OR combination of include filters (all in one filter, separated by comma if
      // needed)
      for (int i = 0; i < filterTokens.length; i++)
      {
        filterString.append(i > 0 ? "," : "");
        filterString.append("file:");
        filterString.append(filterTokens[i]);
      }
      command.addOption(new APIOption(IAPIOption.FILTER, filterString.toString()));
    }

    // Checking if our exclude list has any entries
    if (null != excludeList && excludeList.length() > 0)
    {
      String[] filterTokens = excludeList.split(",|;");
      // prepare a AND combination of exclude filters (one filter each filter)
      for (int i = 0; i < filterTokens.length; i++)
      {
        if (filterTokens[i] != null)
        {
          command.addOption(new APIOption(IAPIOption.FILTER, "!file:" + filterTokens[i]));
        }
      }
    }
  }

  /**
   * Primes the Integrity Project Member metadata information
   * 
   * @param cpBasedMode
   * 
   * @return response Integrity API Response
   * @throws APIException
   * @throws SQLException
   * @throws AbortException
   * @throws ExecutionException
   * @throws InterruptedException
   */
  private Response initializeCMProjectMembers()
      throws APIException, SQLException, AbortException, InterruptedException, ExecutionException
  {
    IntegrityCMProject siProject = getIntegrityProject();

    // Lets parse this project
    IAPICommand command = CommandFactory.createCommand(IAPICommand.VIEW_PROJECT_COMMAND,
        DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig));

    command.addOption(new APIOption(IAPIOption.PROJECT, siProject.getConfigurationPath()));
    MultiValue mv = APIUtils.createMultiValueField(IAPIFields.FIELD_SEPARATOR, IAPIFields.NAME,
        IAPIFields.CONTEXT, IAPIFields.CP_ID, IAPIFields.MEMBER_REV, IAPIFields.MEMBER_TIMESTAMP,
        IAPIFields.MEMBER_DESCRIPTION, IAPIFields.TYPE);
    command.addOption(new APIOption(IAPIOption.FIELDS, mv));

    // Apply our include/exclude filters
    applyMemberFilters(command);

    LOGGER.fine("Preparing to execute si viewproject for " + siProject.getConfigurationPath());
    Response viewRes = command.execute();

    // Update Derby DB with the API results
    siProject.parseProject(viewRes.getWorkItems());

    try
    {
      // Terminate the Session associated with the view project command - with_interim session
      command.terminateAPI();
    } catch (Exception e)
    {
      // Log and ignore. This exception is thrown if there is an exception while invalidating
      // session pool session.
      LOGGER.log(Level.FINE, "Exception terminating interim API Session for View Project");
    }
    return viewRes;
  }

  /**
   * Overridden checkout function This is the real invocation of this plugin. Currently, we will do
   * a project info and determine the true nature of the project Subsequent to that we will run a
   * view project command and cache the information on each member, so that we can execute project
   * checkout commands. This obviously eliminates the need for a sandbox and can wily nilly delete
   * the workspace directory as needed
   */
  @Override
  public void checkout(Run<?, ?> run, Launcher launcher, FilePath workspace, TaskListener listener,
      File changeLogFile, SCMRevisionState baseline) throws IOException, InterruptedException
  {
    // Log the invocation...
    LOGGER.fine("Start execution of checkout() routine...!");

    // Provide links to the Change and Build logs for easy access from Integrity
    listener.getLogger().println("Change Log: " + ciServerURL + run.getUrl() + "changes");
    listener.getLogger().println("Build Log: " + ciServerURL + run.getUrl() + "console");

    Map<CPInfo, List<CPMember>> membersInCP = new HashMap<CPInfo, List<CPMember>>();

    // Lets start with creating an authenticated Integrity API Session for various parts of this
    // operation...
    IntegrityConfigurable desSettings =
        DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig);
    IntegrityConfigurable coSettings = new IntegrityConfigurable("TEMP_ID",
        desSettings.getIpHostName(), desSettings.getIpPort(), desSettings.getHostName(),
        desSettings.getPort(), desSettings.getSecure(), userName, password.getPlainText());

    // Lets also open the change log file for writing...
    // Override file.encoding property so that we write as UTF-8 and do not have problems with
    // special characters
    PrintWriter writer =
        new PrintWriter(new OutputStreamWriter(new FileOutputStream(changeLogFile), "UTF-8"));
    try
    {
      // Register the project cache for this build
      Job<?, ?> job = run.getParent();
      String projectCacheTable =
          DerbyUtils.registerProjectCache(((DescriptorImpl) this.getDescriptor()).getDataSource(),
              job.getName(), configurationName, run.getNumber());

      // Next, load up the information for this Integrity Project's configuration
      // listener.getLogger().println("Preparing to execute si projectinfo for " +
      // resolvedConfigPath);
      listener.getLogger().println("Preparing to execute si projectinfo for " + configPath);
      initializeCMProject(run.getEnvironment(listener), projectCacheTable);
      IntegrityCMProject siProject = getIntegrityProject();

      // Check to see we need to checkpoint before the build
      if (checkpointBeforeBuild)
      {
        checkPointBeforeBuild(run, listener, siProject);
      }

      listener.getLogger()
          .println("Preparing to execute si viewproject for " + siProject.getConfigurationPath());
      initializeCMProjectMembers();

      // Now, we need to find the project state from the previous build.
      String prevProjectCache = null;
      if (null != baseline && baseline instanceof IntegrityRevisionState)
      {
        IntegrityRevisionState irs = (IntegrityRevisionState) baseline;
        prevProjectCache = irs.getProjectCache();

        if (null != prevProjectCache && prevProjectCache.length() > 0)
        {
          if (CPBasedMode && !cleanCopy)
          {
            Set<String> projectCPIDs = new HashSet<String>();
            Run<?, ?> lastSuccjob = job.getLastSuccessfulBuild();
            if (lastSuccjob != null)
            {
              Date lastSuccBuildDate = new Date(lastSuccjob.getStartTimeInMillis());
              projectCPIDs = siProject.projectCPDiff(
                  DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig),
                  lastSuccBuildDate);

              IntegrityCMMember.viewCP(
                  DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig), projectCPIDs,
                  job.getFullName().replace("/", "_"), membersInCP);
            }
          }

          // Compare the current project with the old revision state
          LOGGER.fine("Found previous project state " + prevProjectCache);
          DerbyUtils.compareBaseline(serverConfig, prevProjectCache, projectCacheTable, membersInCP,
              skipAuthorInfo, CPBasedMode);
        }
      } else
      {
        // We don't have the previous Integrity Revision State!
        LOGGER.fine("Cannot construct previous Integrity Revision State!");
        // Prime the author information for the current build as this could be the first build
        if (!skipAuthorInfo)
        {
          DerbyUtils.primeAuthorInformation(serverConfig, projectCacheTable);
        }
      }

      // After all that insane interrogation, we have the current Project state that is
      // correctly initialized and either compared against its baseline or is a fresh baseline
      // itself
      // Now, lets figure out how to populate the workspace...
      List<Hashtable<CM_PROJECT, Object>> projectMembersList =
          DerbyUtils.viewProject(projectCacheTable);
      List<String> dirList = DerbyUtils.getDirList(projectCacheTable);
      String resolvedAltWkspace = IntegrityCheckpointAction
          .evalGroovyExpression(run.getEnvironment(listener), alternateWorkspace);
      // If we we were not able to establish the previous project state, then always do full
      // checkout. cleanCopy = true
      // Otherwise, update the workspace in accordance with the user's cleanCopy option

      IntegrityCheckoutTask coTask = new IntegrityCheckoutTask(projectMembersList, dirList,
          resolvedAltWkspace, lineTerminator, restoreTimestamp,
          ((null == prevProjectCache || prevProjectCache.length() == 0) ? true : cleanCopy),
          fetchChangedWorkspaceFiles, checkoutThreadPoolSize, checkoutThreadTimeout, listener, coSettings);

      // Execute the IntegrityCheckoutTask.invoke() method to do the actual synchronization...
      if (workspace.act(coTask))
      {
        // Now that the workspace is updated, lets save the current project state for future
        // comparisons
        listener.getLogger().println("Saving current Integrity Project configuration...");
        if (fetchChangedWorkspaceFiles)
        {
          DerbyUtils.updateChecksum(projectCacheTable, coTask.getChecksumUpdates());
        }
        // Write out the change log file, which will be used by the parser to report the updates
        listener.getLogger().println("Writing build change log...");
        if (CPBasedMode)
        {
          writer.println(
              siProject.getChangeLogforCPMode(String.valueOf(run.getNumber()), membersInCP));
        } else
        {
          writer
              .println(siProject.getChangeLog(String.valueOf(run.getNumber()), projectMembersList));
        }
        listener.getLogger()
            .println("Change log successfully generated: " + changeLogFile.getAbsolutePath());
        // Delete non-members in this workspace.
        if (deleteNonMembers)
        {
          IntegrityDeleteNonMembersTask deleteNonMembers = new IntegrityDeleteNonMembersTask(
              listener, resolvedAltWkspace, projectMembersList, dirList);
          if (!workspace.act(deleteNonMembers))
          {
            throw new AbortException("Failed to delete non-members!");
          }
        }
      } else
      {
        // Checkout failed! Returning false...
        throw new AbortException("Failed to synchronize workspace!");
      }
    } catch (APIException aex)
    {
      LOGGER.severe("API Exception caught...");
      listener.getLogger().println("An API Exception was caught!");
      ExceptionHandler eh = new ExceptionHandler(aex);
      LOGGER.severe(eh.getMessage());
      listener.getLogger().println(eh.getMessage());
      LOGGER.fine(eh.getCommand() + " returned exit code " + eh.getExitCode());
      listener.getLogger().println(eh.getCommand() + " returned exit code " + eh.getExitCode());
      throw new AbortException("Caught Integrity APIException!");
    } catch (SQLException sqlex)
    {
      LOGGER.severe("SQL Exception caught...");
      listener.getLogger().println("A SQL Exception was caught!");
      listener.getLogger().println(sqlex.getMessage());
      LOGGER.log(Level.SEVERE, "SQLException", sqlex);
      throw new AbortException("Caught Derby SQLException!");
    } catch (ExecutionException e)
    {
      LOGGER.log(Level.SEVERE, "Execution Exception while parsing Derby Project Members", e);
      listener.getLogger()
          .println("Execution Exception while parsing Derby Project Members : " + e.getMessage());
      throw new AbortException("Execution Exception while parsing Derby Project Members");
    } finally
    {
      if (writer != null)
      {
        writer.close();
      }
    }

    // Log the completion...
    LOGGER.fine("Completed execution of checkout() routine...!");
  }

  /**
   * @param run
   * @param listener
   * @param siProject
   * @throws APIException
   * @throws AbortException
   * @throws IOException
   * @throws InterruptedException
   * @throws InterruptedException
   */
  private void checkPointBeforeBuild(Run<?, ?> run, TaskListener listener,
      IntegrityCMProject siProject) throws AbortException, IOException, InterruptedException,
          com.mks.api.response.InterruptedException, APIException
  {
    // Make sure we don't have a build project configuration
    if (!siProject.isBuild())
    {
      // Execute a pre-build checkpoint...
      listener.getLogger().println(
          "Preparing to execute pre-build si checkpoint for " + siProject.getConfigurationPath());
      Response res =
          siProject.checkpoint(DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig),
              IntegrityCheckpointAction.evalGroovyExpression(run.getEnvironment(listener),
                  checkpointLabel));
      LOGGER.fine(res.getCommandString() + " returned " + res.getExitCode());
      WorkItem wi = res.getWorkItem(siProject.getConfigurationPath());
      String chkpt = wi.getResult().getField(IAPIFields.RESULTANT).getItem().getId();
      listener.getLogger().println("Successfully executed pre-build checkpoint for project "
          + siProject.getConfigurationPath() + ", new revision is " + chkpt);
      // Update the siProject to use the new checkpoint as the basis for this build
      IAPICommand command = CommandFactory.createCommand(IAPICommand.PROJECT_INFO_COMMAND,
          DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig));
      
      command.addOption(new APIOption(IAPIOption.PROJECT, siProject.getProjectName()));
      command.addOption(new APIOption(IAPIOption.PROJECT_REVISION, chkpt));

      Response infoRes = command.execute();
      siProject.initializeProject(infoRes.getWorkItems().next());
    } else
    {
      listener.getLogger()
          .println("Cannot perform a pre-build checkpoint for build project configuration!");
    }
  }

  /**
   * Overridden compareRemoteRevisionWith function that against the current to determine if the
   * project has changed Loads up the previous project configuration and compares
   */
  @Override
  public PollingResult compareRemoteRevisionWith(Job<?, ?> job, Launcher launcher,
      FilePath workspace, TaskListener listener, SCMRevisionState baseline)
          throws IOException, InterruptedException
  {
    // Log the call for now...
    LOGGER.fine("compareRemoteRevisionWith() invoked...!");
    int changeCount = 0;

    // Lets get the baseline from our last build
    if (null != baseline && baseline instanceof IntegrityRevisionState)
    {
      IntegrityRevisionState irs = (IntegrityRevisionState) baseline;
      String prevProjectCache = irs.getProjectCache();
      if (null != prevProjectCache && prevProjectCache.length() > 0)
      {
        // Compare the current project with the old revision state
        LOGGER.fine("Found previous project state " + prevProjectCache);

        // Next, load up the information for the current Integrity Project
        // Lets start with creating an authenticated Integrity API Session for various parts of this
        // operation...
        try
        {
          // Get the project cache table name
          String projectCacheTable = DerbyUtils.registerProjectCache(
              ((DescriptorImpl) this.getDescriptor()).getDataSource(), job.getName(),
              configurationName, 0);

          initializeCMProject(job.getCharacteristicEnvVars(), projectCacheTable);
          Map<CPInfo, List<CPMember>> membersInCP = new HashMap<CPInfo, List<CPMember>>();

          if (CPBasedMode)
          {
            Set<String> projectCPIDs = new HashSet<String>();
            Run<?, ?> lastSuccjob = job.getLastSuccessfulBuild();
            if (lastSuccjob != null)
            {
              Date lastSuccBuildDate = new Date(lastSuccjob.getStartTimeInMillis());
              projectCPIDs = getIntegrityProject().projectCPDiff(
                  DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig),
                  lastSuccBuildDate);

              IntegrityCMMember.viewCP(
                  DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig), projectCPIDs,
                  job.getFullName().replace("/", ""), membersInCP);
              changeCount = membersInCP.size();
            }
          } else
          {
            initializeCMProjectMembers();

            // Compare this project with the old project for file mode
            changeCount = DerbyUtils.compareBaseline(serverConfig, prevProjectCache,
                projectCacheTable, membersInCP, skipAuthorInfo, false);
          }
          // Finally decide whether or not we need to build again
          if (changeCount > 0)
          {
            if (CPBasedMode)
              listener.getLogger()
                  .println("Detected total " + changeCount + " closed change packages.");
            else
              listener.getLogger()
                  .println("Project contains changes a total of " + changeCount + " changes!");
            return PollingResult.SIGNIFICANT;
          } else
          {
            listener.getLogger().println("No new changes detected in project!");
            return PollingResult.NO_CHANGES;
          }
        } catch (APIException aex)
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
        } catch (SQLException sqlex)
        {
          LOGGER.severe("SQL Exception caught...");
          listener.getLogger().println("A SQL Exception was caught!");
          listener.getLogger().println(sqlex.getMessage());
          LOGGER.log(Level.SEVERE, "SQLException", sqlex);
          return PollingResult.NO_CHANGES;
        } catch (ExecutionException e)
        {
          LOGGER.log(Level.SEVERE, "Execution Exception while parsing Derby Project Members", e);
          listener.getLogger().println(
              "Execution Exception while parsing Derby Project Members : " + e.getMessage());
          return PollingResult.NO_CHANGES;
        }
      } else
      {
        // We've got no previous builds, build now!
        LOGGER.fine("No prior Integrity Project state can be found!  Advice to build now!");
        return PollingResult.BUILD_NOW;
      }
    } else
    {
      // We've got no previous builds, build now!
      LOGGER.fine("No prior Integrity Project state can be found!  Advice to build now!");
      return PollingResult.BUILD_NOW;
    }
  }

  /**
   * The relationship of Descriptor and SCM (the describable) is akin to class and object. This
   * means the descriptor is used to create instances of the describable. Usually the Descriptor is
   * an internal class in the SCM class named DescriptorImpl. The Descriptor should also contain the
   * global configuration options as fields, just like the SCM class contains the configurations
   * options for a job.
   */
  public static final class DescriptorImpl extends SCMDescriptor<IntegritySCM>
      implements ModelObject
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
      System.setProperty(DerbyUtils.DERBY_SYS_HOME_PROPERTY,
          Jenkins.getInstance().getRootDir().getAbsolutePath());;
      DerbyUtils.loadDerbyDriver();
      LOGGER.info("Creating Integrity SCM cache db connection...");
      dataSource = DerbyUtils
          .createConnectionPoolDataSource(Jenkins.getInstance().getRootDir().getAbsolutePath());
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
      scm.browser =
          RepositoryBrowsers.createInstance(IntegrityWebUI.class, req, formData, "browser");
      if (scm.browser == null)
      {
        scm.browser = new IntegrityWebUI(null);
      }

      return scm;
    }

    /**
     * Returns the name of the SCM, this is the name that will show up next to CVS, Subversion, etc.
     * when configuring a job.
     */
    @Override
    public String getDisplayName()
    {
      return "Integrity";
    }

    /**
     * This method is invoked when the global configuration page is submitted. In the method the
     * data in the web form should be copied to the Descriptor's fields. To persist the fields to
     * the global configuration XML file, the save() method must be called. Data is defined in the
     * global.jelly page.
     */
    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException
    {
      // Log the request to configure
      LOGGER.fine("Request to configure IntegritySCM (SCMDescriptor) invoked...");
      this.configurations =
          req.bindJSONToList(IntegrityConfigurable.class, formData.get("serverConfig"));
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
     * 
     * @return
     */
    public ConnectionPoolDataSource getDataSource()
    {
      return dataSource;
    }

    /**
     * Returns the default groovy expression for the checkpoint label
     * 
     * @return
     */
    public String getCheckpointLabel()
    {
      return IntegrityCheckpointDescriptorImpl.defaultCheckpointLabel;
    }

    /**
     * Returns the default thread pool size for a new project
     * 
     * @return
     */
    public int getCheckoutThreadPoolSize()
    {
      return DEFAULT_THREAD_POOL_SIZE;
    }

    /**
     * Returns a default value for the Configuration Name
     * 
     * @return
     */
    public String getConfigurationName()
    {
      return UUID.randomUUID().toString();
    }
    
    /**
     * Returns the default checkout thread timeout for a specific project
     * 
     * @return
     */
    public int getCheckoutThreadTimeout()
    {
      return DEFAULT_CHECKOUT_THREAD_TIMEOUT;
    }

    /**
     * Returns the list of Integrity Server connections.
     * 
     * @return A list of IntegrityConfigurable objects.
     */
    public List<IntegrityConfigurable> getConfigurations()
    {
      if (null == this.configurations)
      {
        this.configurations = new ArrayList<IntegrityConfigurable>();
      }

      return this.configurations;
    }

    /**
     * Sets the list of Integrity Server connections.
     * 
     * @param configurations A list of IntegrityConfigurable objects.
     */
    public void setConfigurations(List<IntegrityConfigurable> configurations)
    {
      this.configurations = configurations;
    }

    /**
     * Return the IntegrityConfigurable object for the specified simple name
     * 
     * @param name
     * @return
     */
    public IntegrityConfigurable getConfiguration(String name)
    {
      for (IntegrityConfigurable configuration : this.configurations)
      {
        if (name.equals(configuration.getConfigId()))
        {
          return configuration;
        }
      }

      return null;
    }

    /**
     * Provides a list box for users to choose from a list of Integrity Server configurations
     * 
     * @param configuration Simple configuration name
     * @return
     */
    public ListBoxModel doFillServerConfigItems(@QueryParameter String serverConfig)
    {
      ListBoxModel listBox = new ListBoxModel();

      if (null != this.configurations && this.configurations.size() > 0)
      {
        for (IntegrityConfigurable config : this.configurations)
        {
          listBox.add(config.getName(), config.getConfigId());
        }
      }
      return listBox;
    }

    /**
     * A credentials validation helper
     * 
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
     * @throws APIException 
     */
    public FormValidation doTestConnection(
        @QueryParameter("serverConfig.hostName") final String hostName,
        @QueryParameter("serverConfig.port") final int port,
        @QueryParameter("serverConfig.userName") final String userName,
        @QueryParameter("serverConfig.password") final String password,
        @QueryParameter("serverConfig.secure") final boolean secure,
        @QueryParameter("serverConfig.ipHostName") final String ipHostName,
        @QueryParameter("serverConfig.ipPort") final int ipPort)
            throws IOException, ServletException, APIException
    {
      LOGGER.fine("Testing Integrity API Connection...");
      LOGGER.fine("hostName: " + hostName);
      LOGGER.fine("port: " + port);
      LOGGER.fine("userName: " + userName);
      LOGGER.fine("password: " + Secret.fromString(password).getEncryptedValue());
      LOGGER.fine("secure: " + secure);
      LOGGER.fine("ipHostName: " + ipHostName);
      LOGGER.fine("ipPort: " + ipPort);

      IntegrityConfigurable ic = new IntegrityConfigurable(null, ipHostName, ipPort, hostName, port,
          secure, userName, password);
      ISession api = APISession.create(ic);
      if (null != api)
      {
    	Command  cmd = new Command(Command.IM, "about");
    	Response res = api.runCommand(cmd);
    	WorkItemIterator wit = res.getWorkItems();
    	while(wit.hasNext())
    	{
    		WorkItem wi = wit.next();
    		String version = wi.getField("version").getValueAsString();
    		String versions[] = version.split("\\.");
    		int majorVer = Integer.parseInt(versions[0]);
    		int minorVer = Integer.parseInt(versions[1]);
    		String strVerMsg = "Integrity server version: " + version;
    		LOGGER.fine(strVerMsg);
    		if (majorVer <= 10 && (majorVer == 10 && minorVer < 8))
   			    LOGGER.fine("This plugin version is unsupported with " + strVerMsg);
    	}
        api.terminate();
        return FormValidation.ok("Connection successful!");
      } else
      {
        return FormValidation.error("Failed to establish connection!");
      }
    }

    /**
     * Validates that the thread pool size is numeric and within a valid range
     * 
     * @param value Integer value for Thread Pool Size
     * @return
     */
    public FormValidation doValidCheckoutThreadPoolSizeCheck(@QueryParameter String value)
    {
      // The field checkoutThreadPoolSize will be validated through the checkUrl.
      // When the user has entered some information and moves the focus away from field,
      // Jenkins will call DescriptorImpl.doValidCheckoutThreadPoolSizeCheck to validate that data
      // entered.
      try
      {
        int intValue = Integer.parseInt(value);
        if (intValue < 1 || intValue > 10)
        {
          return FormValidation.error("Thread pool size must be between 1 an 10");
        }
      } catch (NumberFormatException nfe)
      {
        return FormValidation.error("Value must be numeric!");
      }

      // Validation was successful if we got here, so we'll return all good!
      return FormValidation.ok();
    }
    
    /**
     * Validates that the thread timeout is numeric and within a valid range
     * 
     * @param value Integer value for Thread Timeout
     * @return
     */
    public FormValidation doValidCheckoutThreadTimeoutCheck(@QueryParameter String value)
    {
      // The field checkoutThreadTimeout will be validated through the checkUrl.
      // When the user has entered some information and moves the focus away from field,
      // Jenkins will call DescriptorImpl.validCheckoutThreadTimeoutCheck to validate that data
      // entered.
      try
      {
        int intValue = Integer.parseInt(value);
        if (intValue < 1 || intValue > 90)
        {
          return FormValidation.error("Checkout Thread timeout must be between 1 minute and 90 minutes");
        }
      } catch (NumberFormatException nfe)
      {
        return FormValidation.error("Value must be numeric!");
      }

      // Validation was successful if we got here, so we'll return all good!
      return FormValidation.ok();
    }
  }
}
