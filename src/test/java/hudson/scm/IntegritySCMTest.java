/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/
package hudson.scm;
import hudson.model.Cause;
import org.junit.Rule;
import org.junit.Test;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 *  These integration tests necessitate a local client installation with sample data installed
 */
public class IntegritySCMTest
{
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    protected String successConfigPath="#/JenkinsBulkProject1";
    //protected String successConfigPath="#/DummyProject";
    protected String failureConfigPath="#/THISSHOULDFAIL";

    @Test
    public void testBuildFailure() throws Exception
    {
        String error = "MKS125212: The project file /THISSHOULDFAIL/project.pj is not registered as a top level project with the current server";
	FreeStyleProject project = setupIntegrityProjectWithRemoteClientWithCheckpointOff(failureConfigPath);
	FreeStyleBuild build = build(project);
	jenkinsRule.assertBuildStatus(Result.FAILURE, build);
	String buildLog = build.getLog();
	assertNotNull(buildLog);
	assertTrue(buildLog.contains(error));
	jenkinsRule.assertBuildStatus(Result.FAILURE, build);
    }

    @Test
    public void testBuildSuccessWithRemoteClient() throws Exception
    {
        String successprojectinfo = "Preparing to execute si projectinfo for "+ successConfigPath;
	String successviewproject = "Preparing to execute si viewproject for "+ successConfigPath;
	String successbuildchangelog = "Writing build change log";
	String successchangelog = "Change log successfully generated";
	String successinitdeletenonmembers = "Delete Non-Members: Checkout directory is";
	String successcompletedelete = "Delete Non-Members: Task is complete";

	FreeStyleProject project = setupIntegrityProjectWithRemoteClientWithCheckpointOff(successConfigPath);
	FreeStyleBuild build = build(project);
	jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
	String buildLog = build.getLog();
	assertNotNull(buildLog);
	assertTrue(buildLog.contains(successprojectinfo));
	assertTrue(buildLog.contains(successviewproject));
	assertTrue(buildLog.contains(successbuildchangelog));
	assertTrue(buildLog.contains(successchangelog));
	assertTrue(buildLog.contains(successinitdeletenonmembers));
	assertTrue(buildLog.contains(successcompletedelete));
    }

    @Test
    public void testBuildSuccessWithLocalClient() throws Exception
    {
	FreeStyleProject project = setupIntegrityProjectWithLocalClientWithCheckpointOff(successConfigPath);
	FreeStyleBuild build = build(project);
	jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
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

    protected FreeStyleBuild build(final FreeStyleProject project) throws Exception {
	final FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause()).get();
	System.out.println(build.getLog(200));
	return build;
    }
}