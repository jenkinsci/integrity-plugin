package hudson.scm.localclient;

import com.mks.api.response.APIException;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.IntegrityCMProject;
import hudson.scm.IntegrityConfigurable;
import jenkins.security.Roles;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;

import java.io.File;
import java.io.IOException;

/**
 * Created by asen on 15-06-2017.
 */
public class IntegrityViewSandboxTask implements FilePath.FileCallable<Boolean>
{
    private final String alternateWorkspaceDir;
    private final TaskListener listener;
    private final IntegrityConfigurable integrityConfigurable;
    private final SandboxUtils sandboxUtil;

    public IntegrityViewSandboxTask(IntegrityConfigurable coSettings,
                    TaskListener listener,
                    String alternateWorkspace)
    {
        this.integrityConfigurable = coSettings;
        this.alternateWorkspaceDir = alternateWorkspace;
        this.listener = listener;
        this.sandboxUtil = new SandboxUtils(integrityConfigurable, listener);
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
            return false;
        }
    }

    @Override
    public void checkRoles(RoleChecker roleChecker) throws SecurityException
    {
        roleChecker.check((RoleSensitive) this, Roles.SLAVE);
    }
}
