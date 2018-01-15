package hudson.scm.localclient;

import static hudson.scm.api.session.APISession.createLocalIntegrationPoint;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.scm.AbstractIntegritySCM;
import hudson.scm.IntegrityCMProject;
import hudson.scm.IntegrityConfigurable;
import hudson.scm.IntegritySCM;
import hudson.scm.api.option.APIOption;
import hudson.scm.api.option.IAPIOption;
import hudson.scm.api.session.ISession;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Objects;

import com.mks.api.Command;
import com.mks.api.Option;
import com.mks.api.response.APIException;
import com.mks.api.response.CommandException;
import com.mks.api.response.Response;
import com.mks.api.response.WorkItem;
import com.mks.api.response.WorkItemIterator;

/**
 * Created by asen on 07-06-2017.
 */
public class SandboxUtils implements Serializable
{
    private static final long serialVersionUID = -6355703584281019909L;
    public static final String PROJECT_PJ = OsUtils.isWindows()?"\\project.pj":"/project.pj"; // Solaris not considered here!
    public static final String DEVELOPMENT_PATH = "DevelopmentPath";
    public static final String BUILD_REVISION = "BuildRevision";
    private final IntegrityConfigurable integrityConfigurable;
    private final TaskListener listener;

    public SandboxUtils(IntegrityConfigurable integrityConfigurable,
		    TaskListener listener)
    {
        this.integrityConfigurable = integrityConfigurable;
        this.listener = listener;
    }

    public ISession getLocalAPISession()
    {
	return createLocalIntegrationPoint(integrityConfigurable);
    }

    private String getQualifiedWorkspaceName(FilePath workspace)
    {
	StringBuilder sbr = new StringBuilder(workspace.getRemote());
	return sbr.toString();
    }

    FilePath getFilePath(File workspaceFile, String alternateWorkspaceDir)
    {
	// Figure out where we should be checking out this project
	File checkOutDir = (null != alternateWorkspaceDir && alternateWorkspaceDir.length() > 0)
			? new File(alternateWorkspaceDir) : workspaceFile;
	// Convert the file object to a hudson FilePath (helps us with workspace.deleteContents())
	return new FilePath(checkOutDir.isAbsolute() ? checkOutDir
			: new File(workspaceFile.getAbsolutePath() + IntegritySCM.FS + checkOutDir.getPath()));
    }

    /**
     *
     * @throws APIException
     * @param session
     * @param siProject
     * @param workspace
     * @param lineTerminator
     */
    protected boolean verifyCreateSandbox(
		    ISession session,
		    IntegrityCMProject siProject,
		    FilePath workspace, String lineTerminator, String sandboxScope) throws APIException
    {
		if(verifySandbox(session, siProject, workspace)) {
		    return createSandbox(session, siProject, workspace, lineTerminator, sandboxScope);
		} else {
			return configureSandbox(session, siProject, workspace, lineTerminator, sandboxScope);
		}
    }
    
    private boolean configureSandbox(ISession session,
		    IntegrityCMProject siProject, FilePath workspace,
		    String lineTerminator, String sandboxScope) throws APIException
    {
		session.checkifAlive();
		listener.getLogger().println(
				"[LocalClient] Executing ConfigureSandbox :"
						+ getQualifiedWorkspaceName(workspace));
		Command cmd = new Command(Command.SI, "configuresandbox");
		cmd.addOption(new Option(IAPIOption.LINE_TERMINATOR, lineTerminator));
		if (sandboxScope != null && !sandboxScope.isEmpty()) {
			setSandboxScope(sandboxScope, cmd);
		}
		cmd.addSelection(getQualifiedWorkspaceName(workspace).concat(PROJECT_PJ));
		Response response = session.runCommand(cmd);
		if (response != null) {
			listener.getLogger().println(
					"[LocalClient] ConfigureSandbox response:"
							+ response.getExitCode());
			return response.getExitCode() == 0;
		}
		return false;
    }

