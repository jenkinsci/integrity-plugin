package hudson.scm;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.mks.api.Command;
import com.mks.api.FileOption;
import com.mks.api.Option;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;

public class IntegrityCheckinTask implements FileCallable<Boolean>
{
	private static final long serialVersionUID = 4165773747683187630L;
	private static final Logger LOGGER = Logger.getLogger("IntegritySCM");
	private final String itemID;
	private final String buildID;
	private final String ciConfigPath;
	private final String ciWorkspaceDir;
	private final String ciIncludes;
	private final String ciExcludes;
	private final BuildListener listener;
	private IntegrityConfigurable integrityConfig;
    	
	/**
	 * The check-in task provides updates back to an Integrity CM Project
	 * @param ciConfigPath Configuration path for the project to check-in artifacts after the build
	 * @param ciWorkspaceDir Workspace directory containing the check-in artifacts created as a result of the build
	 * @param ciIncludes Ant-style includes filter for check-in files
	 * @param ciExcludes Ant-style excludes filter for check-in files
	 * @param build Hudson abstract build object
	 * @param listener The Hudson build listener
	 * @throws InterruptedException 
	 * @throws IOException 
	 */
	public IntegrityCheckinTask(String ciConfigPath, String ciWorkspaceDir, String ciIncludes, String ciExcludes, 
									AbstractBuild<?, ?> build, BuildListener listener, IntegrityConfigurable integrityConfig) throws IOException, InterruptedException
	{
		
		this.itemID = build.getEnvironment(listener).get("ItemID", "");
		this.buildID = build.getFullDisplayName();
		this.ciConfigPath = IntegrityCheckpointAction.evalGroovyExpression(build.getEnvironment(listener), ciConfigPath);
		this.ciWorkspaceDir = ciWorkspaceDir;
		this.ciIncludes = ciIncludes;
		this.ciExcludes = ciExcludes;
		this.listener = listener;
		this.integrityConfig = integrityConfig;
		LOGGER.fine("Integrity Checkin Task Created!");
	}
		
    private String createCP(APISession api) throws APIException
    {
    	// Return the generated CP ID
    	String cpid = ":none";

		// Check to see if the Item ID contains the magic keyword
		if( ":bypass".equalsIgnoreCase(itemID) || "bypass".equalsIgnoreCase(itemID) )
		{
			return ":bypass";
		}
    	
    	// First figure out what Integrity Item to use for the Change Package
    	try
    	{
    		int intItemID = Integer.parseInt(itemID);
    		if( intItemID <= 0 )
    		{
    			LOGGER.fine("Couldn't determine Integrity Item ID, defaulting cpid to ':none'!");
    			return cpid;
    		}
    	}
    	catch( NumberFormatException nfe )
    	{
    		LOGGER.fine("Couldn't determine Integrity Item ID, defaulting cpid to ':none'!");
    		return cpid;
    	}
    	
    	// Construct a summary/description for the Change Package
    	String desc = "Build updates from " + buildID;

    	// Construct the create cp command
    	Command cmd = new Command(Command.SI, "createcp");
    	cmd.addOption(new Option("summary", desc));
    	cmd.addOption(new Option("description", desc));
    	cmd.addOption(new Option("issueId", itemID));

    	// Execute the command
    	Response res = api.runCommand(cmd);

    	// Process the response object
    	if( null != res )
    	{
    		// Parse the response object to extract the CP ID
    		if( res.getExitCode() == 0 )
    		{
    			cpid = res.getResult().getPrimaryValue().getId();
    		}
    		else // Abort the script is the command failed
    		{
    			LOGGER.severe("An error occured creating Change Package to check-in build updates!");
    		}
    	}
    	else
    	{
    		LOGGER.severe("An error occured creating Change Package to check-in build updates!");
    	}

    	return cpid;
    }
    
