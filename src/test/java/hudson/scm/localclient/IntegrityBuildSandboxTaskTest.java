package hudson.scm.localclient;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import hudson.scm.IntegritySCM;
import hudson.scm.IntegritySCMTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mks.api.response.APIException;

/**
 * Created by asen on 06-06-2017.
 * Integration Tests for Local Client Testing
 * 
 * Note - You need to create a project structure using script file - /src/main/resources/hudson/scm/ProjectSetup/create_Project_WithSubsAnd_MembersCopy.ksh.
 * Also need to create a Development Path named as DP_0.3813840334796077 on the project created using steps.
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
    
    @Test
    public void testCreateBuildScopedSandboxForFileType() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope(TYPE_TEXT);
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB0_MBR_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateBuildScopedSandboxForSpecificMemberName() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope(NAME_MBR_1_0_0_0_TXT);
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateScopedSandboxForExcludeSpecificMember() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope("!name:mbr-1-0-0-0.txt");
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_1_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
    }
    
    @Test
    public void testCreateScopedSandboxForWildCard() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope("name:*.txt");
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateBuildScopedSandboxForMemberRevLabel() throws Exception
    {
    addLabel("XYZ", "#/JenkinsBulkProject1#sub0");
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope("memberrevlabellike:XYZ");
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB0_MBR_0_1_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateBuildScopedSandboxForAnyRevLabel() throws Exception
    {
    addLabel("Label2", "#/JenkinsBulkProject1#sub0");
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope(ANYREVLABELLIKE_LABEL2);
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB0_MBR_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateBuildScopedSandboxForSubProject() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope(SUBPROJECT_SUB1_SUB1_0_SUB1_0_0);
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateBuildScopedSandboxForAttrAny() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope(ANY);
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateBuildBlankScopedSandbox() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope(EMPTY_STRING);
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }

    @Test
    public void testReconfigureBuildBlankScopedSandbox() throws Exception
    {
	build = build(localBuildClientProject, Result.SUCCESS);
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope(EMPTY_STRING);
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testReconfigureScopedSandbox() throws Exception
    {
    addLabel("Label2", "#/JenkinsBulkProject1#sub0");
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope(ANYREVLABELLIKE_LABEL2);
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB0_MBR_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
	
	addLabel("MyLabel", "#/JenkinsBulkProject1#sub0");
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope(MEMBERREVLABELLIKE_MY_LABEL);
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB0_MBR_0_1_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
	
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope(TYPE_TEXT);
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB0_MBR_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateBuildScopedSandboxForDifferentMembersOfSameProject() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope(NAME_MBR_1_0_0_0_TXT);
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
	
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope(NAME_MBR_1_0_0_1_TXT);
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_1_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateBuildScopedSandboxForMultipleSubProject() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope(MULTIPLE_PROJECTS);
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateBuildScopedSandboxForInvalidScopeValue() throws Exception
    {
		// Change the sandbox Scope
		String scopeName = "inValidScope";
		((IntegritySCM) localBuildClientProject.getScm()).setSandboxScope(scopeName);
		localBuildClientProject.save();
		build = build(localBuildClientProject, Result.FAILURE);
		List<String> log = build.getLog(200);
		assertTrue(log
				.contains("[LocalClient] IntegrityCreateSandboxTask Exception Caught : Failed to create sandbox : MKS124812: Unknown filter name: "
						+ scopeName));
    }
    
    @Test
    public void testCreateBuildScopedSandboxForPathAttribute() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope(WAILDCARD_SCOPE_ATTR);
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_1_MBR_1_0_1_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateBuildScopedSandboxForANDOperation() throws Exception
    {
    addLabel("QQQQ", "#/JenkinsBulkProject1#sub1/sub1-0/sub1-0-0");
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope(SCOPE_WITH_AND_OPERATOR);
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT2))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testReconfigureBuildScopedSandboxForANDOperation() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope(NAME_MBR_1_2_4_0_TXT);
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_2_SUB1_2_4_MBR_1_2_4_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
	
	addLabel("QQQQ", "#/JenkinsBulkProject1#sub1/sub1-2/sub1-2-2/sub1-2-2-1");
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope(MEMBERREVLABELLIKE_QQQQ_NAME_MBR_1_2_2_1_0_TXT);
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_2_SUB1_2_2_SUB1_2_2_1_MBR_1_2_2_1_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
	
	addLabel("MyLabel", "#/JenkinsBulkProject1#sub0");
	// Change the sandbox Scope
	((IntegritySCM)localBuildClientProject.getScm()).setSandboxScope(MEMBERREVLABELLIKE_MY_LABEL);
	localBuildClientProject.save();
	build = build(localBuildClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
	           String.valueOf(build.getWorkspace().child(SUB0_MBR_0_1_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
	           String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
}