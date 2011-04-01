package hudson.scm;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.NoSuchElementException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.mks.api.response.APIException;
import com.mks.api.response.WorkItemIterator;
import com.mks.api.response.WorkItem;
import com.mks.api.response.Field;
import com.mks.api.si.SIModelTypeName;

/**
 * This class represents a MKS Integrity Configuration Management Project
 * Provides metadata information about a Project
 */
public class IntegrityCMProject implements Serializable
{
	private static final long serialVersionUID = 6452315129657215760L;
	public static final String NORMAL_PROJECT = "Normal";
	public static final String VARIANT_PROJECT = "Variant";
	public static final String BUILD_PROJECT = "Build";
		
	private String projectName;
	private String projectType;
	private String fullConfigSyntax;
	private Date lastCheckpoint;
	private String lineTerminator;
	private boolean restoreTimestamp;
	private List<IntegrityCMMember> memberList;
	private List<IntegrityCMMember> newMemberList;
	private List<IntegrityCMMember> updatedMemberList;
	private List<IntegrityCMMember> deletedMemberList;
	private StringBuffer changeLog;
	
    // Create a custom comparator to compare project members
    public static final Comparator<IntegrityCMMember> FILES_ORDER = new Comparator<IntegrityCMMember>(){ 
    	public int compare(IntegrityCMMember cmm1, IntegrityCMMember cmm2) 
    		{
    			return cmm1.getMemberName().compareToIgnoreCase(cmm2.getMemberName());
    		}
    	};	
	
	private static final Log logger = LogFactory.getLog(IntegrityCMProject.class);
	
	/**
	 * Creates an instance of an Integrity CM Project
	 * and extracts all information from the API Response Field
	 * @param wi Work Item associated with the response from running si projectinfo
	 */
	public IntegrityCMProject(WorkItem wi)
	{
		// Initialize the project with default options
		lineTerminator = "native";
		restoreTimestamp = true;
		
		// Initialize the full member list for this project
		memberList = new ArrayList<IntegrityCMMember>();
		// Initialize the new members list, if we need to do a comparison
		newMemberList = new ArrayList<IntegrityCMMember>();		
		// Initialize the updated members list, if we need to do a comparison
		updatedMemberList = new ArrayList<IntegrityCMMember>();
		// Initialize the deleted members list, if we need to do a comparison
		deletedMemberList = new ArrayList<IntegrityCMMember>();
		// Initialize the change log report, if we need to compare with a baseline
		changeLog = new StringBuffer();
		
		// Parse the current project information
		try
		{
			// Get the metadata information about the project 
			Field pjNameFld = wi.getField("projectName");
			Field pjTypeFld = wi.getField("projectType");
			Field pjCfgPathFld = wi.getField("fullConfigSyntax");
			Field pjChkptFld = wi.getField("lastCheckpoint");
			
			// Convert to our class fields
			// First obtain the project name field
			if( null != pjNameFld && null != pjNameFld.getValueAsString() )
			{
				projectName = pjNameFld.getValueAsString();
			}
			else
			{
				logger.warn("Project info did not provide a value for the 'projectName' field!");
				projectName = "";
			}
			// Next, we'll need to know the project type
			if( null != pjTypeFld && null != pjTypeFld.getValueAsString() )
			{
				projectType = pjTypeFld.getValueAsString();
			}
			else
			{
				logger.warn("Project info did not provide a value for the 'projectType' field!");
				projectType = "";
			}
			// Most important is the configuration path
			if( null != pjCfgPathFld && null != pjCfgPathFld.getValueAsString() )
			{
				fullConfigSyntax = pjCfgPathFld.getValueAsString();
			}
			else
			{
				logger.error("Project info did not provide a value for the 'fullConfigSyntax' field!");
				fullConfigSyntax = "";				
			}
			// Finally, we'll need to store the last checkpoint to figure out differences, etc.
			if( null != pjChkptFld && null != pjChkptFld.getDateTime() )
			{
				lastCheckpoint = pjChkptFld.getDateTime();
			}
			else
			{
				logger.warn("Project info did not provide a value for the 'lastCheckpoint' field!");
				lastCheckpoint = Calendar.getInstance().getTime();				
			}			
		}
		catch(NoSuchElementException nsee)
		{
			logger.error("Project info did not provide a value for field " + nsee.getMessage());
		}
	}

	/**
	 * Sets the optional line terminator option for this project
	 * @param lineTerminator
	 */
	public void setLineTerminator(String lineTerminator)
	{
		this.lineTerminator = lineTerminator;
	}

	/**
	 * Sets the optional restore timestamp option for this project
	 * @param restoreTimestamp
	 */
	public void setRestoreTimestamp(boolean restoreTimestamp)
	{
		this.restoreTimestamp = restoreTimestamp;
	}
	
