package hudson.scm;

import java.util.Date;
import java.io.File;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.mks.api.Command;
import com.mks.api.Option;
import com.mks.api.FileOption;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;
import com.mks.api.response.WorkItem;

/**
 * This class represents an Integrity CM Member
 * It contains all the necessary metadata to check this file out individually
 */
public class IntegrityCMMember implements Serializable
{
	private static final long serialVersionUID = 880318565064448175L;
	private static final String ENCODING = "UTF-8"; 
	private String memberID;
	private String memberName;
	private Date memberTimestamp;
	private String memberDescription;
	private String projectConfigPath;
	private String memberRev;
	private String priorRev;
	private File targetFile;
	private String relativeFile;
	private String lineTerminator;
	private String overwriteExisting;
	private String restoreTimestamp;
	private final Log logger = LogFactory.getLog(getClass());
	
	/**
	 * This class represents an MKS Integrity Source File
	 * It needs the Member Name (relative path to pj), Full Member Path, Project Configuration Path, Revision,
	 * Project's Root Path, and the current Workspace directory (to compute the working file path) for its
	 * instantiation.  This helper class will be used to then perform a project checkout from the repository 
	 * @param wi A MKS API Response Work Item representing metadata related to a Integrity Member
	 * @param configPath Configuration Path for this file's project/subproject
	 * @param projectRoot Full path to the root location for this file's parent project
	 */
	public IntegrityCMMember(WorkItem wi, String configPath, String projectRoot)
	{
		this.projectConfigPath = configPath;
		this.memberID = wi.getId();
		this.memberName = wi.getField("name").getValueAsString();
		this.memberRev = wi.getField("memberrev").getItem().getId();
		this.priorRev = ""; // This will be empty until a baseline comparison is performed!
		this.memberTimestamp = wi.getField("membertimestamp").getDateTime();
		if( null != wi.getField("memberdescription") && null != wi.getField("memberdescription").getValueAsString() )
		{
			this.memberDescription = wi.getField("memberdescription").getValueAsString();
		}
		else
		{
			this.memberDescription = new String("");
		}
		this.lineTerminator = "native";
		this.overwriteExisting = "overwriteExisting";
		this.restoreTimestamp = "restoreTimestamp";
		this.relativeFile = this.memberName.substring(projectRoot.length());
		// At this point just initialize the target file to a relative path!
		this.targetFile = new File(relativeFile);
	}

	/**
	 * Constructs an absolute path for the target file based on the workspace it will be checked out to
	 * @param workspaceDir Full path to the root location where this file will be checked out
	 */
	public void setWorkspaceDir(String workspaceDir)
	{
		// Construct the targetFilePath based on the previously established relative path and workspace directory
		targetFile = new File(workspaceDir + relativeFile);		
	}
	
	/**
	 * Returns a string representation of this file's full path name,
	 * where it will checked out to disk for the build.
	 * NOTE: This assumes that setWorkspaceDir() was called prior to making this call!
	 * @return
	 */
	public String getTargetFilePath()
	{
		return targetFile.getAbsolutePath();
	}

	/**
	 * Returns a string representation of this member's revision
	 * @return
	 */
	public String getRevision()
	{
		return memberRev;
	}
	
	/**
	 * Returns the date/time associated with this member revision
	 * @return
	 */
	public Date getTimestamp()
	{
		return memberTimestamp;
	}
	
	/**
	 * Returns any check-in comments associated with this revision
	 * @return
	 */
	public String getDescription()
	{
		return memberDescription;
	}
	
	/**
	 * Returns the full server-side member path for this member
	 * @return
	 */
	public String getMemberName()
	{
		return memberName;
	}

	/**
	 * Returns only the file name portion for this full server-side member path
	 * @return
	 */
	public String getName()
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
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public String getAnnotatedLink() throws UnsupportedEncodingException
	{
		StringBuilder sb = new StringBuilder();
		sb.append("annotate?projectName=");
		sb.append(URLEncoder.encode(projectConfigPath, ENCODING));
		sb.append("&revision=");
		sb.append(memberRev);
		sb.append("&selection=");
		sb.append(URLEncoder.encode(memberID, ENCODING));
		return sb.toString();	
	}
	
