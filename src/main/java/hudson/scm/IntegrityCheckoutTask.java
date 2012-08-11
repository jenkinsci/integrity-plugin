package hudson.scm;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.model.BuildListener;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.mks.api.response.APIException;
import com.mks.api.util.Base64;

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
    private ConcurrentHashMap<String, String> checksumHash;
    // Counts
    private int addCount;
    private int updateCount;
    private int dropCount;
    private int fetchCount;
    private int checkoutThreadPoolSize;
    
	
	
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
									boolean cleanCopy, boolean fetchChangedWorkspaceFiles,int checkoutThreadPoolSize, BuildListener listener)
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
		this.checkoutThreadPoolSize = checkoutThreadPoolSize;
		this.checksumHash = new ConcurrentHashMap<String, String>();
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
	public ConcurrentHashMap<String, String> getChecksumUpdates()
	{
		return checksumHash;
	}
	
	/**
	 * Nested class to manage the open file handle count for the entire checkout process
	 */
	private static class ThreadLocalOpenFileHandler extends ThreadLocal<Integer>
	{
		/**
		 * Returns the initial value for the open file handle count
		 */
        @Override
        protected Integer initialValue()
        {
            Logger.debug("Trying to retrieve initial value for open file handler" );
            return new Integer(1);
        }
	}
	
	/**
	 * Nested class to manage the APISessions for the checkout thread pool
	 */
    private static class ThreadLocalAPISession extends ThreadLocal<APISession>
    {
        final String ipHost;
        final int ipPortNum;
        final String host;
        final int portNum;
        final String user;
        final String paswd;
        final boolean secure;
        // Using a thread safe Vector instead of a List
        private Vector<APISession> sessions = new Vector<APISession>();
   
        /**
         * Initialize our constructor with the all the information needed to create an APISession 
         * @param ipHost Integration Point host name
         * @param ipPortNum Integration Point port
         * @param host Integrity Server host name
         * @param portNum Integrity Server port
         * @param user Integrity Server user id
         * @param paswd Integrity Server user's password
         * @param secure Flag to determine whether or not secure sockets are in use
         */
        public ThreadLocalAPISession(String ipHost, int ipPortNum, String host, int portNum, String user, String paswd, boolean secure)
        {
            this.ipHost = ipHost;
            this.ipPortNum = ipPortNum;
            this.host = host;
            this.portNum = portNum;
            this.user = user;
            this.paswd = paswd;
            this.secure = secure;
        }

        /**
         * Terminates all the active APISessions started by the thread pool
         */
        @Override
        public void remove()
        {
            for(APISession session:sessions)
            {
                try
                {
                    session.Terminate();
                }
                catch(Exception ex)
                {
                    Logger.debug("Error while shuting down thread API session: " + ex.getMessage());
                }
            }
            super.remove();
        }

        /**
         * Returns an initial APISession for this thread
         */
        @Override
        protected APISession initialValue() 
        {
            Logger.debug("Trying to initialize new thread session");
            try 
            {
                APISession session = new APISession(ipHost, ipPortNum, host, portNum, user, paswd, secure);
                Logger.debug("Initialized thread session: " + session.toString());
                sessions.add(session);
                return session;
            }
            catch (APIException e) 
            {
                throw new RuntimeException(e);
            }
        }
    }
    
    /**
     * Nested class that performs the actual checkout operation
     */
    private final class CheckOutTask implements Callable<Void> 
    {
        private final ThreadLocalAPISession apiSession;
        private final ThreadLocalOpenFileHandler openFileHandler;
        private final String configPath;
        private final String memberID;
        private final String memberName;
        private final String memberRev;
        private final File targetFile;
        private final boolean calculateChecksum;
        
        public CheckOutTask(ThreadLocalAPISession apiSession, ThreadLocalOpenFileHandler openFileHandler,
        					String memberName, String configPath, String memberID, String memberRev, File targetFile, boolean calculateChecksum)
        {
            this.apiSession = apiSession;
            this.openFileHandler = openFileHandler;
            this.configPath = configPath;
            this.memberID = memberID;
            this.memberName = memberName;
            this.memberRev = memberRev;
            this.targetFile = targetFile;
            this.calculateChecksum = calculateChecksum;
        }
        
        public Void call() throws Exception 
        {
            APISession api = apiSession.get();
            // Check to see if we need to release the APISession to clear some file handles
            Logger.debug("API open file handles: " + openFileHandler.get() );
            if( openFileHandler.get() >= CHECKOUT_TRESHOLD  )
            {
                Logger.debug("Checkout threshold reached for session " + api.toString() + ", refreshing API session");
                api.refreshAPISession();
                openFileHandler.set(1);
            }
            Logger.debug("Checkout on API thread: " + api.toString());
            IntegrityCMMember.checkout(api, configPath, memberID, memberRev, targetFile, restoreTimestamp, lineTerminator);
            openFileHandler.set( openFileHandler.get() + 1);
            if(calculateChecksum)
            {
                checksumHash.put(memberName, IntegrityCMMember.getMD5Checksum(targetFile));
            }
            return null;
        }

    }
    
	/**
	 * This task wraps around the code necessary to checkout Integrity CM Members on remote machines
	 */
	public Boolean invoke(File workspaceFile, VirtualChannel channel) throws IOException 
    {
		// Figure out where we should be checking out this project
		File checkOutDir = (null != alternateWorkspaceDir && alternateWorkspaceDir.length() > 0) ? new File(alternateWorkspaceDir) : workspaceFile;
		// Convert the file object to a hudson FilePath (helps us with workspace.deleteContents())
		FilePath workspace = new FilePath(checkOutDir.isAbsolute() ? checkOutDir : new File(workspaceFile.getAbsolutePath() + IntegritySCM.FS + checkOutDir.getPath()));
		listener.getLogger().println("Checkout directory is " + workspace);
		final ThreadLocalAPISession generateAPISession = new ThreadLocalAPISession(ipHostName, ipPort, hostName, port, userName, Base64.decode(password), secure);
		final ThreadLocalOpenFileHandler openFileHandler = new ThreadLocalOpenFileHandler();
        ExecutorService executor = Executors.newFixedThreadPool(checkoutThreadPoolSize);
        @SuppressWarnings("rawtypes")
        final List<Future> coThreads = new ArrayList<Future>();
		// If we got here, then APISession was created successfully!
		try
		{
			// Keep count of the open file handles generated on the server
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
					coThreads.add(executor.submit(new CheckOutTask(generateAPISession, openFileHandler, memberName, configPath, memberID, memberRev, targetFile, fetchChangedWorkspaceFiles)));			
				}
				else if( deltaFlag == 0 && fetchChangedWorkspaceFiles && checksum.length() > 0 )
				{
					if( ! checksum.equals(IntegrityCMMember.getMD5Checksum(targetFile)) )
					{
						Logger.debug("Attempting to restore changed workspace file: " + targetFile.getAbsolutePath() + " to revision " + memberRev);
						coThreads.add(executor.submit(new CheckOutTask(generateAPISession, openFileHandler, memberName, configPath, memberID, memberRev, targetFile, false)));
						fetchCount++;
					}
				}
				else if( deltaFlag == 1 )
				{
					Logger.debug("Attempting to get new file: " + targetFile.getAbsolutePath() + " at revision " + memberRev);
					coThreads.add(executor.submit(new CheckOutTask(generateAPISession, openFileHandler, memberName, configPath, memberID, memberRev, targetFile, fetchChangedWorkspaceFiles)));
					addCount++;									
				}
				else if( deltaFlag == 2 )
				{
					Logger.debug("Attempting to update file: " + targetFile.getAbsolutePath() + " to revision " + memberRev);
					coThreads.add(executor.submit(new CheckOutTask(generateAPISession, openFileHandler, memberName, configPath, memberID, memberRev, targetFile, fetchChangedWorkspaceFiles)));
					updateCount++;														
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
			}
			
            int checkoutMembers = 0;
            int previousCount = 0;
            int canceledMembers = 0;
            int totalMembers = coThreads.size();
            while (!coThreads.isEmpty()) 
            {
                @SuppressWarnings("rawtypes")
                Iterator<Future> iter = coThreads.iterator();
                while (iter.hasNext()) 
                {
                    Future<?> future = iter.next();
                    if(future.isCancelled())
                    {
                        listener.getLogger().println("Checkout thread " + future.toString() + " was cancelled");
                        canceledMembers++;
                        iter.remove();
                    }
                    else if(future.isDone())
                    {
                        checkoutMembers++;  
                        iter.remove();
                    }
                }
                if(previousCount != (checkoutMembers + canceledMembers))
                {
                    Logger.debug("Checkout process: " + checkoutMembers + " of " + totalMembers + (canceledMembers>0? "(Canceled: " +  canceledMembers +  ")":"") );
                }
                previousCount = checkoutMembers + canceledMembers;
                // Wait 2 seconds a check again if all threads are done
                Thread.sleep(2000);
            }
            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.MINUTES);
            
            // Lets advice the user that we've checked out all the members
            if (cleanCopy) 
            {
                listener.getLogger().println("Successfully checked out " + projectMembersList.size() + " files!");
            } 
            else 
            {
                // Lets advice the user that we've performed the updates to the workspace
                listener.getLogger().println("Successfully updated workspace with " + (addCount + updateCount) + " updates and cleaned up " + dropCount + " files!");
                if (fetchChangedWorkspaceFiles && fetchCount > 0) 
                {
                    listener.getLogger().println("Additionally, a total of " + fetchCount + " files were restored to their original repository state!");
                }
            }
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
		    if( generateAPISession != null )
		    {
		    	generateAPISession.remove();
		    }
		}
		
	    //If we got here, everything is good on the checkout...		
		return true;
    }
}
