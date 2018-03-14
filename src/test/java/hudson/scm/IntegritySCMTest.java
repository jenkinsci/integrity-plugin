/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/
package hudson.scm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import hudson.FilePath;
import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.Cause;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.remoting.VirtualChannel;
import hudson.scm.api.option.APIOption;
import hudson.scm.api.session.APISession;
import hudson.scm.api.session.ISession;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import hudson.triggers.SCMTrigger;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestEnvironment;

import com.mks.api.Command;
import com.mks.api.Option;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;

/**
 *  These integration tests necessitate a local client installation with sample data installed
 */
public class IntegritySCMTest
{
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule(){
        private void purgeSlaves(){
	    List<Computer> disconnectingComputers = new ArrayList<Computer>();
	    List<VirtualChannel> closingChannels = new ArrayList<VirtualChannel>();
	    for (Computer computer: jenkins.getComputers()) {
		if (!(computer instanceof SlaveComputer)) {
		    continue;
		}
		// disconnect slaves.
		// retrieve the channel before disconnecting.
		// even a computer gets offline, channel delays to close.
		if (!computer.isOffline()) {
		    VirtualChannel ch = computer.getChannel();
		    computer.disconnect(null);
		    disconnectingComputers.add(computer);
		    closingChannels.add(ch);
		}
	    }

	    try {
		// Wait for all computers disconnected and all channels closed.
		for (Computer computer: disconnectingComputers) {
		    computer.waitUntilOffline();
		}
		for (VirtualChannel ch: closingChannels) {
		    ch.join();
		}
	    } catch (InterruptedException e) {
		e.printStackTrace();
	    }
	}

	@Override
	public void after() throws Exception
	{
	    if(Functions.isWindows()) {
		purgeSlaves();
	    }
	    super.after();
	    if(TestEnvironment.get() != null)
	    {
		try
		{
		    TestEnvironment.get().dispose();
		}
		catch(Exception e)
		{
		    e.printStackTrace();
		}
	    }
	}
    };
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    protected static String successConfigPath="#/JenkinsBulkProject1";
    //protected String successConfigPath="#/DummyProject";
    protected static String failureConfigPath="#/THISSHOULDFAIL";
    protected static TaskListener listener;
    protected FreeStyleProject localClientProject;
    protected FreeStyleProject localClientProjectCleanCopy;
    protected FreeStyleProject remoteProject;
    protected ISession session;
    protected File myFile;
    protected File testFile;
    protected String fileName;
    protected Command cmd;
    protected Response response;
    protected FreeStyleBuild build;
    protected FreeStyleProject localClientVariantProject;
    protected FreeStyleProject localClientVariantProjectCleanCopy;
    protected String variantName = null;
    protected FreeStyleProject localBuildClientProjectCleanCopy;
    protected FreeStyleProject localBuildClientProject;
    protected static ExecutorService singleThreadExecutor;
    protected DumbSlave slave0;
    protected DumbSlave slave1;
    protected static final String ANY = "any";
    protected static final String SUBPROJECT_SUB1_SUB1_0_SUB1_0_0 = "subproject:#sub1#sub1-0#sub1-0-0";
	protected static final String PATH_SUB1_0_0 = "path:sub1-0-0";
	protected static final String ANYREVLABELLIKE_LABEL2 = "anyrevlabellike:Label2";
	protected static final String MEMBERREVLABELLIKE_MY_LABEL = "memberrevlabellike:MyLabel";
	protected static final String NAME_MBR_1_0_0_0_TXT = "name:mbr-1-0-0-0.txt";
	protected static final String TYPE_TEXT = "type:text";
	protected static final String EMPTY_STRING = "";
	protected static final String SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT = "sub1\\sub1-0\\sub1-0-0\\mbr-1-0-0-0.txt";
	protected static final String SUB0_MBR_0_0_TXT = "sub0\\mbr-0-0.txt";
	protected static final String JAVA_FILE_JAVA = "JavaFile.java";
	protected static final String SUB0_MBR_0_1_TXT = "sub0\\mbr-0-1.txt";
	protected static final String SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_1_TXT = "sub1\\sub1-0\\sub1-0-0\\mbr-1-0-0-1.txt";
	protected static final String NAME_MBR_1_0_0_1_TXT = "name:mbr-1-0-0-1.txt";
	protected static final String MULTIPLE_PROJECTS = "subproject:#sub1#sub1-0#sub1-0-0,subproject:#sub1-2-2#sub1-2-2-0";
	protected static final String SUB1_SUB1_0_SUB1_0_1_MBR_1_0_1_0_TXT = "sub1\\sub1-0\\sub1-0-1\\mbr-1-0-1-0.txt";
	protected static final String WAILDCARD_SCOPE_ATTR = "path:*sub1-0-1";
	protected static final String SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT2 = "sub1\\sub1-0\\sub1-0-0\\mbr-1-0-0-0.txt";
	protected static final String SCOPE_WITH_AND_OPERATOR = "memberrevlabellike:QQQQ && name:mbr-1-0-0-0.txt";
	protected static final String NAME_MBR_1_2_4_0_TXT = "name:mbr-1-2-4-0.txt";
	protected static final String SUB1_SUB1_2_SUB1_2_4_MBR_1_2_4_0_TXT = "sub1\\sub1-2\\sub1-2-4\\mbr-1-2-4-0.txt";
	protected static final String MEMBERREVLABELLIKE_QQQQ_NAME_MBR_1_2_2_1_0_TXT = "memberrevlabellike:QQQQ && name:mbr-1-2-2-1-0.txt";
	protected static final String SUB1_2_2_SUB1_2_2_1_MBR_1_2_2_1_0_TXT = "sub1-2-2\\sub1-2-2-1\\mbr-1-2-2-1-0.txt";
	protected static final String PROJECT_PJ = OsUtils.isWindows()?"\\project.pj":"/project.pj"; // Solaris not considered here!