    private boolean createSandbox(ISession session,
		    IntegrityCMProject siProject, FilePath workspace,
		    String lineTerminator, String sandboxScope) throws APIException
    {
	session.checkifAlive();
	listener.getLogger()
			.println("[LocalClient] Executing CreateSandbox :" +
					getQualifiedWorkspaceName(
							workspace));
	Command cmd = new Command(Command.SI, "createsandbox");
	cmd.addOption(new Option(IAPIOption.PROJECT,
			siProject.getProjectName()));
	cmd.addOption(new Option(IAPIOption.LINE_TERMINATOR,
			lineTerminator));
	if(sandboxScope != null && !sandboxScope.isEmpty()){
		setSandboxScope(sandboxScope, cmd);
	}
	// Don't populate sandbox here. Doing it on resync
	cmd.addOption(new APIOption("nopopulate"));
	if (siProject.isVariant())
	    cmd.addOption(new Option(IAPIOption.DEVPATH,
			    siProject.getVariantName()));
	else if (siProject.isBuild())
	    cmd.addOption(new Option(IAPIOption.PROJECT_REVISION,
			    siProject.getProjectRevision()));
	cmd.addSelection(getQualifiedWorkspaceName(workspace));
	Response response = session.runCommand(cmd);
	if (response != null) {
	    listener.getLogger()
			    .println("[LocalClient] CreateSandbox response:" +
					    response.getExitCode());
	    return response.getExitCode() == 0;
	}
	return false;
    }

	private void setSandboxScope(String sandboxScope, Command cmd) {
		String[] sandboxScopeAttributes = sandboxScope.split(" && ");
		if(sandboxScopeAttributes.length == 1){
			cmd.addOption(new Option(IAPIOption.SCOPE,sandboxScope));
		} else {
			for(int counter = 0; counter < sandboxScopeAttributes.length; counter++){
				cmd.addOption(new Option(IAPIOption.SCOPE,sandboxScopeAttributes[counter].trim()));
			}
		}
	}

    protected boolean verifySandbox(ISession session,
		    IntegrityCMProject siProject,
		    FilePath workspace) throws APIException
    {
	boolean sandboxToBeDropped = false;
	Response response = null;
	session.checkifAlive();
	listener.getLogger()
			.println("[LocalClient] Checking sandbox exists for :"+siProject.getConfigurationPath());
	Command cmd = new Command(Command.SI, "sandboxinfo");
	cmd.addOption(new Option(IAPIOption.SANDBOX,
			getQualifiedWorkspaceName(workspace).concat(PROJECT_PJ)));
	try {
	    response  = session.runCommand(cmd);
	} catch (CommandException e) {
	    listener.getLogger().println("[LocalClient] "+e.getMessage());
	    return true;
	}
	if(response !=null && response.getExitCode() == 0){
	    // Determine if the sandbox is the on the same backing project as the jenkins project
	    WorkItemIterator it = response.getWorkItems();
	    WorkItem wi = it.next();
	    String projectName = wi.getField("ProjectName")
			    .getValueAsString();
	    String devPath = wi.contains(DEVELOPMENT_PATH)?wi.getField(DEVELOPMENT_PATH).getValueAsString():"";
	    String buildRev = wi.contains(BUILD_REVISION)?wi.getField(BUILD_REVISION).getValueAsString():"";

	    listener.getLogger()
			    .println("[LocalClient] Existing workspace sandbox :"+wi.getField("sandboxName").getValueAsString());
	    listener.getLogger()
			    .println("[LocalClient] Checking sandbox. Sandbox Project: "+ projectName
					    + " & Jenkins project: "+siProject.getProjectName());
	    if(Objects.equals(siProject.getProjectName(), projectName)) {
		if (siProject.isVariant()) {
		    listener.getLogger()
				    .println("[LocalClient] Checking sandbox. Sandbox Variant: "+ devPath
						    + " & Jenkins project variant: "+siProject.getVariantName());
		    if(Objects.equals(siProject.getVariantName(), devPath))
		    {
		        // Same variant. Don't recreate sandbox
			return false;
		    } else {
			sandboxToBeDropped = true;
		    }
		}
		else if (siProject.isBuild()){
		    listener.getLogger()
				    .println("[LocalClient] Checking sandbox. Sandbox Revision: "+ buildRev
						    + " & Jenkins project revision: "+siProject.getProjectRevision());
		    if(Objects.equals(siProject
				    .getProjectRevision(), buildRev))
		    {
			// Same revision. Don't recreate sandbox
			return false;
		    } else {
			sandboxToBeDropped = true;
		    }
		} else {
		    return false;
		}
	    }
	    else {
		sandboxToBeDropped = true;
	    }

	    if(sandboxToBeDropped) {
		return dropSandbox(session, workspace, siProject);
	    }
	}
	return true;
    }

