package hudson.scm.localclient;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.api.session.ISession;
import jenkins.security.Roles;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;

/**
 * Created by asen on 08-06-2017.
 */
public class IntegrityResyncSandboxTask implements FilePath.FileCallable<Boolean>
{
	private static final long serialVersionUID = 4886592227995487766L;
	private final String alternateWorkspaceDir;
    private final SandboxUtils sandboxUtil;
    private final TaskListener listener;
    private final boolean cleanCopy;
    private final File changeLogFile;
    private final String excludeList;
    private final String includeList;
    private final boolean restoreTimestamp;
    private final boolean deleteNonMembers;
    private final String sandboxScope;

    public IntegrityResyncSandboxTask(SandboxUtils sboxUtil,
		    boolean cleanCopy, boolean deleteNonMembers,
		    boolean restoreTimestamp, File changeLogFile,
		    String alternateWorkspace,
		    String includeList, String excludeList,
		    TaskListener listener, String sandboxScope)
    {
        this.alternateWorkspaceDir = alternateWorkspace;
        this.listener = listener;
        this.cleanCopy = cleanCopy;
        this.changeLogFile = changeLogFile;
        this.sandboxUtil = sboxUtil;
        this.includeList = includeList;
        this.excludeList = excludeList;
        this.deleteNonMembers = deleteNonMembers;
        this.restoreTimestamp = restoreTimestamp;
        this.sandboxScope = sandboxScope;
    }

    @Override
    public Boolean invoke(File workspaceFile, VirtualChannel virtualChannel)
		    throws IOException, InterruptedException
    {
        FilePath workspace = sandboxUtil.getFilePath(workspaceFile, alternateWorkspaceDir);
        try (ISession session = sandboxUtil.getLocalAPISession()){
            listener.getLogger()
                            .println("[LocalClient] Executing IntegrityResyncSandboxTask :"+ workspaceFile);
            return sandboxUtil.resyncSandbox(session, workspace, cleanCopy, deleteNonMembers, restoreTimestamp, changeLogFile, includeList, excludeList, sandboxScope);
        } catch (Exception e) {

            listener.getLogger()
                            .println("[LocalClient] IntegrityResyncSandboxTask invoke Exception :"+ e.getLocalizedMessage());
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
