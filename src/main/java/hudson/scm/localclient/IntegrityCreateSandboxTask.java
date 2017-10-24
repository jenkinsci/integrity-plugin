package hudson.scm.localclient;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.IntegrityCMProject;
import hudson.scm.api.session.ISession;
import jenkins.security.Roles;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;

/**
 * Created by asen on 06-06-2017.
 */
public class IntegrityCreateSandboxTask implements FilePath.FileCallable<Boolean>
{
	private static final long serialVersionUID = -2075969423423385945L;
	private final String alternateWorkspaceDir;
    private final IntegrityCMProject siProject;
    private final SandboxUtils sandboxUtil;
    private final TaskListener listener;
    private final String lineTerminator;

    public IntegrityCreateSandboxTask(
		    SandboxUtils sboxUtil,
		    IntegrityCMProject siProject,
		    String alternateWorkspace,
		    TaskListener listener, String lineTerminator)
    {
	this.siProject = siProject;
	this.alternateWorkspaceDir = alternateWorkspace;
	this.listener = listener;
	this.lineTerminator = lineTerminator;
	this.sandboxUtil = sboxUtil;
    }

    @Override
    public Boolean invoke(File workspaceFile, VirtualChannel virtualChannel)
		    throws IOException, InterruptedException
    {
	FilePath workspace = sandboxUtil.getFilePath(workspaceFile, alternateWorkspaceDir);
	try (ISession session = sandboxUtil.getLocalAPISession()){
	    return sandboxUtil.verifyCreateSandbox(session, siProject, workspace,
			    lineTerminator);
	} catch (Exception e) {
	    listener.getLogger()
			    .println("[LocalClient] IntegrityCreateSandboxTask Exception Caught : "+ e.getLocalizedMessage());
	    e.printStackTrace(listener.getLogger());
	    return false;
	}
    }

    @Override
    public void checkRoles(RoleChecker roleChecker) throws SecurityException
    {
	roleChecker.check(this, Roles.SLAVE);
    }
}
