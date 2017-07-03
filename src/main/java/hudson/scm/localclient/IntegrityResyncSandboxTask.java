package hudson.scm.localclient;

import com.mks.api.response.APIException;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.IntegrityConfigurable;
import jenkins.security.Roles;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;

/**
 * Created by asen on 08-06-2017.
 */
public class IntegrityResyncSandboxTask implements FilePath.FileCallable<Boolean>
{
    private final String alternateWorkspaceDir;
    private final SandboxUtils sandboxUtil;
    private final TaskListener listener;
    private final boolean cleanCopy;
    private final File changeLogFile;
    private final String excludeList;
    private final String includeList;

    public IntegrityResyncSandboxTask(SandboxUtils sboxUtil,
                    boolean cleanCopy, File changeLogFile,
                    String alternateWorkspace,
                    String includeList, String excludeList,
                    TaskListener listener)
    {
        this.alternateWorkspaceDir = alternateWorkspace;
        this.listener = listener;
        this.cleanCopy = cleanCopy;
        this.changeLogFile = changeLogFile;
        this.sandboxUtil = sboxUtil;
        this.includeList = includeList;
        this.excludeList = excludeList;
    }

    @Override
    public Boolean invoke(File workspaceFile, VirtualChannel virtualChannel)
		    throws IOException, InterruptedException
    {
        FilePath workspace = sandboxUtil.getFilePath(workspaceFile, alternateWorkspaceDir);

        try {
            listener.getLogger()
                            .println("[LocalClient] Executing IntegrityResyncSandboxTask :"+ workspaceFile);
            return sandboxUtil.resyncSandbox(workspace, cleanCopy, changeLogFile, includeList, excludeList);
        } catch (APIException e) {
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
