package hudson.scm;

import com.mks.api.Command;
import com.mks.api.Option;
import com.mks.api.response.Response;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.scm.api.session.APISession;
import hudson.scm.api.session.ISession;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by asen on 06-06-2017.
 * Integration Tests for Local Client Testing
 */
public class IntegrityCreateSandboxTaskTest extends IntegritySCMTest
{

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    private FreeStyleProject localClientProject;
    private FreeStyleProject localClientProjectCleanCopy;
    private ISession session;
    private String configPath;
    private File myFile;
    private File testFile;
    private String fileName;
    private Command cmd;
    private Response response;
    private FreeStyleBuild build;

    @Before
    public void setup() throws Exception
    {
        IntegrityConfigurable integrityConfigurable = new IntegrityConfigurable("test", "localhost",
                        7001, "localhost",7001, false,
                        "Administrator", "password");
        configPath="#/DummyProject";
        localClientProject = setupIntegrityProjectWithLocalClient(configPath);
        localClientProjectCleanCopy = setupIntegrityProjectWithLocalClientCleanCopy(configPath);
        session = APISession.createLocalIntegrationPoint(integrityConfigurable);
    }

    @Test
    /**
     *  Test createSandbox
     */
    public void testSandboxWithMultipleBuilds() throws Exception
    {
        build = build(localClientProject);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
        build = build(localClientProject);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
        build = build(localClientProject);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
        build = build(localClientProject);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
    }

    @Test
    /**
     *  Test createSandbox
     */
    public void testSandboxWithConcurrentBuilds() throws Exception
    {
        localClientProject.setConcurrentBuild(true);
        build = build(localClientProject);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
        build = build(localClientProject);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
        build = build(localClientProject);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
        build = build(localClientProject);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
    }

    @Test
    /**
     *  Test createSandbox
     */
    public void testSandboxCreateSuccessWithCleanCopyResync() throws Exception
    {
        build = build(localClientProjectCleanCopy);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
    }

    @Test
    /**
     *  Test createSandbox
     */
    public void testSandboxCreateSuccessResync() throws Exception
    {
        build = build(localClientProject);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
    }

    @Test
    /**
     *  Test createSandbox
     */
    public void testSandboxCreateSuccessResyncWithNewFile() throws Exception
    {
        assert session != null;
        cmd = new Command(Command.SI, "projectadd");
        cmd.addOption(new Option("project", configPath));
        fileName = Math.random()+".txt";
        testFile = testFolder.newFile(fileName);
        cmd.addOption(new Option("sourceFile", testFile.getAbsolutePath()));
        cmd.addSelection(fileName);
        response = session.runCommand(cmd);
        assertEquals(response.getExitCode(),0);
        build = build(localClientProject);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);

