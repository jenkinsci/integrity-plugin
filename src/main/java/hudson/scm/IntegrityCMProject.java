package hudson.scm;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.mks.api.Command;
import com.mks.api.Option;
import com.mks.api.response.APIException;
import com.mks.api.response.Field;
import com.mks.api.response.Response;
import com.mks.api.response.WorkItem;

/**
 * This class represents an Integrity Configuration Management Project
 * Provides metadata information about a SCM Project
 */
public class IntegrityCMProject implements Serializable
{
	private static final long serialVersionUID = 6452315129657215760L;
	private static final Logger LOGGER = Logger.getLogger("IntegritySCM");
	
	public static final String NORMAL_PROJECT = "Normal";
	public static final String VARIANT_PROJECT = "Variant";
	public static final String BUILD_PROJECT = "Build";

	private String projectCacheTable;
	private String projectName;
	private String projectType;
	private String projectRevision;	
	private String fullConfigSyntax;
	private Date lastCheckpoint;
	private String lineTerminator;
	private boolean restoreTimestamp;
	private boolean skipAuthorInfo;
	private boolean checkpointBeforeBuild;

	private Document xmlDoc;
	private StringBuffer changeLog;
	
	/**
	 * Creates an instance of an Integrity CM Project
	 * and extracts all information from the API Response Field
	 * @param wi Work Item associated with the response from running si projectinfo
	 * @param projectCacheTable SCM cache table name for this project configuration
	 */
	public IntegrityCMProject(WorkItem wi, String projectCacheTable)
	{
		// Initialize the project with default options
		lineTerminator = "native";
		restoreTimestamp = true;
		skipAuthorInfo = false;
		
		// Initialize the project's DB location
		this.projectCacheTable = projectCacheTable;
		// Initialize the change log report, if we need to compare with a baseline
		changeLog = new StringBuffer();
		
		// Parse the current output from si projectinfo and cache the contents
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
				LOGGER.warning("Project info did not provide a value for the 'projectName' field!");
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
						LOGGER.warning("Project info did not provide a vale for the 'revision' field!");
					}
				}				
			}
			else
			{
				LOGGER.warning("Project info did not provide a value for the 'projectType' field!");
				projectType = "";
			}
			// Most important is the configuration path
			if( null != pjCfgPathFld && null != pjCfgPathFld.getValueAsString() )
			{
				fullConfigSyntax = pjCfgPathFld.getValueAsString();
			}
			else
			{
				LOGGER.severe("Project info did not provide a value for the 'fullConfigSyntax' field!");
				fullConfigSyntax = "";				
			}
			// Finally, we'll need to store the last checkpoint to figure out differences, etc.
			if( null != pjChkptFld && null != pjChkptFld.getDateTime() )
			{
				lastCheckpoint = pjChkptFld.getDateTime();
			}
			else
			{
				LOGGER.warning("Project info did not provide a value for the 'lastCheckpoint' field!");
				lastCheckpoint = Calendar.getInstance().getTime();				
			}			
		}
		catch(NoSuchElementException nsee)
		{
			LOGGER.severe("Project info did not provide a value for field " + nsee.getMessage());
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
	 * Returns the line terminator setting for this project
	 * @return
	 */
	public String getLineTerminator()
	{
		return lineTerminator;
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
	 * Returns the restore timestamp setting for this project
	 * @return
	 */
	public boolean getRestoreTimestamp()
	{
		return restoreTimestamp;
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
	 * Returns the flag on whether or not to skip author information
	 * @param skipAuthorInfo
	 */
	public boolean getSkipAuthorInfo()
	{
		return skipAuthorInfo;
	}
	
	/**
	 * Returns the Change Log based on the project baseline comparison
	 * This assumes that compareBaseline() has been called already
	 * @return 
	 * @throws DOMException 
	 */
	public String getChangeLog(String version, List<Hashtable<CM_PROJECT, Object>> projectMembersList) throws DOMException
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

			// Create the <items> element
			Element items = xmlDoc.createElement("items");	
			// Set the version attribute to the <items> element
			items.setAttribute("version", version);
			// Append the <items> to the root element <changelog>
			changeLogElem.appendChild(items);
			
			// Process the changes...
			for( Iterator<Hashtable<CM_PROJECT, Object>> it = projectMembersList.iterator(); it.hasNext(); )
			{
				Hashtable<CM_PROJECT, Object> memberInfo = it.next();
				if( null != memberInfo.get(CM_PROJECT.DELTA) )
				{
					short deltaFlag = Short.valueOf(memberInfo.get(CM_PROJECT.DELTA).toString());
					if( deltaFlag > 0 )
					{
						// Create the individual <item> element for the add/update/drop
						Element item = xmlDoc.createElement("item");
						// Set the action attribute
						if( deltaFlag == 1 ){ item.setAttribute("action", "add"); }
						else if( deltaFlag == 2 ){ item.setAttribute("action", "update"); }
						else if( deltaFlag == 3 ){ item.setAttribute("action", "delete"); }
						else{ item.setAttribute("action", "undefined"); }
						// Append the <item> to the <items> element
						items.appendChild(writeChangeLog(item, memberInfo));	
					}
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
			LOGGER.warning("Caught Parser Configuration Exception while generating Change Log!");
			LOGGER.warning(pce.getMessage());			
		}
		catch(TransformerException tfe)
		{
			LOGGER.warning("Caught Transformer Exception while generating Change Log!");
			LOGGER.warning(tfe.getMessage());			
		}
		catch(IOException ioe)
		{
			LOGGER.warning("Caught IO Exception while generating Change Log!");
			LOGGER.warning(ioe.getMessage());			
		}		
				
		return changeLog.toString();
	}
	
	/**
	 * Helper function to append details to the Change Log for each member
	 * Convenience method to wrap the details around adds, updates, and deletes
	 * @param item  XML Element representing the item node
	 * @param memberInfo Hashtable representing the member information
	 */
	private Element writeChangeLog(Element item, Hashtable<CM_PROJECT, Object> memberInfo)
	{
		// Create and append the <file> element
		Element file = xmlDoc.createElement("file");
		file.appendChild(xmlDoc.createTextNode(memberInfo.get(CM_PROJECT.NAME).toString()));
		item.appendChild(file);
		// Create and append the <user> element
		Element user = xmlDoc.createElement("user");
		if(memberInfo != null)
		{
		    Object o = memberInfo.get(CM_PROJECT.AUTHOR);
		    if(o != null)
		    {
		        user.appendChild(xmlDoc.createTextNode(o.toString()));
		        item.appendChild(user);
		    }
		}
		
		// Create and append the <rev> element
		Element revision = xmlDoc.createElement("rev");
		revision.appendChild(xmlDoc.createTextNode(memberInfo.get(CM_PROJECT.REVISION).toString()));
		item.appendChild(revision);
		// Create and append the <date> element
		Element date = xmlDoc.createElement("date");
		date.appendChild(xmlDoc.createTextNode(IntegritySCM.SDF.format((Timestamp)memberInfo.get(CM_PROJECT.TIMESTAMP))));
		item.appendChild(date);
		// Create and append the annotation and differences links
		try
		{
			
			// Add the <annotation> element
			Element annotation = xmlDoc.createElement("annotation");
			annotation.appendChild(xmlDoc.createCDATASection(IntegrityCMMember.getAnnotatedLink(
																memberInfo.get(CM_PROJECT.CONFIG_PATH).toString(),
																memberInfo.get(CM_PROJECT.MEMBER_ID).toString(),
																memberInfo.get(CM_PROJECT.REVISION).toString())));
			item.appendChild(annotation);
			// Add the <differences> element
			Element differences = xmlDoc.createElement("differences");
			String oldRev = (null != memberInfo.get(CM_PROJECT.OLD_REVISION) ? memberInfo.get(CM_PROJECT.OLD_REVISION).toString() : "");
			differences.appendChild(xmlDoc.createCDATASection(oldRev.length() > 0 ? 
																IntegrityCMMember.getDifferencesLink(
																memberInfo.get(CM_PROJECT.CONFIG_PATH).toString(),
																memberInfo.get(CM_PROJECT.MEMBER_ID).toString(),
																memberInfo.get(CM_PROJECT.REVISION).toString(), oldRev) : ""));
			item.appendChild(differences);
		}
		catch(UnsupportedEncodingException uee)
		{
			LOGGER.warning("Caught Unsupported Encoding Exception while generating Integrity Source links!");
			LOGGER.warning(uee.getMessage());			
		}
		
		// Finally, create and append the <msg> element
		Element msg = xmlDoc.createElement("msg");
		msg.appendChild(xmlDoc.createCDATASection(memberInfo.get(CM_PROJECT.DESCRIPTION).toString()));
		item.appendChild(msg);
		
		// Return the updated <item> element
		return item;
	}
	
	/**
	 * Performs a checkpoint on this Integrity CM Project
	 * @param api Authenticated Integrity API Session
	 * @param chkptLabel Checkpoint label string
	 * @return Integrity API Response object
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
	 * @param api Authenticated Integrity API Session
	 * @param chkptLabel Checkpoint label string
	 * @return Integrity API Response object
	 * @throws APIException
	 */
	public Response addProjectLabel(APISession api, String chkptLabel, String projectName, String projectRevision) throws APIException
	{
		// Construct the addprojectlabel command
		Command siAddProjectLabel = new Command(Command.SI, "addprojectlabel");
		// Set the project name
		siAddProjectLabel.addOption(new Option("project", projectName));
		// Set the project revision
		siAddProjectLabel.addOption(new Option("projectRevision", projectRevision));
		// Set the label
		siAddProjectLabel.addOption(new Option("label", chkptLabel));
		// Move the label, if a previous one was applied
		siAddProjectLabel.addOption(new Option("moveLabel"));
		return api.runCommand(siAddProjectLabel);
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

	/** 
	 * Sets if the project is checkpointed before the build (configuration parameter) 
	 * @param checkpointBeforeBuild
	 */
	public void setCheckpointBeforeBuild(boolean checkpointBeforeBuild) 
	{
		this.checkpointBeforeBuild = checkpointBeforeBuild;	
	}
	
	/** 
	 * Returns if the project is checkpointed before the build (configuration parameter) 
	 * @return
	 */
	public boolean getCheckpointBeforeBuild() 
	{
		return checkpointBeforeBuild;
	}
	
	/**
	 * Returns the cache table name for this project configuration
	 */
	public String getProjectCacheTable()
	{
		return projectCacheTable;
	}
}

