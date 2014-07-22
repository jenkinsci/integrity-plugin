package hudson.scm;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;

import hudson.tasks.Publisher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.test.TestResult;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.AggregatedTestResultAction;
import hudson.tasks.test.AggregatedTestResultAction.ChildReport;

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
import com.mks.api.util.Base64;

public class IntegrityItemAction extends Notifier implements Serializable, IntegrityConfigurable
{
	private static final long serialVersionUID = 7067049279037277420L;
	private String host;
	private int port;
	private boolean secure;
    private String userName;
    private String password;
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

    public String getHost()
    {
    	return host;
    }

    public int getPort()
    {
    	return port;
    }

    public boolean getSecure()
    {
    	return secure;
    }

    public String getUserName()
    {
    	return userName;
    }

    public String getPassword()
    {
    	return APISession.ENC_PREFIX + password;
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
		if( testSuiteContainsField == null || testSuiteContainsField.length() == 0 )
		{
			testSuiteContainsField = ITEM_DESCRIPTOR.getDefaultTestSuiteContainsField();
		}
		
    	return testSuiteContainsField;
    }

    /**
	 * Returns the name for the 'Passed' verdict
	 * @return
	 */
    public String getTestPassedVerdictName()
    {
		if( testPassedVerdictName == null || testPassedVerdictName.length() == 0 )
		{
			testPassedVerdictName = ITEM_DESCRIPTOR.getDefaultTestPassedVerdictName();
		}
		
    	return testPassedVerdictName;
    }

    /**
	 * Returns the name for the 'Failed' verdict
	 * @return
	 */
    public String getTestFailedVerdictName()
    {
		if( testFailedVerdictName == null || testFailedVerdictName.length() == 0 )
		{
			testFailedVerdictName = ITEM_DESCRIPTOR.getDefaultTestFailedVerdictName();
		}
		
    	return testFailedVerdictName;
    }

    /**
	 * Returns the name for the 'Skipped' verdict
	 * @return
	 */
    public String getTestSkippedVerdictName()
    {
		if( testSkippedVerdictName == null || testSkippedVerdictName.length() == 0 )
		{
			testSkippedVerdictName = ITEM_DESCRIPTOR.getDefaultTestSkippedVerdictName();
		}
		
    	return testSkippedVerdictName;
    }
    
    public void setHost(String host)
    {
    	this.host = host;
    }
    
    public void setPort(int port)
    {
    	this.port = port;
    }

    public void setSecure(boolean secure)
    {
    	this.secure = secure;
    }

    public void setUserName(String userName)
    {
    	this.userName = userName;
    }
    
