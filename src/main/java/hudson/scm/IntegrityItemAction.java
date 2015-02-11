package hudson.scm;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.scm.IntegritySCM.DescriptorImpl;
import hudson.tasks.Publisher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.Extension;
import hudson.Launcher;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.test.TestResult;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.AggregatedTestResultAction;
import hudson.tasks.test.AggregatedTestResultAction.ChildReport;
import hudson.util.ListBoxModel;
import hudson.util.Secret;

import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import net.sf.json.JSONObject;

import com.mks.api.Command;
import com.mks.api.MultiValue;
import com.mks.api.Option;
import com.mks.api.response.APIException;
import com.mks.api.response.Field;
import com.mks.api.response.Item;
import com.mks.api.response.Response;
import com.mks.api.response.WorkItemIterator;

public class IntegrityItemAction extends Notifier implements Serializable
{
	private static final long serialVersionUID = 7067049279037277420L;
	private static final Logger LOGGER = Logger.getLogger("IntegritySCM");
	private String serverConfig;
	private String queryDefinition;
	private String stateField;
	private String successValue;
	private String failureValue;
	private String logField;
	private String testSessionField;
	private String testSessionStateField;
	private String testSessionActiveState;
	private String testSessionTestsField;
	private String testCaseTestNameField;
	private String testSuiteContainsField;
	private String testPassedVerdictName;
	private String testFailedVerdictName;
	private String testSkippedVerdictName;
	
	@Extension
	public static final IntegrityItemDescriptorImpl ITEM_DESCRIPTOR = new IntegrityItemDescriptorImpl();

	/**
	 * Returns the simple server configuration name
	 * @return
	 */
    public String getServerConfig()
    {
    	return serverConfig;
    }
	
	/**
	 * Returns the query definition expression
	 * @return Query Definition
	 */
	public String getQueryDefinition()
	{
		return queryDefinition;
	}

	/**
	 * Returns the status/state field for the "build" item
	 * @return
	 */
	public String getStateField()
	{
		return stateField;
	}
	
	/**
	 * Returns the success value that will be set when the build is a success
	 * @return
	 */
	public String getSuccessValue()
	{
		return successValue;
	}
	
	/**
	 * Returns the failure value that will be set when the build has failed
	 * @return
	 */
	public String getFailureValue()
	{
		return failureValue;
	}
	
	/**
	 * Returns the log field associated with the "build" item
	 * @return
	 */
	public String getLogField()
	{
		return logField;
	}
	
	/**
	 * Returns the Test Session relationship field for the "build" item
	 * @return
	 */
	public String getTestSessionField()
	{
		return testSessionField;
	}

	/**
	 * Returns the State field for a Test Session item
	 * @return
	 */
	public String getTestSessionStateField()
	{
		return testSessionStateField;
	}
	
	/**
	 * Returns the active State for a Test Session item
	 * @return
	 */
	public String getTestSessionActiveState()
	{
		return testSessionActiveState;
	}
	
	/**
	 * Returns the Tests field associated with the Test Session item
	 * @return
	 */
	public String getTestSessionTestsField()
	{
		return testSessionTestsField;
	}
	
	/**
	 * Returns the JUnit Test Name or External ID field for the Test Case
	 * @return
	 */
	public String getTestCaseTestNameField()
	{
		return testCaseTestNameField;
	}

    /**
	 * Returns the name of the 'Contains' Test Suite structural relationship field
	 * @return
	 */
    public String getTestSuiteContainsField()
    {
    	return testSuiteContainsField;
    }

    /**
	 * Returns the name for the 'Passed' verdict
	 * @return
	 */
    public String getTestPassedVerdictName()
    {
    	return testPassedVerdictName;
    }

    /**
	 * Returns the name for the 'Failed' verdict
	 * @return
	 */
    public String getTestFailedVerdictName()
    {
    	return testFailedVerdictName;
    }

