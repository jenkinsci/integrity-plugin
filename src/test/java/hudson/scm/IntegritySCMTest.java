/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/
package hudson.scm;
import hudson.model.Cause;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 *  These integration tests necessitate a local client installation with sample data installed
 */
public class IntegritySCMTest
{
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    String successConfigPath="#/DummyProject";
    String failureConfigPath="#/test";

    @Test
    public void testBuildFailure() throws Exception
    {
        String error = "MKS125212: The project file /test/project.pj is not registered as a top level project with the current server";
	FreeStyleProject project = setupIntegrityProjectWithRemoteClient(failureConfigPath);
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
        String successprojectinfo = "Preparing to execute si projectinfo for #/DummyProject";
	String successviewproject = "Preparing to execute si viewproject for #/DummyProject";
	String successbuildchangelog = "Writing build change log";
	String successchangelog = "Change log successfully generated";
	String successinitdeletenonmembers = "Delete Non-Members: Checkout directory is";
	String successcompletedelete = "Delete Non-Members: Task is complete";

	FreeStyleProject project = setupIntegrityProjectWithRemoteClient(successConfigPath);
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
	FreeStyleProject project = setupIntegrityProjectWithLocalClient(successConfigPath);
	FreeStyleBuild build = build(project);
	jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
	String buildLog = build.getLog();
	assertNotNull(buildLog);
    }

    /**
     *
     * @param configPath
     * @param isLocalClient
     * @param cleanCopy
     * @return
     * @throws Exception
     */
    protected FreeStyleProject setupProject(String configPath,
		    boolean isLocalClient, boolean cleanCopy) throws Exception
    {
	IntegritySCM scm = new IntegritySCM("test", configPath, "test");
	FreeStyleProject project = jenkinsRule.createFreeStyleProject();
	scm.setLocalClient(isLocalClient);
	scm.setCleanCopy(cleanCopy);
	project.setScm(scm);
	project.save();
	return project;
    }

    /**
     *
     * @param configPath
     * @return
     * @throws Exception
     */
    protected FreeStyleProject setupIntegrityProjectWithRemoteClient(
		    String configPath) throws Exception
    {
	setupIntegrityConfigurable();
	FreeStyleProject project = setupProject(configPath, false, false);
	return project;
    }

    /**
     *
     * @param configPath
     * @return
     * @throws Exception
     */
    protected FreeStyleProject setupIntegrityProjectWithLocalClient(String configPath)
		    throws Exception
    {
	setupIntegrityConfigurable();
	FreeStyleProject project = setupProject(configPath, true, false);
	return project;
    }

    /**
     *
     * @param configPath
     * @return
     */
    protected FreeStyleProject setupIntegrityProjectWithLocalClientCleanCopy(
		    String configPath) throws Exception
    {
	setupIntegrityConfigurable();
	FreeStyleProject project = setupProject(configPath, true, true);
	return project;
    }


    /**
     *
     */
    protected void setupIntegrityConfigurable()
    {
	IntegrityConfigurable integrityConfigurable = new IntegrityConfigurable("test", "localhost",
			7001, "localhost",7001, false,
			"Administrator", "password");
	List<IntegrityConfigurable> configurations = new ArrayList<IntegrityConfigurable>();
	configurations.add(integrityConfigurable);
	IntegritySCM.DescriptorImpl.INTEGRITY_DESCRIPTOR.setConfigurations(configurations);
    }

    /**
     *
     * @param project
     * @return
     * @throws Exception
     */
    protected FreeStyleBuild build(final FreeStyleProject project) throws Exception {
	final FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause()).get();
	System.out.println(build.getLog(200));
	return build;
    }
}