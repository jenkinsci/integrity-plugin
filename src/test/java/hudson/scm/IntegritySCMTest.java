package hudson.scm;
import org.junit.Test;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;


public class IntegritySCMTest extends AbstractIntegrityTestCase
{
	
	@Test
	public void testProjectUnlockMembers() throws Exception
	{
		testUnlockMembers();
	}
	
	@Test
	public void testProjectSetup() throws Exception
	{
		FreeStyleProject project = setupIntegrityProject();
        
     // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        final String fielcontent = "commitFile1";
        commit(commitFile1,fielcontent, name1, "Commit number 1");
	}
	
	@Test
	public void testPostBuildCheckin() throws Exception
	{
		FreeStyleProject project = setupIntegrityProject();
     // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        final String fielcontent = "Testing post build Check in action.";
        commit(commitFile1,fielcontent, name1, "Commit number 1");
        
        FreeStyleBuild build = build(project, Result.SUCCESS, commitFile1);
        setupPostBuildCheckIn(build);
	}
	
	@Test
	public void testPostBuildWithItemIDFieldQuery() throws Exception
	{
		FreeStyleProject project = setupIntegrityProject();
     // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        final String fielcontent = "Testing post build with ItemID(Integrity-Workflow Item) as field query.";
        commit(commitFile1,fielcontent, name1, "Commit number 1");
        FreeStyleBuild build = build(project, Result.SUCCESS, commitFile1);
        setupPostBuildBuildItem(build, true);
	}
	
	@Test
	public void testPostBuildWithParameterizedItemID() throws Exception
	{
		FreeStyleProject project = setupIntegrityProject();
     // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        final String fielcontent = "Testing post build with parameterized ItemID(Integrity-Workflow Item).";
        commit(commitFile1,fielcontent, name1, "Commit number 1");
        FreeStyleBuild build = build(project, Result.SUCCESS, commitFile1);
        setupPostBuildBuildItem(build, false);
	}	
	
}