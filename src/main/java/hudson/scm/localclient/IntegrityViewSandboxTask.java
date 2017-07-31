package hudson.scm.localclient;

import java.io.File;
import java.io.IOException;

import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;

import com.mks.api.response.APIException;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;

/**
 * Created by asen on 15-06-2017.
 */
public class IntegrityViewSandboxTask implements FilePath.FileCallable<Boolean>
{
	private static final long serialVersionUID = -3691106939728961721L;
	private final String alternateWorkspaceDir;
    private final TaskListener listener;
    private final SandboxUtils sandboxUtil;

    public IntegrityViewSandboxTask(SandboxUtils sboxUtil,
                    TaskListener listener,
                    String alternateWorkspace)
    {
        this.alternateWorkspaceDir = alternateWorkspace;
        this.listener = listener;
        this.sandboxUtil = sboxUtil;
    }

    @Override
    public Boolean invoke(File workspaceFile, VirtualChannel virtualChannel)
                    throws IOException, InterruptedException
    {
        FilePath workspace = sandboxUtil.getFilePath(workspaceFile, alternateWorkspaceDir);

        try {
            return sandboxUtil.viewSandboxChanges(workspace);
        } catch (APIException e) {
            listener.getLogger()
                            .println("[LocalClient] IntegrityViewSandboxTask invoke Exception :"+ e.getLocalizedMessage());
            e.printStackTrace(listener.getLogger());
            return false;
        }
    }

    @Override
    public void checkRoles(RoleChecker roleChecker) throws SecurityException
    {
        roleChecker.check((RoleSensitive) this, Roles.SLAVE);
    }
}