    /**
	 * Returns the name for the 'Skipped' verdict
	 * @return
	 */
    public String getTestSkippedVerdictName()
    {
    	return testSkippedVerdictName;
    }
    
    /**
     * Sets the simple server configuration name
     * @param serverConfig
     */
    public void setServerConfig(String serverConfig)
    {
    	this.serverConfig = serverConfig;
    }
    	
	/**
	 * Sets the query definition expression to obtain the build item
	 * @param queryDefinition Query Definition Expression
	 */
	public void setQueryDefinition(String queryDefinition)
	{
		this.queryDefinition = queryDefinition;
	}

	/**
	 * Sets the status/state field for the "build" item
	 * @param stateField Status/State field
	 */
	public void setStateField(String stateField)
	{
		this.stateField = stateField;
	}
	
	/**
	 * Sets the success value that will be used when the build is a success
	 * @param successValue Value to be set when the build is a success
	 */
	public void setSuccessValue(String successValue)
	{
		this.successValue = successValue;
	}
	
	/**
	 * Sets the failure value that will be set when the build has failed
	 * @param failureValue Value to be set when the build has failed
	 */
	public void setFailureValue(String failureValue)
	{
		this.failureValue = failureValue;
	}
	
	/**
	 * Sets the log field associated with the "build" item
	 * @param logField Log field that is used to store the build log
	 */
	public void setLogField(String logField)
	{
		this.logField = logField;
	}
	
	/**
	 * Sets the Test Session relationship field for the "build" item
	 * @return
	 */
	public void setTestSessionField(String testSessionField)
	{
		this.testSessionField = testSessionField;
	}

	/**
	 * Sets the State field for a Test Session item
	 * @return
	 */
	public void setTestSessionStateField(String testSessionStateField)
	{
		this.testSessionStateField = testSessionStateField;
	}
	
	/**
	 * Sets the active State for a Test Session item
	 * @return
	 */
	public void setTestSessionActiveState(String testSessionActiveState)
	{
		this.testSessionActiveState = testSessionActiveState;
	}
	
	/**
	 * Sets the Tests field associated with the Test Session item
	 * @return
	 */
	public void setTestSessionTestsField(String testSessionTestsField)
	{
		this.testSessionTestsField = testSessionTestsField;
	}
	
	/**
	 * Sets the JUnit Test Name or External ID field for the Test Case
	 * @return
	 */
	public void setTestCaseTestNameField(String testCaseTestNameField)
	{
		this.testCaseTestNameField = testCaseTestNameField;
	}
	
	/**
	 * Sets the sunfire reports folder for JUnit test results
	 * @param testResultsDir
	 */
	public void setTestSuiteContainsField(String testSuiteContainsField)
	{
		this.testSuiteContainsField = testSuiteContainsField;
	}
		
    /**
	 * Sets the name for the 'Passed' verdict
	 * @return
	 */
    public void setTestPassedVerdictName(String testPassedVerdictName)
    {
    	this.testPassedVerdictName = testPassedVerdictName;
    }

    /**
	 * Sets the name for the 'Failed' verdict
	 * @return
	 */
    public void setTestFailedVerdictName(String testFailedVerdictName)
    {
    	this.testFailedVerdictName = testFailedVerdictName;
    }

    /**
	 * Sets the name for the 'Skipped' verdict
	 * @return
	 */
    public void setTestSkippedVerdictName(String testSkippedVerdictName)
    {
    	this.testSkippedVerdictName = testSkippedVerdictName;
    }
    
	/**
	 * Obtains the root project for the build
	 * @param abstractProject
	 * @return
	 */
	private AbstractProject<?,?> getRootProject(AbstractProject<?,?> abstractProject)
	{
		if (abstractProject.getParent() instanceof Hudson)
		{
			return abstractProject;
		}
		else
		{
			return getRootProject((AbstractProject<?,?>) abstractProject.getParent());
		}
	}
	
