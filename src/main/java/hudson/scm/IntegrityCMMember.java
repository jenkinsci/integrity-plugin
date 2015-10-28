package hudson.scm;

import hudson.FilePath;

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

import com.mks.api.Command;
import com.mks.api.Option;
import com.mks.api.FileOption;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;

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
	 * @throws APIException
	 */
	public static final boolean checkout(APISession api, String configPath, String memberID, String memberRev, Timestamp memberTimestamp,
							File targetFile, boolean restoreTimestamp, String lineTerminator) throws APIException
	{
		// Make sure the directory is created
		if( ! targetFile.getParentFile().isDirectory() )
		{
			targetFile.getParentFile().mkdirs();
		}
		// Construct the project check-co command
		Command coCMD = new Command(Command.SI, "projectco");
		coCMD.addOption(new Option("overwriteExisting"));
		coCMD.addOption(new Option("nolock"));
		coCMD.addOption(new Option("project", configPath));
		coCMD.addOption(new FileOption("targetFile", targetFile));
		coCMD.addOption(new Option(restoreTimestamp ? "restoreTimestamp" : "norestoreTimestamp"));
		coCMD.addOption(new Option("lineTerminator", lineTerminator));
		coCMD.addOption(new Option("revision", memberRev));
		// Add the member selection
		coCMD.addSelection(memberID);
		
		// Execute the checkout command
		Response res = api.runCommand(coCMD);
		LOGGER.fine("Command: " + res.getCommandString() + " completed with exit code " + res.getExitCode());
		
		// Return true if we were successful
		if( res.getExitCode() == 0 )
		{
			// Per JENKINS-13765 - providing a workaround due to API bug
			// Update the timestamp for the file, if appropriate
			if( restoreTimestamp )
			{
				targetFile.setLastModified(memberTimestamp.getTime());
			}
			return true;
		}
		// Otherwise return false...
		else
		{
			return false;
		}
	}
	
	/**
	 * Performs a revision info on this Integrity Source File
	 * @param api Integrity API Session
	 * @param configPath Full project configuration path
	 * @param memberID Member ID for this file
	 * @param memberRev Member Revision for this file
	 * @return User responsible for making this change
	 */
	public static String getAuthor(APISession api, String configPath, String memberID, String memberRev)
	{
		// Initialize the return value
		String author = "unknown";
		
		// Construct the revision-info command
		Command revInfoCMD = new Command(Command.SI, "revisioninfo");
		revInfoCMD.addOption(new Option("project", configPath));
		revInfoCMD.addOption(new Option("revision", memberRev));
		// Add the member selection
		revInfoCMD.addSelection(memberID);
		try
		{
			// Execute the revision-info command
			Response res = api.runCommand(revInfoCMD);
			LOGGER.fine("Command: " + res.getCommandString() + " completed with exit code " + res.getExitCode());			
			// Return the author associated with this update
			if( res.getExitCode() == 0 )
			{
				author = res.getWorkItem(memberID).getField("author").getValueAsString();
			}
		}
		catch(APIException aex)
		{
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
	 * @param api Integrity API Session
	 * @param configPath Full project configuration path
	 * @param member Member name for this file
	 * @param relativePath Workspace relative file path
	 * @param cpid Change Package ID
	 * @param desc Checkin description
	 * @throws APIException
	 */
	public static final void updateMember(APISession api, String configPath, FilePath member, String relativePath, String cpid, String desc) throws APIException
	{
		// Construct the lock command
		Command lock = new Command(Command.SI, "lock");
		lock.addOption(new Option("cpid", cpid));
		lock.addOption(new Option("project", configPath));
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
			ci.addOption(new Option("project", configPath));
			ci.addOption(new FileOption("sourceFile", new File(""+member)));
			ci.addOption(new Option("saveTimestamp"));
			ci.addOption(new Option("nocloseCP"));
			ci.addOption(new Option("nodifferentNames"));
			ci.addOption(new Option("branchVariant"));
			ci.addOption(new Option("nocheckinUnchanged"));
			ci.addOption(new Option("description", desc));

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
				add.addOption(new Option("project", configPath));
				add.addOption(new FileOption("sourceFile", new File(""+member)));
				add.addOption(new Option("onExistingArchive", "sharearchive"));
				add.addOption(new Option("saveTimestamp"));
				add.addOption(new Option("nocloseCP"));
				add.addOption(new Option("description", desc));

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
	
	/**
	 * Performs a recursive unlock on all current user's locked members
	 * @param api Integrity API Session
	 * @param configPath Full project configuration path
	 */
	public static final void unlockMembers(APISession api, String configPath)
	{
		// Construct the unlock command
		Command unlock = new Command(Command.SI, "unlock");
		unlock.addOption(new Option("project", configPath));
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

	/**
	 * Creates a Change Package for updating Integrity SCM projects
	 * @param api Integrity API Session
	 * @param itemID Integrity Lifecycle Manager Item ID
	 * @param desc Change Package Description
	 * @return
	 * @throws APIException
	 */
    public static final String createCP(APISession api, String itemID, String desc) throws APIException
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
	 * Submits the change package used for updating the Integrity SCM project
	 * @param api Integrity API Session
	 * @param cpid Change Package ID
	 * @throws APIException
	 */
	public static final void submitCP(APISession api, String cpid) throws APIException
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
}