    @BeforeClass
    public static void setupClass() throws Exception
    {
	singleThreadExecutor = Executors.newSingleThreadExecutor();
	listener = StreamTaskListener.fromStderr();
    }

    @Before
    public void setUp() throws Exception {
	IntegrityConfigurable integrityConfigurable = new IntegrityConfigurable("test", "localhost",
			7001, "localhost",7001, false,
			"Administrator", "password");
	session = APISession.createLocalIntegrationPoint(integrityConfigurable);
	slave0 = jenkinsRule.createOnlineSlave(Label.get("slave0"));
	slave1 = jenkinsRule.createOnlineSlave(Label.get("slave1"));
    }

    @Test
    public void testBuildFailure() throws Exception
    {
	FreeStyleProject project = setupIntegrityProjectWithRemoteClientWithCheckpointOff(failureConfigPath);
	FreeStyleBuild build = build(project, Result.FAILURE);
	String buildLog = build.getLog();
	assertNotNull(buildLog);
	assertTrue(buildLog.contains(
			"MKS125212: The project file /THISSHOULDFAIL/project.pj is not registered as a top level project with the current server"));
	jenkinsRule.assertBuildStatus(Result.FAILURE, build);
    }

    @Test
    public void testBuildSuccessWithLocalClient() throws Exception
    {
	FreeStyleProject project = setupIntegrityProjectWithLocalClientWithCheckpointOff(successConfigPath);
	FreeStyleBuild build = build(project, Result.SUCCESS);
	String buildLog = build.getLog();
	assertNotNull(buildLog);
    }

    protected FreeStyleProject setupProject(String configPath,
		    boolean isLocalClient, boolean cleanCopy,
		    boolean checkpointBeforebuild) throws Exception
    {
	setupIntegrityConfigurable();
        IntegritySCM scm = new IntegritySCM("test", configPath, "test");
	FreeStyleProject project = jenkinsRule.createFreeStyleProject();
	scm.setLocalClient(isLocalClient);
	scm.setCleanCopy(cleanCopy);
	scm.setCheckpointBeforeBuild(checkpointBeforebuild);
	//scm.setCheckoutThreadTimeout(15);
	project.setScm(scm);
	project.save();
	return project;
    }

    protected FreeStyleProject setupVariantIntegrityProjectWithLocalClientWithCheckpointOff(
		    String configPath) throws Exception
    {
	configPath = getDevPath();
	FreeStyleProject project = setupProject(configPath, true, false, false);
	return project;
    }


