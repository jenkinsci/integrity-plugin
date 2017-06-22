package hudson.scm.localclient;

import hudson.model.Label;
import hudson.scm.IntegritySCMTest;
import hudson.triggers.SCMTrigger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by asen on 15-06-2017.
 */
public class IntegrityPipelineTest extends IntegritySCMTest
{
    @Before
    public void setup() throws Exception
    {
        super.setUp();
    }

    @Test
    public void pipeLineTestWithLocalClient() throws Exception
    {
        addTestFileInSource();
	WorkflowJob wfJob= jenkinsRule.jenkins.createProject(WorkflowJob.class, "demo");
	wfJob.setDefinition(new CpsFlowDefinition(
			"node {\n" +
				"    ws {\n" +
				"    checkout changelog: false, poll: false, " +
				     "scm: [$class: 'IntegritySCM', checkpointBeforeBuild: false, " +
				     "configPath: '"+successConfigPath+"', configurationName: 'test', " +
				     "serverConfig: 'test', localClient: true] \n" +
				"    }\n" +
			     "}"));
	WorkflowRun b = jenkinsRule.assertBuildStatusSuccess(wfJob.scheduleBuild2(0));
	assertTrue(b.getArtifactManager().root().child(fileName).isFile());

	addTestFileInSource();
	b = jenkinsRule.assertBuildStatusSuccess(wfJob.getLastBuild());
	assertEquals(2, b.number);
	assertTrue(b.getArtifactManager().root().child(fileName).isFile());
    }

    @Test
    public void pipeLineTestWithLocalClientonRemoteNode() throws Exception
    {
	addTestFileInSource();
	// Create a remote slave0
	jenkinsRule.createOnlineSlave(Label.get("remote"));
	WorkflowJob wfJob= jenkinsRule.jenkins.createProject(WorkflowJob.class, "demo");
	wfJob.addTrigger(new SCMTrigger(""));
	wfJob.setQuietPeriod(3); // so it only does one build
	wfJob.setDefinition(new CpsFlowDefinition(
			"node ('remote') {\n" +
					"    ws {\n" +
					"    checkout changelog: false, poll: false, " +
					"scm: [$class: 'IntegritySCM', checkpointBeforeBuild: false, " +
					"configPath: '"+successConfigPath+"', configurationName: 'test', " +
					"serverConfig: 'test', localClient: true] \n" +
					"    }\n" +
					"}"));
	WorkflowRun b = jenkinsRule.assertBuildStatusSuccess(wfJob.scheduleBuild2(0));
	assertTrue(b.getArtifactManager().root().child(fileName).isFile());

	addTestFileInSource();
	b = jenkinsRule.assertBuildStatusSuccess(wfJob.getLastBuild());
	assertEquals(2, b.number);
	assertTrue(b.getArtifactManager().root().child(fileName).isFile());
    }
}
