package hudson.scm;

import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;

public class IntegrityDeleteNonMembersAction extends Notifier implements Serializable
{
	private static final long serialVersionUID = 654691931521381720L;
	private static final Logger LOGGER = Logger.getLogger(IntegritySCM.class.getSimpleName());
	
	@Extension
    public static final IntegrityDeleteNonMembersDescriptorImpl DELETENONMEMBERS_DESCRIPTOR = new IntegrityDeleteNonMembersDescriptorImpl();

    /**
     * Executes the actual Integrity Delete Non Members operation
     */
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException
    {
        AbstractProject<?, ?> rootProject = build.getProject().getRootProject();

        if (!(rootProject.getScm() instanceof IntegritySCM))
        {
            listener.getLogger().println("Integrity DeleteNonMembers is being executed for an invalid context!  Current SCM is " + rootProject.getScm() + "!");
            return true;
        }

        IntegritySCM scm = IntegritySCM.class.cast(rootProject.getScm());
        try
        {
        	String resolvedAltWkspace = IntegrityCheckpointAction.evalGroovyExpression(build.getEnvironment(listener), scm.getAlternateWorkspace());
        	IntegrityDeleteNonMembersTask deleteNonMembers = new IntegrityDeleteNonMembersTask(listener, resolvedAltWkspace, 
        															DerbyUtils.viewProject(scm.getIntegrityProject().getProjectCacheTable()),
        															DerbyUtils.getDirList(scm.getIntegrityProject().getProjectCacheTable()));
        	if (!build.getWorkspace().act(deleteNonMembers))
        	{
        		return false;
        	}
        }
        catch (SQLException e)
        {
            listener.getLogger().println("A SQL Exception was caught!"); 
            listener.getLogger().println(e.getMessage());
            LOGGER.log(Level.SEVERE, "SQLException", e);
            return false;       
        }

        return true;
    }

    /**
     * Returns the build step we're monitoring
     */
    public BuildStepMonitor getRequiredMonitorService()
    {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public BuildStepDescriptor<Publisher> getDescriptor()
    {
        return DELETENONMEMBERS_DESCRIPTOR;
    }

    @Override
    public boolean needsToRunAfterFinalized()
    {
        return false;
    }

    /**
     * The relationship of Descriptor and IntegrityDeleteNonMembersAction (the describable) is akin to class and object.
     * This means the descriptor is used to create instances of the describable.
     * Usually the Descriptor is an internal class in the IntegrityDeleteNonMembersAction class named DescriptorImpl. 
     */
    public static class IntegrityDeleteNonMembersDescriptorImpl extends BuildStepDescriptor<Publisher> 
    {

        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType)
        {
            return true;
        }

        @Override
        public String getDisplayName()
        {
            return "Integrity - Delete Non Members";
        }
        
        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException
        {
            return new IntegrityDeleteNonMembersAction();
        }
    }
}
