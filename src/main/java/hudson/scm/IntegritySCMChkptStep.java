/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/

package hudson.scm;

import java.util.logging.Logger;

import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.IntegritySCM.DescriptorImpl;
import hudson.util.ListBoxModel;
import hudson.util.Secret;


public class IntegritySCMChkptStep extends AbstractStepImpl
{
  private static final Logger LOGGER = Logger.getLogger(IntegritySCM.class.getSimpleName());

  private String serverConfig;
  private String userName;
  private Secret password;
  private String configPath;
  private String checkpointLabel;
  private String checkpointDesc;
  private IntegrityConfigurable connectionSettings;

  public String getServerConfig()
  {
    return this.serverConfig;
  }

  @DataBoundSetter
  public void setUserName(String userName)
  {
    this.userName = userName;
  }

  public String getUserName()
  {
    return this.userName;
  }

  @DataBoundSetter
  public void setPassword(String password)
  {
    this.password = Secret.fromString(password);
  }

  /**
   * Returns the project specific encrypted password of the user connecting to the Integrity Server
   * 
   * @return
   */
  public String getPassword()
  {
    return this.password.getEncryptedValue();
  }

  @DataBoundSetter
  public void setConfigPath(String configPath)
  {
    this.configPath = configPath;
  }

  public String getConfigPath()
  {
    return this.configPath;
  }

  @DataBoundSetter
  public void setCheckpointLabel(String checkpointLabel)
  {
    this.checkpointLabel = checkpointLabel;
  }

  public String getCheckpointLabel()
  {
    return this.checkpointLabel;
  }

  @DataBoundSetter
  public void setCheckpointDesc(String checkpointDesc)
  {
    this.checkpointDesc = checkpointDesc;
  }

  public String getCheckpointDesc()
  {
    return this.checkpointDesc;
  }

  public IntegrityConfigurable getConnectionSettings()
  {
    IntegrityConfigurable desSettings =
        DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig);
    this.connectionSettings = new IntegrityConfigurable("TEMP_ID", desSettings.getIpHostName(),
        desSettings.getIpPort(), desSettings.getHostName(), desSettings.getPort(),
        desSettings.getSecure(), null == userName ? desSettings.getUserName() : userName,
        null == password ? desSettings.getPasswordInPlainText() : password.getPlainText());

    return this.connectionSettings;
  }

  @DataBoundConstructor
  public IntegritySCMChkptStep(String serverConfig)
  {
    this.serverConfig = serverConfig;
    IntegrityConfigurable config = getConnectionSettings();
    this.userName = config.getUserName();
    this.password = Secret.fromString(config.getPassword());
    this.configPath = "";
    this.checkpointLabel = "";
    this.checkpointDesc = "";

    LOGGER.fine("IntegritySCMChkptStep() constructed!");
  }

  @Extension(optional = true)
  public static final class IntegritySCMChkptDescriptorImpl extends AbstractStepDescriptorImpl
  {

    public IntegritySCMChkptDescriptorImpl()
    {
      super(IntegritySCMChkptStepExecution.class);

      LOGGER.fine("IntegritySCMChkptDescriptorImpl() invoked!");
    }

    @Override
    public String getFunctionName()
    {
      return "sicheckpoint";
    }

    @Override
    public String getDisplayName()
    {
      return "Windchill RV&S SCM Checkpoint";
    }

    /**
     * Provides a list box for users to choose from a list of Integrity Server configurations
     * 
     * @param configuration Simple configuration name
     * @return
     */
    public ListBoxModel doFillServerConfigItems(@QueryParameter String serverConfig)
    {
      return DescriptorImpl.INTEGRITY_DESCRIPTOR.doFillServerConfigItems(serverConfig);
    }
  }

  public static class IntegritySCMChkptStepExecution extends AbstractSynchronousStepExecution<Void>
  {
    private static final long serialVersionUID = 7564942554899422192L;
    @Inject
    private transient IntegritySCMChkptStep step;
    @StepContextParameter
    private transient Run<?, ?> run;
    @StepContextParameter
    private transient FilePath workspace;
    @StepContextParameter
    private transient TaskListener listener;
    @StepContextParameter
    private transient Launcher launcher;

    @Override
    protected Void run() throws Exception
    {
      IntegritySCMChkptNotifierStep notifier =
          new IntegritySCMChkptNotifierStep(step.getConnectionSettings(), step.getConfigPath(),
              step.getCheckpointLabel(), step.getCheckpointDesc());
      notifier.perform(run, workspace, launcher, listener);
      return null;
    }
  }
}
