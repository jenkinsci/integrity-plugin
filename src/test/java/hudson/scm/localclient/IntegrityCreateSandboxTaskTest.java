package hudson.scm.localclient;

import hudson.model.Node;
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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

/**
 * Created by asen on 06-06-2017.
 * Integration Tests for Local Client Testing
 */
public class IntegrityCreateSandboxTaskTest extends IntegritySCMTest
{

    @Before
    public void setUp() throws Exception {
        super.setUp();
        localClientProject = setupIntegrityProjectWithLocalClientWithCheckpointOff(successConfigPath);
        localClientProjectCleanCopy = setupIntegrityProjectWithLocalClientCleanCopyCheckpointOff(successConfigPath);
    }

    @Test
    public void testSandboxWithMultipleBuilds() throws Exception
    {
        // Test multiple builds within same sandbox
        build = build(localClientProject, Result.SUCCESS);
        build = build(localClientProject, Result.SUCCESS);
        build = build(localClientProject, Result.SUCCESS);
        build = build(localClientProject, Result.SUCCESS);
    }

    @Test
    public void testCleanSandboxWithMultipleBuilds() throws Exception
    {
        // Test multiple builds within same sandbox
        build = build(localClientProjectCleanCopy, Result.SUCCESS);
        build = build(localClientProjectCleanCopy, Result.SUCCESS);
        build = build(localClientProjectCleanCopy, Result.SUCCESS);
        build = build(localClientProjectCleanCopy, Result.SUCCESS);
    }

    @Test
    public void testSandboxWithConcurrentBuilds() throws Exception
    {
        // Test multiple builds within same sandbox concurrently
        localClientProject.setConcurrentBuild(true);
        build = build(localClientProject, Result.SUCCESS);
        build = build(localClientProject, Result.SUCCESS);
        build = build(localClientProject, Result.SUCCESS);
        build = build(localClientProject, Result.SUCCESS);
    }

    @Test
    public void testCleanSandboxWithConcurrentBuilds() throws Exception
    {
        // Test multiple builds within same sandbox concurrently
        localClientProject.setConcurrentBuild(true);
        build = build(localClientProjectCleanCopy, Result.SUCCESS);
        build = build(localClientProjectCleanCopy, Result.SUCCESS);
        build = build(localClientProjectCleanCopy, Result.SUCCESS);
        build = build(localClientProjectCleanCopy, Result.SUCCESS);
    }

    @Test
    public void testSandboxCreateSuccessWithCleanCopyResync() throws Exception
    {
        build = build(localClientProjectCleanCopy, Result.SUCCESS);
    }

    @Test
    public void testSandboxCreateSuccessResync() throws Exception
    {
        build = build(localClientProject, Result.SUCCESS);
    }

    @Test
    public void testSandboxCreateSuccessResyncOnSlave() throws Exception
    {
        localClientProject.setAssignedNode(slave1);
        localClientProject.save();
        build = build(localClientProject, Result.SUCCESS);
        assertThat("Needs to build on the slave1 to check serialization", build.getBuiltOn(), is((Node) slave1));
    }

    @Test
    public void testSandboxViewWhilePollingWithChanges()
                    throws Exception
    {
        build = build(localClientProject, Result.SUCCESS);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);

        addTestFileInSource();
        triggerSCMTrigger(localClientProject.getTrigger(SCMTrigger.class));

