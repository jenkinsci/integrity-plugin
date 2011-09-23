package hudson.scm;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
 
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.mks.api.Command;
import com.mks.api.Option;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;
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
	private String projectRevision;	
	private String fullConfigSyntax;
	private Date lastCheckpoint;
	private String lineTerminator;
	private boolean restoreTimestamp;
	private boolean skipAuthorInfo;
	private List<String> dirList;
	private List<IntegrityCMMember> memberList;
	private List<IntegrityCMMember> newMemberList;
	private List<IntegrityCMMember> updatedMemberList;
	private List<IntegrityCMMember> deletedMemberList;
	private Document xmlDoc;
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
		skipAuthorInfo = false;
		
		// Initialize the list of directories in this project
		dirList = new ArrayList<String>();
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
		// Parse the current output from si projectinfo
		initializeProject(wi);
	}

	public void initializeProject(WorkItem wi)
	{
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
				if( isBuild() )
				{
					// Next, we'll need to know the current build checkpoint for this configuration
					Field pjRevFld = wi.getField("revision");
					if( null != pjRevFld && null != pjRevFld.getItem() )
					{
						projectRevision = pjRevFld.getItem().getId();
					}
					else
					{
						projectRevision = "";
						logger.warn("Project info did not provide a vale for the 'revision' field!");
					}
				}				
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
	 * Toggles whether or not to obtain the author using 'si revisioninfo'
	 * @param skipAuthorInfo
	 */
	public void setSkipAuthorInfo(boolean skipAuthorInfo)
	{
		this.skipAuthorInfo = skipAuthorInfo;
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
				// Save the relative directory path for this subproject
				String pjDir = wi.getField("name").getValueAsString().substring(projectRoot.length());
				pjDir = pjDir.substring(0, pjDir.lastIndexOf('/'));
				if( !dirList.contains(pjDir) ){ dirList.add(pjDir); }
			}
			else if( wi.getModelType().equals(SIModelTypeName.MEMBER) )
			{
				// Figure out this member's parent project's canonical path name
				String parentProject = wi.getField("parent").getValueAsString();
				// Instantiate our Integrity CM Member object
				IntegrityCMMember iCMMember = new IntegrityCMMember(wi, pjConfigHash.get(parentProject), projectRoot);
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
		logger.debug("Parsing project " + fullConfigSyntax + " complete!");		
	}

	/**
	 * Compares this version of the project to a previous/new version to determine what are the updates and what was deleted
	 * @param baselineProject The previous baseline (build) for this Integrity CM Project
	 * @param api The current MKS API Session to obtain the author information
	 */
	public void compareBaseline(IntegrityCMProject baselineProject, APISession api)
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
					// Initialize the author information as requested
					if( ! skipAuthorInfo ){ iMember.setAuthor(api); }
					// Save this member to the changed list
					updatedMemberList.add(iMember);
				}
				else
				{
					// This member did not change, so lets copy its author information
					iMember.setAuthor(oldMember.getAuthor());
				}
				
				// Remove this member from the old member hashtable, so we'll be left with items that are dropped
				oldMemberHash.remove(oldMember.getMemberName());
			}
			else // We've found a new file
			{
				// Initialize the author information as requested
				if( ! skipAuthorInfo ){ iMember.setAuthor(api); }				
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
		try
		{
			// Initialize the XML document builder
			DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			// Initialize the XML Document and change log
			xmlDoc = docBuilder.newDocument();
			changeLog = new StringBuffer();
			// Create the root <changelog> element
			Element changeLogElem = xmlDoc.createElement("changelog");
			// Add the <changelog> to the xmlDoc
			xmlDoc.appendChild(changeLogElem);

			// Add the change log details, if the project has changed 
			if( hasProjectChanged() )
			{
				// Create the <items> element
				Element items = xmlDoc.createElement("items");	
				// Set the version attribute to the <items> element
				items.setAttribute("version", version);
				// Append the <items> to the root element <changelog>
				changeLogElem.appendChild(items);
				
				// Process the adds
				for( Iterator<IntegrityCMMember> it = newMemberList.iterator(); it.hasNext(); )
				{
					// Create the individual <item> element for the add
					Element item = xmlDoc.createElement("item");
					// Set the action attribute
					item.setAttribute("action", "add");
					// Append the <item> to the <items> element
					items.appendChild(writeChangeLog(item, it.next(), api));	
				}
				// Process the updates
				for( Iterator<IntegrityCMMember> it = updatedMemberList.iterator(); it.hasNext(); )
				{
					// Create the individual <item> element for the update
					Element item = xmlDoc.createElement("item");
					// Set the action attribute
					item.setAttribute("action", "update");
					// Append the <item> to the <items> element
					items.appendChild(writeChangeLog(item, it.next(), api));	
				}
				// Process the drops
				for( Iterator<IntegrityCMMember> it = deletedMemberList.iterator(); it.hasNext(); )
				{
					// Create the individual <item> element for the drops
					Element item = xmlDoc.createElement("item");
					// Set the action attribute
					item.setAttribute("action", "delete");
					// Append the <item> to the <items> element
					items.appendChild(writeChangeLog(item, it.next(), api));
				}			
			}
		 
			// Write the content into a String
			TransformerFactory tfactory = TransformerFactory.newInstance();
        	Transformer serializer = tfactory.newTransformer();
            // Setup indenting for a readable output
            serializer.setOutputProperty(OutputKeys.INDENT, "yes");
            serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StringWriter sw = new StringWriter();
            serializer.transform(new DOMSource(xmlDoc), new StreamResult(sw));
            changeLog.append(sw.toString());
            sw.close();
		}
		catch(ParserConfigurationException pce)
		{
			logger.warn("Caught Parser Configuration Exception while generating Change Log!");
			logger.warn(pce.getMessage());			
		}
		catch(TransformerException tfe)
		{
			logger.warn("Caught Transformer Exception while generating Change Log!");
			logger.warn(tfe.getMessage());			
		}
		catch(IOException ioe)
		{
			logger.warn("Caught IO Exception while generating Change Log!");
			logger.warn(ioe.getMessage());			
		}		
				
		return changeLog.toString();
	}
	
	/**
	 * Helper function to append details to the Change Log for each member
	 * Convenience method to wrap the details around adds, updates, and deletes
	 * @param item  XML Element representing the item node
	 * @param iMember Integrity CM Member
	 * @param api MKS API Session
	 * @throws APIException
	 */
	private Element writeChangeLog(Element item, IntegrityCMMember iMember, APISession api) throws APIException
	{
		// Create and append the <file> element
		Element file = xmlDoc.createElement("file");
		file.appendChild(xmlDoc.createTextNode(iMember.getMemberName()));
		item.appendChild(file);
		// Create and append the <user> element
		Element user = xmlDoc.createElement("user");
		user.appendChild(xmlDoc.createTextNode(iMember.getAuthor()));
		item.appendChild(user);
		// Create and append the <rev> element
		Element revision = xmlDoc.createElement("rev");
		revision.appendChild(xmlDoc.createTextNode(iMember.getRevision()));
		item.appendChild(revision);
		// Create and append the <date> element
		Element date = xmlDoc.createElement("date");
		date.appendChild(xmlDoc.createTextNode(IntegritySCM.SDF.format(iMember.getTimestamp())));
		item.appendChild(date);
		// Create and append the annotation and differences links
		try
		{
			// Add the <annotation> element
			Element annotation = xmlDoc.createElement("annotation");
			annotation.appendChild(xmlDoc.createCDATASection(iMember.getAnnotatedLink()));
			item.appendChild(annotation);
			// Add the <differences> element
			Element differences = xmlDoc.createElement("differences");
			differences.appendChild(xmlDoc.createCDATASection(iMember.getDifferencesLink()));
			item.appendChild(differences);
		}
		catch(UnsupportedEncodingException uee)
		{
			logger.warn("Caught Unsupported Encoding Exception while generating MKS Integrity Source links!");
			logger.warn(uee.getMessage());			
		}
		// Finally, create and append the <msg> element
		Element msg = xmlDoc.createElement("msg");
		msg.appendChild(xmlDoc.createTextNode(iMember.getDescription()));
		item.appendChild(msg);
		
		// Return the updated <item> element
		return item;
	}
	
	/**
	 * Performs a checkpoint on this Integrity CM Project
	 * @param api Authenticated MKS API Session
	 * @param chkptLabel Checkpoint label string
	 * @return MKS API Response object
	 * @throws APIException
	 */
	public Response checkpoint(APISession api, String chkptLabel) throws APIException
	{
		// Construct the checkpoint command
		Command siCheckpoint = new Command(Command.SI, "checkpoint");
		// Set the project name
		siCheckpoint.addOption(new Option("project", fullConfigSyntax));
		// Set the label and description if applicable
		if( null != chkptLabel && chkptLabel.length() > 0 )
		{
			// Set the label
			siCheckpoint.addOption(new Option("label", chkptLabel));
			// Set the description
			siCheckpoint.addOption(new Option("description", chkptLabel));
		}
		return api.runCommand(siCheckpoint);
	}
	
	/**
	 * Applies a Project Label on this Integrity CM Project
	 * @param api Authenticated MKS API Session
	 * @param chkptLabel Checkpoint label string
	 * @return MKS API Response object
	 * @throws APIException
	 */
	public Response addProjectLabel(APISession api, String chkptLabel) throws APIException
	{
		// Construct the addprojectlabel command
		Command siAddProjectLabel = new Command(Command.SI, "addprojectlabel");
		// Set the project name
		siAddProjectLabel.addOption(new Option("project", fullConfigSyntax));
		// Set the label
		siAddProjectLabel.addOption(new Option("label", chkptLabel));
		// Move the label, if a previous one was applied
		siAddProjectLabel.addOption(new Option("moveLabel"));
		return api.runCommand(siAddProjectLabel);
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
	 * Returns a string list of relative paths to all directories in this project
	 * @return
	 */
	public List<String> getDirList()
	{
		return dirList;
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
	 * Returns the project revision for this Integrity SCM Project
	 * @return
	 */
	public String getProjectRevision()
	{
		return projectRevision;
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

