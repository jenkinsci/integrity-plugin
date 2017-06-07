package hudson.scm;

import hudson.model.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.SortedMap;

import static org.junit.Assert.*;

/**
 * Created by asen on 06-06-2017.
 */
public class IntegrityCreateSandboxTaskTest extends IntegritySCMTest
{

    @Test
    /**
     *  Test createSandbox
     */
    public void testSandboxCreateFailure() throws Exception
    {
        testBuildSuccessWithLocalClient();
        String configPath="#/DummyProject";
        FreeStyleProject project = setupIntegrityProjectWithLocalClient(configPath);
        FreeStyleBuild build = build(project);
        jenkinsRule.assertBuildStatus(Result.FAILURE, build);
    }

    @Test
    /**
     *  Test createSandbox
     */
    public void testSandboxCreateSuccess() throws Exception
    {
        String configPath="#/DummyProject";
        FreeStyleProject project = setupIntegrityProjectWithLocalClient(configPath);
        FreeStyleBuild build = build(project);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
    }

}