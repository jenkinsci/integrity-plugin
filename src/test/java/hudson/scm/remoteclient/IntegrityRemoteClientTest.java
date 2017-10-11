package hudson.scm.remoteclient;

import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import hudson.scm.IntegritySCMTest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IntegrityRemoteClientTest extends IntegritySCMTest
{
    @Before
    public void setUp() throws Exception {
	super.setUp();
	remoteProject = setupIntegrityProjectWithRemoteClientWithCheckpointOff(successConfigPath);
    }

    @Test
    public void testBuildSuccessWithRemoteClient() throws Exception
    {
	FreeStyleBuild build = build(remoteProject, Result.SUCCESS);
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

    @Test(timeout=30000000)
    public void testRemoteWithConcurrentBuilds() throws Exception
    {
	// Test concurrent remote builds
	jenkinsRule.jenkins.setNumExecutors(4);
	remoteProject.setConcurrentBuild(true);
	QueueTaskFuture<FreeStyleBuild> build1 = remoteProject.scheduleBuild2(0, new Cause.UserIdCause());
	build1.waitForStart(); // trigger the build!
	QueueTaskFuture<FreeStyleBuild> build2 = remoteProject.scheduleBuild2(0, new Cause.UserIdCause());
	build2.waitForStart();
	QueueTaskFuture<FreeStyleBuild> build3 = remoteProject.scheduleBuild2(0, new Cause.UserIdCause());
	build3.waitForStart();
	QueueTaskFuture<FreeStyleBuild> build4 = remoteProject.scheduleBuild2(0, new Cause.UserIdCause());

	jenkinsRule.assertBuildStatusSuccess(build1.get());
	jenkinsRule.assertBuildStatusSuccess(build2.get());
	jenkinsRule.assertBuildStatusSuccess(build3.get());
	jenkinsRule.assertBuildStatusSuccess(build4.get());
    }

    @Test
    public void pipeLineTestWithRemoteClient() throws Exception
    {
	jenkinsRule.getInstance().getScm("IntegritySCM");
	WorkflowJob wfJob= jenkinsRule.getInstance().createProject(WorkflowJob.class, "demo");
	wfJob.setDefinition(new CpsFlowDefinition(
			"node {\n" +
					"    ws {\n" +
					"    checkout changelog: false, poll: false, " +
					"scm: [$class: 'IntegritySCM', checkpointBeforeBuild: false, " +
					"configPath: '"+successConfigPath+"', configurationName: 'test', " +
					"serverConfig: 'test', localClient: false] \n" +
					"    }\n" +
					"}"));
	WorkflowRun b = jenkinsRule.assertBuildStatusSuccess(wfJob.scheduleBuild2(0));
	assertTrue(b.getArtifactManager().root().child(fileName).isFile());
	b = jenkinsRule.assertBuildStatusSuccess(wfJob.getLastBuild());
	assertEquals(2, b.number);
	assertTrue(b.getArtifactManager().root().child(fileName).isFile());
    }

}