    /**
     * Wrapper function to edit a specific Integrity Build Item with a status and log
     * @param build Jenkins abstract build item
     * @param listener Build listener
     * @param api Integrity API Session
     * @param buildItemID Integrity Item ID
     * @throws IOException
     * @throws APIException
     */
    private boolean editBuildItem(AbstractBuild<?, ?> build, BuildListener listener, APISession api, String buildItemID) throws IOException, APIException
    {
		// Setup the edit item command to update the build item with the results of the build
		Command editIssue = new Command(Command.IM, "editissue");
		// Load up the build log, if required
		if( null != logField && logField.length() > 0 )
		{
			StringWriter writer = new StringWriter();
			build.getLogText().writeHtmlTo(0, writer);
			writer.flush();
			writer.close();
			// Rid the log of NUL characters as it will blow up the im editissue command
			String log = writer.getBuffer().toString().replace((char)0, ' ');
			log = log.replaceAll(IntegritySCM.NL, "<br>");
			MultiValue mvLog = new MultiValue("=");
			mvLog.add(logField);
			mvLog.add(log);
			editIssue.addOption(new Option("richContentField", mvLog));
		}
		
		// Lets update the build item based on the success/failure of the build
		MultiValue mvState = new MultiValue("=");
		mvState.add(stateField);
		if( Result.SUCCESS.equals(build.getResult()) )
		{
			// Successful build update
			listener.getLogger().println("Preparing to update item '" + buildItemID + "' with value " + stateField + " = " + successValue);
			mvState.add(successValue);
		}
		else
		{
			// Failed build update
			listener.getLogger().println("Preparing to update item '" + buildItemID + "' with values " + stateField + " = " + failureValue);
			mvState.add(failureValue);
		}
		editIssue.addOption(new Option("field", mvState));
		editIssue.addSelection(buildItemID);	    			

		// Finally execute the edit item command
		Response editIssueResponse = api.runCommand(editIssue);
		int returnCode = editIssueResponse.getExitCode();
		LOGGER.fine(editIssueResponse.getCommandString() + " returned " + returnCode);        					
		listener.getLogger().println("Updated build item '" + buildItemID + "' with build status!");
		
		return (returnCode == 0 ? true :  false);
    }

    /**
     * Executes the Integrity edit test result command for given Test Session and Test Case
     * @param api Integrity API Session
     * @param annotation Test Result Annotation
     * @param verdict Test Result Verdict
     * @param testSessionID Test Session ID
     * @param testCaseID Test Case ID
     * @throws APIException 
     */
    private void editTestResult(APISession api, String annotation, String verdict, String testSessionID, String testCaseID) throws APIException
    {
		// Setup the edit test result command
		Command editresult = new Command(Command.TM, "editresult");
		editresult.addOption(new Option("annotation", annotation));		
		editresult.addOption(new Option("verdict", verdict));
		editresult.addOption(new Option("forceCreate"));
		editresult.addOption(new Option("sessionID", testSessionID));
		editresult.addSelection(testCaseID);
		
		// Update Integrity for this Test Case result
		try
		{
			LOGGER.fine("Attempting to update test result for Integrity Test - " + testCaseID);
			api.runCommand(editresult);
		}
		catch (APIException aex)
		{
			LOGGER.fine("Caught API Exception...");
			LOGGER.log(Level.SEVERE, "APIException", aex);
			
			ExceptionHandler eh = new ExceptionHandler(aex);
			LOGGER.fine(eh.getMessage());
			String message = eh.getMessage();
			if( message.indexOf("MKS124746") > 0 || (message.indexOf("result for test case") > 0 && message.indexOf("does not exist") > 0) )
			{
				LOGGER.fine(eh.getCommand() + " returned exit code " + eh.getExitCode());
				LOGGER.fine(eh.getMessage());
				LOGGER.warning("An attempt was made to update a Test Result for non-meaningful content.  Perhaps you've incorrectly configured your Tests?");
			}
			else
			{
				throw aex;
			}
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Exception", e);
		}

    }
    
