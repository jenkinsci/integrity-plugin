/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/
package hudson.scm;
import com.mks.api.Command;
import com.mks.api.Option;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;
import hudson.model.*;
import hudson.scm.api.session.APISession;
import hudson.scm.api.session.ISession;
import hudson.util.StreamTaskListener;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 *  These integration tests necessitate a local client installation with sample data installed
 */
public class IntegritySCMTest
{
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    protected String successConfigPath="#/JenkinsBulkProject1";
    //protected String successConfigPath="#/DummyProject";
    protected String failureConfigPath="#/THISSHOULDFAIL";
    protected TaskListener listener;
    protected FreeStyleProject localClientProject;
    protected FreeStyleProject localClientProjectCleanCopy;
    protected ISession session;
    protected File myFile;
    protected File testFile;
    protected String fileName;
    protected Command cmd;
    protected Response response;
    protected FreeStyleBuild build;
    protected FreeStyleProject localClientVariantProject;
    protected FreeStyleProject localClientVariantProjectCleanCopy;
    protected String variantName;

    @Before
    public void setUp() throws Exception {
	listener = StreamTaskListener.fromStderr();
	IntegrityConfigurable integrityConfigurable = new IntegrityConfigurable("test", "localhost",
			7001, "localhost",7001, false,
			"Administrator", "password");
	session = APISession.createLocalIntegrationPoint(integrityConfigurable);
	localClientProject = setupIntegrityProjectWithLocalClientWithCheckpointOff(successConfigPath);
	localClientVariantProject = setupVariantIntegrityProjectWithLocalClientWithCheckpointOff(successConfigPath);
	localClientProjectCleanCopy = setupIntegrityProjectWithLocalClientCleanCopyCheckpointOff(successConfigPath);
	localClientVariantProjectCleanCopy = setupVariantIntegrityProjectWithLocalClientCleanCopyCheckpointOff(successConfigPath);
    }

    @Test
    public void testBuildFailure() throws Exception
    {
	FreeStyleProject project = setupIntegrityProjectWithRemoteClientWithCheckpointOff(failureConfigPath);
	FreeStyleBuild build = build(project, Result.FAILURE);
	//jenkinsRule.assertBuildStatus(Result.FAILURE, build);
	String buildLog = build.getLog();
	assertNotNull(buildLog);
	assertTrue(buildLog.contains(
			"MKS125212: The project file /THISSHOULDFAIL/project.pj is not registered as a top level project with the current server"));
	jenkinsRule.assertBuildStatus(Result.FAILURE, build);
    }

    @Test
    public void testBuildSuccessWithRemoteClient() throws Exception
    {
	FreeStyleProject project = setupIntegrityProjectWithRemoteClientWithCheckpointOff(successConfigPath);
	FreeStyleBuild build = build(project, Result.SUCCESS);
	String buildLog = build.getLog();
	assertNotNull(buildLog);
	assertTrue(buildLog.contains("Preparing to execute si projectinfo for "+ successConfigPath));
	assertTrue(buildLog.contains("Preparing to execute si viewproject for "+ successConfigPath));
	assertTrue(buildLog.contains("Writing build change log"));
	assertTrue(buildLog.contains("Change log successfully generated"));
	assertTrue(buildLog.contains(
			"Delete Non-Members: Checkout directory is"));
	assertTrue(buildLog.contains("Delete Non-Members: Task is complete"));
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
		    boolean isLocalClient, boolean cleanCopy, boolean checkpointBeforebuild) throws Exception
    {
	IntegritySCM scm = new IntegritySCM("test", configPath, "test");
	FreeStyleProject project = jenkinsRule.createFreeStyleProject();
	scm.setLocalClient(isLocalClient);
	scm.setCleanCopy(cleanCopy);
	scm.setCheckpointBeforeBuild(checkpointBeforebuild);
	project.setScm(scm);
	project.save();
	return project;
    }

    private FreeStyleProject setupVariantIntegrityProjectWithLocalClientWithCheckpointOff(
		    String configPath) throws Exception
    {
	setupIntegrityConfigurable();
	configPath = createDevPath();
	FreeStyleProject project = setupProject(configPath, true, false, false);
	return project;
    }


