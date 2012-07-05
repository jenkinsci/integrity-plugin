package hudson.scm;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import com.mks.api.response.APIException;
import com.mks.api.util.Base64;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.model.BuildListener;
import hudson.remoting.VirtualChannel;

public class IntegrityCheckoutTask implements FileCallable<Boolean> 
{
	private static final long serialVersionUID = 1240357991626897900L;
	private static final int CHECKOUT_TRESHOLD = 500;	
	private final List<Hashtable<CM_PROJECT, Object>> projectMembersList;
	private final List<String> dirList;
	private final String lineTerminator;
	private final boolean restoreTimestamp;
	private final boolean cleanCopy;
	private final String alternateWorkspaceDir;
	private final boolean fetchChangedWorkspaceFiles;
	private final BuildListener listener;
	// API connection information
	private String ipHostName;
	private String hostName;
	private int ipPort = 0;
	private int port;
	private boolean secure;
    private String userName;
    private String password;
    // Checksum Hash
    private Hashtable<String, String> checksumHash;
    // Counts
    private int addCount;
    private int updateCount;
    private int dropCount;
    private int fetchCount;
    
	
	
	/**
	 * Hudson supports building on distributed machines, and the SCM plugin must 
	 * be able to be executed on other machines than the master. 
	 * @param projectMembersList A list of all the members that are in this Integrity SCM project
	 * @param dirList A list of all the unique directories in this Integrity SCM project
	 * @param alternateWorkspaceDir Specifies an alternate location for checkout other than the default workspace
	 * @param lineTerminator The line termination setting for this checkout operation
	 * @param restoreTimestamp Toggles whether to use the current date/time or the original date/time for the member
	 * @param cleanCopy Indicates whether or not the workspace needs to be cleaned up prior to checking out files
	 * @param fetchChangedWorkspaceFiles Toggles whether or not to calculate checksums, so if changed then it will be overwritten
	 * @param listener The Hudson build listener
	 */
	public IntegrityCheckoutTask(List<Hashtable<CM_PROJECT, Object>> projectMembersList, List<String> dirList,
									String alternateWorkspaceDir, String lineTerminator, boolean restoreTimestamp,
									boolean cleanCopy, boolean fetchChangedWorkspaceFiles, BuildListener listener)
	{
		this.projectMembersList = projectMembersList;
		this.dirList = dirList;
		this.alternateWorkspaceDir = alternateWorkspaceDir;
		this.lineTerminator = lineTerminator;
		this.restoreTimestamp = restoreTimestamp;
		this.cleanCopy = cleanCopy;
		this.fetchChangedWorkspaceFiles = fetchChangedWorkspaceFiles;
		this.listener = listener;
		this.ipHostName = "";
		this.ipPort = 0;
		this.hostName = "";
		this.port = 7001;
		this.secure = false;
		this.userName = "";
		this.password = "";
		this.addCount = 0;
		this.updateCount = 0;
		this.dropCount = 0;
		this.fetchCount = 0;
		this.checksumHash = new Hashtable<String, String>();
		Logger.debug("Integrity Checkout Task Created!");
	}
	
	/**
	 * Helper function to initialize all the variables needed to establish an APISession
	 * @param ipHostName Integration Point Hostname
	 * @param ipPort Integration Point Port
	 * @param hostName Integrity Server Hostname
	 * @param port Integrity Server Port
	 * @param secure Toggles whether Integrity Server is SSL enabled
	 * @param userName Username to connect to the Integrity Server
	 * @param password Password for the Username connection to the Integrity Server
	 */
	public void initAPIVariables(String ipHostName, int ipPort, String hostName, int port, boolean secure, String userName, String password)
	{
		this.ipHostName = ipHostName;
		this.ipPort = ipPort;
		this.hostName = hostName;
		this.port = port;
		this.secure = secure;
		this.userName = userName;
		this.password = password;
	}
	
