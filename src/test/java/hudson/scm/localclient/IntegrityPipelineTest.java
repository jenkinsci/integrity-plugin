package hudson.scm.localclient;

import hudson.model.queue.QueueTaskFuture;
import hudson.scm.ChangeLogSet;
import hudson.scm.IntegritySCMTest;
import hudson.triggers.SCMTrigger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.recipes.WithTimeout;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by asen on 15-06-2017.
 */
public class IntegrityPipelineTest extends IntegritySCMTest
{
    @Before
    public void setup() throws Exception
    {
        super.setUp();
	localClientProject = setupIntegrityProjectWithLocalClientWithCheckpointOff(successConfigPath);
    }

    @Test
    public void pipeLineTestWithLocalClient() throws Exception
    {
	addTestFileInSource();
        jenkinsRule.getInstance().getScm("IntegritySCM");
	WorkflowJob wfJob= jenkinsRule.getInstance().createProject(WorkflowJob.class, "demo");
	wfJob.setDefinition(new CpsFlowDefinition(
			"node {\n" +
				"    checkout(" +
				     "[$class: 'IntegritySCM', checkpointBeforeBuild: false, " +
				     "configPath: '"+successConfigPath+"', configurationName: 'test', " +
				     "serverConfig: 'test', localClient: true])" +
			     "}"));
	WorkflowRun b = jenkinsRule.assertBuildStatusSuccess(wfJob.scheduleBuild2(0));
	List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
	assertEquals(1, changeSets.size());
	ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
	assertEquals(b, changeSet.getRun());
	assertEquals("integrity", changeSet.getKind());
	/*changeSet = changeSets.get(0);
	Iterator<? extends ChangeLogSet.Entry> iterator = changeSet.iterator();
	assertTrue(iterator.hasNext());
	ChangeLogSet.Entry entry = iterator.next();
	assertEquals(fileName, entry.getAffectedPaths().toString());
	assertFalse(iterator.hasNext());*/
    }

    @Test
    public void pipeLineTestWithLocalClientonRemoteNode() throws Exception
    {
	WorkflowJob wfJob= jenkinsRule.jenkins.createProject(WorkflowJob.class, "demo");
	wfJob.addTrigger(new SCMTrigger(""));
	wfJob.setQuietPeriod(3); // so it only does one build
	wfJob.setDefinition(new CpsFlowDefinition(
			"node ('slave0') {\n" +
					"checkout " +
					"([$class: 'IntegritySCM', checkpointBeforeBuild: false, " +
					"configPath: '"+successConfigPath+"', configurationName: 'test', " +
					"serverConfig: 'test', localClient: true])" +
					"}"));
	WorkflowRun b = jenkinsRule.assertBuildStatusSuccess(wfJob.scheduleBuild2(0));
	List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
	assertEquals(1, changeSets.size());
	ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
	assertEquals(b, changeSet.getRun());
	assertEquals("integrity", changeSet.getKind());
    }

