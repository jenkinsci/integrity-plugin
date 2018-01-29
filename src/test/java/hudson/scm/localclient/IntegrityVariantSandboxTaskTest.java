package hudson.scm.localclient;

import hudson.model.*;
import hudson.model.queue.QueueTaskFuture;
import hudson.scm.IntegritySCM;
import hudson.scm.IntegritySCMTest;
import hudson.scm.PollingResult;
import hudson.triggers.SCMTrigger;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mks.api.response.APIException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

/**
 * Created by asen on 20-06-2017.
 * 
 * Note - You need to create a project structure using script file - /src/main/resources/hudson/scm/ProjectSetup/create_Project_WithSubsAnd_MembersCopy.ksh.
 * Also need to create a Development Path named as DP_0.3813840334796077 on the project created using steps.
 */
public class IntegrityVariantSandboxTaskTest extends IntegritySCMTest
{

	@Before
    public void setUp() throws Exception {
        super.setUp();
	createDevPath();
	localClientVariantProject = setupVariantIntegrityProjectWithLocalClientWithCheckpointOff(successConfigPath);
	localClientVariantProjectCleanCopy = setupVariantIntegrityProjectWithLocalClientCleanCopyCheckpointOff(successConfigPath);
    }

	@After
	public void cleanUp() throws APIException{
		if(build != null)
			dropSandbox(build.getWorkspace());
	}
	
    @Test
    public void testVariantSandboxCreateSuccessResync() throws Exception
    {
	build = build(localClientVariantProject, Result.SUCCESS);
    }

    @Test
    public void testVariantSandboxCreateSuccessResyncWithSlave() throws Exception
    {
	localClientVariantProject.setAssignedNode(slave0);
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertThat("Needs to build on the slave0 to check serialization", build.getBuiltOn(), is((Node) slave0));
    }

    @Test
    public void testVariantSandboxCreateSuccessWithCleanCopyResync() throws Exception
    {
	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
    }

    @Test(timeout=300000)
    public void testVariantCleanSandboxWithMultipleBuilds() throws Exception
    {
	// Test multiple builds within same sandbox
	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
	build = build(localClientVariantProjectCleanCopy, Result.SUCCESS);
    }

    @Test(timeout=300000)
    public void testVariantSandboxWithConcurrentBuilds() throws Exception
    {
	jenkinsRule.jenkins.setNumExecutors(4);
	localClientVariantProject.setConcurrentBuild(true);
	QueueTaskFuture<FreeStyleBuild> build1 = localClientVariantProject.scheduleBuild2(0, new Cause.UserIdCause());
	build1.waitForStart();
	QueueTaskFuture<FreeStyleBuild> build2 = localClientVariantProject.scheduleBuild2(0, new Cause.UserIdCause());
	build2.waitForStart();
	QueueTaskFuture<FreeStyleBuild> build3 = localClientVariantProject.scheduleBuild2(0, new Cause.UserIdCause());
	build3.waitForStart();
	QueueTaskFuture<FreeStyleBuild> build4 = localClientVariantProject.scheduleBuild2(0, new Cause.UserIdCause());

	jenkinsRule.assertBuildStatusSuccess(build1.get());
	jenkinsRule.assertBuildStatusSuccess(build2.get());
	jenkinsRule.assertBuildStatusSuccess(build3.get());
	jenkinsRule.assertBuildStatusSuccess(build4.get());
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
    
    @Test
    public void testCreateVariantScopedSandboxForFileType() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope(TYPE_TEXT);
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB0_MBR_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateScopedSandboxForSpecificMemberName() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope(NAME_MBR_1_0_0_0_TXT);
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateScopedSandboxForExcludeSpecificMember() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope("!name:mbr-1-0-0-0.txt");
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_1_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
    }
    
    @Test
    public void testCreateScopedSandboxForWildCard() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope("name:*.txt");
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateVariantScopedSandboxForMemberRevLabel() throws Exception
    {
	addLabel("YourLabel1", "#/JenkinsBulkProject1#sub0");
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope("memberrevlabellike:YourLabel1");
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB0_MBR_0_1_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateVariantScopedSandboxForAnyRevLabel() throws Exception
    {
    addLabel("YourLabel11", "#/JenkinsBulkProject1#sub0");
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope("memberrevlabellike:YourLabel11");
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child("sub0\\mbr-0-0.txt"))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateVariantScopedSandboxForSubProject() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope(SUBPROJECT_SUB1_SUB1_0_SUB1_0_0);
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateVariantScopedSandboxForAttrAny() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope(ANY);
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateBlankVariantScopedSandbox() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope(EMPTY_STRING);
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }

    @Test
    public void testReconfigureVariantBlankScopedSandbox() throws Exception
    {
	build = build(localClientVariantProject, Result.SUCCESS);
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope(EMPTY_STRING);
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testReconfigureVariantScopedSandbox() throws Exception
    {
    addLabel("Label2", "#/JenkinsBulkProject1#sub0");
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope(ANYREVLABELLIKE_LABEL2);
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB0_MBR_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
	
	addLabel("MyLabel", "#/JenkinsBulkProject1#sub0");
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope(MEMBERREVLABELLIKE_MY_LABEL);
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB0_MBR_0_1_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
	
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope(TYPE_TEXT);
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB0_MBR_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateVariantScopedSandboxForDifferentMembersOfSameProject() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope(NAME_MBR_1_0_0_0_TXT);
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());

	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope(NAME_MBR_1_0_0_1_TXT);
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_1_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateVariantScopedSandboxForMultipleSubProject() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope(MULTIPLE_PROJECTS);
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateVariantScopedSandboxForInvalidScopeValue() throws Exception
    {
		// Change the sandbox Scope
		String scopeName = "inValidScope";
		((IntegritySCM) localClientVariantProject.getScm()).setSandboxScope(scopeName);
		localClientVariantProject.save();
		build = build(localClientVariantProject, Result.FAILURE);
		List<String> log = build.getLog(200);
		assertTrue(log
				.contains("[LocalClient] IntegrityCreateSandboxTask Exception Caught : Failed to create sandbox : MKS124812: Unknown filter name: "
						+ scopeName));
    }
    
    @Test
    public void testCreateVariantScopedSandboxForPathAttribute() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope(WAILDCARD_SCOPE_ATTR);
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_1_MBR_1_0_1_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateVariantScopedSandboxForANDOperation() throws Exception
    {
    addLabel("QQQQ", "#/JenkinsBulkProject1#sub1/sub1-0/sub1-0-0");
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope(SCOPE_WITH_AND_OPERATOR); 
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT2))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testReconfigureVariantScopedSandboxForANDOperation() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope(NAME_MBR_1_2_4_0_TXT);
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_2_4_MBR_1_2_4_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
	
	addLabel("QQQQ", "#/JenkinsBulkProject1#sub1-2-2/sub1-2-2-1");
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope(MEMBERREVLABELLIKE_QQQQ_NAME_MBR_1_2_2_1_0_TXT);
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_2_2_SUB1_2_2_1_MBR_1_2_2_1_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
	
	addLabel("MyLabel", "#/JenkinsBulkProject1#sub0");
	// Change the sandbox Scope
	((IntegritySCM)localClientVariantProject.getScm()).setSandboxScope(MEMBERREVLABELLIKE_MY_LABEL);
	localClientVariantProject.save();
	build = build(localClientVariantProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
	           String.valueOf(build.getWorkspace().child(SUB0_MBR_0_1_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
	           String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
}