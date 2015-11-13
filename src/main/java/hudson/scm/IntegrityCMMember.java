package hudson.scm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.util.logging.Logger;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.BooleanUtils;

import com.mks.api.response.APIException;
import com.mks.api.response.InterruptedException;
import com.mks.api.response.Response;

import hudson.AbortException;
import hudson.FilePath;
import hudson.scm.IntegritySCM.DescriptorImpl;
import hudson.scm.api.APISession;
import hudson.scm.api.APIUtils;
import hudson.scm.api.ExceptionHandler;
import hudson.scm.api.command.CloseCPCommand;
import hudson.scm.api.command.CreateCPCommand;
import hudson.scm.api.command.IAPICommand;
import hudson.scm.api.command.LockCommand;
import hudson.scm.api.command.ProjectAddCommand;
import hudson.scm.api.command.ProjectCheckinCommand;
import hudson.scm.api.command.ProjectCheckoutCommand;
import hudson.scm.api.command.RevisionInfoCommand;
import hudson.scm.api.command.SubmitCPCommand;
import hudson.scm.api.command.UnlockCommand;
import hudson.scm.api.option.APIOption;
import hudson.scm.api.option.FileAPIOption;
import hudson.scm.api.option.IAPIOption;

/**
 * This class is intended to represent an Integrity CM Member
 * However, due to scalability constraints, the bulk of the member metadata
 * will be stored in an embedded database.  The purpose of this class is to
 * statically assist with various Integrity member operations, like co.
 */
public final class IntegrityCMMember
{
	private static final Logger LOGGER = Logger.getLogger("IntegritySCM");
	private static final String ENCODING = "UTF-8"; 
	
	/**
	 * Returns only the file name portion for this full server-side member path
	 * @param memberID The full server side path for the Integrity member
	 * @return
	 */
	public static final String getName(String memberID)
	{
		if( memberID.indexOf('/') > 0 )
		{
			return memberID.substring(memberID.lastIndexOf('/')+1);
		}
		else if( memberID.indexOf('\\') > 0 )
		{
			return memberID.substring(memberID.lastIndexOf('\\')+1);
		}
		else
		{
			return memberID;
		}
	}
	
	/**
	 * Returns an URL encoded string representation for invoking this Integrity member's annotated view
	 * @param configPath Full server side path for this Integrity member's project/subproject
	 * @param memberID Full server side path for this Integrity member
	 * @param memberRev Member revision string for this Integrity member
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public static final String getAnnotatedLink(String configPath, String memberID, String memberRev) throws UnsupportedEncodingException
	{
		StringBuilder sb = new StringBuilder();
		sb.append("annotate?projectName=");
		sb.append(URLEncoder.encode(configPath, ENCODING));
		sb.append("&revision=");
		sb.append(memberRev);
		sb.append("&selection=");
		sb.append(URLEncoder.encode(memberID, ENCODING));
		return sb.toString();	
	}
	
	/**
	 * Returns an URL encoded string representation for invoking this Integrity member's differences view
	 * This assumes that IntegrityCMProject.compareBaseline() was invoked!
	 * @param configPath Full server side path for this Integrity member's project/subproject
	 * @param memberID Full server side path for this Integrity member
	 * @param memberRev Member revision string for this Integrity member
	 * @param oldMemberRev Revision string representing the previous or next revision
	 * @return
	 */
	public static final String getDifferencesLink(String configPath, String memberID, String memberRev, String oldMemberRev) throws UnsupportedEncodingException
	{
		StringBuilder sb = new StringBuilder();
		sb.append("diff?projectName=");
		sb.append(URLEncoder.encode(configPath, ENCODING));
		sb.append("&oldRevision=");
		sb.append(oldMemberRev);
		sb.append("&newRevision=");
		sb.append(memberRev);		
		sb.append("&selection=");
		sb.append(URLEncoder.encode(memberID, ENCODING));		
		return sb.toString();
	}
	
