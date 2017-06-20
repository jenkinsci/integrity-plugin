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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by asen on 20-06-2017.
 */
public class IntegrityCreateVariantSandboxTaskTest extends IntegritySCMTest
{
    @Test
    public void testVariantSandboxCreateSuccessResync() throws Exception
    {
	build = build(localClientVariantProject, Result.SUCCESS);
    }

    @Test
    public void testVariantSandboxCreateSuccessWithCleanCopyResync() throws Exception
    {
	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
    }

    @Test
    public void testVariantCleanSandboxWithConcurrentBuilds() throws Exception
    {
	// Test multiple builds within same sandbox concurrently
	localClientProject.setConcurrentBuild(true);
	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
    }

    @Test
    public void testVariantSandboxWithConcurrentBuilds() throws Exception
    {
	// Test multiple builds within same sandbox concurrently
	localClientProject.setConcurrentBuild(true);
	build = build(localClientVariantProject, Result.SUCCESS);
	build = build(localClientVariantProject, Result.SUCCESS);
	build = build(localClientVariantProject, Result.SUCCESS);
	build = build(localClientVariantProject, Result.SUCCESS);
    }

    @Test
    public void testVariantSandboxCreateSuccessResyncWithNewFile() throws Exception
    {
	// Create the sandbox
	build = build(localClientVariantProject, Result.SUCCESS);
	// Add a random file into Integrity Source project directly
	addTestFileInSource(variantName);
	//  Check polling detects the file
	assertTrue("scm polling should detect a change after build",
			localClientVariantProject.poll(listener)
					.hasChanges());
	// Run the build
	build = build(localClientVariantProject, Result.SUCCESS);
	// Check the changelog for changeset count
	final Set<User> culprits = build.getCulprits();
	assertEquals("The build should have only one culprit", 1,
			culprits.size());
	// Assert the file has gotten resynced into workspace
	assertTrue("File Exists in workspace!", new File(
			String.valueOf(build.getWorkspace()
					.child(fileName))).isFile());
	// Polling once more. No more changes should be detected
	assertFalse("scm polling should not detect any more changes after build",
			localClientVariantProject.poll(listener)
					.hasChanges());
    }

    @Test
    public void testVariantSandboxCreateSuccessResyncWithExistingFile()
		    throws Exception
    {
	String testData = "hello world";
	// Add a random file into Integrity Source project directly
	addTestFileInSource(variantName);

	assertTrue("scm polling should detect a change after build", localClientVariantProject.poll(listener).hasChanges());
	build = build(localClientVariantProject, Result.SUCCESS);

	assertTrue("File Exists in workspace!", new File(
			String.valueOf(build.getWorkspace().child(fileName))).isFile());
	//Assert the file in workspace/sandbox contents are null
	try(BufferedReader br = new BufferedReader(new FileReader(
			String.valueOf(build.getWorkspace().child(fileName))))) {
	    assertEquals(br.readLine(), null);
	}

	// Check out the random file (not in workspace/sandbox)
	checkoutFileFromSource(variantName);
	// Write test data into the checked out file
	FileUtils.writeStringToFile(testFile, testData);
	// Checkin the file directly to the Integrity Source project
	checkinFileIntoSource(variantName);

	assertTrue("scm polling should detect a change after build", localClientVariantProject.poll(listener).hasChanges());

	build = build(localClientVariantProject, Result.SUCCESS);

	// Assert that after build, file with test data content is present in workspace
	myFile =  new File(
			String.valueOf(build.getWorkspace().child(fileName)));
	try(Scanner s = new Scanner(myFile)) {
	    String content = s.useDelimiter("\\n").next();
	    assertEquals(content, testData);
	}
	assertFalse("scm polling should not detect any more changes after build", localClientVariantProject.poll(listener).hasChanges());
    }

    @Test
    public void testVariantSandboxCreateSuccessResyncWithCleanCopyExistingFile()
		    throws Exception
    {
	String testData = "hello world";
	addTestFileInSource(variantName);

	assertTrue("scm polling should detect a change after build", localClientVariantProjectCleanCopy.poll(listener).hasChanges());
	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
	assertFalse("scm polling should not detect any more changes after build", localClientVariantProjectCleanCopy.poll(listener).hasChanges());

	try(BufferedReader br = new BufferedReader(new FileReader(
			String.valueOf(build.getWorkspace().child(fileName))))) {
	    assertEquals(br.readLine(), null);
	}

	checkoutFileFromSource(variantName);
	FileUtils.writeStringToFile(testFile, testData);
	checkinFileIntoSource(variantName);

	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
	assertFalse("scm polling should not detect any more changes after build", localClientVariantProjectCleanCopy.poll(listener).hasChanges());

	myFile =  new File(
			String.valueOf(build.getWorkspace().child(fileName)));
	try(Scanner s = new Scanner(myFile)) {
	    String content = s.useDelimiter("\\n").next();
	    assertEquals(content, testData);
	}
    }

    @Test
    public void testVariantSandboxCreateSuccessResyncWithDeletedFile()
		    throws Exception
    {
	addTestFileInSource(variantName);
	assertTrue("scm polling should detect a change after build", localClientVariantProject.poll(listener).hasChanges());
	build = build(localClientVariantProject, Result.SUCCESS);
	// Assert file exists in workspace
	assertTrue(new File(
			String.valueOf(build.getWorkspace().child(fileName))).isFile());
	dropTestFileFromSource(variantName);
	assertTrue("scm polling should detect a change after build", localClientVariantProject.poll(listener).hasChanges());
	build = build(localClientVariantProject, Result.SUCCESS);
	assertFalse("scm polling should not detect any more changes after build", localClientVariantProject.poll(listener).hasChanges());
	// Assert that after build, file doesn't exist in in workspace
	assertFalse((new File(
			String.valueOf(build.getWorkspace().child(fileName))).isFile()));
    }
}
