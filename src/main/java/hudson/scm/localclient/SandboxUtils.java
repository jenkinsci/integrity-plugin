package hudson.scm.localclient;

import com.mks.api.Command;
import com.mks.api.Option;
import com.mks.api.response.*;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.scm.IntegrityCMProject;
import hudson.scm.IntegrityConfigurable;
import hudson.scm.IntegritySCM;
import hudson.scm.api.APIUtils;
import hudson.scm.api.option.APIOption;
import hudson.scm.api.option.IAPIOption;
import hudson.scm.api.session.ISession;

import java.io.*;
import java.util.Objects;
import java.util.logging.Logger;

import static hudson.scm.api.session.APISession.createLocalIntegrationPoint;

/**
 * Created by asen on 07-06-2017.
 */
public class SandboxUtils implements Serializable
{
    private final IntegrityConfigurable integrityConfigurable;
    private static final Logger LOGGER = Logger.getLogger(SandboxUtils.class.getSimpleName());
    private final TaskListener listener;

    public SandboxUtils(IntegrityConfigurable integrityConfigurable,
		    TaskListener listener)
    {
        this.integrityConfigurable = integrityConfigurable;
        this.listener = listener;
    }

    private ISession getAPISession()
    {
	return createLocalIntegrationPoint(integrityConfigurable);
    }