        File myFile = new File(
                        String.valueOf(build.getWorkspace().child(fileName)));
        assertTrue(myFile.exists());
    }

    @Test
    /**
     *  Test createSandbox
     */
    public void testSandboxCreateSuccessResyncWithNewFileAndCleanCopy()
                    throws Exception
    {
        assert session != null;
        cmd = new Command(Command.SI, "projectadd");
        cmd.addOption(new Option("project", configPath));
        fileName = Math.random()+".txt";
        testFile = testFolder.newFile(fileName);
        cmd.addOption(new Option("sourceFile", testFile.getAbsolutePath()));
        cmd.addSelection(fileName);
        response = session.runCommand(cmd);
        assertEquals(response.getExitCode(),0);
        build = build(localClientProjectCleanCopy);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);

        myFile = new File(
                        String.valueOf(build.getWorkspace().child(fileName)));
        assertTrue(myFile.exists());
    }

    @Test
    /**
     *  Test createSandbox
     */
    public void testSandboxCreateSuccessResyncWithExistingFile()
                    throws Exception
    {
        String testData = "hello world";
        assert session != null;
        cmd = new Command(Command.SI, "projectadd");
        cmd.addOption(new Option("project", configPath));
        fileName = Math.random()+".txt";
        testFile = testFolder.newFile(fileName);
        cmd.addOption(new Option("sourceFile", testFile.getAbsolutePath()));
        cmd.addSelection(fileName);
        response = session.runCommand(cmd);
        assertEquals(response.getExitCode(),0);

        build = build(localClientProject);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);

        try(BufferedReader br = new BufferedReader(new FileReader(
                        String.valueOf(build.getWorkspace().child(fileName))))) {
            assertEquals(br.readLine(), null);
        }

        cmd = new Command(Command.SI, "projectco");
        cmd.addOption(new Option("project", configPath));
        cmd.addOption(new Option("targetFile", testFile.getAbsolutePath()));
        cmd.addSelection(fileName);

        response = session.runCommand(cmd);
        assertEquals(response.getExitCode(),0);
        FileUtils.writeStringToFile(testFile, testData);

        cmd = new Command(Command.SI, "projectci");
        cmd.addOption(new Option("project", configPath));
        cmd.addOption(new Option("sourceFile", testFile.getAbsolutePath()));
        cmd.addOption(new Option("description", "checkin"));
        cmd.addSelection(fileName);

        response = session.runCommand(cmd);
        assertEquals(response.getExitCode(),0);

        build = build(localClientProject);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);

        myFile =  new File(
                        String.valueOf(build.getWorkspace().child(fileName)));
        try(Scanner s = new Scanner(myFile)) {
            String content = s.useDelimiter("\\n").next();
            assertEquals(content, testData);
        }
    }

    @Test
    /**
     *  Test createSandbox
     */
    public void testSandboxCreateSuccessResyncWithCleanCopyExistingFile()
                    throws Exception
    {
        String testData = "hello world";
        assert session != null;
        cmd = new Command(Command.SI, "projectadd");
        cmd.addOption(new Option("project", configPath));
        fileName = Math.random()+".txt";
        testFile = testFolder.newFile(fileName);
        cmd.addOption(new Option("sourceFile", testFile.getAbsolutePath()));
        cmd.addSelection(fileName);
        response = session.runCommand(cmd);
        assertEquals(response.getExitCode(),0);

        build = build(localClientProjectCleanCopy);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);

        try(BufferedReader br = new BufferedReader(new FileReader(
                        String.valueOf(build.getWorkspace().child(fileName))))) {
            assertEquals(br.readLine(), null);
        }

        cmd = new Command(Command.SI, "projectco");
        cmd.addOption(new Option("project", configPath));
        cmd.addOption(new Option("targetFile", testFile.getAbsolutePath()));
        cmd.addSelection(fileName);

        response = session.runCommand(cmd);
        assertEquals(response.getExitCode(),0);
        FileUtils.writeStringToFile(testFile, testData);

        cmd = new Command(Command.SI, "projectci");
        cmd.addOption(new Option("project", configPath));
        cmd.addOption(new Option("sourceFile", testFile.getAbsolutePath()));
        cmd.addOption(new Option("description", "checkin"));
        cmd.addSelection(fileName);

        response = session.runCommand(cmd);
        assertEquals(response.getExitCode(),0);

        build = build(localClientProjectCleanCopy);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);

        myFile =  new File(
                        String.valueOf(build.getWorkspace().child(fileName)));
        try(Scanner s = new Scanner(myFile)) {
            String content = s.useDelimiter("\\n").next();
            assertEquals(content, testData);
        }
    }

    @Test
    /**
     *  Test createSandbox
     */
    public void testSandboxCreateSuccessResyncWithDeletedFile()
                    throws Exception
    {
        assert session != null;
        cmd = new Command(Command.SI, "projectadd");
        cmd.addOption(new Option("project", configPath));
        fileName = Math.random()+".txt";
        testFile = testFolder.newFile(fileName);
        cmd.addOption(new Option("sourceFile", testFile.getAbsolutePath()));
        cmd.addSelection(fileName);
        response = session.runCommand(cmd);
        assertEquals(response.getExitCode(),0);

        build = build(localClientProject);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);

        myFile = new File(
                        String.valueOf(build.getWorkspace().child(fileName)));
        assertTrue(myFile.exists());

        assert session != null;
        cmd = new Command(Command.SI, "drop");
        cmd.addOption(new Option("project", configPath));
        cmd.addSelection(fileName);
        response = session.runCommand(cmd);
        assertEquals(response.getExitCode(),0);

        build = build(localClientProject);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);

        assertTrue(!(new File(
                        String.valueOf(build.getWorkspace().child(fileName))).exists()));
    }
}