    protected FreeStyleProject setupIntegrityProjectWithRemoteClientWithCheckpointOff(
		    String configPath) throws Exception
    {
	FreeStyleProject project = setupProject(configPath, false, false, false);
	return project;
    }

    protected FreeStyleProject setupIntegrityProjectWithLocalClientWithCheckpointOff(String configPath)
		    throws Exception
    {
	FreeStyleProject project = setupProject(configPath, true, false, false);
	return project;
    }

    protected FreeStyleProject setupIntegrityProjectWithLocalClientCleanCopyCheckpointOff(
		    String configPath) throws Exception
    {
	FreeStyleProject project = setupProject(configPath, true, true, false);
	return project;
    }

    protected FreeStyleProject setupVariantIntegrityProjectWithLocalClientCleanCopyCheckpointOff(
		    String configPath) throws Exception
    {
	configPath = getDevPath();
	FreeStyleProject project = setupProject(configPath, true, true, false);
	return project;
    }

    protected FreeStyleProject setupBuildIntegrityProjectWithLocalClientWithCheckpointOff(
		    String configPath) throws Exception
    {
	configPath = createCheckpointPath();
	FreeStyleProject project = setupProject(configPath, true, true, false);
	return project;
    }

    protected FreeStyleProject setupBuildIntegrityProjectWithLocalClientCleanCopyCheckpointOff(
		    String configPath) throws Exception
    {
	configPath = createCheckpointPath();
	FreeStyleProject project = setupProject(configPath, true, true, false);
	return project;
    }

    protected static void setupIntegrityConfigurable()
    {
	IntegrityConfigurable integrityConfigurable = new IntegrityConfigurable("test", "localhost",
			7001, "localhost",7001, false,
			"Administrator", "password");
	List<IntegrityConfigurable> configurations = new ArrayList<IntegrityConfigurable>();
	configurations.add(integrityConfigurable);
	IntegritySCM.DescriptorImpl.INTEGRITY_DESCRIPTOR.setConfigurations(configurations);
    }