        PollingResult poll = localClientProject.poll(listener);
        assertTrue(poll.hasChanges());
    }

    @Test
    public void testSandboxViewWhilePollingWithChangesWithSlave()
                    throws Exception
    {
        localClientProject.setAssignedNode(slave1);
        localClientProject.save();

        build = build(localClientProject, Result.SUCCESS);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);

        addTestFileInSource();
        triggerSCMTrigger(localClientProject.getTrigger(SCMTrigger.class));

        PollingResult poll = localClientProject.poll(listener);
        assertTrue(poll.hasChanges());
    }

    @Test
    public void testSandboxViewWhilePollingWithNoChanges()
                    throws Exception
    {
        build = build(localClientProject, Result.SUCCESS);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);

        triggerSCMTrigger(localClientProject.getTrigger(SCMTrigger.class));

        PollingResult poll = localClientProject.poll(listener);
        assertFalse(poll.hasChanges());
    }

    @Test
    public void testSandboxCreateSuccessResyncWithNewFile() throws Exception
    {
        // Create the sandbox
        build = build(localClientProject, Result.SUCCESS);
        // Add a random file into Integrity Source project directly
        addTestFileInSource();
        //  Check polling detects the file
        assertTrue("scm polling should detect a change after build", localClientProject.poll(listener).hasChanges());
        // Run the build
        build = build(localClientProject, Result.SUCCESS);

        // Check the changelog for changeset count
        final Set<User> culprits = build.getCulprits();
        assertEquals("The build should have only one culprit", 1, culprits.size());

        // Assert the file has gotten resynced into workspace
        assertTrue("File Exists in workspace!",new File(
                        String.valueOf(build.getWorkspace().child(fileName))).isFile());
        // Polling once more. No more changes should be detected
        assertFalse("scm polling should not detect any more changes after build", localClientProject.poll(listener).hasChanges());
    }

    @Test
    public void testSandboxCreateSuccessResyncWithNewFileWithSlave() throws Exception
    {
        localClientProject.setAssignedNode(slave1);
        localClientProject.save();
        // Create the sandbox
        build = build(localClientProject, Result.SUCCESS);
        // Add a random file into Integrity Source project directly
        addTestFileInSource();
        //  Check polling detects the file
        assertTrue("scm polling should detect a change after build", localClientProject.poll(listener).hasChanges());
        // Run the build
        build = build(localClientProject, Result.SUCCESS);

        // Check the changelog for changeset count
        final Set<User> culprits = build.getCulprits();
        assertEquals("The build should have only one culprit", 1, culprits.size());

        // Assert the file has gotten resynced into workspace
        assertTrue("File Exists in workspace!",new File(
                        String.valueOf(build.getWorkspace().child(fileName))).isFile());
        // Polling once more. No more changes should be detected
        assertFalse("scm polling should not detect any more changes after build", localClientProject.poll(listener).hasChanges());
        assertThat("Needs to build on the slave1 to check serialization", build.getBuiltOn(), is((Node) slave1));
    }

    @Test
    public void testSandboxCreateSuccessResyncWithExistingFile()
                    throws Exception
    {
        String testData = "hello world";
        // Add a random file into Integrity Source project directly
        addTestFileInSource();

        assertTrue("scm polling should detect a change after build", localClientProject.poll(listener).hasChanges());
        build = build(localClientProject, Result.SUCCESS);

        assertTrue("File Exists in workspace!", new File(
                        String.valueOf(build.getWorkspace().child(fileName))).isFile());
        //Assert the file in workspace/sandbox contents are null
        try(BufferedReader br = new BufferedReader(new FileReader(
                        String.valueOf(build.getWorkspace().child(fileName))))) {
            assertEquals(br.readLine(), null);
        }

        // Check out the random file (not in workspace/sandbox)
        checkoutFileFromSource();
        // Write test data into the checked out file
        FileUtils.writeStringToFile(testFile, testData);
        // Checkin the file directly to the Integrity Source project
        checkinFileIntoSource();

        assertTrue("scm polling should detect a change after build", localClientProject.poll(listener).hasChanges());

        build = build(localClientProject, Result.SUCCESS);

        // Assert that after build, file with test data content is present in workspace
        myFile =  new File(
                        String.valueOf(build.getWorkspace().child(fileName)));
        try(Scanner s = new Scanner(myFile)) {
            String content = s.useDelimiter("\\n").next();
            assertEquals(content, testData);
        }
        assertFalse("scm polling should not detect any more changes after build", localClientProject.poll(listener).hasChanges());
    }

    @Test
    public void testSandboxCreateSuccessResyncWithExistingFileWithSlave()
                    throws Exception
    {
        localClientProject.setAssignedNode(slave1);
        localClientProject.save();
        String testData = "hello world";
        // Add a random file into Integrity Source project directly
        addTestFileInSource();

        assertTrue("scm polling should detect a change after build", localClientProject.poll(listener).hasChanges());
        // Build on Slave 1
        build = build(localClientProject, Result.SUCCESS);

        assertTrue("File Exists in workspace!", new File(
                        String.valueOf(build.getWorkspace().child(fileName))).isFile());
        assertThat("Needs to build on the slave1 to check serialization", build.getBuiltOn(), is((Node) slave1));
        //Assert the file in workspace/sandbox contents are null
        try(BufferedReader br = new BufferedReader(new FileReader(
                        String.valueOf(build.getWorkspace().child(fileName))))) {
            assertEquals(br.readLine(), null);
        }

        // Check out the random file (not in workspace/sandbox)
        checkoutFileFromSource();
        // Write test data into the checked out file
        FileUtils.writeStringToFile(testFile, testData);
        // Checkin the file directly to the Integrity Source project
        checkinFileIntoSource();

        localClientProject.setAssignedNode(slave2);
        localClientProject.save();
        assertTrue("scm polling should detect a change after build", localClientProject.poll(listener).hasChanges());
        // Build on Slave 2
        build = build(localClientProject, Result.SUCCESS);
        assertThat("Needs to build on the slave1 to check serialization", build.getBuiltOn(), is((Node) slave2));

        // Switch back to Slave 1
        localClientProject.setAssignedNode(slave1);
        localClientProject.save();

        // Check that there are no polling updates after switching nodes from slave 2 to Slave 1
        assertFalse("scm polling should not detect any more changes after build", localClientProject.poll(listener).hasChanges());

        // Assert that after build on Slave 2, file with test data content is present in workspace
        myFile =  new File(
                        String.valueOf(build.getWorkspace().child(fileName)));
        try(Scanner s = new Scanner(myFile)) {
            String content = s.useDelimiter("\\n").next();
            assertEquals(content, testData);
        }
        assertFalse("scm polling should not detect any more changes after build", localClientProject.poll(listener).hasChanges());

        // build again. this time back on Slave 1
        build = build(localClientProject, Result.SUCCESS);
        myFile =  new File(
                        String.valueOf(build.getWorkspace().child(fileName)));
        try(Scanner s = new Scanner(myFile)) {
            String content = s.useDelimiter("\\n").next();
            assertEquals(content, testData);
        }
        assertFalse("scm polling should not detect any more changes after build", localClientProject.poll(listener).hasChanges());
        assertThat("Needs to build on the slave1 to check serialization", build.getBuiltOn(), is((Node) slave1));
    }

    @Test
    public void testSandboxCreateSuccessResyncWithCleanCopyExistingFile()
                    throws Exception
    {
        String testData = "hello world";
        addTestFileInSource();

        assertTrue("scm polling should detect a change after build", localClientProjectCleanCopy.poll(listener).hasChanges());
        build = build(localClientProjectCleanCopy, Result.SUCCESS);
        assertFalse("scm polling should not detect any more changes after build", localClientProjectCleanCopy.poll(listener).hasChanges());

        try(BufferedReader br = new BufferedReader(new FileReader(
                        String.valueOf(build.getWorkspace().child(fileName))))) {
            assertEquals(br.readLine(), null);
        }

        checkoutFileFromSource();
        FileUtils.writeStringToFile(testFile, testData);
        checkinFileIntoSource();

        build = build(localClientProjectCleanCopy, Result.SUCCESS);
        assertFalse("scm polling should not detect any more changes after build", localClientProjectCleanCopy.poll(listener).hasChanges());

        myFile =  new File(
                        String.valueOf(build.getWorkspace().child(fileName)));
        try(Scanner s = new Scanner(myFile)) {
            String content = s.useDelimiter("\\n").next();
            assertEquals(content, testData);
        }
    }

    @Test
    public void testSandboxCreateSuccessResyncWithDeletedFile()
                    throws Exception
    {
        addTestFileInSource();
        assertTrue("scm polling should detect a change after build", localClientProject.poll(listener).hasChanges());
        build = build(localClientProject, Result.SUCCESS);
        // Assert file exists in workspace
        assertTrue(new File(
                        String.valueOf(build.getWorkspace().child(fileName))).isFile());
        dropTestFileFromSource();
        assertTrue("scm polling should detect a change after build", localClientProject.poll(listener).hasChanges());
        build = build(localClientProject, Result.SUCCESS);
        assertFalse("scm polling should not detect any more changes after build", localClientProject.poll(listener).hasChanges());
        // Assert that after build, file doesn't exist in in workspace
        assertFalse((new File(
                        String.valueOf(build.getWorkspace().child(fileName))).isFile()));
    }

    @Test
    public void testSandboxCreateSuccessResyncWithDeletedFileWithMultipleSlaves()
                    throws Exception
    {
        localClientProject.setAssignedNode(slave1);
        localClientProject.save();
        addTestFileInSource();
        assertTrue("scm polling should detect a change after build", localClientProject.poll(listener).hasChanges());
        build = build(localClientProject, Result.SUCCESS);
        // Assert file exists in workspace
        assertTrue(new File(
                        String.valueOf(build.getWorkspace().child(fileName))).isFile());
        assertThat("Needs to build on the slave1 to check serialization", build.getBuiltOn(), is((Node) slave1));
        // Drop the file from Integrity Project
        dropTestFileFromSource();
        localClientProject.setAssignedNode(slave2);
        localClientProject.save();
        assertTrue("scm polling should detect a change after build", localClientProject.poll(listener).hasChanges());
        build = build(localClientProject, Result.SUCCESS);
        assertFalse("scm polling should not detect any more changes after build", localClientProject.poll(listener).hasChanges());
        // Assert that after build, file doesn't exist in in workspace
        assertFalse((new File(
                        String.valueOf(build.getWorkspace().child(fileName))).isFile()));
        assertThat("Needs to build on the slave1 to check serialization", build.getBuiltOn(), is((Node) slave2));
    }
}