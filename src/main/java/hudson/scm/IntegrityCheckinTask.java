/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/
package hudson.scm;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;

import com.mks.api.response.APIException;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.api.ExceptionHandler;
import jenkins.security.Roles;

public class IntegrityCheckinTask implements FileCallable<Boolean>
{
  private static final long serialVersionUID = 4165773747683187630L;
  private static final Logger LOGGER = Logger.getLogger(IntegritySCM.class.getSimpleName());
  private final String itemID;
  private final String buildID;
  private final String ciConfigPath;
  private final String ciWorkspaceDir;
  private final String ciIncludes;
  private final String ciExcludes;
  private final BuildListener listener;
  private IntegrityConfigurable integrityConfig;

  /**
   * The check-in task provides updates back to an Integrity CM Project
   * 
   * @param ciConfigPath Configuration path for the project to check-in artifacts after the build
   * @param ciWorkspaceDir Workspace directory containing the check-in artifacts created as a result
   *        of the build
   * @param ciIncludes Ant-style includes filter for check-in files
   * @param ciExcludes Ant-style excludes filter for check-in files
   * @param build Hudson abstract build object
   * @param listener The Hudson build listener
   * @throws Exception 
   */
  public IntegrityCheckinTask(String ciConfigPath, String ciWorkspaceDir, String ciIncludes,
      String ciExcludes, AbstractBuild<?, ?> build, BuildListener listener,
      IntegrityConfigurable integrityConfig) throws Exception
  {

    this.itemID = build.getEnvironment(listener).get("ItemID", "");
    this.buildID = build.getFullDisplayName();
    this.ciConfigPath = IntegrityCheckpointAction
        .evalGroovyExpression(build.getEnvironment(listener), ciConfigPath);
    this.ciWorkspaceDir = ciWorkspaceDir;
    this.ciIncludes = ciIncludes;
    this.ciExcludes = ciExcludes;
    this.listener = listener;
    this.integrityConfig = integrityConfig;
    LOGGER.fine("Windchill RV&S Checkin Task Created!");
  }

  /**
   * Indicates that this task can be run slaves.
   * 
   * @param checker RoleChecker
   */
  public void checkRoles(RoleChecker checker) throws SecurityException
  {
    checker.check((RoleSensitive) this, Roles.SLAVE);
  }

  /**
   * This task wraps around the code necessary to checkout Integrity CM Members on remote machines
   * 
   * @param workspaceFile Build environment's workspace directory
   * @param channel Virtual Channel
   */
  @Override
  public Boolean invoke(File workspaceFile, VirtualChannel channel) throws IOException
  {
    // Figure out what folder should be used to update Integrity
    File checkinDir = new File(ciWorkspaceDir);
    // Convert the file object to a hudson FilePath
    FilePath workspace = new FilePath(checkinDir.isAbsolute() ? checkinDir
        : new File(workspaceFile.getAbsolutePath() + IntegritySCM.FS + checkinDir.getPath()));

    listener.getLogger().println(
        "Windchill RV&S project '" + ciConfigPath + "' will be updated from directory " + workspace);

    try
    {
      // Determine what files need to be checked-in
      FilePath[] artifacts = workspace.list(ciIncludes, ciExcludes);
      if (artifacts.length > 0)
      {
        // Create our Change Package for the supplied itemID
        String cpid =
            IntegrityCMMember.createCP(integrityConfig, itemID, "Build updates from " + buildID);
        for (int i = 0; i < artifacts.length; i++)
        {
          FilePath member = artifacts[i];
          String relativePath = ("" + member).substring(("" + workspace).length() + 1);

          // This is not a recursive directory tree check-in, only process files found
          if (!member.isDirectory())
          {
            IntegrityCMMember.updateMember(integrityConfig, ciConfigPath, member, relativePath,
                cpid, "Build updates from " + buildID);
          }
        }

        // Finally submit the build updates Change Package if its not :none or :bypass
        if (!cpid.equals(":none") && !cpid.equals(":bypass"))
        {
          IntegrityCMMember.submitCP(integrityConfig, cpid);
        } else
        {
          IntegrityCMMember.unlockMembers(integrityConfig, ciConfigPath);
        }

        // Log the success
        listener.getLogger().println("Successfully updated Windchill RV&S project '" + ciConfigPath
            + "' with contents of workspace (" + workspace + ")!");
      }

    } catch (InterruptedException iex)
    {
      LOGGER.severe("Interrupted Exception caught...");
      listener.getLogger().println("An Interrupted Exception was caught!");
      LOGGER.log(Level.SEVERE, "InterruptedException", iex);
      listener.getLogger().println(iex.getMessage());
      listener.getLogger().println("Failed to update Windchill RV&S project '" + ciConfigPath
          + "' with contents of workspace (" + workspace + ")!");
      return false;
    } catch (APIException aex)
    {
      LOGGER.severe("API Exception caught...");
      listener.getLogger().println("An API Exception was caught!");
      ExceptionHandler eh = new ExceptionHandler(aex);
      LOGGER.severe(eh.getMessage());
      listener.getLogger().println(eh.getMessage());
      listener.getLogger().println("Failed to update Windchill RV&S project '" + ciConfigPath
          + "' with contents of workspace (" + workspace + ")!");
      LOGGER.fine(eh.getCommand() + " returned exit code " + eh.getExitCode());
      LOGGER.log(Level.SEVERE, "APIException", aex);
      return false;
    }

    // If we got here, everything is good on the check-in...
    return true;
  }
}