    /** 
	 * This task wraps around the code necessary to checkout Integrity CM Members on remote machines
	 * @param workspaceFile Build environment's workspace directory
	 * @param channel Virtual Channel
     */
	public Boolean invoke(File workspaceFile, VirtualChannel channel) throws IOException 
    {
		// Figure out what folder should be used to update Integrity
		File checkinDir = new File(ciWorkspaceDir);
		// Convert the file object to a hudson FilePath
		FilePath workspace = new FilePath(checkinDir.isAbsolute() ? checkinDir : new File(workspaceFile.getAbsolutePath() + IntegritySCM.FS + checkinDir.getPath()));
		
		listener.getLogger().println("Integrity project '" + ciConfigPath + "' will be updated from directory " + workspace);

		// Open our connection to the Integrity Server		
		APISession api = APISession.create(integrityConfig);
		if( null != api )
		{
			try
			{
				// Determine what files need to be checked-in
				FilePath[] artifacts = workspace.list(ciIncludes, ciExcludes);
				if( artifacts.length > 0 )
				{
					// Create our Change Package for the supplied itemID
					String cpid = createCP(api);
					for( int i = 0; i < artifacts.length; i++ )
					{
						FilePath member = artifacts[i];
						String relativePath = (""+member).substring((""+workspace).length()+1);
		
						// This is not a recursive directory tree check-in, only process files found
						if( !member.isDirectory() )
						{
							// Construct the lock command
							Command lock = new Command(Command.SI, "lock");
							lock.addOption(new Option("cpid", cpid));
							lock.addOption(new Option("project", ciConfigPath));
							// Add the member selection
							lock.addSelection(relativePath);
							
							try
							{
								// Execute the lock command
								api.runCommand(lock);
								// If the lock was successful, check-in the updates
								LOGGER.fine("Attempting to checkin file: " + member);
								
								// Construct the project check-in command
								Command ci = new Command(Command.SI, "projectci");
								ci.addOption(new Option("cpid", cpid));
								ci.addOption(new Option("project", ciConfigPath));
								ci.addOption(new FileOption("sourceFile", new File(""+member)));
								ci.addOption(new Option("saveTimestamp"));
								ci.addOption(new Option("nocloseCP"));
								ci.addOption(new Option("nodifferentNames"));
								ci.addOption(new Option("branchVariant"));
								ci.addOption(new Option("nocheckinUnchanged"));
								ci.addOption(new Option("description", "Build updates from " + buildID));
	
								// Add the member selection
								ci.addSelection(relativePath);
	
								// Execute the check-in command
								api.runCommand(ci);
								
							}
							catch( APIException ae )
							{
								// If the command fails, add only if the error indicates a missing member
								ExceptionHandler eh = new ExceptionHandler(ae);
								String exceptionString = eh.getMessage();
	
								// Ensure exception is due to member does not exist
								if( exceptionString.indexOf("is not a current or destined or pending member") > 0 )
								{
									LOGGER.fine("Lock failed: " + exceptionString);
									LOGGER.fine("Attempting to add file: " + member);
								
									// Construct the project add command
									Command add = new Command(Command.SI, "projectadd");
									add.addOption(new Option("cpid", cpid));
									add.addOption(new Option("project", ciConfigPath));
									add.addOption(new FileOption("sourceFile", new File(""+member)));
									add.addOption(new Option("onExistingArchive", "sharearchive"));
									add.addOption(new Option("saveTimestamp"));
									add.addOption(new Option("nocloseCP"));
									add.addOption(new Option("description", "Build updates from " + buildID));
	
									// Add the member selection
									add.addSelection(relativePath);
	
									// Execute the add command
									api.runCommand(add);								
								}
								else
								{
									// Re-throw the error as we need to troubleshoot
									throw ae;
								}
							}						
						}	
					}
					
					// Finally submit the build updates Change Package if its not :none or :bypass
					if( !cpid.equals(":none") && !cpid.equals(":bypass") )
					{
						LOGGER.fine("Submitting Change Package: " + cpid);
						
						// Construct the close cp command
						Command closecp = new Command(Command.SI, "closecp");
						closecp.addOption(new Option("releaseLocks"));
						closecp.addSelection(cpid);
						
						// First we'll attempt to close the cp to release locks on files that haven't changed,
						// next we will submit the cp which will submit it for review or 
						// it will get automatically closed in the case of transactional cps
						try
						{
							api.runCommand(closecp);
						}
						catch( APIException ae )
						{
							ExceptionHandler eh = new ExceptionHandler(ae);
							String exceptionString = eh.getMessage();
		
							// Ensure exception is due to member does not exist
							if( exceptionString.indexOf("has pending entries and can not be closed") > 0 )
							{
								LOGGER.fine("Close cp failed: " + exceptionString);
								LOGGER.fine("Attempting to submit cp: " + cpid);
								
								// Construct the submit cp command
								Command submitcp = new Command(Command.SI, "submitcp");
								submitcp.addOption(new Option("closeCP"));
								submitcp.addOption(new Option("commit"));
		
								// Add the cpid selection
								submitcp.addSelection(cpid);
		
								// Execute the submit cp command
								api.runCommand(submitcp);
							}
							else
							{
								// Re-throw the error as we need to troubleshoot
								throw ae;
							}
							
						}
					}
					else
					{
						// Construct the unlock command
						Command unlock = new Command(Command.SI, "unlock");
						unlock.addOption(new Option("project", ciConfigPath));
						unlock.addOption(new Option("action", "remove"));
						unlock.addOption(new Option("recurse"));
						unlock.addOption(new Option("yes"));
						
						// Execute the unlock command					
						try
						{
							api.runCommand(unlock);
						}
						catch( APIException ae )
						{
				    		ExceptionHandler eh = new ExceptionHandler(ae);
				    		LOGGER.severe(eh.getMessage());
				    		LOGGER.fine(eh.getCommand() + " returned exit code " + eh.getExitCode());							
						}
					}
	
					// Log the success
					listener.getLogger().println("Successfully updated Integrity project '" + ciConfigPath + "' with contents of workspace (" + workspace + ")!");
				}
	
			}
			catch( InterruptedException iex )
			{
				LOGGER.severe("Interrupted Exception caught...");
	    		listener.getLogger().println("An Interrupted Exception was caught!"); 
	    		LOGGER.log(Level.SEVERE, "InterruptedException", iex);
	    		listener.getLogger().println(iex.getMessage());
	    		listener.getLogger().println("Failed to update Integrity project '" + ciConfigPath + "' with contents of workspace (" + workspace + ")!");
	    		return false;			
			}
	    	catch( APIException aex )
	    	{
	    		LOGGER.severe("API Exception caught...");
	    		listener.getLogger().println("An API Exception was caught!"); 
	    		ExceptionHandler eh = new ExceptionHandler(aex);
	    		LOGGER.severe(eh.getMessage());
	    		listener.getLogger().println(eh.getMessage());
	    		listener.getLogger().println("Failed to update Integrity project '" + ciConfigPath + "' with contents of workspace (" + workspace + ")!");
	    		LOGGER.fine(eh.getCommand() + " returned exit code " + eh.getExitCode());
	    		LOGGER.log(Level.SEVERE, "APIException", aex);
	    		return false;
	    	}		
			finally
			{
			    if( null != api )
			    {
			    	api.Terminate();
			    }
			}
		}
		else
		{
			listener.getLogger().println("Couldn't create API Session for check-in task!"); 
    		listener.getLogger().println("Failed to update Integrity project '" + ciConfigPath + "' with contents of workspace (" + workspace + ")!");
    		return false;
		}
		
	    // If we got here, everything is good on the check-in...		
		return true;
    }
}
