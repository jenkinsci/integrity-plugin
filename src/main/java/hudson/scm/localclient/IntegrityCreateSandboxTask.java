package hudson.scm.localclient;

import com.mks.api.Command;
import com.mks.api.Option;
import com.mks.api.response.*;
import hudson.FilePath;
import hudson.model.Job;
import hudson.remoting.VirtualChannel;
import hudson.scm.IntegrityCMProject;
import hudson.scm.IntegrityConfigurable;
import hudson.scm.IntegritySCM;
import hudson.scm.api.APIUtils;
import hudson.scm.api.option.IAPIFields;
import hudson.scm.api.session.APISession;
import hudson.scm.api.session.ISession;
import jenkins.security.Roles;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;

import java.io.File;
import java.io.IOException;
import java.lang.InterruptedException;
import java.util.logging.Logger;

/**
 * Created by asen on 06-06-2017.
 */
public class IntegrityCreateSandboxTask implements FilePath.FileCallable<Boolean>
{
    private final Job<?, ?> job;
    private final IntegrityConfigurable integrityConfigurable;
    private final String alternateWorkspaceDir;
    private final IntegrityCMProject siProject;

    private static final Logger LOGGER = Logger.getLogger(IntegrityCreateSandboxTask.class.getSimpleName());

    /**
     *  @param job
     * @param integrityConfigurable
     * @param siProject
     * @param alternateWorkspace
     */
    public IntegrityCreateSandboxTask(
		    Job<?, ?> job,
		    IntegrityConfigurable integrityConfigurable,
		    IntegrityCMProject siProject,
		    String alternateWorkspace)
    {
        this.job = job;
	this.integrityConfigurable = integrityConfigurable;
	this.siProject = siProject;
	this.alternateWorkspaceDir = alternateWorkspace;
    }

    /**
     *
     * @throws APIException
     * @param siProject
     * @param workspace
     */
    private void createSandbox(IntegrityCMProject siProject,
		    FilePath workspace) throws APIException
    {
	ISession session = getAPISession();
	session.ping();
	createSandbox(session, siProject, workspace);
    }

    /**
     *  @param session
     * @param siProject
     * @param workspace
     */
    private void createSandbox(ISession session,
		    IntegrityCMProject siProject, FilePath workspace) throws APIException
    {
	Command cmd = new Command(Command.SI, "createsandbox");
	cmd.addOption(new Option("project", siProject.getProjectName()));
	cmd.addSelection(workspace.getName());
	Response res = session.runCommand(cmd);
    }

    /**
     *
     * @return
     */
    private ISession getAPISession()
    {
	return APISession.createLocalIntegrationPoint(integrityConfigurable);
    }

    @Override
    public Boolean invoke(File workspaceFile, VirtualChannel virtualChannel)
		    throws IOException, InterruptedException
    {
	// Figure out where we should be checking out this project
	File checkOutDir = (null != alternateWorkspaceDir && alternateWorkspaceDir.length() > 0)
			? new File(alternateWorkspaceDir) : workspaceFile;
	// Convert the file object to a hudson FilePath (helps us with workspace.deleteContents())
	FilePath workspace = new FilePath(checkOutDir.isAbsolute() ? checkOutDir
			: new File(workspaceFile.getAbsolutePath() + IntegritySCM.FS + checkOutDir.getPath()));
	try {
	    createSandbox(this.siProject, workspace);
	    verifySandbox(this.siProject, workspace);
	} catch (APIException e) {
	    if(e.getExceptionId().equalsIgnoreCase("si.NEW_SANDBOX_ALREADY_EXISTS")){
	        LOGGER.fine("Sandbox already exists!");
		try {
		    verifySandbox(this.siProject, workspace);
		} catch (APIException e1) {
		    e1.printStackTrace();
		}
	    }
	    else
		throw new InterruptedException(e.getMessage());
	}
	return true;
    }

    /**
     *
     * @param siProject
     * @param workspace
     */
    public void verifySandbox(IntegrityCMProject siProject, FilePath workspace)
		    throws APIException
    {
	ISession session = getAPISession();
	session.ping();
	verifySandbox(session, siProject, workspace);
    }

    private boolean verifySandbox(ISession session, IntegrityCMProject siProject,
		    FilePath workspace) throws APIException
    {
	Command cmd = new Command(Command.SI, "sandboxinfo");
	cmd.addOption(new Option("sandbox", workspace.getName()));
	Response response = session.runCommand(cmd);
	if(response != null && response.getExitCode() == 0){
	    WorkItem item = APIUtils.getWorkItem(response);
	    System.out.println(item);
	    Field projectName = item.getField(IAPIFields.PROJECT_NAME);
	    Field sandboxName = item.getField("sandboxname");
	    Field projectType = item.getField("projecttype");
	    Field server = item.getField("server");
	    Field configPath = item.getField("FullConfigSyntax");
	    Field lineTeminator = item.getField("LineTerminator");
	    Field description = item.getField("Description");
	}

	return false;
    }

    @Override
    public void checkRoles(RoleChecker roleChecker) throws SecurityException
    {
	roleChecker.check((RoleSensitive) this, Roles.SLAVE);
    }
}
