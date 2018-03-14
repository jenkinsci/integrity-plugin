package hudson.scm.localclient;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import hudson.model.Result;
import hudson.scm.IntegritySCM;
import hudson.scm.IntegritySCMTest;

import java.io.File;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mks.api.response.APIException;

/**
 * Created by asen on 05-01-2018.
 * Unit tests for scoped sandbox.
 * 
 * Note - You need to create a project structure using script file - /src/main/resources/hudson/scm/ProjectSetup/create_Project_WithSubsAnd_MembersCopy.ksh.
 * Also need to create a Development Path named as DP_0.3813840334796077 on the project created using steps.
 */
public class IntegrityScopedSandboxTest extends IntegritySCMTest
{

	@Before
    public void setUp() throws Exception {
	super.setUp();
	localClientProject = setupIntegrityProjectWithLocalClientWithCheckpointOff(successConfigPath);
	localClientProjectCleanCopy = setupIntegrityProjectWithLocalClientCleanCopyCheckpointOff(successConfigPath);
    }
	
    @Test
    public void testCreateScopedSandboxForFileType() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope(TYPE_TEXT);
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB0_MBR_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateScopedSandboxForSpecificMemberName() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope(NAME_MBR_1_0_0_0_TXT);
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateScopedSandboxForExcludeSpecificMember() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope("!name:mbr-1-0-0-0.txt");
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_1_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
    }
    
    @Test
    public void testCreateScopedSandboxForWildCard() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope("name:*.txt");
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateScopedSandboxForMemberRevLabel() throws Exception
    {
   	addLabel("YourLabel", "#/JenkinsBulkProject1#sub0/sub0-1");
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope("memberrevlabellike:YourLabel");
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child("sub0\\sub0-1\\mbr-0-1-0.txt"))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateScopedSandboxForAnyRevLabel() throws Exception
    {
    addLabel("TestLabel", "#/JenkinsBulkProject1#sub0");
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope("memberrevlabellike:TestLabel");
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB0_MBR_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateScopedSandboxForSubProject() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope(SUBPROJECT_SUB1_SUB1_0_SUB1_0_0);
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateScopedSandboxForAttrAny() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope(ANY);
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
	
    }
    
    @Test
    public void testCreateBlankScopedSandbox() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope(EMPTY_STRING);
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }

    @Test
    public void testReconfigureBlankScopedSandbox() throws Exception
    {
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope(EMPTY_STRING);
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
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
	((IntegritySCM)localClientProject.getScm()).setSandboxScope(ANYREVLABELLIKE_LABEL2);
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB0_MBR_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
	
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope(MEMBERREVLABELLIKE_MY_LABEL);
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB0_MBR_0_1_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
	
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope(TYPE_TEXT);
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB0_MBR_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateScopedSandboxForDifferentMembersOfSameProject() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope(NAME_MBR_1_0_0_0_TXT);
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
	
	((IntegritySCM)localClientProject.getScm()).setSandboxScope(NAME_MBR_1_0_0_1_TXT);
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_1_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateScopedSandboxForMultipleSubProject() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope(MULTIPLE_PROJECTS);
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateScopedSandboxForInvalidScopeValue() throws Exception
    {
		// Change the sandbox Scope
		String scopeName = "inValidScope";
		((IntegritySCM) localClientProject.getScm()).setSandboxScope(scopeName);
		localClientProject.save();
		build = build(localClientProject, Result.FAILURE);
		List<String> log = build.getLog(200);
		assertTrue(log
				.contains("[LocalClient] IntegrityCreateSandboxTask Exception Caught : Failed to create sandbox : MKS124812: Unknown filter name: "
						+ scopeName));
    }
    
    @Test
    public void testCreateScopedSandboxForPathAttribute() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope(WAILDCARD_SCOPE_ATTR);
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_1_MBR_1_0_1_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
    @Test
    public void testCreateScopedSandboxForANDOperation() throws Exception
    {
    addLabel("QQQQ", "#/JenkinsBulkProject1#sub1/sub1-0/sub1-0-0");
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope(SCOPE_WITH_AND_OPERATOR);
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_0_SUB1_0_0_MBR_1_0_0_0_TXT2))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
	
    }
    
    @Test
    public void testReconfigureScopedSandboxForANDOperation() throws Exception
    {
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope(NAME_MBR_1_2_4_0_TXT);
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_SUB1_2_SUB1_2_4_MBR_1_2_4_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
	
	addLabel("QQQQ", "#/JenkinsBulkProject1#sub1-2-2/sub1-2-2-1");
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope(MEMBERREVLABELLIKE_QQQQ_NAME_MBR_1_2_2_1_0_TXT);
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
            String.valueOf(build.getWorkspace().child(SUB1_2_2_SUB1_2_2_1_MBR_1_2_2_1_0_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
            String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
	
	addLabel("MyLabel", "#/JenkinsBulkProject1#sub0");
	// Change the sandbox Scope
	((IntegritySCM)localClientProject.getScm()).setSandboxScope(MEMBERREVLABELLIKE_MY_LABEL);
	localClientProject.save();
	build = build(localClientProject, Result.SUCCESS);
	assertTrue("File Exists in workspace!", new File(
	           String.valueOf(build.getWorkspace().child(SUB0_MBR_0_1_TXT))).isFile());
	assertFalse("File does not Exist in workspace!", new File(
	           String.valueOf(build.getWorkspace().child(JAVA_FILE_JAVA))).isFile());
    }
    
}
