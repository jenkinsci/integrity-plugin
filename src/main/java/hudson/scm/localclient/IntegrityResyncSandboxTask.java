package hudson.scm.localclient;

import com.mks.api.response.APIException;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.IntegrityConfigurable;
import jenkins.security.Roles;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;

import java.io.File;
import java.io.IOException;

/**
 * Created by asen on 08-06-2017.
 */
public class IntegrityResyncSandboxTask implements FilePath.FileCallable<Boolean>
{
    private final String alternateWorkspaceDir;
    private final IntegrityConfigurable integrityConfigurable;
    private final SandboxUtils sandboxUtil;
    private final TaskListener listener;
    private final boolean cleanCopy;

    public IntegrityResyncSandboxTask(IntegrityConfigurable coSettings,
                    boolean cleanCopy, String alternateWorkspace,
                    TaskListener listener)
    {
        this.integrityConfigurable = coSettings;
        this.alternateWorkspaceDir = alternateWorkspace;
        this.listener = listener;
        this.cleanCopy = cleanCopy;
        this.sandboxUtil = new SandboxUtils(integrityConfigurable, listener);
    }

    @Override
    public Boolean invoke(File workspaceFile, VirtualChannel virtualChannel)
		    throws IOException, InterruptedException
    {
        FilePath workspace = sandboxUtil.getFilePath(workspaceFile, alternateWorkspaceDir);

        try {
            sandboxUtil.resyncSandbox(workspace, cleanCopy);
        } catch (APIException e) {
            listener.getLogger()
                            .println("[LocalClient] IntegrityResyncSandboxTask invoke Exception :"+ e.getLocalizedMessage());
            return false;
        }
        return true;
    }

    @Override
    public void checkRoles(RoleChecker roleChecker) throws SecurityException
    {
	roleChecker.check((RoleSensitive) this, Roles.SLAVE);
    }
}
