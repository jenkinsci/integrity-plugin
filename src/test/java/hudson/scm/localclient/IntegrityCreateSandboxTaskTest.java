package hudson.scm.localclient;

import hudson.model.Result;
import hudson.model.User;
import hudson.scm.IntegritySCMTest;
import org.apache.commons.io.FileUtils;
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
public class IntegrityCreateSandboxTaskTest extends IntegritySCMTest
{

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
        jenkinsRule.createOnlineSlave().setNodeName("remote");
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
    public void testSandboxCreateSuccessResyncWithNewFile() throws Exception
    {
        // Create the sandbox
        build = build(localClientProject, Result.SUCCESS);
        // Add a random file into Integrity Source project directly
        addTestFileInSource(null);
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
    public void testSandboxCreateSuccessResyncWithExistingFile()
                    throws Exception
    {
        String testData = "hello world";
        // Add a random file into Integrity Source project directly
        addTestFileInSource(null);

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
        checkoutFileFromSource(null);
        // Write test data into the checked out file
        FileUtils.writeStringToFile(testFile, testData);
        // Checkin the file directly to the Integrity Source project
        checkinFileIntoSource(null);

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
    public void testSandboxCreateSuccessResyncWithCleanCopyExistingFile()
                    throws Exception
    {
        String testData = "hello world";
        addTestFileInSource(null);

        assertTrue("scm polling should detect a change after build", localClientProjectCleanCopy.poll(listener).hasChanges());
        build = build(localClientProjectCleanCopy, Result.SUCCESS);
        assertFalse("scm polling should not detect any more changes after build", localClientProjectCleanCopy.poll(listener).hasChanges());

        try(BufferedReader br = new BufferedReader(new FileReader(
                        String.valueOf(build.getWorkspace().child(fileName))))) {
            assertEquals(br.readLine(), null);
        }

        checkoutFileFromSource(null);
        FileUtils.writeStringToFile(testFile, testData);
        checkinFileIntoSource(null);

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
        addTestFileInSource(null);
        assertTrue("scm polling should detect a change after build", localClientProject.poll(listener).hasChanges());
        build = build(localClientProject, Result.SUCCESS);
        // Assert file exists in workspace
        assertTrue(new File(
                        String.valueOf(build.getWorkspace().child(fileName))).isFile());
        dropTestFileFromSource(null);
        assertTrue("scm polling should detect a change after build", localClientProject.poll(listener).hasChanges());
        build = build(localClientProject, Result.SUCCESS);
        assertFalse("scm polling should not detect any more changes after build", localClientProject.poll(listener).hasChanges());
        // Assert that after build, file doesn't exist in in workspace
        assertFalse((new File(
                        String.valueOf(build.getWorkspace().child(fileName))).isFile()));
    }
}