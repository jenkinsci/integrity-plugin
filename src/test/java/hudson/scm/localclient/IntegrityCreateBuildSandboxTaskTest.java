package hudson.scm.localclient;

import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.User;
import hudson.scm.IntegritySCMTest;
import hudson.scm.PollingResult;
import hudson.triggers.SCMTrigger;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Scanner;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Created by asen on 06-06-2017.
 * Integration Tests for Local Client Testing
 */
public class IntegrityCreateBuildSandboxTaskTest extends IntegritySCMTest
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