    @Test(timeout=60000000)
    @WithTimeout(60000000)
    public void testConcurrentPipelineBuilds() throws Exception
    {
	jenkinsRule.getInstance().getScm("IntegritySCM");
	WorkflowJob wfJob= jenkinsRule.getInstance().createProject(WorkflowJob.class, "demo");
	wfJob.setDefinition(new CpsFlowDefinition(
			"node {\n" +
					"    checkout(" +
					"[$class: 'IntegritySCM', checkpointBeforeBuild: false, " +
					"configPath: '"+successConfigPath+"', configurationName: 'test', " +
					"serverConfig: 'test', localClient: true])" +
					"}"));

	QueueTaskFuture<WorkflowRun> build1 = wfJob.scheduleBuild2(0);
	build1.waitForStart();
	QueueTaskFuture<WorkflowRun> build2 = wfJob.scheduleBuild2(0);
	build2.waitForStart();
	QueueTaskFuture<WorkflowRun> build3 = wfJob.scheduleBuild2(0);
	build3.waitForStart();
	QueueTaskFuture<WorkflowRun> build4 = wfJob.scheduleBuild2(0);
	build4.waitForStart();
	QueueTaskFuture<WorkflowRun> build5 = wfJob.scheduleBuild2(0);
	build5.waitForStart();
	QueueTaskFuture<WorkflowRun> build6 = wfJob.scheduleBuild2(0);
	build6.waitForStart();
	QueueTaskFuture<WorkflowRun> build7 = wfJob.scheduleBuild2(0);
	build7.waitForStart();
	QueueTaskFuture<WorkflowRun> build8 = wfJob.scheduleBuild2(0);
	build8.waitForStart();
	QueueTaskFuture<WorkflowRun> build9 = wfJob.scheduleBuild2(0);
	build9.waitForStart();
	QueueTaskFuture<WorkflowRun> build10 = wfJob.scheduleBuild2(0);
	build10.waitForStart();
	QueueTaskFuture<WorkflowRun> build11 = wfJob.scheduleBuild2(0);
	build11.waitForStart();


	jenkinsRule.assertBuildStatusSuccess(build1.get());
	jenkinsRule.assertBuildStatusSuccess(build2.get());
	jenkinsRule.assertBuildStatusSuccess(build3.get());
	jenkinsRule.assertBuildStatusSuccess(build4.get());
	jenkinsRule.assertBuildStatusSuccess(build5.get());
	jenkinsRule.assertBuildStatusSuccess(build6.get());
	jenkinsRule.assertBuildStatusSuccess(build7.get());
	jenkinsRule.assertBuildStatusSuccess(build8.get());
	jenkinsRule.assertBuildStatusSuccess(build9.get());
	jenkinsRule.assertBuildStatusSuccess(build10.get());
	jenkinsRule.assertBuildStatusSuccess(build11.get());

    }
    
    @Test(timeout=60000000)
    @WithTimeout(60000000)
    public void testConcurrentPipelineBuildsWithScope() throws Exception
    {
	jenkinsRule.getInstance().getScm("IntegritySCM");
	WorkflowJob wfJob= jenkinsRule.getInstance().createProject(WorkflowJob.class, "demo");
	wfJob.setDefinition(new CpsFlowDefinition(
			"node {\n" +
					"    checkout(" +
					"[$class: 'IntegritySCM', checkpointBeforeBuild: false, " +
					"configPath: '"+successConfigPath+"', configurationName: 'test', " +
					"serverConfig: 'test', localClient: true, sandboxScope:'"+NAME_MBR_1_0_0_0_TXT+"'])" +
					"}"));

	QueueTaskFuture<WorkflowRun> build1 = wfJob.scheduleBuild2(0);
	build1.waitForStart();
	QueueTaskFuture<WorkflowRun> build2 = wfJob.scheduleBuild2(0);
	build2.waitForStart();
	QueueTaskFuture<WorkflowRun> build3 = wfJob.scheduleBuild2(0);
	build3.waitForStart();
	QueueTaskFuture<WorkflowRun> build4 = wfJob.scheduleBuild2(0);
	build4.waitForStart();
	QueueTaskFuture<WorkflowRun> build5 = wfJob.scheduleBuild2(0);
	build5.waitForStart();
	QueueTaskFuture<WorkflowRun> build6 = wfJob.scheduleBuild2(0);
	build6.waitForStart();
	QueueTaskFuture<WorkflowRun> build7 = wfJob.scheduleBuild2(0);
	build7.waitForStart();
	QueueTaskFuture<WorkflowRun> build8 = wfJob.scheduleBuild2(0);
	build8.waitForStart();
	QueueTaskFuture<WorkflowRun> build9 = wfJob.scheduleBuild2(0);
	build9.waitForStart();
	QueueTaskFuture<WorkflowRun> build10 = wfJob.scheduleBuild2(0);
	build10.waitForStart();
	QueueTaskFuture<WorkflowRun> build11 = wfJob.scheduleBuild2(0);
	build11.waitForStart();


	jenkinsRule.assertBuildStatusSuccess(build1.get());
	jenkinsRule.assertBuildStatusSuccess(build2.get());
	jenkinsRule.assertBuildStatusSuccess(build3.get());
	jenkinsRule.assertBuildStatusSuccess(build4.get());
    }
}
