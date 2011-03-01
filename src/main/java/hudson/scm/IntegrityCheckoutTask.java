package hudson.scm;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.mks.api.response.APIException;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.model.BuildListener;
import hudson.remoting.VirtualChannel;

public class IntegrityCheckoutTask implements FileCallable<Boolean> 
{
	private static final long serialVersionUID = 1240357991626897900L;
	private final Log logger = LogFactory.getLog(getClass());	
	private final IntegritySCM scm;
	private final IntegrityCMProject siProject;
	private final boolean cleanCopy;
	private final BuildListener listener;
	
	/**
	 * Hudson supports building on distributed machines, and the SCM plugin must 
	 * be able to be executed on other machines than the master. 
	 * @param scm Out IntegritySCM object that contains all the configuration information we need
	 * @param siProject IntegrityCMProject object containing details about the Integrity Project and Members
	 * @param cleanCopy Indicates whether or not the workspace needs to be cleaned up prior to checking out files
	 * @param listener The Hudson build listener
	 */
	public IntegrityCheckoutTask(IntegritySCM scm, IntegrityCMProject siProject, boolean cleanCopy, BuildListener listener)
	{
		this.scm = scm;
		this.siProject = siProject;
		this.cleanCopy = cleanCopy;
		this.listener = listener;
		logger.info("Integrity Checkout Task Created!");
	}
	
	/**
	 * This task wraps around the code necessary to checkout Integrity CM Members on remote machines
	 */
	public Boolean invoke(File workspaceFile, VirtualChannel channel) throws IOException 
    {
		// Convert the file object to a hudson FilePath (helps us with workspace.deleteContents())
		FilePath workspace = new FilePath(workspaceFile);
		// Create a fresh API Session as we may/will be executing from another server
		APISession api = scm.createAPISession();
		
		try
		{
			if( cleanCopy )
			{ 
				listener.getLogger().println("A clean copy is requested; deleting contents of " + workspace); 
				logger.info("Deleting contents of workspace " + workspace); 
				workspace.deleteContents();
				listener.getLogger().println("Populating clean workspace...");
				// Perform a fresh checkout of each file in the member list...
				List<IntegrityCMMember> projectMembers = siProject.getProjectMembers(); 
				for( Iterator<IntegrityCMMember> it = projectMembers.iterator(); it.hasNext(); )
				{
					IntegrityCMMember siMember = it.next();
					siMember.setWorkspaceDir(""+workspace);
					logger.info("Attempting to checkout file: " + siMember.getTargetFilePath() + " at revision " + siMember.getRevision());
					siMember.checkout(api);
				}
				// Lets advice the user that we've checked out all the members
				listener.getLogger().println("Successfully checked out " + projectMembers.size() + " files!");
			}
			else // We'll need to update the existing workspace...
			{
				// First lets process the adds
				List<IntegrityCMMember> newMembersList = siProject.getAddedMembers(); 
				for( Iterator<IntegrityCMMember> it = newMembersList.iterator(); it.hasNext(); )
				{
					IntegrityCMMember siMember = it.next();
					siMember.setWorkspaceDir(""+workspace);
					logger.info("Attempting to get new file: " + siMember.getTargetFilePath() + " at revision " + siMember.getRevision());
					siMember.checkout(api);				
				}
				// Next, lets process the updates
				List<IntegrityCMMember> updatedMembersList = siProject.getUpdatedMembers();
				for( Iterator<IntegrityCMMember> it = updatedMembersList.iterator(); it.hasNext(); )
				{
					IntegrityCMMember siMember = it.next();
					siMember.setWorkspaceDir(""+workspace);
					logger.info("Attempting to update file: " + siMember.getTargetFilePath() + " to revision " + siMember.getRevision());
					siMember.checkout(api);				
				}				
				// Finally, lets process the drops
				List<IntegrityCMMember> memberDropList = siProject.getDroppedMembers();
				for( Iterator<IntegrityCMMember> it = memberDropList.iterator(); it.hasNext(); )
				{
					IntegrityCMMember siMember = it.next();
					siMember.setWorkspaceDir(""+workspace);
					logger.info("Attempting to drop file: " + siMember.getTargetFilePath() + " was at revision " + siMember.getRevision());
					File dropFile = new File(siMember.getTargetFilePath());
					if( dropFile.exists() && !dropFile.delete() )
					{
						listener.getLogger().println("Failed to clean up workspace file " + dropFile.getAbsolutePath() + "!");
						return false;
					}
				}
				// Lets advice the user that we've performed the updates to the workspace
				listener.getLogger().println("Successfully updated workspace with " + (newMembersList.size() + updatedMembersList.size()) 
												+ " updates and cleaned up " +  memberDropList.size() + " files!");			
			}
		}
		catch(APIException aex)
		{
    		logger.error("API Exception caught...");
    		listener.getLogger().println("An API Exception was caught!"); 
    		ExceptionHandler eh = new ExceptionHandler(aex);
    		logger.error(eh.getMessage());
    		listener.getLogger().println(eh.getMessage());
    		logger.info(eh.getCommand() + " returned exit code " + eh.getExitCode());
    		listener.getLogger().println(eh.getCommand() + " returned exit code " + eh.getExitCode());
    		aex.printStackTrace();
    		return false;			
		}
		catch(InterruptedException iex)
		{
    		logger.error("Interrupted Exception caught...");
    		listener.getLogger().println("An Interrupted Exception was caught!"); 
    		logger.error(iex.getMessage());
    		listener.getLogger().println(iex.getMessage());
    		listener.getLogger().println("Failed to clean up workspace (" + workspace + ") contents!");
    		return false;			
		}
		finally
		{
			// Close out the API Session created on this slave.
			api.Terminate();
		}
		
	    //If we got here, everything is good on the checkout...		
		return true;
    }
}