    /** @throws APIException
     * @param workspace
     */
    protected boolean dropSandbox(ISession session,
		    FilePath workspace, IntegrityCMProject siProject) throws APIException
    {

        listener.getLogger()
			.println("[LocalClient] Executing DropSandbox :"+ getQualifiedWorkspaceName(workspace));
	Command cmd = new Command(Command.SI, "dropsandbox");
	cmd.addOption(new Option("delete", "all"));
	cmd.addOption(new APIOption("forceConfirm","yes"));
	cmd.addSelection(getQualifiedWorkspaceName(workspace).replace("\\", "/").concat(PROJECT_PJ));
	session.checkifAlive();
	Response response = session.runCommand(cmd);
	if((response != null) && (response.getExitCode() == 0)){
	    listener.getLogger()
			    .println("[LocalClient] DropSandbox Response: "+ response.getExitCode());
	    listener.getLogger().println("[LocalClient] Sandbox Dropped: "+ workspace);
	    listener.getLogger().println("[LocalClient] For Project: "+ siProject.getProjectName());
	    return true;
	}
	return false;
    }

    /**
     * Resync the sandbox - cleanCopy forces the resync unchangedFiles as well.
     *
     *
     * @param session
     * @param workspace
     * @param cleanCopy
     * @param deleteNonMembers
     * @param restoreTimestamp
 *
     * @param changeLogFile
     * @param includeList
     * @param excludeList @return
     * @throws APIException
     */
    public boolean resyncSandbox(
		    ISession session, FilePath workspace,
		    boolean cleanCopy, boolean deleteNonMembers,
		    boolean restoreTimestamp, File changeLogFile,
		    String includeList, String excludeList, String sandboxScope)
		    throws APIException, FileNotFoundException,
		    UnsupportedEncodingException
    {
	session.checkifAlive();
	listener.getLogger()
			.printf("[LocalClient] Executing ResyncSandbox :%s" +
							AbstractIntegritySCM.NL,
					getQualifiedWorkspaceName(
							workspace));
	Command cmd = new Command(Command.SI, "resync");
	cmd.addOption(new APIOption(IAPIOption.RECURSE));
	cmd.addOption(new APIOption("f"));
	applyMemberFilters(cmd, includeList, excludeList);
	if (cleanCopy) {
	    cmd.addOption(new APIOption("overwriteUnchanged"));
	} else {
	    cmd.addOption(new Option("filter", "changed:all"));
	}
	// Either deleteNonMembers is checked OR scope is defined. In both cases, remove out of scope members.
	if (deleteNonMembers || (sandboxScope != null && !sandboxScope.isEmpty())) {
	    cmd.addOption(new APIOption("removeOutOfScope"));
	}
	if (restoreTimestamp) {
	    cmd.addOption(new APIOption("restoreTimestamp"));
	}
	cmd.addOption(new Option(IAPIOption.SANDBOX,
			getQualifiedWorkspaceName(workspace)
					.concat(PROJECT_PJ)));
	Response response = session.runCommand(cmd);
	if (response != null) {
	    listener.getLogger()
			    .println("[LocalClient] ResyncSandbox Response:" +
					    response.getExitCode());
	    if (response.getExitCode() == 0)
		return generateChangeLogFile(response, changeLogFile);
	}
	return false;
    }