    public void setPassword(String password)
    {
    	if( password.indexOf(APISession.ENC_PREFIX) == 0 )
    	{
    		this.password = Base64.encode(Base64.decode(password.substring(APISession.ENC_PREFIX.length())));
    	}
    	else
    	{
    		this.password = Base64.encode(password);
    	}
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
		Logger.debug(editIssueResponse.getCommandString() + " returned " + returnCode);        					
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
			Logger.debug("Attempting to update test result for Integrity Test - " + testCaseID);
			api.runCommand(editresult);
		}
		catch (APIException aex)
		{
			Logger.debug("Caught API Exception...");
			Logger.debug(aex);
			
			ExceptionHandler eh = new ExceptionHandler(aex);
			Logger.debug(eh.getMessage());
			String message = eh.getMessage();
			if( message.indexOf("MKS124746") > 0 || (message.indexOf("result for test case") > 0 && message.indexOf("does not exist") > 0) )
			{
				Logger.debug(eh.getCommand() + " returned exit code " + eh.getExitCode());
				Logger.debug(eh.getMessage());
				Logger.warn("An attempt was made to update a Test Result for non-meaningful content.  Perhaps you've incorrectly configured your Tests?");
			}
			else
			{
				throw aex;
			}
		}
		catch (Exception e)
		{
			Logger.fatal(e);
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
    			Logger.warn("Invalid format for Test Case ID - should be in the format <packagename>.<classname>.<testname>!");
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
	private void updateTestResult(TestResult testResult, BuildListener listener, APISession api, String testSessionID, List<Item> testCaseList) throws APIException
    {
		// Look for the specific Tests we're interested in...
		for( Item test : testCaseList  )
		{ 
			Field testCaseIDFld = test.getField(testCaseTestNameField);
			if( null != testCaseIDFld && null != testCaseIDFld.getValueAsString() )
			{
				String testCaseID = testCaseIDFld.getValueAsString();
				String junitTestName = getJUnitID(testCaseID);
				Logger.debug("Looking for external test " + testCaseID + " internal JUnit ID " + junitTestName);
				TestResult caseResult = testResult.findCorrespondingResult(junitTestName);
				// Update Integrity only if we find a matching Test Case Result
				if( null != caseResult )
				{
					// Execute the edit test result command
					Logger.debug("Located internal JUnit Test - " + junitTestName);
					editTestResult(api, (caseResult.isPassed() ? "Test " + caseResult.getId() + " has passed" : caseResult.getErrorDetails()), 
									(caseResult.isPassed() ? testPassedVerdictName : testFailedVerdictName), testSessionID, test.getId());
				}
				else
				{
					// Lets mark the Test Result as skipped for this Test Case
					Logger.warn("Could not locate internal JUnit Test - " + junitTestName);
					editTestResult(api, "Test " + getJUnitID(testCaseID) + " was not run!", testSkippedVerdictName, testSessionID, test.getId());
				}
			}
			
			// Process the next level of Test Cases
			Field containsFld = test.getField(testSuiteContainsField);
			if( null != containsFld && null != containsFld.getList() )
			{
				updateTestResult(testResult, listener, api, testSessionID, containsFld.getList());
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
        		Logger.debug("Total tests run: " + testResult.getTotalCount());
        		Logger.debug("Total passed count: " + testResult.getPassCount());
        		Logger.debug("Total failed count: " + testResult.getFailCount());
        		Logger.debug("Total skipped count: " + testResult.getSkipCount());
        		Logger.debug("Failed Test Details:");
        		Iterator<? extends TestResult> failedResultIterator = testResult.getFailedTests().iterator();
        		while( failedResultIterator.hasNext() )
        		{
        			TestResult caseResult = failedResultIterator.next();
        			Logger.debug("ID: " + caseResult.getId() + " " + caseResult.getErrorDetails());	
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
				updateTestResult(getTestResult(testResultAction), listener, api, testSessionID, testSessionFld.getList()); 
			}
		}				
		
		// If we got here then everything went as planned!
		return true;
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

		APISession api = APISession.create(this);
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
	        						if( null != session.getField(testSessionStateField) && session.getField(testSessionStateField).getValueAsString().equals(testSessionActiveState) )
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
            	Logger.error("API Exception caught...");
            	ExceptionHandler eh = new ExceptionHandler(aex);
	        	aex.printStackTrace(listener.fatalError(aex.getMessage()));            	
            	Logger.error(eh.getMessage());
            	Logger.debug(eh.getCommand() + " returned exit code " + eh.getExitCode());
            	return false;
	        }
        	finally
        	{
        		api.Terminate();
        	}			
		}
		else
		{
			Logger.error("An API Session could not be established!  Cannot update Integrity Build Item!");
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
		return true;
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
    	private String defaultQueryDefinition;
    	private String defaultTestSuiteContainsField;
    	private String defaultTestPassedVerdictName;
    	private String defaultTestFailedVerdictName;
    	private String defaultTestSkippedVerdictName;
    			
    	public IntegrityItemDescriptorImpl()
    	{
        	// Log the construction...
    		super(IntegrityItemAction.class);
    		// Initial variable initializations
			defaultQueryDefinition = "((field[Type] = \"Build Request\") and (field[State] = \"Approved\"))";
			defaultTestSuiteContainsField = "Contains";
	    	defaultTestPassedVerdictName = "Passed";
	    	defaultTestFailedVerdictName = "Failed";
	    	defaultTestSkippedVerdictName = "Skipped";
			
			load();
        	Logger.debug("IntegrityItemAction.IntegrityItemDescriptorImpl() constructed!");        	            
    	}

		@Override
		public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException
		{
			IntegrityItemAction itemAction = new IntegrityItemAction();
			itemAction.setHost(formData.getString("host"));
			itemAction.setPort(formData.getInt("port"));
			itemAction.setUserName(formData.getString("userName"));
			itemAction.setPassword(formData.getString("password"));
			itemAction.setSecure(formData.getBoolean("secure"));
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
			
			Logger.debug("IntegrityItemAction.IntegrityItemDescriptorImpl.newInstance() executed!");   
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
			defaultQueryDefinition = Util.fixEmptyAndTrim(req.getParameter("mks.queryDefinition"));
			defaultTestSuiteContainsField = Util.fixEmptyAndTrim(req.getParameter("mks.testSuiteContainsField"));
	    	defaultTestPassedVerdictName = Util.fixEmptyAndTrim(req.getParameter("mks.testPassedVerdictName"));
	    	defaultTestFailedVerdictName = Util.fixEmptyAndTrim(req.getParameter("mks.testFailedVerdictName"));
	    	defaultTestSkippedVerdictName = Util.fixEmptyAndTrim(req.getParameter("mks.testSkippedVerdictName"));
			
			save();
			Logger.debug("IntegrityItemAction.IntegrityItemDescriptorImpl.configure() executed!");
			return super.configure(req, formData);
		}

		public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType)
		{
			Logger.debug("IntegrityItemAction.IntegrityItemDescriptorImpl.isApplicable executed!");
			return true;
		}

	    /**
	     * By default, return the IntegrtySCM host name for the Integrity Server 
	     * @return defaultHostName
	     */
	    public String getDefaultHostName()
	    {
	    	return IntegritySCM.DescriptorImpl.INTEGRITY_DESCRIPTOR.getDefaultHostName();
	    }
	    
	    /**
	     * By default, return the IntegritySCM port for the Integrity Server
	     * @return defaultPort
	     */    
	    public int getDefaultPort()
	    {
	    	return IntegritySCM.DescriptorImpl.INTEGRITY_DESCRIPTOR.getDefaultPort();
	    }

	    /**
	     * By default, return the IntegritySCM secure setting for the Integrity Server
	     * @return defaultSecure
	     */        
	    public boolean getDefaultSecure()
	    {
	    	return IntegritySCM.DescriptorImpl.INTEGRITY_DESCRIPTOR.getDefaultSecure();
	    }

	    /**
	     * By default, return the IntegritySCM for the User connecting to the Integrity Server
	     * @return defaultUserName
	     */    
	    public String getDefaultUserName()
	    {
	    	return IntegritySCM.DescriptorImpl.INTEGRITY_DESCRIPTOR.getDefaultUserName();
	    }
	    
	    /**
	     * By default, return the IntegritySCM user's password connecting to the Integrity Server
	     * @return defaultPassword
	     */        
	    public String getDefaultPassword()
	    {
	    	return IntegritySCM.DescriptorImpl.INTEGRITY_DESCRIPTOR.getDefaultPassword();
	    }

	    /**
	     * Returns the default query definition that will be used to find the 'build' item
	     * @return defaultQueryDefinition
	     */
		public String getDefaultQueryDefinition()
		{
			return defaultQueryDefinition;
		}
		
		/**
		 * Returns the default name for the Integrity 'Contains' field
		 * @return
		 */
		public String getDefaultTestSuiteContainsField()
		{
			return defaultTestSuiteContainsField;
		}
		
		/**
		 * Returns the default name for the Integrity 'Passed' verdict
		 * @return
		 */
		public String getDefaultTestPassedVerdictName()
		{
			return defaultTestPassedVerdictName;
		}

		/**
		 * Returns the default name for the Integrity 'Failed' verdict
		 * @return
		 */
		public String getDefaultTestFailedVerdictName()
		{
			return defaultTestFailedVerdictName;
		}
		
		/**
		 * Returns the default name for the Integrity 'Skipped' verdict
		 * @return
		 */
		public String getDefaultTestSkippedVerdictName()
		{
			return defaultTestSkippedVerdictName;
		}		
    }

	public String getIntegrationPointHost() 
	{
		return null;
	}

	public void setIntegrationPointHost(String host) 
	{
		
	}
	
	public int getIntegrationPointPort() 
	{
		return 0;
	}
	
	public void setIntegrationPointPort(int port) 
	{
		
	}

	public String getConfigurationName() 
	{
		return null;
	}
	
	public void setConfigurationName(String configurationName) 
	{

	}	
}
