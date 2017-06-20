package hudson.scm.localclient;

import hudson.model.Result;
import hudson.scm.IntegritySCMTest;
import hudson.scm.PollingResult;
import hudson.triggers.SCMTrigger;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.*;

/**
 * Created by asen on 15-06-2017.
 */
public class IntegrityViewSandboxTaskTest extends IntegritySCMTest
{

    private ExecutorService singleThreadExecutor;

    @Before
    public void setUp() throws Exception {
	//expectChanges = false;
	super.setUp();
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
	build = build(localClientProject, Result.SUCCESS);
	jenkinsRule.assertBuildStatus(Result.SUCCESS, build);

	addTestFileInSource(null);
	triggerSCMTrigger(localClientProject.getTrigger(SCMTrigger.class));

	PollingResult poll = localClientProject.poll(listener);
	assertTrue(poll.hasChanges());
    }

    @Test
    public void testSandboxViewWhilePollingWithNoChanges()
		    throws Exception
    {
	build = build(localClientProject, Result.SUCCESS);
	jenkinsRule.assertBuildStatus(Result.SUCCESS, build);

	triggerSCMTrigger(localClientProject.getTrigger(SCMTrigger.class));

	PollingResult poll = localClientProject.poll(listener);
	assertFalse(poll.hasChanges());
    }
}