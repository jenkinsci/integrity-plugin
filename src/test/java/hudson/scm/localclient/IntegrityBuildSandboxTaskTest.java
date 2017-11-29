package hudson.scm.localclient;

import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import hudson.scm.IntegritySCMTest;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by asen on 06-06-2017.
 * Integration Tests for Local Client Testing
 */
public class IntegrityBuildSandboxTaskTest extends IntegritySCMTest
{

    @Before
    public void setUp() throws Exception {
        super.setUp();
        localBuildClientProject = setupBuildIntegrityProjectWithLocalClientWithCheckpointOff(successConfigPath);
        localBuildClientProjectCleanCopy = setupBuildIntegrityProjectWithLocalClientCleanCopyCheckpointOff(successConfigPath);
    }

    @Test
    public void testSandboxWithMultipleBuilds() throws Exception
    {
        // Test multiple builds within same sandbox
        build = build(localBuildClientProject, Result.SUCCESS);
        build = build(localBuildClientProject, Result.SUCCESS);
        build = build(localBuildClientProject, Result.SUCCESS);
        build = build(localBuildClientProject, Result.SUCCESS);
    }

    @Test
    public void testCleanSandboxWithMultipleBuilds() throws Exception
    {
        // Test multiple builds within same sandbox
        build = build(localBuildClientProjectCleanCopy, Result.SUCCESS);
        build = build(localBuildClientProjectCleanCopy, Result.SUCCESS);
        build = build(localBuildClientProjectCleanCopy, Result.SUCCESS);
        build = build(localBuildClientProjectCleanCopy, Result.SUCCESS);
    }

    @Test
    public void testCleanSandboxWithConcurrentBuilds() throws Exception
    {
        // Test concurrent builds apread across sandboxes
        jenkinsRule.jenkins.setNumExecutors(4);
        localBuildClientProject.setConcurrentBuild(true);
        QueueTaskFuture<FreeStyleBuild> build1 = localBuildClientProject.scheduleBuild2(0, new Cause.UserIdCause());
        build1.waitForStart(); // trigger the build!
        QueueTaskFuture<FreeStyleBuild> build2 = localBuildClientProject.scheduleBuild2(0, new Cause.UserIdCause());
        build2.waitForStart();
        QueueTaskFuture<FreeStyleBuild> build3 = localBuildClientProject.scheduleBuild2(0, new Cause.UserIdCause());
        build3.waitForStart();
        QueueTaskFuture<FreeStyleBuild> build4 = localBuildClientProject.scheduleBuild2(0, new Cause.UserIdCause());
        build4.waitForStart();

        jenkinsRule.assertBuildStatusSuccess(build1.get());
        jenkinsRule.assertBuildStatusSuccess(build2.get());
        jenkinsRule.assertBuildStatusSuccess(build3.get());
        jenkinsRule.assertBuildStatusSuccess(build4.get());

    }

    @Test
    public void testSandboxCreateSuccessWithCleanCopyResync() throws Exception
    {
        build = build(localBuildClientProjectCleanCopy, Result.SUCCESS);
    }

    @Test
    public void testSandboxCreateSuccessResync() throws Exception
    {

        build = build(localBuildClientProject, Result.SUCCESS);
    }
}