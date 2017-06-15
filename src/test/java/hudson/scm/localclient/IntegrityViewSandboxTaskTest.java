package hudson.scm.localclient;

import com.mks.api.Command;
import com.mks.api.Option;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.scm.PollingResult;
import hudson.triggers.SCMTrigger;
import hudson.util.StreamTaskListener;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;

/**
 * Created by asen on 15-06-2017.
 */
public class IntegrityViewSandboxTaskTest extends IntegrityCreateSandboxTaskTest
{

    private ExecutorService singleThreadExecutor;

    @Before
    public void setUp() throws Exception {
	//expectChanges = false;
	singleThreadExecutor = Executors.newSingleThreadExecutor();
    }

    private Future<Void> triggerSCMTrigger(final SCMTrigger trigger)
    {
	if(trigger == null) return null;
	Callable<Void> callable = new Callable<Void>() {
	    public Void call() throws Exception
	    {
		trigger.run();
		return null;
	    }
	};
	return singleThreadExecutor.submit(callable);
    }

    @Test
    public void testSandboxViewWhilePollingWithChanges()
		    throws Exception
    {
	build = build(localClientProject);
	jenkinsRule.assertBuildStatus(Result.SUCCESS, build);

        assert session != null;
	cmd = new Command(Command.SI, "projectadd");
	cmd.addOption(new Option("project", successConfigPath));
	fileName = Math.random()+".txt";
	testFile = testFolder.newFile(fileName);
	cmd.addOption(new Option("sourceFile", testFile.getAbsolutePath()));
	cmd.addOption(new Option("cpid", ":bypass"));
	cmd.addSelection(fileName);
	response = session.runCommand(cmd);
	assertEquals(response.getExitCode(),0);

	triggerSCMTrigger(localClientProject.getTrigger(SCMTrigger.class));

	TaskListener listener = StreamTaskListener.fromStderr();
	PollingResult poll = localClientProject.poll(listener);
	assertEquals(true, poll.hasChanges());
    }

    @Test
    public void testSandboxViewWhilePollingWithNoChanges()
		    throws Exception
    {
	build = build(localClientProject);
	jenkinsRule.assertBuildStatus(Result.SUCCESS, build);

	triggerSCMTrigger(localClientProject.getTrigger(SCMTrigger.class));

	TaskListener listener = StreamTaskListener.fromStderr();
	PollingResult poll = localClientProject.poll(listener);
	assertEquals(false, poll.hasChanges());
    }
}