	/**
	 * Performs a checkout of this Integrity Source File to a 
	 * working file location on the build server represented by targetFile
	 * @param api Integrity API Session
	 * @param configPath Full server side path for this Integrity member's project/subproject
	 * @param memberID Full server side path for this Integrity member
	 * @param memberRev Member revision string for this Integrity member
	 * @param targetFile File object representing the target location for this file
	 * @param restoreTimestamp Toggles whether or not the original timestamp should be used
	 * @param lineTerminator Sets the line terminator for this file (native, crlf, or lf)
	 * @return true if the operation succeeded or false if failed
	 * @throws InterruptedException 
	 * @throws APIException
	 */
	public static final boolean checkout(APISession api, String configPath, String memberID, String memberRev, Timestamp memberTimestamp,
							File targetFile, boolean restoreTimestamp, String lineTerminator) throws APIException
	{
	    	IAPICommand command = new ProjectCheckoutCommand();
		
		command.addOption(new APIOption(IAPIOption.PROJECT, configPath));
		command.addOption(new FileAPIOption(IAPIOption.TARGET_FILE, targetFile));
		command.addOption(new APIOption(restoreTimestamp ? IAPIOption.RESTORE_TIMESTAMP : IAPIOption.NORESTORE_TIMESTAMP));
		command.addOption(new APIOption(IAPIOption.LINE_TERMINATOR, lineTerminator));
		command.addOption(new APIOption(IAPIOption.REVISION, memberRev));
		// Add the member selection
		command.addSelection(memberID);
		
		command.addAdditionalParameters(IAPIOption.MEMBER_TIMESTAMP,memberTimestamp);
		command.addAdditionalParameters(IAPIOption.RESTORE_TIMESTAMP,restoreTimestamp);
		command.addAdditionalParameters(IAPIOption.TARGET_FILE,targetFile);
		
		Response response =  command.execute(api);
	        return BooleanUtils.toBoolean(response.getExitCode(), 0, 1);
	}
	
	/**
	 * Performs a revision info on this Integrity Source File
	 * @param configPath Full project configuration path
	 * @param memberID Member ID for this file
	 * @param memberRev Member Revision for this file
	 * @return User responsible for making this change
	 * @throws AbortException 
	 * @throws APICommandException 
	 */
	public static String getAuthorFromRevisionInfo(String serverConfigId, String configPath, String memberID, String memberRev) throws AbortException
	{
		String author = "unknown";
		
		// Construct the revision-info command
		IAPICommand command = new RevisionInfoCommand(DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfigId));
		command.addOption(new APIOption(IAPIOption.PROJECT, configPath));
		command.addOption(new APIOption(IAPIOption.REVISION, memberRev));
		command.addSelection(memberID);
		
		Response response;
		try {
		    	response = command.execute();
		    	author = APIUtils.getAuthorInfo(response,memberID);
		    	
		} catch (APIException aex) {
		    ExceptionHandler eh = new ExceptionHandler(aex);
		    LOGGER.severe("API Exception caught...");
		    LOGGER.severe(eh.getMessage());
		    LOGGER.fine(eh.getCommand() + " returned exit code " + eh.getExitCode());
		    aex.printStackTrace();
		}
		
