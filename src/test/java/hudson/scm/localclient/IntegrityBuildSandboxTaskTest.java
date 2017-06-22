package hudson.scm.localclient;

import hudson.model.Result;
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
    public void testSandboxWithConcurrentBuilds() throws Exception
    {
        // Test multiple builds within same sandbox concurrently
        localBuildClientProject.setConcurrentBuild(true);
        build = build(localBuildClientProject, Result.SUCCESS);
        build = build(localBuildClientProject, Result.SUCCESS);
        build = build(localBuildClientProject, Result.SUCCESS);
        build = build(localBuildClientProject, Result.SUCCESS);
    }

    @Test
    public void testCleanSandboxWithConcurrentBuilds() throws Exception
    {
        // Test multiple builds within same sandbox concurrently
        localBuildClientProject.setConcurrentBuild(true);
        build = build(localBuildClientProjectCleanCopy, Result.SUCCESS);
        build = build(localBuildClientProjectCleanCopy, Result.SUCCESS);
        build = build(localBuildClientProjectCleanCopy, Result.SUCCESS);
        build = build(localBuildClientProjectCleanCopy, Result.SUCCESS);
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