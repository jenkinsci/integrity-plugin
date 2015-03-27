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
	
}