    /**
     * Converts a Package.class.test style Test Case ID to a JUnit Test ID
     * @param testCaseID Fully qualified Test Case ID
     * @return
     */
    private String getJUnitID(String testCaseID)
    {
    	// This ID is already in the JUnit ID (or other test type) format
    	if( testCaseID.indexOf("/") > 0 )
    	{
    		return testCaseID;
    	}
    	else
    	{
    		// Lets convert the package.class.test to a JUnit ID format
    		StringBuilder junitTestCaseID = new StringBuilder("junit/");
    		
    		String[] tokens = testCaseID.split("\\.");
    		
    		// Root package test
    		if( tokens.length == 2 && testCaseID.indexOf('.') == testCaseID.lastIndexOf('.') )
    		{
    			junitTestCaseID.append("(root)/" + testCaseID.replace('.', '/'));
    			return junitTestCaseID.toString();
    		}
    		
    		// Package name test
    		else if( tokens.length > 2 )
    		{
    			for( int i = 0; i < tokens.length; i++ )
    			{
    				junitTestCaseID.append(tokens[i]);
    				if( i < (tokens.length-1) )
    				{
    					junitTestCaseID.append(i >= (tokens.length-3) ? "/" : ".");
    				}
    			}
    			return junitTestCaseID.toString();
    		}

    		else
    		{
        		// Seems like a root package type test, but there is no test name - very odd!
    			LOGGER.warning("Invalid format for Test Case ID - should be in the format <packagename>.<classname>.<testname>!");
    			junitTestCaseID.append("(root)/" + testCaseID);
    			return junitTestCaseID.toString();
    		}
    	}
    }
    
    /**
     * Recursively update the Test Results for all Test Cases that have an 'External ID' populated
     * @param testResult AbstractTestResultAction object
     * @param listener BuildListener
     * @param api Integrity API Session
     * @param testSessionID Integrity Test Session ID
     * @param testCaseList A list of Integrity Test Cases
     * @throws APIException
     */
	@SuppressWarnings("unchecked")
	private void updateTestResult(TestResult testResult, BuildListener listener, APISession api, String testSessionID, Response walkResponse, List<Item> testCaseList) throws APIException
    {
		// Look for the specific Tests we're interested in...
		for( Item test : testCaseList  )
		{ 
			Field testCaseIDFld = null;
			Field containsFld = null;
			try
			{
				// This should work on a 10.5 and prior...
				testCaseIDFld = test.getField(testCaseTestNameField);
				containsFld = test.getField(testSuiteContainsField);
			}
			catch( NoSuchElementException nsee )
			{
				// 10.6 and beyond...
				testCaseIDFld = walkResponse.getWorkItem(test.getId()).getField(testCaseTestNameField);
				containsFld = walkResponse.getWorkItem(test.getId()).getField(testSuiteContainsField);
			}
			
			if( null != testCaseIDFld && null != testCaseIDFld.getValueAsString() )
			{
				String testCaseID = testCaseIDFld.getValueAsString();
				String junitTestName = getJUnitID(testCaseID);
				LOGGER.fine("Looking for external test " + testCaseID + " internal JUnit ID " + junitTestName);
				TestResult caseResult = testResult.findCorrespondingResult(junitTestName);
				// Update Integrity only if we find a matching Test Case Result
				if( null != caseResult )
				{
					// Execute the edit test result command
					LOGGER.fine("Located internal JUnit Test - " + junitTestName);
					editTestResult(api, (caseResult.isPassed() ? "Test " + caseResult.getId() + " has passed" : caseResult.getErrorDetails()), 
									(caseResult.isPassed() ? testPassedVerdictName : testFailedVerdictName), testSessionID, test.getId());
				}
				else
				{
					// Lets mark the Test Result as skipped for this Test Case
					LOGGER.warning("Could not locate internal JUnit Test - " + junitTestName);
					editTestResult(api, "Test " + getJUnitID(testCaseID) + " was not run!", testSkippedVerdictName, testSessionID, test.getId());
				}
			}
			
			// Process the next level of Test Cases
			if( null != containsFld && null != containsFld.getList() )
			{
				updateTestResult(testResult, listener, api, testSessionID, walkResponse, containsFld.getList());
			}
		}
    }
    