    private FreeStyleProject setupIntegrityProjectWithRemoteClientWithCheckpointOff(
		    String configPath) throws Exception
    {
	setupIntegrityConfigurable();
	FreeStyleProject project = setupProject(configPath, false, false, false);
	return project;
    }

    private FreeStyleProject setupIntegrityProjectWithLocalClientWithCheckpointOff(String configPath)
		    throws Exception
    {
	setupIntegrityConfigurable();
	FreeStyleProject project = setupProject(configPath, true, false, false);
	return project;
    }

    private FreeStyleProject setupIntegrityProjectWithLocalClientCleanCopyCheckpointOff(
		    String configPath) throws Exception
    {
	setupIntegrityConfigurable();
	FreeStyleProject project = setupProject(configPath, true, true, false);
	return project;
    }

    private FreeStyleProject setupVariantIntegrityProjectWithLocalClientCleanCopyCheckpointOff(
		    String configPath) throws Exception
    {
	setupIntegrityConfigurable();
	configPath = createDevPath();
	FreeStyleProject project = setupProject(configPath, true, true, false);
	return project;
    }

    private void setupIntegrityConfigurable()
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

    private String createDevPath() throws APIException
    {
        // Create a checkpoint
        String checkpointLabel = "TestCheckpoint"+Math.random();
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
	variantName = response.getResult().getField("DevelopmentPath").getValueAsString().trim();
	return successConfigPath +"#d="+response.getResult().getField("DevelopmentPath").getValueAsString().trim();
    }

    protected void addTestFileInSource(String variantName) throws IOException, APIException
    {
	// Add a random file into Integrity Source project directly
	assert session != null;
	cmd = new Command(Command.SI, "projectadd");
	cmd.addOption(new Option("project", successConfigPath));
	if(variantName != null)
	    cmd.addOption(new Option("devpath", variantName));
	fileName = Math.random()+".txt";
	testFile = testFolder.newFile(fileName);
	cmd.addOption(new Option("sourceFile", testFile.getAbsolutePath()));
	cmd.addOption(new Option("cpid", ":bypass"));
	cmd.addSelection(fileName);
	response = session.runCommand(cmd);
	assertEquals("Test File "+fileName+" added Successfully to "+successConfigPath, response.getExitCode(),0);
    }

    protected void dropTestFileFromSource(String variantName) throws APIException
    {
	// Drop the file from project
	assert session != null;
	cmd = new Command(Command.SI, "drop");
	cmd.addOption(new Option("project", successConfigPath));
	cmd.addOption(new Option("cpid", ":bypass"));
	if(variantName != null)
	    cmd.addOption(new Option("devpath", variantName));
	cmd.addSelection(fileName);
	response = session.runCommand(cmd);
	assertEquals("Test File "+fileName+" Dropped Successfully from "+successConfigPath, response.getExitCode(),0);
    }

    protected void checkinFileIntoSource(String variantName) throws APIException
    {
	cmd = new Command(Command.SI, "projectci");
	cmd.addOption(new Option("project", successConfigPath));
	cmd.addOption(new Option("sourceFile", testFile.getAbsolutePath()));
	cmd.addOption(new Option("description", "checkin"));
	cmd.addOption(new Option("cpid", ":bypass"));
	if(variantName != null)
	    cmd.addOption(new Option("devpath", variantName));
	cmd.addSelection(fileName);
	response = session.runCommand(cmd);
	assertEquals("Test File "+fileName+" Checked in Successfully to "+successConfigPath, response.getExitCode(),0);
    }

    protected void checkoutFileFromSource(String variantName) throws APIException
    {
	cmd = new Command(Command.SI, "projectco");
	cmd.addOption(new Option("project", successConfigPath));
	cmd.addOption(new Option("targetFile", testFile.getAbsolutePath()));
	cmd.addOption(new Option("cpid", ":bypass"));
	if(this.variantName != null)
	    cmd.addOption(new Option("devpath", this.variantName));
	cmd.addSelection(fileName);
	response = session.runCommand(cmd);
	assertEquals("Test File "+fileName+" checked out successfully from "+successConfigPath, response.getExitCode(),0);
    }
}