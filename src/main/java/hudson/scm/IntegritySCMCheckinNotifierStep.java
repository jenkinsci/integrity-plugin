/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/
package hudson.scm;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mks.api.response.APIException;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.api.ExceptionHandler;
import hudson.scm.api.Retrier;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import jenkins.tasks.SimpleBuildStep;

@SuppressWarnings("unchecked")
public class IntegritySCMCheckinNotifierStep extends Notifier implements SimpleBuildStep
{
  private static final Logger LOGGER = Logger.getLogger(IntegritySCM.class.getSimpleName());
  private static final String WITH_CONTENTS_OF_WS = "' with contents of workspace (";
  private final IntegrityConfigurable ciSettings;
  private final String configPath;
  private final String includes;
  private final String excludes;
  private final String itemID;

  public IntegritySCMCheckinNotifierStep(IntegrityConfigurable ciSettings, String configPath,
      String includes, String excludes, String itemID)
  {
    this.ciSettings = ciSettings;
    this.configPath = configPath;
    this.includes = includes;
    this.excludes = excludes;
    this.itemID = itemID;
  }

  public BuildStepMonitor getRequiredMonitorService()
  {
    return BuildStepMonitor.NONE;
  }

  public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
      throws InterruptedException, IOException
  {
    listener.getLogger().println(
        "Integrity project '" + configPath + "' will be updated from directory " + workspace);
    listener.getLogger()
        .println("Change Package ID will be derived from '" + itemID + "' supplied...");
    String buildID = run.getFullDisplayName();

    // For handling retries
    Retrier retryHandler = new Retrier(3, 1000);
    while (true) {
        try
        {
          // Determine what files need to be checked-in
          FilePath[] artifacts = workspace.list(includes, excludes);
          if (artifacts.length > 0)
          {
            // Create our Change Package for the supplied itemID
            String cpid =
                IntegrityCMMember.createCP(ciSettings, itemID, "Build updates from " + buildID);
            for (int i = 0; i < artifacts.length; i++)
            {
              FilePath member = artifacts[i];
              String relativePath = ("" + member).substring(("" + workspace).length() + 1);

              // This is not a recursive directory tree check-in, only process files found
              if (!member.isDirectory())
              {
                IntegrityCMMember.updateMember(ciSettings, configPath, member, relativePath, cpid,
                    "Build updates from " + buildID);
              }
            }

            // Finally submit the build updates Change Package if its not :none or :bypass
            if (!cpid.equals(":none") && !cpid.equals(":bypass"))
            {
              IntegrityCMMember.submitCP(ciSettings, cpid);
            } else
            {
              IntegrityCMMember.unlockMembers(ciSettings, configPath);
            }

            // Log the success
            listener.getLogger().println("Successfully updated Integrity project '" + configPath
                + WITH_CONTENTS_OF_WS + workspace + ")!");
          }

        } catch (InterruptedException iex)
        {
          LOGGER.severe("Interrupted Exception caught...");
          listener.getLogger().println("An Interrupted Exception was caught!");
          LOGGER.log(Level.SEVERE, "InterruptedException", iex);
          listener.getLogger().println(iex.getMessage());
          listener.getLogger().println("Failed to update Integrity project '" + configPath
              + WITH_CONTENTS_OF_WS + workspace + ")!");
          throw iex;
        } catch (APIException aex)
        {
          LOGGER.severe("API Exception caught...");
          listener.getLogger().println("An API Exception was caught!");
          ExceptionHandler eh = new ExceptionHandler(aex);
          LOGGER.severe(eh.getMessage());
          listener.getLogger().println(eh.getMessage());
          listener.getLogger().println("Failed to update Integrity project '" + configPath
              + WITH_CONTENTS_OF_WS + workspace + ")!");
          LOGGER.fine(eh.getCommand() + " returned exit code " + eh.getExitCode());
          LOGGER.log(Level.SEVERE, "APIException", aex);    
          retryHandler.exceptionOccurred();
          continue;
        }        
     }
  }
}