	/**
	 * Returns the first Test Result encountered in an Aggregated Test Result Action
	 * @param atr Aggregated Test Result Action
	 * @return
	 */
	private TestResult getTestResult(AggregatedTestResultAction testResultAction)
	{
		List<ChildReport> cList = testResultAction.getChildReports();
        for (ChildReport childReport : cList) 
        {
            if (childReport.result instanceof TestResult) 
            {
            	TestResult testResult = (TestResult) childReport.result;            	
        		LOGGER.fine("Total tests run: " + testResult.getTotalCount());
        		LOGGER.fine("Total passed count: " + testResult.getPassCount());
        		LOGGER.fine("Total failed count: " + testResult.getFailCount());
        		LOGGER.fine("Total skipped count: " + testResult.getSkipCount());
        		LOGGER.fine("Failed Test Details:");
        		Iterator<? extends TestResult> failedResultIterator = testResult.getFailedTests().iterator();
        		while( failedResultIterator.hasNext() )
        		{
        			TestResult caseResult = failedResultIterator.next();
        			LOGGER.fine("ID: " + caseResult.getId() + " " + caseResult.getErrorDetails());	
        		}
            	
                return testResult;
            }
        }	        			
        return null;
	}
	
	/**
	 * Collects the Test Results based on the list of Tests contained within an Integrity Test Session
	 * @param testResultAction AggregatedTestResultAction
	 * @param listener BuildListener
	 * @param api Integrity API Session
	 * @param testSessionID Integrity Test Session ID
	 * @return
	 * @throws APIException
	 */
    @SuppressWarnings("unchecked")
	private boolean collectTestResults(AggregatedTestResultAction testResultAction, BuildListener listener, APISession api, String testSessionID) throws APIException
    {
		// Read the Test Case relationships from the Test Session
		Command walk = new Command(Command.IM, "relationships");
		walk.addOption(new Option("fields", testCaseTestNameField));
		MultiValue traverseFields = new MultiValue(",");
		traverseFields.add(testSessionTestsField);
		traverseFields.add(testSuiteContainsField);
		walk.addOption(new Option("traverseFields", traverseFields));
		walk.addSelection(testSessionID);
		Response walkResponse = api.runCommand(walk);
		if( null != walkResponse )
		{
			Field testSessionFld = walkResponse.getWorkItem(testSessionID).getField(testSessionTestsField);
			if( null != testSessionFld && null != testSessionFld.getList() )
			{
				updateTestResult(getTestResult(testResultAction), listener, api, testSessionID, walkResponse, testSessionFld.getList()); 
			}
		}				
		
		// If we got here then everything went as planned!
		return true;
    }
    
	/**
	 * Gets the project specific user/password for this build
	 * @param thisBuild Jenkins AbstractBuild
	 * @return
	 */
	private IntegrityConfigurable getProjectSettings(AbstractBuild<?,?> thisBuild) 
	{
		IntegrityConfigurable desSettings = DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfig);
		IntegrityConfigurable ciSettings = new IntegrityConfigurable("TEMP_ID", desSettings.getIpHostName(), desSettings.getIpPort(), desSettings.getHostName(), 
																		desSettings.getPort(), desSettings.getSecure(), "", "");		
		AbstractProject<?,?> thisProject = thisBuild.getProject();
		if( thisProject.getScm() instanceof IntegritySCM )
		{
			String userName = ((IntegritySCM)thisProject.getScm()).getUserName();
			ciSettings.setUserName(userName);
			LOGGER.fine("IntegrityItemAction - Project Userame = " + userName);
			
			Secret password = ((IntegritySCM)thisProject.getScm()).getSecretPassword();
			ciSettings.setPassword(password.getEncryptedValue());
			LOGGER.fine("IntegrityItemAction - Project User password = " + password.getEncryptedValue());
		}
		else
		{
			LOGGER.severe("IntegrityItemAction - Failed to initialize project specific connection settings!");
			return desSettings;
		}
		
