package hudson.scm;
import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.InvisibleAction;
import hudson.model.AbstractBuild;

public class IntegrityTestParametersAction extends InvisibleAction implements EnvironmentContributingAction{
	private String value;

	  public IntegrityTestParametersAction(String value)
	  {
	    this.value = value;
	  }

	  /* from EnvironmentContributingAction */
	  public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
	    env.put("ItemID", value);
	  }
}