	/**
	 * Returns an URL encoded string representation for invoking this Integrity member's differences view
	 * This assumes that IntegrityCMProject.compareBaseline() was invoked!
	 * @return
	 */
	public String getDifferencesLink() throws UnsupportedEncodingException
	{
		if( null != priorRev && priorRev.length() > 0 )
		{
			StringBuilder sb = new StringBuilder();
			sb.append("diff?projectName=");
			sb.append(URLEncoder.encode(projectConfigPath, ENCODING));
			sb.append("&oldRevision=");
			sb.append(priorRev);
			sb.append("&newRevision=");
			sb.append(memberRev);		
			sb.append("&selection=");
			sb.append(URLEncoder.encode(memberID, ENCODING));		
			return sb.toString();
		}
		else
		{
			return new String("");
		}
	}
	
	/**
	 * Access function to initialize the prior revision, used for differences view
	 * This will be invoked from the IntegrityCMProject.compareBaseline() function
	 * @param revision
	 */
	public void setPriorRevision(String revision)
	{
		priorRev = revision;
	}
	
	/**
	 * Optionally, one may set a line terminator, if the default is not desired.
	 * Configured under advanced options for the SCM plugin
	 * @param lineTerminator
	 */
	public void setLineTerminator(String lineTerminator)
	{
		this.lineTerminator = lineTerminator;
	}
	
	/**
	 * Optionally, one may choose not to overwrite existing files, this may speed
	 * up the synchronization process if a clear copy is not requested.
	 * @param overwriteExisting
	 */
	public void setOverwriteExisting(String overwriteExisting)
	{
		this.overwriteExisting = overwriteExisting; 
	}
	
	/**
	 * Optionally, one might want to restore the timestamp, if the build
	 * is smart not to recompile files that were not touched.
	 * This option is configured under advanced options for the SCM plugin
	 * @param restoreTimestamp
	 */
	public void setRestoreTimestamp(boolean restoreTime)
	{
		if( restoreTime )
		{
			this.restoreTimestamp = "restoreTimestamp";
		}
		else
		{
			this.restoreTimestamp = "norestoreTimestamp";
		}
	}
	
	/**
	 * Performs a checkout of this MKS Integrity Source File to a 
	 * working file location on the build server represented by targetFile
	 * @param api MKS API Session
	 * @return true if the operation succeeded or false if failed
	 * @throws APIException
	 */
	public boolean checkout(APISession api) throws APIException
	{
		// Make sure the directory is created
		if( ! targetFile.getParentFile().isDirectory() )
		{
			targetFile.getParentFile().mkdirs();
		}
		// Construct the project check-co command
		Command coCMD = new Command(Command.SI, "projectco");
		coCMD.addOption(new Option(overwriteExisting));
		coCMD.addOption(new Option("nolock"));
		coCMD.addOption(new Option("project", projectConfigPath));
		coCMD.addOption(new FileOption("targetFile", targetFile));
		coCMD.addOption(new Option(restoreTimestamp));
		coCMD.addOption(new Option("lineTerminator", lineTerminator));
		coCMD.addOption(new Option("revision", memberRev));
		// Add the member selection
		coCMD.addSelection(memberID);
		
		// Execute the checkout command
		Response res = api.runCommand(coCMD);
		logger.info("Command: " + res.getCommandString() + " completed with exit code " + res.getExitCode());
		
		// Return true if we were successful
		if( res.getExitCode() == 0 )
		{
			return true;
		}
		// Otherwise return false...
		else
		{
			return false;
		}
	}
	
	/**
	 * Performs a revision info on this MKS Integrity Source File
	 * @param api MKS API Session
	 * @return User responsible for making this change
	 * @throws APIException
	 */
	public String getAuthor(APISession api) throws APIException
	{
		// Construct the revision-info command
		Command revInfoCMD = new Command(Command.SI, "revisioninfo");
		revInfoCMD.addOption(new Option("project", projectConfigPath));
		revInfoCMD.addOption(new Option("revision", memberRev));
		// Add the member selection
		revInfoCMD.addSelection(memberID);
		// Execute the revision-info command
		Response res = api.runCommand(revInfoCMD);
		logger.info("Command: " + res.getCommandString() + " completed with exit code " + res.getExitCode());			
		// Return the author associated with this update
		if( res.getExitCode() == 0 )
		{
			return res.getWorkItem(memberID).getField("author").getValueAsString();
		}
		else
		{
			return "unknown";
		}
	}
	
	
	@Override
	public boolean equals(Object o)
	{
		if( o instanceof IntegrityCMMember )
		{
			if( null != o )
			{
				return ((IntegrityCMMember)o).getMemberName().equals(this.getMemberName());
			}
		}
		return false;
	}
}
