package hudson.scm;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import hudson.model.AbstractBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.scm.api.command.CommandFactory;
import hudson.scm.api.command.IAPICommand;
import hudson.scm.api.session.APISession;
import hudson.scm.api.session.ISession;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ CommandFactory.class, IntegrityConfigurable.class, APISession.class})
@PowerMockIgnore({"javax.crypto.*" })
public class IntegritySCMTest extends AbstractIntegrityTestCase
{
	
	@Test
	public void testProjectUnlockMembers() throws Exception
	{
	    	IntegrityConfigurable fakeConfigObj = PowerMockito.mock(IntegrityConfigurable.class);
	    	
	    	PowerMockito.mockStatic(CommandFactory.class);
		PowerMockito.mockStatic(APISession.class);
		ISession api = FakeAPISession.create(fakeConfigObj);
		Mockito.when(APISession.create(fakeConfigObj)).thenReturn(api);
		
	    	IAPICommand unlockcommandMock = new MockAPICommand(fakeConfigObj, IAPICommand.UNLOCK_COMMAND);
	    	Mockito.when(CommandFactory.createCommand(IAPICommand.UNLOCK_COMMAND, fakeConfigObj)).thenReturn(unlockcommandMock);
	    	
	    	IAPICommand createCpcommandMock = new MockAPICommand(fakeConfigObj, IAPICommand.CREATE_CP_COMMAND);
	    	Mockito.when(CommandFactory.createCommand(IAPICommand.CREATE_CP_COMMAND, fakeConfigObj)).thenReturn(createCpcommandMock);
	    	
	    	IAPICommand pcicommandMock = new MockAPICommand(fakeConfigObj, IAPICommand.PROJECT_CHECKIN_COMMAND);
	    	Mockito.when(CommandFactory.createCommand(IAPICommand.PROJECT_CHECKIN_COMMAND, fakeConfigObj)).thenReturn(pcicommandMock);
	    	
	    	IAPICommand lockcommandMock = new MockAPICommand(fakeConfigObj, IAPICommand.LOCK_COMMAND);
	    	Mockito.when(CommandFactory.createCommand(IAPICommand.LOCK_COMMAND, fakeConfigObj)).thenReturn(lockcommandMock);
	    	
	    	IAPICommand closecpcommandMock = new MockAPICommand(fakeConfigObj, IAPICommand.CLOSE_CP_COMMAND);
	    	Mockito.when(CommandFactory.createCommand(IAPICommand.CLOSE_CP_COMMAND, fakeConfigObj)).thenReturn(closecpcommandMock);
	    	
	    	IAPICommand submitcpcommandMock = new MockAPICommand(fakeConfigObj, IAPICommand.SUBMIT_CP_COMMAND);
	    	Mockito.when(CommandFactory.createCommand(IAPICommand.SUBMIT_CP_COMMAND, fakeConfigObj)).thenReturn(submitcpcommandMock);
		
		
		//IntegrityCMMember.unlockMembers(fakeConfigObj, configPath);
		
		//IntegritySCM scm = new IntegritySCM("server1", configPath, "devjob");
		IntegritySCM scm = PowerMockito.mock(IntegritySCM.class);
		
	    	FreeStyleProject project = jenkinsRule.createFreeStyleProject();    	
	    	project.setScm(scm);
	    	project.save();
	    	
    		// create initial commit and then run the build against it:
    		final String commitFile1 = "commitFile1";
    		final String fielcontent = "Testing post build Check in action.";
    		commit(commitFile1,fielcontent, name1, "Commit number 1");
    		    
    		FreeStyleBuild build = build(project, Result.SUCCESS, commitFile1);
    		IntegrityCheckinTask ciTask = new IntegrityCheckinTask(configPath, ".", "*.*", "", (AbstractBuild<?, ?>)build, buildListener, fakeConfigObj);
    		
    		build.getWorkspace().act(ciTask);
    		setupPostBuildCheckIn(build);
		
		
	}
	
	//@Test
	public void testProjectSetup() throws Exception
	{
		FreeStyleProject project = setupIntegrityProject();
        
     // create initial commit and then run the build against it:
        final String commitFile1 = "commitFile1";
        final String fielcontent = "commitFile1";
        commit(commitFile1,fielcontent, name1, "Commit number 1");
	}
	
	//@Test
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
	
	//@Test
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
	
	//@Test
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