	/**
	 * Parses the output from the si viewproject command to get a list of members
	 * @param wit WorkItemIterator
	 * @param api The current MKS API Session
	 * @throws APIException
	 * @return The list of IntegrityCMMember objects for this project 
	 */
	public void parseProject(WorkItemIterator wit, APISession api) throws APIException
	{
		// Re-initialize the member list for this project
		memberList = new ArrayList<IntegrityCMMember>();
		// Initialize the project config hash
		Hashtable<String, String> pjConfigHash = new Hashtable<String, String>();
		// Add the mapping for the current project
		pjConfigHash.put(this.projectName, this.fullConfigSyntax);
		// Compute the project root directory
		String projectRoot = projectName.substring(0, projectName.lastIndexOf('/'));

		// Iterate through the list of members returned by the API
		while( wit.hasNext() )
		{
			WorkItem wi = wit.next();
			if( wi.getModelType().equals(SIModelTypeName.SI_SUBPROJECT) )
			{
				// Save the configuration path for the current subproject, using the canonical path name
				pjConfigHash.put(wi.getField("name").getValueAsString(), wi.getId());
			}
			else if( wi.getModelType().equals(SIModelTypeName.MEMBER) )
			{
				// Figure out this member's parent project's canonical path name
				String parentProject = wi.getField("parent").getValueAsString();
				// Instantiate our Integrity CM Member object
				IntegrityCMMember iCMMember = new IntegrityCMMember(wi, pjConfigHash.get(parentProject), projectRoot, api);
				// Set the line terminator for this file
				iCMMember.setLineTerminator(lineTerminator);
				// Set the restore timestamp option when checking out this file
				iCMMember.setRestoreTimestamp(restoreTimestamp);
				// Add this to the full list of members in this project
				memberList.add(iCMMember);
			}
			else
			{
				logger.warn("View project output contains an invalid model type: " + wi.getModelType());
			}
		}

		// Sort the files list...
		Collections.sort(memberList, FILES_ORDER);		
		logger.info("Parsing project " + fullConfigSyntax + " complete!");		
	}

	/**
	 * Compares this version of the project to a previous/new version
	 * to determine what are the updates and what was deleted
	 * @param baselineProject
	 */
	public void compareBaseline(IntegrityCMProject baselineProject)
	{
		List<IntegrityCMMember> oldMemberList = baselineProject.getProjectMembers();
	
		// Create a hashtable to hold the old updates for easy access
		Hashtable<String, IntegrityCMMember> oldMemberHash = new Hashtable<String, IntegrityCMMember>();
		// Populate the oldMemberHash
		for( Iterator<IntegrityCMMember> it = oldMemberList.iterator(); it.hasNext(); )
		{
			IntegrityCMMember iMember = it.next();
			oldMemberHash.put(iMember.getMemberName(), iMember);
		}
		
		// Now we will compare the adds and updates between the current project and the baseline
		for( Iterator<IntegrityCMMember> it = memberList.iterator(); it.hasNext(); )
		{
			IntegrityCMMember iMember = it.next();
			IntegrityCMMember oldMember = oldMemberHash.get(iMember.getMemberName());
			// This file was in the previous baseline as well...
			if( null != oldMember )
			{
				// Did it change? Either by an update or roll back (update member revision)?
				if( iMember.getTimestamp().after(oldMember.getTimestamp()) || iMember.getTimestamp().before(oldMember.getTimestamp()))
				{
					// Initialize the prior revision
					iMember.setPriorRevision(oldMember.getRevision());
					// Save this member to the changed list
					updatedMemberList.add(iMember);
				}
				// Remove this member from the old member hashtable, so we'll be left with items that are dropped
				oldMemberHash.remove(oldMember.getMemberName());
			}
			else // We've found a new file
			{
				// Save this member to the new members list
				newMemberList.add(iMember);
			}
		}
		
		// Now, we should be left with the deletes.  Exist only in the old baseline and not the current one.
		Enumeration<IntegrityCMMember> deletedMembers = oldMemberHash.elements();
		while( deletedMembers.hasMoreElements() )
		{
			deletedMemberList.add(deletedMembers.nextElement());
		}
	}		
	
	/**
	 * Returns the Change Log based on the project baseline comparison
	 * This assumes that compareBaseline() has been called already
	 * @return
	 */
	public String getChangeLog(String version, APISession api) throws APIException
	{
		changeLog = new StringBuffer();
		writeChangeLog("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");	
		if( hasProjectChanged() )
		{
			writeChangeLog("<changelog>");
			writeChangeLog(String.format("\t<items version=\"%s\">", version));
			// Process the adds
			for( Iterator<IntegrityCMMember> it = newMemberList.iterator(); it.hasNext(); )
			{
				writeChangeLog(String.format("\t\t<item action=\"%s\">", "add"));
				writeChangeLog(it.next(), api);	
			}
			// Process the updates
			for( Iterator<IntegrityCMMember> it = updatedMemberList.iterator(); it.hasNext(); )
			{
				writeChangeLog(String.format("\t\t<item action=\"%s\">", "update"));
				writeChangeLog(it.next(), api);	
			}
			// Process the drops
			for( Iterator<IntegrityCMMember> it = deletedMemberList.iterator(); it.hasNext(); )
			{
				writeChangeLog(String.format("\t\t<item action=\"%s\">", "delete"));
				writeChangeLog(it.next(), api);	
			}			
			writeChangeLog("\t</items>");				
			writeChangeLog("</changelog>");
		}
		else
		{
			writeChangeLog("<changelog/>");
		}
		
		return changeLog.toString();
	}
	