    private boolean generateChangeLogFile(Response response,
		    File changeLogFile)
		    throws APIException, FileNotFoundException,
		    UnsupportedEncodingException
   {
       try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
		       new FileOutputStream(changeLogFile), "UTF-8"))) {
	   WorkItemIterator workItemIterator = response.getWorkItems();
	   while (workItemIterator.hasNext()) {
	       WorkItem wit = workItemIterator.next();
	       writer.print("msg:" + wit.getResult().getMessage().trim());
	       writer.print(",");
	       writer.print("file:" + wit.getId());
	       writer.print(",");
	       writer.print("context:" + wit.getContext());
	       writer.print(AbstractIntegritySCM.NL);
	   }
       }
       listener.getLogger()
		       .println("[LocalClient] Change log successfully generated: " +
				       changeLogFile.getAbsolutePath());
       return true;
   }

    /**
     * View the sandbox for any changes during Polling
     * @param session
     * @param workspace
     */
    public boolean viewSandboxChanges(ISession session,
		    FilePath workspace)
		    throws APIException
    {
	session.checkifAlive();
	listener.getLogger()
			.println("[LocalClient] Executing ViewSandBox : " +
					getQualifiedWorkspaceName(
							workspace));
	boolean isMember = false;
	Command cmd = new Command(Command.SI, "viewsandbox");
	cmd.addOption(new APIOption(IAPIOption.RECURSE));
	// The filterSubs option doesn't work via Java API!! 1094136 on MKS1
	cmd.addOption(new APIOption("filterSubs"));
	cmd.addOption(new Option("filter", "changed:all"));
	cmd.addOption(new Option(IAPIOption.SANDBOX,
			getQualifiedWorkspaceName(workspace)
					.concat(PROJECT_PJ)));
	Response response = session.runCommand(cmd);
	listener.getLogger()
			.println("[LocalClient] ViewSandBox Response : " +
					response.getExitCode());
	// Have to deal with this ugliness as --filtersubs not exposed
	WorkItemIterator witerator = response.getWorkItems();
	while (witerator.hasNext()) {
	    WorkItem wit = witerator.next();
	    String type = wit.getField("type").getValueAsString();
	    if (!type.equals("subsandbox") &&
			    !type.equals("variant-subsandbox") &&
			    !type.equals("build-subsandbox") &&
			    !type.equals("shared-subsandbox")
			    && !type.equals("shared-variant-subsandbox") &&
			    !type.equals("shared-build-subsandbox")) {
		isMember = true;
		break;
	    }
	}
	return isMember;
    }

	/**
     * TODO: Refactor: This method is a duplicate of IntegritySCM.applyMemberFilters()
     * @param command
     * @param includeList
     * @param excludeList
     */
    private void applyMemberFilters(Command command,
		    String includeList, String excludeList)
    {
	// Checking if our include list has any entries
	if (null != includeList && includeList.length() > 0)
	{
	    StringBuilder filterString = new StringBuilder();
	    String[] filterTokens = includeList.split(",|;");
	    // prepare a OR combination of include filters (all in one filter, separated by comma if
	    // needed)
	    for (int i = 0; i < filterTokens.length; i++)
	    {
		filterString.append(i > 0 ? "," : "");
		filterString.append("file:");
		filterString.append(filterTokens[i]);
	    }
	    command.addOption(new APIOption(IAPIOption.FILTER, filterString.toString()));
	}

	// Checking if our exclude list has any entries
	if (null != excludeList && excludeList.length() > 0)
	{
	    String[] filterTokens = excludeList.split(",|;");
	    // prepare a AND combination of exclude filters (one filter each filter)
	    for (int i = 0; i < filterTokens.length; i++)
	    {
		if (filterTokens[i] != null)
		{
		    command.addOption(new APIOption(IAPIOption.FILTER, "!file:" + filterTokens[i]));
		}
	    }
	}
    }

    public int terminateClient()
    {
	ISession session = getLocalAPISession();
	try {
	    session.ping();
	    listener.getLogger()
			    .println("[LocalClient] Terminating Client instances");
	    Command cmd = new Command(Command.IM, "exit");
	    cmd.addOption(new APIOption("noabort"));
	    Response response = session.runCommand(cmd);
	    return response.getExitCode();
	} catch (APIException e) {
	    listener.getLogger()
			    .println("[LocalClient] Exception occured while terminating client "+e.getLocalizedMessage());
	}
	return 0;
    }

    static final class OsUtils
    {
	private static String OS = null;
	public static String getOsName()
	{
	    if(OS == null) { OS = System.getProperty("os.name"); }
	    return OS;
	}
	public static boolean isWindows()
	{
	    return getOsName().contains("Windows");
	}

	public static boolean isUnix() {
	    return getOsName().contains("linux");
	}

	public static boolean isSolaris() {
	    return getOsName().contains("solaris");
	}
    }
}
