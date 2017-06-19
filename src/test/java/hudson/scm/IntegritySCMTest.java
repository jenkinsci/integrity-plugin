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

    @Before
    public void setUp() throws Exception {
	listener = StreamTaskListener.fromStderr();
	IntegrityConfigurable integrityConfigurable = new IntegrityConfigurable("test", "localhost",
			7001, "localhost",7001, false,
			"Administrator", "password");
	localClientProject = setupIntegrityProjectWithLocalClientWithCheckpointOff(successConfigPath);
	localClientProjectCleanCopy = setupIntegrityProjectWithLocalClientCleanCopyCheckpointOff(successConfigPath);
	session = APISession.createLocalIntegrationPoint(integrityConfigurable);
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

    protected FreeStyleProject setupIntegrityProjectWithRemoteClientWithCheckpointOff(
		    String configPath) throws Exception
    {
	setupIntegrityConfigurable();
	FreeStyleProject project = setupProject(configPath, false, false, false);
	return project;
    }

    protected FreeStyleProject setupIntegrityProjectWithLocalClientWithCheckpointOff(String configPath)
		    throws Exception
    {
	setupIntegrityConfigurable();
	FreeStyleProject project = setupProject(configPath, true, false, false);
	return project;
    }

    protected FreeStyleProject setupIntegrityProjectWithLocalClientCleanCopyCheckpointOff(
		    String configPath) throws Exception
    {
	setupIntegrityConfigurable();
	FreeStyleProject project = setupProject(configPath, true, true, false);
	return project;
    }

    protected void setupIntegrityConfigurable()
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

    protected void addTestFileInSource() throws IOException, APIException
    {
	// Add a random file into Integrity Source project directly
	assert session != null;
	cmd = new Command(Command.SI, "projectadd");
	cmd.addOption(new Option("project", successConfigPath));
	fileName = Math.random()+".txt";
	testFile = testFolder.newFile(fileName);
	cmd.addOption(new Option("sourceFile", testFile.getAbsolutePath()));
	cmd.addOption(new Option("cpid", ":bypass"));
	cmd.addSelection(fileName);
	response = session.runCommand(cmd);
	assertEquals(response.getExitCode(),0);
    }

    protected void dropTestFileFromSource() throws APIException
    {
	// Drop the file from project
	assert session != null;
	cmd = new Command(Command.SI, "drop");
	cmd.addOption(new Option("project", successConfigPath));
	cmd.addOption(new Option("cpid", ":bypass"));
	cmd.addSelection(fileName);
	response = session.runCommand(cmd);
	assertEquals(response.getExitCode(),0);
    }

    protected void checkinFileIntoSource() throws APIException
    {
	cmd = new Command(Command.SI, "projectci");
	cmd.addOption(new Option("project", successConfigPath));
	cmd.addOption(new Option("sourceFile", testFile.getAbsolutePath()));
	cmd.addOption(new Option("description", "checkin"));
	cmd.addOption(new Option("cpid", ":bypass"));
	cmd.addSelection(fileName);
	response = session.runCommand(cmd);
	assertEquals(response.getExitCode(),0);
    }

    protected void checkoutFileFromSource() throws APIException
    {
	cmd = new Command(Command.SI, "projectco");
	cmd.addOption(new Option("project", successConfigPath));
	cmd.addOption(new Option("targetFile", testFile.getAbsolutePath()));
	cmd.addOption(new Option("cpid", ":bypass"));
	cmd.addSelection(fileName);
	response = session.runCommand(cmd);
	assertEquals(response.getExitCode(),0);
    }
}