    /**
     * Creates an authenticated API Session against the Integrity Server
     * @return An authenticated API Session
     */
    public APISession createAPISession()
    {
    	// Attempt to open a connection to the Integrity Server
    	try
    	{
    		Logger.debug("Creating Integrity API Session...");
    		return new APISession(ipHostName, ipPort, hostName, port, userName, Base64.decode(password), secure);
    	}
    	catch(APIException aex)
    	{
    		Logger.error("API Exception caught...");
    		ExceptionHandler eh = new ExceptionHandler(aex);
    		Logger.error(eh.getMessage());
    		Logger.debug(eh.getCommand() + " returned exit code " + eh.getExitCode());
    		Logger.fatal(aex);
    		return null;
    	}				
    }
	
	/**
	 * Creates the folder structure for the project's contents allowing empty folders to be created
	 * @param workspace
	 */
	private void createFolderStructure(FilePath workspace)
	{
		Iterator<String> folders = dirList.iterator();
		while( folders.hasNext() )
		{
			File dir = new File(workspace + folders.next());
			if( ! dir.isDirectory() )
			{
				Logger.debug("Creating folder: " + dir.getAbsolutePath());
				dir.mkdirs();
			}
		}
	}
	
	/**
	 * Returns all the changes to the checksums that were performed
	 * @return
	 */
	public Hashtable<String, String> getChecksumUpdates()
	{
		return checksumHash;
	}
	