	/**
	 * Helper function to write details to the Change Log for each member
	 * Convenience method to wrap the details around adds, updates, and deletes
	 * @param iMember
	 * @param api
	 * @throws APIException
	 */
	private void writeChangeLog(IntegrityCMMember iMember, APISession api) throws APIException
	{
		// Write out the other details about the member...
		writeChangeLog(String.format("\t\t\t<file>%s</file>", iMember.getMemberName()));
		writeChangeLog(String.format("\t\t\t<user>%s</user>", iMember.getAuthor()));							
		writeChangeLog(String.format("\t\t\t<revision>%s</revision>", iMember.getRevision()));						    
		writeChangeLog(String.format("\t\t\t<date>%s</date>", IntegritySCM.SDF.format(iMember.getTimestamp())));
		try
		{
			writeChangeLog(String.format("\t\t\t<annotation><![CDATA[%s]]></annotation>", iMember.getAnnotatedLink()));
			writeChangeLog(String.format("\t\t\t<differences><![CDATA[%s]]></differences>", iMember.getDifferencesLink()));
		}
		catch(UnsupportedEncodingException uee)
		{
			logger.warn("Caught Unsupported Encoding Exception while generating MKS Integrity Source links!");
			logger.warn(uee.getMessage());			
		}
		writeChangeLog(String.format("\t\t\t<msg><![CDATA[%s]]></msg>", iMember.getDescription()));
		writeChangeLog("\t\t</item>");																			
	}
	
	/**
	 * Helper function to append to the change log to 
	 * avoid having to repeat the IntegritySCM.NL for every line
	 * @param line
	 */
	private void writeChangeLog(String line)
	{
		changeLog.append(line + IntegritySCM.NL);
	}
	
	/**
	 * Determines whether this project has changed based on a baseline comparison
	 * This assumes that compareBaseline() has been called already
	 * @return
	 */
	public boolean hasProjectChanged()
	{
		if( getChangeCount() > 0 )
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	/**
	 * Returns the total number of changes found from the baseline comparison
	 * This assumes that compareBaseline() has been called already	 * 
	 * @return
	 */
	public int getChangeCount()
	{
		return 	newMemberList.size() + updatedMemberList.size() + deletedMemberList.size();
	}
	
	/**
	 * Returns the previously parsed output from the si viewproject command to get a list of members
	 * @return
	 */
	public List<IntegrityCMMember> getProjectMembers()
	{
		return memberList;
	}
	
	/**
	 * Returns the newly added members to this project based on a baseline comparison
	 * @return
	 */
	public List<IntegrityCMMember> getAddedMembers()
	{
		return newMemberList;
	}

	/**
	 * Returns the updated members to this project based on a baseline comparison
	 * @return
	 */	
	public List<IntegrityCMMember> getUpdatedMembers()
	{
		return updatedMemberList;
	}
	
	/**
	 * Returns the dropped members to this project based on a baseline comparison
	 * @return
	 */	
	public List<IntegrityCMMember> getDroppedMembers()
	{
		return deletedMemberList;
	}
	
	/**
	 * Returns the project path for this Integrity CM Project
	 * @return
	 */
	public String getProjectName()
	{
		return projectName;
	}
	
	/**
	 * Returns true is this is a Normal Project
	 * @return
	 */
	public boolean isNormal()
	{
		if( projectType.equalsIgnoreCase(NORMAL_PROJECT) )
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	/**
	 * Returns true if this is a Variant Project
	 * @return
	 */
	public boolean isVariant()
	{
		if( projectType.equalsIgnoreCase(VARIANT_PROJECT) )
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
	/**
	 * Returns true if this is a Build Project
	 * @return
	 */
	public boolean isBuild()
	{
		if( projectType.equalsIgnoreCase(BUILD_PROJECT) )
		{
			return true;
		}
		else
		{
			return false;
		}
	}	

	/**
	 * Returns the Full Configuration Path for this Integrity CM Project
	 * @return
	 */
	public String getConfigurationPath()
	{
		return fullConfigSyntax;
	}
	
	/**
	 * Returns the date when the last checkpoint was performed on this Project
	 * @return
	 */
	public Date getLastCheckpointDate()
	{
		return lastCheckpoint;
	}
}