		return ciSettings;
	}
	
	/**
	 * Executes the actual Integrity Update Item operation
	 */
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException
	{
		boolean success = true;
		AbstractProject<?,?> rootProject = getRootProject(build.getProject());
		if( !(rootProject.getScm() instanceof IntegritySCM) )
		{
			listener.getLogger().println("Integrity Item update is being executed for an invalid context!  Current SCM is " + rootProject.getScm() + "!");
			return true;
		}

		APISession api = APISession.create(getProjectSettings(build));
		if( null != api )
		{
        	try
        	{
        		// First lets find the build item or test session id
        		int intBuildItemID = 0;
        		int intTestSessionID = 0;

        		String buildItemID = build.getEnvironment(listener).get("ItemID", "");
        		// Convert the string Item ID to an integer for comparison...
        		try { intBuildItemID = Integer.parseInt(buildItemID); }
        		catch( NumberFormatException nfe ){ intBuildItemID = 0; }

        		String testSessionID = build.getEnvironment(listener).get("SessionID", "");
        		// Convert the string Item ID to an integer for comparison...
        		try { intTestSessionID = Integer.parseInt(testSessionID); }
        		catch( NumberFormatException nfe ){ intTestSessionID = 0; }

        		// Figure out if we need to query Integrity for the Build item
        		if( intBuildItemID <= 0 )
        		{
        			if( queryDefinition.length() > 0 )
        			{
	        			// Let's query for the build item id
		        		Command issues = new Command(Command.IM, "issues");
		        		issues.addOption(new Option("fields", "ID"));
		        		issues.addOption(new Option("queryDefinition", queryDefinition));
		        		Response issuesResponse = api.runCommand(issues);
		        		if( null != issuesResponse )
		        		{
		        			WorkItemIterator wit = issuesResponse.getWorkItems();
		        			// Get the first item returned by the query definition
		        			if( wit.hasNext() )
		        			{
		        				buildItemID = wit.next().getField("ID").getValueAsString();
		    	        		try { intBuildItemID = Integer.parseInt(buildItemID); }
		    	        		catch( NumberFormatException nfe ){ intBuildItemID = 0; }			        				
		        			}
		        			else
		        			{
		        				listener.getLogger().println("Cannot find an Integrity Build Item!  Response from executing custom query is null!");
		        				return false;
		        			}
		        		}
		        		else
		        		{
		        			listener.getLogger().println("Cannot find an Integrity Build Item!  Response from executing custom query is null!");
		        			return false;
		        		}
        			}
	        		else
	        		{
	        			listener.getLogger().println("WARNING: No configuration information provided to locate an Integrity Build Item!");
	        		}	        			
        		}
        		
        		// Figure out if we need to do anything with the Test Results of this build...
        		AbstractTestResultAction<?> testResult = build.getAction(AbstractTestResultAction.class);
        		if( null != testResult && testResult.getTotalCount() > 0 )
        		{	        			
	        		// Figure out if we need to interrogate the Build item for the Test Session item
	        		if( intTestSessionID <= 0 && testSessionField.length() > 0 && intBuildItemID > 0 )
	        		{
	        			// Get the relationships for the Build item
	        			Command walk = new Command(Command.IM, "relationships");
	        			walk.addOption(new Option("fields", testSessionStateField));
	        			walk.addOption(new Option("traverseFields", testSessionField));
	        			walk.addSelection(buildItemID);
	        			Response walkResponse = api.runCommand(walk);
	        			if( null != walkResponse )
	        			{
	        				Field testSessionFld = walkResponse.getWorkItem(buildItemID).getField(testSessionField);
	        				if( null != testSessionFld && null != testSessionFld.getList() )
	        				{
	        					@SuppressWarnings("unchecked")
	        					List<Item> sessionList = testSessionFld.getList(); 
	        					for( Item session : sessionList  )
	        					{
	        						// Look for the first Test Session in an Active state...
	        						Field stateField = null;
	        						try
	        						{
	        							// This should work on a 10.5 and prior...
	        							stateField = session.getField(testSessionStateField);
	        						}
	        						catch( NoSuchElementException nsee )
	        						{
	        							// 10.6 and beyond...
	        							stateField = walkResponse.getWorkItem(session.getId()).getField(testSessionStateField);
	        						}
	        						
        							if( null != stateField && testSessionActiveState.equals(stateField.getValueAsString()) )
        							{
        								testSessionID = session.getId();
        								try { intTestSessionID = Integer.parseInt(testSessionID); }
        								catch( NumberFormatException nfe ){ intTestSessionID = 0; }		        							
        								break;
        							}	        						
	        					}
	        				}
	        			}	        			
	        		}
	        		
	        		// Update the Test Session with the results from the JUnit tests, if we got a Test Session to work with...
	        		if( intTestSessionID > 0 )
	        		{
	        			listener.getLogger().println("Obtained Integrity Test Session Item '" + testSessionID + "' from build environment!");
	        			success = collectTestResults(build.getAction(AggregatedTestResultAction.class), listener, api, testSessionID);
	        			listener.getLogger().println("Updated Integrity Test Session Item '" + testSessionID + "' with results from automated test execution!");
	        		}
        		}
        		
        		// Finally, lets update the status of the build, if appropriate
        		if( intBuildItemID > 0 )
        		{
        			listener.getLogger().println("Obtained Integrity Build Item '" + buildItemID + "' from build environment!");	        			
        			success = editBuildItem(build, listener, api, buildItemID);
        		}
        		
        	}
        	catch(APIException aex)
        	{
            	LOGGER.severe("API Exception caught...");
            	ExceptionHandler eh = new ExceptionHandler(aex);
	        	aex.printStackTrace(listener.fatalError(aex.getMessage()));            	
            	LOGGER.severe(eh.getMessage());
            	LOGGER.fine(eh.getCommand() + " returned exit code " + eh.getExitCode());
            	return false;
	        }
        	finally
        	{
        		api.Terminate();
        	}			
		}
		else
		{
			LOGGER.severe("An API Session could not be established!  Cannot update Integrity Build Item!");
			listener.getLogger().println("An API Session could not be established!  Cannot update Integrity Build Item!");
			return false;
		}

		return success;
	}

	/**
	 * Toggles whether or not this needs to run after build is finalized
	 */
	@Override
	public boolean needsToRunAfterFinalized()
	{
		return false;
	}

	/**
	 * Returns the build step we're monitoring
	 */
	public BuildStepMonitor getRequiredMonitorService()
	{
		return BuildStepMonitor.BUILD;
	}

	/**
	 * Return the instance of DescriptorImpl object for this class
	 */
	@Override
	public BuildStepDescriptor<Publisher> getDescriptor()
	{
		return ITEM_DESCRIPTOR;
	}
	
	/**
	 * The relationship of Descriptor and IntegrityItemAction (the describable) is akin to class and object.
	 * This means the descriptor is used to create instances of the describable.
	 * Usually the Descriptor is an internal class in the IntegrityItemAction class named DescriptorImpl. 
	 */
    public static class IntegrityItemDescriptorImpl extends BuildStepDescriptor<Publisher> 
    {
    	public static final String defaultQueryDefinition = "((field[Type] = \"Build Request\") and (field[State] = \"Approved\"))";
    	public static final String defaultTestSuiteContainsField = "Contains";
    	public static final String defaultTestPassedVerdictName = "Passed";
    	public static final String defaultTestFailedVerdictName = "Failed";
    	public static final String defaultTestSkippedVerdictName = "Skipped";
    			
    	public IntegrityItemDescriptorImpl()
    	{
        	// Log the construction...
    		super(IntegrityItemAction.class);
			load();
        	LOGGER.fine("IntegrityItemAction.IntegrityItemDescriptorImpl() constructed!");        	            
    	}

		@Override
		public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException
		{
			IntegrityItemAction itemAction = new IntegrityItemAction();
			itemAction.setServerConfig(formData.getString("serverConfig"));
			itemAction.setQueryDefinition(formData.getString("queryDefinition"));
			itemAction.setStateField(formData.getString("stateField"));
			itemAction.setSuccessValue(formData.getString("successValue"));
			itemAction.setFailureValue(formData.getString("failureValue"));
			itemAction.setLogField(formData.getString("logField"));			
			itemAction.setTestSessionField(formData.getString("testSessionField"));
			itemAction.setTestSessionStateField(formData.getString("testSessionStateField"));
			itemAction.setTestSessionActiveState(formData.getString("testSessionActiveState"));
			itemAction.setTestSessionTestsField(formData.getString("testSessionTestsField"));
			itemAction.setTestCaseTestNameField(formData.getString("testCaseTestNameField"));
			itemAction.setTestSuiteContainsField(formData.getString("testSuiteContainsField"));
			itemAction.setTestPassedVerdictName(formData.getString("testPassedVerdictName"));
			itemAction.setTestFailedVerdictName(formData.getString("testFailedVerdictName"));
			itemAction.setTestSkippedVerdictName(formData.getString("testSkippedVerdictName"));
			
			LOGGER.fine("IntegrityItemAction.IntegrityItemDescriptorImpl.newInstance() executed!");   
			return itemAction;
		}    	
    	
		@Override    	
        public String getDisplayName() 
        {
            return "Integrity - Workflow Item";
        }

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException
		{
			save();
			LOGGER.fine("IntegrityItemAction.IntegrityItemDescriptorImpl.configure() executed!");
			return super.configure(req, formData);
		}

		public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType)
		{
			LOGGER.fine("IntegrityItemAction.IntegrityItemDescriptorImpl.isApplicable executed!");
			return true;
		}

		/**
		 * Provides a list box for users to choose from a list of Integrity Server configurations
		 * @param configuration Simple configuration name
		 * @return
		 */
		public ListBoxModel doFillServerConfigItems(@QueryParameter String serverConfig)
		{
			return DescriptorImpl.INTEGRITY_DESCRIPTOR.doFillServerConfigItems(serverConfig);
		}
		
	    /**
	     * Returns the default query definition that will be used to find the 'build' item
	     * @return defaultQueryDefinition
	     */
		public String getQueryDefinition()
		{
			return IntegrityItemDescriptorImpl.defaultQueryDefinition;
		}
		
		/**
		 * Returns the default name for the Integrity 'Contains' field
		 * @return
		 */
		public String getTestSuiteContainsField()
		{
			return IntegrityItemDescriptorImpl.defaultTestSuiteContainsField;
		}
		
		/**
		 * Returns the default name for the Integrity 'Passed' verdict
		 * @return
		 */
		public String getTestPassedVerdictName()
		{
			return IntegrityItemDescriptorImpl.defaultTestPassedVerdictName;
		}

		/**
		 * Returns the default name for the Integrity 'Failed' verdict
		 * @return
		 */
		public String getTestFailedVerdictName()
		{
			return IntegrityItemDescriptorImpl.defaultTestFailedVerdictName;
		}
		
		/**
		 * Returns the default name for the Integrity 'Skipped' verdict
		 * @return
		 */
		public String getTestSkippedVerdictName()
		{
			return IntegrityItemDescriptorImpl.defaultTestSkippedVerdictName;
		}		
    }
}
