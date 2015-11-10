package hudson.scm.test;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.scm.IntegrityCMMember;
import hudson.scm.IntegrityCheckinTask;
import hudson.scm.IntegrityConfigurable;
import hudson.scm.IntegrityItemAction;
import hudson.scm.IntegritySCM;
import hudson.scm.IntegritySCM.DescriptorImpl;
import hudson.util.StreamTaskListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
//import org.mockito.Mockito;

import rpc.IntegrityException;

public abstract class AbstractIntegrityTestCase extends JenkinsRule{
	
	protected TaskListener listener;
	protected BuildListener buildListener;
	protected TestIntegrityRepo testRepo;
	protected File workDir;
	protected FilePath workspace;
	protected String name1;
	protected String name2;
	
	
	String serverConfig="developer@ppumsv-ipdc16d.ptcnet.ptc.com:7001";
	String userName="developer";
	String password="password";
	String configPath="#/Vipin";
	@Rule public JenkinsRule jenkinsRule = new JenkinsRule();
	@Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
	
	@Before
	public void setUp() throws Exception {
        try {
			 listener = StreamTaskListener.fromStderr();
			 File file = temporaryFolder.getRoot();
			 testRepo = new TestIntegrityRepo("unnamed", file, listener);
			 name1=testRepo.userName1;
			 name2=testRepo.userName2;
			 workDir = testRepo.IntegrityDir;
	         workspace = testRepo.IntegrityDirPath;
		}
        catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}     
    }
    
    protected void tearDown() throws Exception{
    	try{
        super.after();}
    	catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    protected void commit(final String fileName,final String fileContent, final String User1, final String message)
            throws IntegrityException, InterruptedException {
        testRepo.commit(fileName, fileContent,User1, message);
    }
    protected void commit(final String fileName, final String User2, final String message)
            throws IntegrityException, InterruptedException {
        testRepo.commit(fileName, User2, message);
    }
    
    protected void setupIntegrityConfigurable()
    {
    	IntegrityConfigurable configObj= new IntegrityConfigurable("server1", "ppumsv-ipdc16d.ptcnet.ptc.com", 7001, "ppumsv-ipdc16d.ptcnet.ptc.com", 7001, false, "developer", "password");
    	FakeAPISession api = FakeAPISession.create(configObj);
    	List<IntegrityConfigurable> configurations = new ArrayList<IntegrityConfigurable>();
    	configurations.add(configObj);
    	DescriptorImpl.INTEGRITY_DESCRIPTOR.setConfigurations(configurations);
    }
    
    protected FreeStyleProject setupProject() throws Exception
    {
    	IntegritySCM scm = new IntegritySCM("server1", configPath, "devjob");
    	FreeStyleProject project = jenkinsRule.createFreeStyleProject();    	
    	project.setScm(scm);
    	project.save();
    	return project;
    }    
    
    protected FreeStyleProject setupIntegrityProject() throws Exception
    {
    	setupIntegrityConfigurable();
    	FreeStyleProject project = setupProject();
    	return project;
    }
    
    protected void setupPostBuildCheckIn(AbstractBuild<?, ?> build) throws Exception
    {
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
		buildListener = new StreamBuildListener(out);
      	IntegrityConfigurable configObj = DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfigurations().get(0);		
    	IntegrityCheckinTask ciTask = new IntegrityCheckinTask("#/Vipin_main", ".", "*.*", "", build, buildListener, configObj);
    	build.getWorkspace().act(ciTask);
    }
    
    protected void setupPostBuildBuildItem(AbstractBuild<?, ?> build, boolean query) throws Exception
    {  	
    	String fieldQuery = "";
    	if (query == true)
    	{
    		fieldQuery = "(field[ID]=852)";
    	}
    	else
    	{
	    	EnvVars env = new EnvVars();
	        env = build.getEnvironment(listener);
	    	IntegrityTestParametersAction act = new IntegrityTestParametersAction("852");       
	        act.buildEnvVars(build, env);
	    	build.addAction(act);
    	}    	
    	
    	IntegrityItemAction itemAction = new IntegrityItemAction();
		itemAction.setServerConfig("server1");
		itemAction.setQueryDefinition(fieldQuery);
		itemAction.setStateField("Assigned User");
		itemAction.setSuccessValue("developer");
		itemAction.setFailureValue("administrator");
		itemAction.setLogField("Description");
		
		Launcher launcher = workspace.createLauncher(listener);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		buildListener = new StreamBuildListener(out);
		itemAction.perform(build, launcher, buildListener);
    }    
    
    protected FreeStyleBuild build(final FreeStyleProject project, final Result expectedResult, final String...expectedNewlyCommittedFiles) throws Exception {
       final FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause()).get();
    	
        System.out.println(build.getLog(50));
//        for(final String expectedNewlyCommittedFile : expectedNewlyCommittedFiles) {
//        	 assertTrue(expectedNewlyCommittedFile + " file not found in workspace", build.getWorkspace().child(expectedNewlyCommittedFile).exists());
//        }
//        if(expectedResult != null) {
//            assertBuildStatus(expectedResult, build);
//        }
        return build;
    }
    
    protected void testUnlockMembers() throws Exception
	{
    	IntegrityConfigurable configObj= new IntegrityConfigurable("server1", "ppumsv-ipdc16d.ptcnet.ptc.com", 7001, "ppumsv-ipdc16d.ptcnet.ptc.com", 7001, false, "developer", "password");
    	FakeAPISession api = FakeAPISession.create(configObj);
		IntegrityCMMember.unlockMembers(api, configPath);
	}
   
}