	/**
	 * This task wraps around the code necessary to checkout Integrity CM Members on remote machines
	 */
	public Boolean invoke(File workspaceFile, VirtualChannel channel) throws IOException 
    {
		// Figure out where we should be checking out this project
		File checkOutDir = (null != alternateWorkspaceDir && alternateWorkspaceDir.length() > 0) ? new File(alternateWorkspaceDir) : workspaceFile;
		// Convert the file object to a hudson FilePath (helps us with workspace.deleteContents())
		FilePath workspace = new FilePath(checkOutDir.isAbsolute() ? checkOutDir : 
						new File(workspaceFile.getAbsolutePath() + IntegritySCM.FS + checkOutDir.getPath()));
		listener.getLogger().println("Checkout directory is " + workspace);
		// Create a fresh API Session as we may/will be executing from another server
		APISession api = createAPISession();
		// Ensure we've successfully created an API Session
		if( null == api )
		{
			listener.getLogger().println("Failed to establish an API connection to the Integrity Server!");
			return false;
		}
		
		// If we got here, then APISession was created successfully!
		try
		{
			// Keep count of the open file handles generated on the server
			int openFileHandles = 0;
			if( cleanCopy )
			{ 
				listener.getLogger().println("A clean copy is requested; deleting contents of " + workspace); 
				Logger.debug("Deleting contents of workspace " + workspace); 
				workspace.deleteContents();
				listener.getLogger().println("Populating clean workspace...");
			}
				
			// Create an empty folder structure first
			createFolderStructure(workspace);
			
			// Perform a synchronize of each file in the member list... 
			for( Iterator<Hashtable<CM_PROJECT, Object>> it = projectMembersList.iterator(); it.hasNext(); )
			{
				openFileHandles++;
				Hashtable<CM_PROJECT, Object> memberInfo = it.next();
				short deltaFlag = (null == memberInfo.get(CM_PROJECT.DELTA) ? -1 : Short.valueOf(memberInfo.get(CM_PROJECT.DELTA).toString()));
				File targetFile = new File(workspace + memberInfo.get(CM_PROJECT.RELATIVE_FILE).toString());
				String memberName = memberInfo.get(CM_PROJECT.NAME).toString();
				String memberID = memberInfo.get(CM_PROJECT.MEMBER_ID).toString();
				String memberRev = memberInfo.get(CM_PROJECT.REVISION).toString();
				String configPath = memberInfo.get(CM_PROJECT.CONFIG_PATH).toString();
				String checksum = (null == memberInfo.get(CM_PROJECT.CHECKSUM) ? "" : memberInfo.get(CM_PROJECT.CHECKSUM).toString());
			
				if( cleanCopy && deltaFlag != 3 )
				{
					Logger.debug("Attempting to checkout file: " + targetFile.getAbsolutePath() + " at revision " + memberRev);
					IntegrityCMMember.checkout(api, configPath, memberID, memberRev, targetFile, restoreTimestamp, lineTerminator);
					// Calculate the checksum for this file, so we'll know if its changed on the filesystem
					if( fetchChangedWorkspaceFiles )
					{
						checksumHash.put(memberName, IntegrityCMMember.getMD5Checksum(targetFile));
					}					
				}
				else if( deltaFlag == 0 && fetchChangedWorkspaceFiles && checksum.length() > 0 )
				{
					if( ! checksum.equals(IntegrityCMMember.getMD5Checksum(targetFile)) )
					{
						Logger.debug("Attempting to restore changed workspace file: " + targetFile.getAbsolutePath() + " to revision " + memberRev);
						IntegrityCMMember.checkout(api, configPath, memberID, memberRev, targetFile, restoreTimestamp, lineTerminator);
						fetchCount++;
					}
				}
				else if( deltaFlag == 1 )
				{
					Logger.debug("Attempting to get new file: " + targetFile.getAbsolutePath() + " at revision " + memberRev);
					IntegrityCMMember.checkout(api, configPath, memberID, memberRev, targetFile, restoreTimestamp, lineTerminator);
					addCount++;
					// Calculate the checksum for this file, so we'll know if its changed on the filesystem
					if( fetchChangedWorkspaceFiles )
					{
						checksumHash.put(memberName, IntegrityCMMember.getMD5Checksum(targetFile));
					}										
				}
				else if( deltaFlag == 2 )
				{
					Logger.debug("Attempting to update file: " + targetFile.getAbsolutePath() + " to revision " + memberRev);
					IntegrityCMMember.checkout(api, configPath, memberID, memberRev, targetFile, restoreTimestamp, lineTerminator);
					updateCount++;
					// Calculate the checksum for this file, so we'll know if its changed on the filesystem
					if( fetchChangedWorkspaceFiles )
					{
						checksumHash.put(memberName, IntegrityCMMember.getMD5Checksum(targetFile));
					}															
				}
				else if( deltaFlag == 3 )					
				{
					Logger.debug("Attempting to drop file: " + targetFile.getAbsolutePath() + " was at revision " + memberRev);
					dropCount++;
					if( targetFile.exists() && !targetFile.delete() )
					{
						listener.getLogger().println("Failed to clean up workspace file " + targetFile.getAbsolutePath() + "!");
						return false;
					}
					
				}
					
				// Check to see if we need to release the APISession to clear some file handles
				if( openFileHandles % CHECKOUT_TRESHOLD == 0 )
				{
					api.Terminate();
					api = createAPISession();
				}
			}
			
			// Lets advice the user that we've checked out all the members
			if( cleanCopy )
			{
				listener.getLogger().println("Successfully checked out " + projectMembersList.size() + " files!");
			}
			else
			{
				// Lets advice the user that we've performed the updates to the workspace
				listener.getLogger().println("Successfully updated workspace with " + (addCount+updateCount) + " updates and cleaned up " +  dropCount + " files!");			
				if( fetchChangedWorkspaceFiles && fetchCount > 0 )
				{
					listener.getLogger().println("Additionally, a total of " + fetchCount + " files were restored to their original repository state!");
				}
			}
		}
		catch( APIException aex )
		{
    		Logger.error("API Exception caught...");
    		listener.getLogger().println("An API Exception was caught!"); 
    		ExceptionHandler eh = new ExceptionHandler(aex);
    		Logger.error(eh.getMessage());
    		listener.getLogger().println(eh.getMessage());
    		Logger.debug(eh.getCommand() + " returned exit code " + eh.getExitCode());
    		listener.getLogger().println(eh.getCommand() + " returned exit code " + eh.getExitCode());
    		Logger.fatal(aex);
    		return false;			
		}
		catch( InterruptedException iex )
		{
    		Logger.error("Interrupted Exception caught...");
    		listener.getLogger().println("An Interrupted Exception was caught!"); 
    		Logger.error(iex.getMessage());
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
