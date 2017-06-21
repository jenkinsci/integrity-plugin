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
 * Created by asen on 20-06-2017.
 */
public class IntegrityCreateVariantSandboxTaskTest extends IntegritySCMTest
{

    @Before
    public void setUp() throws Exception {
	super.setUp();
	localClientVariantProject = setupVariantIntegrityProjectWithLocalClientWithCheckpointOff(successConfigPath);
	localClientVariantProjectCleanCopy = setupVariantIntegrityProjectWithLocalClientCleanCopyCheckpointOff(successConfigPath);
    }

    @Test
    public void testVariantSandboxCreateSuccessResync() throws Exception
    {
	build = build(localClientVariantProject, Result.SUCCESS);
    }

    @Test
    public void testVariantSandboxCreateSuccessResyncWithSlave() throws Exception
    {
	localClientVariantProject.setAssignedNode(slave1);
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertThat("Needs to build on the slave1 to check serialization", build.getBuiltOn(), is((Node) slave1));
    }

    @Test
    public void testVariantSandboxCreateSuccessWithCleanCopyResync() throws Exception
    {
	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
    }

    @Test(timeout=300000)
    public void testVariantCleanSandboxWithConcurrentBuilds() throws Exception
    {
	// Test multiple builds within same sandbox concurrently
	localClientVariantProjectCleanCopy.setConcurrentBuild(true);
	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
    }

    @Test(timeout=300000)
    public void testVariantSandboxWithConcurrentBuilds() throws Exception
    {
	// Test multiple builds within same sandbox concurrently
	localClientVariantProject.setConcurrentBuild(true);
	build = build(localClientVariantProject, Result.SUCCESS);
	build = build(localClientVariantProject, Result.SUCCESS);
	build = build(localClientVariantProject, Result.SUCCESS);
	build = build(localClientVariantProject, Result.SUCCESS);
    }

    @Test
    public void testVariantSandboxViewWhilePollingWithChanges()
		    throws Exception
    {
	build = build(localClientVariantProject, Result.SUCCESS);
	jenkinsRule.assertBuildStatus(Result.SUCCESS, build);

	addTestFileInSource();
	triggerSCMTrigger(localClientVariantProject.getTrigger(SCMTrigger.class));

	PollingResult poll = localClientVariantProject.poll(listener);
	assertTrue(poll.hasChanges());
    }

    @Test
    public void testVariantSandboxViewWhilePollingWithNoChanges()
		    throws Exception
    {
	build = build(localClientVariantProject, Result.SUCCESS);
	jenkinsRule.assertBuildStatus(Result.SUCCESS, build);

	triggerSCMTrigger(localClientVariantProject.getTrigger(SCMTrigger.class));

	PollingResult poll = localClientVariantProject.poll(listener);
	assertFalse(poll.hasChanges());
    }

    @Test(timeout=600000)
    public void testVariantSandboxCreateSuccessResyncWithNewFile() throws Exception
    {
	// Create the sandbox
	build = build(localClientVariantProject, Result.SUCCESS);
	// Add a random file into Integrity Source project directly
	addTestFileInSource();
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

    @Test(timeout=600000)
    public void testVariantSandboxCreateSuccessResyncWithExistingFile()
		    throws Exception
    {
	String testData = "hello world";
	// Add a random file into Integrity Source project directly
	addTestFileInSource();

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
	checkoutFileFromSource();
	// Write test data into the checked out file
	FileUtils.writeStringToFile(testFile, testData);
	// Checkin the file directly to the Integrity Source project
	checkinFileIntoSource();

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

    @Test(timeout=600000)
    public void testVariantSandboxCreateSuccessResyncWithCleanCopyExistingFile()
		    throws Exception
    {
	String testData = "hello world";
	addTestFileInSource();

	assertTrue("scm polling should detect a change after build", localClientVariantProjectCleanCopy.poll(listener).hasChanges());
	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
	assertFalse("scm polling should not detect any more changes after build", localClientVariantProjectCleanCopy.poll(listener).hasChanges());

	try(BufferedReader br = new BufferedReader(new FileReader(
			String.valueOf(build.getWorkspace().child(fileName))))) {
	    assertEquals(br.readLine(), null);
	}

	checkoutFileFromSource();
	FileUtils.writeStringToFile(testFile, testData);
	checkinFileIntoSource();

	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
	assertFalse("scm polling should not detect any more changes after build", localClientVariantProjectCleanCopy.poll(listener).hasChanges());

	myFile =  new File(
			String.valueOf(build.getWorkspace().child(fileName)));
	try(Scanner s = new Scanner(myFile)) {
	    String content = s.useDelimiter("\\n").next();
	    assertEquals(content, testData);
	}
    }

    @Test(timeout=600000)
    public void testVariantSandboxCreateSuccessResyncWithDeletedFile()
		    throws Exception
    {
	addTestFileInSource();
	assertTrue("scm polling should detect a change after build", localClientVariantProject.poll(listener).hasChanges());
	build = build(localClientVariantProject, Result.SUCCESS);
	// Assert file exists in workspace
	assertTrue(new File(
			String.valueOf(build.getWorkspace().child(fileName))).isFile());
	dropTestFileFromSource();
	assertTrue("scm polling should detect a change after build", localClientVariantProject.poll(listener).hasChanges());
	build = build(localClientVariantProject, Result.SUCCESS);
	assertFalse("scm polling should not detect any more changes after build", localClientVariantProject.poll(listener).hasChanges());
	// Assert that after build, file doesn't exist in in workspace
	assertFalse((new File(
			String.valueOf(build.getWorkspace().child(fileName))).isFile()));
    }
}