		return author;
	}
	
	/**
	 * Returns the MD5 checksum hash for a file
	 * @param targetFile File object representing the target file
	 * @return
	 * @throws IOException
	 */
	public static final String getMD5Checksum(File targetFile) throws IOException
	{
		String result = "";
		InputStream fis = null;
		try
		{
			LOGGER.fine("Generating checksum for file " + targetFile.getAbsolutePath());
			fis =  new FileInputStream(targetFile);
			result = DigestUtils.md5Hex(fis);
		}
		catch(FileNotFoundException fnfe)
		{
			result = "";
		}
		finally
		{
			if( null != fis ){ fis.close(); }
		}
		return result;
	}
	
	/**
	 * Performs a lock and subsequent project checkin for the specified member
	 * @param ciSettings Integrity API Session
	 * @param configPath Full project configuration path
	 * @param member Member name for this file
	 * @param relativePath Workspace relative file path
	 * @param cpid Change Package ID
	 * @param desc Checkin description
	 * @throws AbortException 
	 * @throws APIException
	 */
	public static final void updateMember(IntegrityConfigurable ciSettings, String configPath, FilePath member, String relativePath, String cpid, String desc) throws AbortException, APIException
	{
		// Construct the lock command
	    	IAPICommand command = new LockCommand(ciSettings);
		command.addOption(new APIOption(IAPIOption.PROJECT, configPath));
		command.addOption(new APIOption(IAPIOption.CP_ID, cpid));
		command.addSelection(relativePath);
		
		try
		{
			// Execute the lock command
		    	command.execute();
			// If the lock was successful, check-in the updates
			LOGGER.fine("Attempting to checkin file: " + member);
			
			IAPICommand cmd = new ProjectCheckinCommand(ciSettings);
			cmd.addOption(new APIOption(IAPIOption.PROJECT, configPath));
			cmd.addOption(new APIOption(IAPIOption.CP_ID, cpid));
			cmd.addOption(new FileAPIOption(IAPIOption.SOURCE_FILE, new File(""+member)));
			cmd.addOption(new APIOption(IAPIOption.DESCRIPTION, desc));
			
			cmd.addSelection(relativePath);
			
			cmd.execute();
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
				IAPICommand addCommand = new ProjectAddCommand(ciSettings);
				addCommand.addOption(new APIOption(IAPIOption.PROJECT, configPath));
				addCommand.addOption(new APIOption(IAPIOption.CP_ID, cpid));
				addCommand.addOption(new FileAPIOption(IAPIOption.SOURCE_FILE, new File(""+member)));
				addCommand.addOption(new APIOption(IAPIOption.DESCRIPTION, desc));
				
				addCommand.addSelection(relativePath);
				// Execute the add command
				addCommand.execute();
			}
			else
			{
				// Re-throw the error as we need to troubleshoot
				throw ae;
			}
		}								
	}
	
	/**
	 * Performs a recursive unlock on all current user's locked members
	 * @param integrityConfig 
	 * @param configPath Full project configuration path
	 * @throws AbortException 
	 * @throws APIException 
	 */
	public static final void unlockMembers(IntegrityConfigurable integrityConfig, String configPath) throws AbortException, APIException
	{
		// Construct the unlock command
		IAPICommand command = new UnlockCommand(integrityConfig);
		command.addOption(new APIOption(IAPIOption.PROJECT, configPath));
		command.execute();
	}

	/**
	 * Creates a Change Package for updating Integrity SCM projects
	 * @param  Integrity API Session
	 * @param itemID Integrity Lifecycle Manager Item ID
	 * @param desc Change Package Description
	 * @return
	 * @throws APIException
	 * @throws AbortException 
	 * @throws InterruptedException 
	 */
    public static final String createCP(IntegrityConfigurable ciSettings, String itemID, String desc) throws APIException, AbortException, InterruptedException
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

    	IAPICommand command = new CreateCPCommand(ciSettings);
    	command.addOption(new APIOption(IAPIOption.DESCRIPTION,desc));
    	command.addOption(new APIOption(IAPIOption.SUMMARY,desc));
    	command.addOption(new APIOption(IAPIOption.ITEM_ID,itemID));
    	
    	Response res = command.execute();

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
	 * Submits the change package used for updating the Integrity SCM project
	 * @param ciSettings Integrity API Session
	 * @param cpid Change Package ID
	 * @throws AbortException 
	 * @throws APIException
	 */
	public static final void submitCP(IntegrityConfigurable ciSettings, String cpid) throws APIException, AbortException
	{
		LOGGER.fine("Submitting Change Package: " + cpid);
		
		IAPICommand command = new CloseCPCommand(ciSettings);
		command.addSelection(cpid);
		
		// First we'll attempt to close the cp to release locks on files that haven't changed,
		// next we will submit the cp which will submit it for review or 
		// it will get automatically closed in the case of transactional cps
		try
		{
		    	command.execute();
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
				IAPICommand submitcpcmd = new SubmitCPCommand(ciSettings);
				submitcpcmd.addSelection(cpid);
				
				submitcpcmd.execute();
			}
			else
			{
				// Re-throw the error as we need to troubleshoot
				throw ae;
			}
			
		}
	}
}