    private String getQualifiedWorkspaceName(FilePath workspace)
    {
	StringBuilder sbr = new StringBuilder(workspace.getRemote());
	//sbr.append("\\project.pj");
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
     * @param siProject
     * @param workspace
     * @param lineTerminator
     */
    protected boolean createSandbox(IntegrityCMProject siProject,
		    FilePath workspace, String lineTerminator) throws APIException
    {
	ISession session = getAPISession();
	session.ping();
	if(verifySandbox(siProject, workspace)) {
	    return createSandbox(session, siProject, workspace, lineTerminator)==0;
	}
	else{
	    /* Sandbox already exists */
	    return true;
	}
    }

    private int createSandbox(ISession session,
		    IntegrityCMProject siProject, FilePath workspace,
		    String lineTerminator) throws APIException
    {
	listener.getLogger()
			.println("[LocalClient] Executing CreateSandbox :"+ getQualifiedWorkspaceName(workspace));
	Command cmd = new Command(Command.SI, "createsandbox");
	cmd.addOption(new Option(IAPIOption.PROJECT, siProject.getProjectName()));
	cmd.addOption(new Option(IAPIOption.LINE_TERMINATOR, lineTerminator));
	cmd.addOption(new APIOption("populate"));
	if(siProject.isVariant())
	    cmd.addOption(new Option(IAPIOption.DEVPATH, siProject.getVariantName()));
	else if(siProject.isBuild())
	    cmd.addOption(new Option(IAPIOption.PROJECT_REVISION, siProject.getProjectRevision()));
	cmd.addSelection(getQualifiedWorkspaceName(workspace));
	Response response = session.runCommand(cmd);
	if(response != null) {
	    listener.getLogger()
			    .println("[LocalClient] CreateSandbox response:" +
					    response.getExitCode());
	    return response.getExitCode();
	}
	else
	    return -1;
    }

    /**
     * Verifies the sandbox associated with the workspace.
     *
     * @param siProject
     * @param workspace
     */
    protected boolean verifySandbox(IntegrityCMProject siProject, FilePath workspace)
		    throws APIException
    {
	ISession session = getAPISession();
	session.ping();
	return verifySandbox(session, siProject, workspace);
    }

    private boolean verifySandbox(ISession session, IntegrityCMProject siProject,
		    FilePath workspace) throws APIException
    {
	listener.getLogger()
			.println("[LocalClient] Executing Sandbox Verification ");
        boolean sandboxExists = false;
	Command cmd = new Command(Command.SI, "sandboxes");
	listener.getLogger()
			.println("[LocalClient] Executing si sandboxes ");
	Response response = session.runCommand(cmd);
	listener.getLogger()
			.println("[LocalClient] si sandboxes response:"+ response.getExitCode());
	listener.getLogger()
			.println("[LocalClient] Searching Sandbox: ["+ getQualifiedWorkspaceName(workspace)+"],");
	listener.getLogger().println("Project Config :[" + siProject.getProjectName()+"],");
	listener.getLogger().println("Project Variant :[" + siProject.getVariantName()+"]");
	if(response != null && response.getExitCode() == 0){
	    WorkItemIterator it = response.getWorkItems();
	    while(it.hasNext()){
	        WorkItem wi = it.next();
	        String sBoxname = wi.getField("SandboxName").getValueAsString();
		String projectName = wi.getField("ProjectName").getValueAsString();
		listener.getLogger()
				.println("[LocalClient] Sandbox: "+ sBoxname + " for project: "+projectName+", ["+
						wi.getField("DevelopmentPath").getValueAsString() +"]");
		if(sBoxname.replace("\\project.pj", "").equalsIgnoreCase(getQualifiedWorkspaceName(workspace))
				&& projectName.equals(siProject.getProjectName())
				&& (!siProject.isVariant() ||
				Objects.equals(siProject.getVariantName(),
						wi.getField("DevelopmentPath")
								.getValueAsString()))
				&& (!siProject.isBuild() ||
				Objects.equals(siProject.getProjectRevision(),
						wi.getField("BuildRevision")
								.getItem()
								.getId()))) {
		    listener.getLogger()
				    .println("[LocalClient] Found Existing Sandbox for Project:["+ projectName+"],");
		    listener.getLogger().println("Sandbox : [" + sBoxname + "],");
		    listener.getLogger().println("Workspace : ["+ getQualifiedWorkspaceName(workspace) + "],");
		    if(siProject.isVariant())
		   	 listener.getLogger().println("Variant : [" +
				    wi.getField("DevelopmentPath").getValueAsString() +"]");
		    if(siProject.isBuild())
			listener.getLogger().println("Build : [" +
					wi.getField("BuildRevision").getValueAsString() +"]");
		    return false;
		}
		else if(sBoxname.replace("\\project.pj", "").equalsIgnoreCase(getQualifiedWorkspaceName(workspace))) {
		    listener.getLogger()
				    .println("[LocalClient] Sandbox marked for deletion: "+ sBoxname);
		    sandboxExists = true;
		}
	    }
	}
	//No existing match found! Drop the existing workspace sandbox if there are no matches
	if(sandboxExists)
	    return dropSandbox(siProject, workspace) == 0;

	listener.getLogger()
			.println("[LocalClient] Sandbox not found in :"+ getQualifiedWorkspaceName(workspace)+"],");
	listener.getLogger().println("Project Config : [" + siProject.getProjectName() + "],");
	listener.getLogger().println("Project Variant : [" + siProject.getVariantName() + "]");
	listener.getLogger().println("Build Revision : [" + siProject.getProjectRevision() + "]");
	return true;
    }

    /** @param siProject
     * @param workspace
     * @throws APIException
     */
    protected int dropSandbox(IntegrityCMProject siProject, FilePath workspace)
		    throws APIException
    {
	ISession session = getAPISession();
	session.ping();
	return dropSandbox(session, workspace);
    }

    private int dropSandbox(ISession session,
		    FilePath workspace) throws APIException
    {
	listener.getLogger()
			.println("[LocalClient] Executing DropSandbox :"+ getQualifiedWorkspaceName(workspace));
	Command cmd = new Command(Command.SI, "dropsandbox");
	cmd.addOption(new Option("delete", "all"));
	cmd.addSelection(workspace.getName());
	Response response = session.runCommand(cmd);
	if((response != null) && (response.getExitCode() == 0)){
	    WorkItem item = APIUtils.getWorkItem(response);
	    System.out.println(item);
	    Field projectName = item.getField("Working-\n" +
			    "Files-Deleted");
	    Field sandboxName = item.getField("Sandbox-\n" +
			    "Directory-\n" +
			    "Deleted");
	    listener.getLogger()
			    .println("[LocalClient] DropSandbox Response:"+ response.getExitCode());
	    return response.getExitCode();
	}
	else
	    return -1;

    }

    /**
     * Resync the sandbox - cleanCopy forces the resync unchangedFiles as well.
     *
     *
     * @param workspace
     * @param cleanCopy
     * @param changeLogFile
     * @return
     * @throws APIException
     */
    public boolean resyncSandbox(FilePath workspace,
		    boolean cleanCopy,
		    File changeLogFile)
		    throws APIException, FileNotFoundException,
		    UnsupportedEncodingException
    {
	ISession session = getAPISession();
	session.ping();
	return resyncSandbox(session, workspace, cleanCopy, changeLogFile)==0;
    }

    private int resyncSandbox(ISession session,
		    FilePath workspace,
		    boolean cleanCopy, File changeLogFile)
		    throws APIException, FileNotFoundException,
		    UnsupportedEncodingException
    {
	listener.getLogger()
			.println("[LocalClient] Executing ResyncSandbox :"+ getQualifiedWorkspaceName(workspace));
	Command cmd = new Command(Command.SI, "resync");
	cmd.addOption(new APIOption(IAPIOption.RECURSE));
	//cmd.addOption(new APIOption("removeOutOfScope"));
	//cmd.addOption(new APIOption("overwriteChanged"));
	//cmd.addOption(new APIOption("overwriteDeferred"));
	cmd.addOption(new APIOption("f"));
	if(cleanCopy) {
	    cmd.addOption(new APIOption("overwriteUnchanged"));
	}
	cmd.addOption(new Option(IAPIOption.SANDBOX, getQualifiedWorkspaceName(workspace).concat("\\project.pj")));
	Response response = session.runCommand(cmd);
	if(response != null) {
	    listener.getLogger()
			    .println("[LocalClient] ResyncSandbox Response:" +
					    response.getExitCode());
	    generateChangeLogFile(response, changeLogFile);
	    return response.getExitCode();
	}
	else
	    return -1;
    }

    private void generateChangeLogFile(Response response,
		    File changeLogFile)
		    throws APIException, FileNotFoundException,
		    UnsupportedEncodingException
    {
        if(response.getExitCode()!=0)
            return;
	try(PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(changeLogFile), "UTF-8"))) {
	    WorkItemIterator workItemIterator = response.getWorkItems();
	    while (workItemIterator.hasNext()) {
		WorkItem wit = workItemIterator.next();
		writer.print("msg:"+wit.getResult().getMessage().trim());
		writer.print(",");
		writer.print("file:"+wit.getContext("id"));
		writer.print("\n");
	    }
	}
	listener.getLogger()
			.println("[Local Client] Change log successfully generated: " +
					changeLogFile.getAbsolutePath());
    }

    /**
     * View the sadnbox for any changes during Polling
     * @param workspace
     */
    public boolean viewSandboxChanges(FilePath workspace) throws APIException
    {
	ISession session = getAPISession();
	session.ping();
	return viewSandboxChanges(session, workspace);
    }

    private boolean viewSandboxChanges(ISession session, FilePath workspace)
		    throws APIException
    {
	listener.getLogger()
			.println("[LocalClient] Executing ViewSandBox : "+ getQualifiedWorkspaceName(workspace));
	boolean isMember = false;
	Command cmd = new Command(Command.SI, "viewsandbox");
	cmd.addOption(new APIOption(IAPIOption.RECURSE));
	// The filterSubs option doesn't work via Java API!! 1094136 on MKS1
	cmd.addOption(new APIOption("filterSubs"));
	cmd.addOption(new Option("filter", "changed:all"));
	cmd.addOption(new Option(IAPIOption.SANDBOX, getQualifiedWorkspaceName(workspace).concat("\\project.pj")));

	Response response = session.runCommand(cmd);
	listener.getLogger()
			.println("[LocalClient] ViewSandBox Response : "+ response.getExitCode());

	// Have to deal with this ugliness as --filtersubs not working
	WorkItemIterator witerator = response.getWorkItems();
	while(witerator.hasNext()){
	    WorkItem wit = witerator.next();
	    String type = wit.getField("type").getValueAsString();
	    if(!type.equals("subsandbox") && !type.equals("variant-subsandbox") && !type.equals("build-subsandbox") && !type.equals("shared-subsandbox")
	    && !type.equals("shared-variant-subsandbox") && !type.equals("shared-build-subsandbox")){
	        isMember = true;
	        break;
	    }
	}
	return isMember;
    }
}