    protected FreeStyleBuild build(final FreeStyleProject project,
		    Result result) throws Exception {
	final FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause()).get();
	System.out.println(build.getLog(200));
	jenkinsRule.assertBuildStatus(result, build);
	return build;
    }

    protected void createDevPath() throws APIException
    {
	// Create a checkpoint
	/*String checkpointLabel = "TestCheckpoint"+Math.random();
	assert session != null;
	cmd = new Command(Command.SI, "checkpoint");
	cmd.addOption(new Option("project", successConfigPath));
	cmd.addOption(new Option("checkpointUnchangedSubprojects"));
	cmd.addOption(new Option("label", checkpointLabel));
	response = session.runCommand(cmd);
	assertEquals("Checkpoint Created Successfully Label: "+checkpointLabel, response.getExitCode(),0);

	// Create a devpath on the above checkpoint
	cmd = new Command(Command.SI, "createdevpath");
	cmd.addOption(new Option("project", successConfigPath));
	cmd.addOption(new Option("devpath", "DP_"+Math.random()));
	cmd.addOption(new Option("projectRevision", checkpointLabel));
	response = session.runCommand(cmd);
	assertEquals("Devpath Created Successfully", response.getExitCode(),0);
	variantName = response.getResult().getField("DevelopmentPath").getValueAsString().trim();*/
    }

    protected String getDevPath() throws APIException
    {
	//return successConfigPath +"#d="+variantName;
	variantName = "DP_0.3813840334796077";
	return successConfigPath +"#d="+variantName;
    }

    private String createCheckpointPath() throws APIException
    {
	String checkpointLabel = "TestCheckpoint"+Math.random();
	assert session != null;
	cmd = new Command(Command.SI, "checkpoint");
	cmd.addOption(new Option("project", successConfigPath));
	cmd.addOption(new Option("checkpointUnchangedSubprojects"));
	cmd.addOption(new Option("label", checkpointLabel));
	response = session.runCommand(cmd);
	assertEquals("Checkpoint Created Successfully Label: "+checkpointLabel, response.getExitCode(),0);
	return successConfigPath+"#b="+checkpointLabel;
    }

    protected void addTestFileInSource() throws IOException, APIException
    {
	// Add a random file into Integrity Source project directly
	assert session != null;
	cmd = new Command(Command.SI, "projectadd");
	cmd.addOption(new Option("project", successConfigPath+(variantName!=null?"#d="+variantName:"")));
	fileName = Math.random()+".txt";
	testFile = testFolder.newFile(fileName);
	cmd.addOption(new Option("sourceFile", testFile.getAbsolutePath()));
	cmd.addOption(new Option("cpid", ":bypass"));
	cmd.addSelection(fileName);
	response = session.runCommand(cmd);
	assertEquals("Test File "+fileName+" added Successfully to "+successConfigPath, response.getExitCode(),0);
    }

    protected void dropTestFileFromSource() throws APIException
    {
	// Drop the file from project
	assert session != null;
	cmd = new Command(Command.SI, "drop");
	cmd.addOption(new Option("project", successConfigPath+(variantName!=null?"#d="+variantName:"")));
	cmd.addOption(new Option("cpid", ":bypass"));
	cmd.addSelection(fileName);
	response = session.runCommand(cmd);
	assertEquals("Test File "+fileName+" Dropped Successfully from "+successConfigPath, response.getExitCode(),0);
    }

    protected void checkinFileIntoSource() throws APIException
    {
	cmd = new Command(Command.SI, "projectci");
	cmd.addOption(new Option("project", successConfigPath+(variantName!=null?"#d="+variantName:"")));
	cmd.addOption(new Option("sourceFile", testFile.getAbsolutePath()));
	cmd.addOption(new Option("description", "checkin"));
	cmd.addOption(new Option("cpid", ":bypass"));
	cmd.addSelection(fileName);
	response = session.runCommand(cmd);
	assertEquals("Test File "+fileName+" Checked in Successfully to "+successConfigPath, response.getExitCode(),0);
    }

    protected void checkoutFileFromSource() throws APIException
    {
	cmd = new Command(Command.SI, "projectco");
	cmd.addOption(new Option("project", successConfigPath+(variantName!=null?"#d="+variantName:"")));
	cmd.addOption(new Option("targetFile", testFile.getAbsolutePath()));
	cmd.addOption(new Option("cpid", ":bypass"));
	cmd.addSelection(fileName);
	response = session.runCommand(cmd);
	assertEquals("Test File "+fileName+" checked out successfully from "+successConfigPath, response.getExitCode(),0);
    }

    protected Future<Void> triggerSCMTrigger(final SCMTrigger trigger)
    {
	if(trigger == null) return null;
	Callable<Void> callable = new Callable<Void>() {
	    public Void call() throws Exception
	    {
		trigger.run();
		return null;
	    }
	};
	return singleThreadExecutor.submit(callable);
    }
    
    protected void addLabel(String label, String projectConfgPath) throws APIException
    {
	assert session != null;
	cmd = new Command(Command.SI, "addlabel");
	cmd.addOption(new Option("label", label));
	cmd.addOption(new Option("project", projectConfgPath));
	response = session.runCommand(cmd);
	assertEquals("Successfully added Label: "+label, response.getExitCode(),0);
    }
    
    protected void dropSandbox(FilePath sandboxLocation) throws APIException
    {
	assert session != null;
	cmd = new Command(Command.SI, "dropsandbox");
	cmd.addOption(new Option("confirm"));
	cmd.addOption(new APIOption("forceConfirm","yes"));
	cmd.addOption(new Option("delete", "all"));
	cmd.addSelection(getQualifiedWorkspaceName(sandboxLocation).replace("\\", "/").concat(PROJECT_PJ));
	response = session.runCommand(cmd);
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
    
    private String getQualifiedWorkspaceName(FilePath workspace)
    {
	StringBuilder sbr = new StringBuilder(workspace.getRemote());
	return sbr.toString();
    }

    @After
	public void cleanUp() throws APIException{
		if(build != null && build.getResult().equals(Result.SUCCESS))
			dropSandbox(build.getWorkspace());
	}
}