package hudson.scm;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

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
import com.mks.api.response.WorkItemIterator;
import com.mks.api.si.SIModelTypeName;

/**
 * This class represents an Integrity Configuration Management Project
 * Provides metadata information about a SCM Project
 */
public class IntegrityCMProject implements Serializable
{
	private static final long serialVersionUID = 6452315129657215760L;
	public static final String NORMAL_PROJECT = "Normal";
	public static final String VARIANT_PROJECT = "Variant";
	public static final String BUILD_PROJECT = "Build";

	private File projectDB;
	private String projectName;
	private String projectType;
	private String projectRevision;	
	private String fullConfigSyntax;
	private Date lastCheckpoint;
	private String lineTerminator;
	private boolean restoreTimestamp;
	private boolean skipAuthorInfo;

	private Document xmlDoc;
	private StringBuffer changeLog;
	private transient int changeCount;
	
	/**
	 * Creates an instance of an Integrity CM Project
	 * and extracts all information from the API Response Field
	 * @param wi Work Item associated with the response from running si projectinfo
	 * @param projectDB Location of where the embedded derby database for this project
	 */
	public IntegrityCMProject(WorkItem wi, File projectDB)
	{
		// Initialize the project with default options
		lineTerminator = "native";
		restoreTimestamp = true;
		skipAuthorInfo = false;
		
		// Initialize the project's DB location
		this.projectDB = projectDB;
		
		// Initialize the change log report, if we need to compare with a baseline
		changeLog = new StringBuffer();
		changeCount = 0;
		
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
				Logger.warn("Project info did not provide a value for the 'projectName' field!");
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
						Logger.warn("Project info did not provide a vale for the 'revision' field!");
					}
				}				
			}
			else
			{
				Logger.warn("Project info did not provide a value for the 'projectType' field!");
				projectType = "";
			}
			// Most important is the configuration path
			if( null != pjCfgPathFld && null != pjCfgPathFld.getValueAsString() )
			{
				fullConfigSyntax = pjCfgPathFld.getValueAsString();
			}
			else
			{
				Logger.error("Project info did not provide a value for the 'fullConfigSyntax' field!");
				fullConfigSyntax = "";				
			}
			// Finally, we'll need to store the last checkpoint to figure out differences, etc.
			if( null != pjChkptFld && null != pjChkptFld.getDateTime() )
			{
				lastCheckpoint = pjChkptFld.getDateTime();
			}
			else
			{
				Logger.warn("Project info did not provide a value for the 'lastCheckpoint' field!");
				lastCheckpoint = Calendar.getInstance().getTime();				
			}			
		}
		catch(NoSuchElementException nsee)
		{
			Logger.error("Project info did not provide a value for field " + nsee.getMessage());
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
	 * Opens a new connection to the embedded Integrity SCM Project cache db
	 * @return Connection to the embedded derby database
	 * @throws SQLException 
	 */
	public Connection openProjectDB() throws SQLException
	{
		return DerbyUtils.createDBConnection(projectDB);
	}
	
	/**
	 * Closes the connections to the embedded derby database
	 */
	public void closeProjectDB()
	{
		DerbyUtils.shutdownDB(projectDB);
	}
	
	/**
	 * Parses the output from the si viewproject command to get a list of members
	 * @param wit WorkItemIterator
	 * @throws APIException 
	 * @throws SQLException 
	 */
	public void parseProject(WorkItemIterator wit) throws APIException, SQLException
	{
		// Setup the Derby DB for this Project
		Connection db = openProjectDB();
		PreparedStatement insert = null;
		try
		{
			// Create a fresh set of tables for this project
			DerbyUtils.createCMProjectTables(db);
			// Initialize the project config hash
			Hashtable<String, String> pjConfigHash = new Hashtable<String, String>();
			// Add the mapping for the current project
			pjConfigHash.put(this.projectName, this.fullConfigSyntax);
			// Compute the project root directory
			String projectRoot = projectName.substring(0, projectName.lastIndexOf('/'));
	
			// Iterate through the list of members returned by the API
			Logger.debug("Attempting to execute query " + DerbyUtils.INSERT_MEMBER_RECORD);
			insert = db.prepareStatement(DerbyUtils.INSERT_MEMBER_RECORD);
			
			
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
					// Save this directory entry
					insert.clearParameters();
					insert.setShort(1, (short)1);														// Type
					insert.setString(2, wi.getField("name").getValueAsString());						// Name
					insert.setString(3, wi.getId());													// MemberID
					insert.setTimestamp(4, new Timestamp(Calendar.getInstance().getTimeInMillis()));	// Timestamp
					insert.setClob(5, new StringReader(""));											// Description
					insert.setString(6, wi.getId());													// ConfigPath
					insert.setString(7, "");															// Revision
					insert.setString(8, pjDir);															// RelativeFile
					insert.executeUpdate();
				}
				else if( wi.getModelType().equals(SIModelTypeName.MEMBER) )
				{
					// Figure out this member's parent project's canonical path name
					String parentProject = wi.getField("parent").getValueAsString();				
					// Save this member entry
					String memberName = wi.getField("name").getValueAsString();
					String description = "";
					if( null != wi.getField("memberdescription") && null != wi.getField("memberdescription").getValueAsString() )
					{
						description = wi.getField("memberdescription").getValueAsString();
						// Char 8211 which is a long dash causes problems for the change log XML, need to fix it!
						description = description.replace((char)8211, '-');
					}
					insert.clearParameters();
					insert.setShort(1, (short)0);														// Type
					insert.setString(2, memberName);													// Name
					insert.setString(3, wi.getId());													// MemberID
					insert.setTimestamp(4, new Timestamp(wi.getField("membertimestamp").getDateTime().getTime()));	// Timestamp
					insert.setClob(5, new StringReader(description));									// Description
					insert.setString(6, pjConfigHash.get(parentProject));								// ConfigPath
					insert.setString(7, wi.getField("memberrev").getItem().getId());					// Revision
					insert.setString(8, memberName.substring(projectRoot.length()));					// RelativeFile
					insert.executeUpdate();				
				}
				else
				{
					Logger.warn("View project output contains an invalid model type: " + wi.getModelType());
				}
			}
			
			// Commit to the database
			db.commit();
		}
		finally
		{
			// Close the insert statement
			if( null != insert ){ insert.close(); }
			
			// Close the database connection
			if( null != db ){ db.close(); }
		}

		// Log the completion of this operation
		Logger.debug("Parsing project " + fullConfigSyntax + " complete!");		
	}

	/**
	 * Updates the author information for all the members in the project
	 * @param api
	 * @throws SQLException
	 * @throws IOException
	 */
	public void primeAuthorInformation(APISession api) throws SQLException, IOException
	{
		Connection db = openProjectDB();
		Statement authSelect = null;
		ResultSet rs = null;
		try
		{
			// Create the select statement for the current project
			authSelect = db.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			rs = authSelect.executeQuery(DerbyUtils.AUTHOR_SELECT);
			while( rs.next() )
			{
				Hashtable<CM_PROJECT, Object> rowHash = DerbyUtils.getRowData(rs);
				rs.updateString(CM_PROJECT.AUTHOR.toString(), 
						IntegrityCMMember.getAuthor(api, 
											rowHash.get(CM_PROJECT.CONFIG_PATH).toString(),
											rowHash.get(CM_PROJECT.MEMBER_ID).toString(),
											rowHash.get(CM_PROJECT.REVISION).toString()));
				rs.updateRow();
			}
			
			// Commit the updates
			db.commit();
		}
		finally
		{
			// Release the result set
			if( null != rs ){ rs.close(); }
			
			// Release the statement
			if( null != authSelect ){ authSelect.close(); }
			
			// Close project db connections
			if( null != db ){ db.close(); }
		}
	}
	
	/**
	 * Updates the underlying Integrity SCM Project table cache with the new checksum information
	 * @param checksumHash Checksum hashtable generated from a checkout operation
	 * @throws SQLException
	 * @throws IOException
	 */
	public void updateChecksum(ConcurrentHashMap<String, String> checksumHash) throws SQLException, IOException
	{
		Connection db = openProjectDB();
		Statement checksumSelect = null;
		ResultSet rs = null;
		try
		{
			// Create the select statement for the current project
			checksumSelect = db.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			rs = checksumSelect.executeQuery(DerbyUtils.CHECKSUM_UPDATE);
			while( rs.next() )
			{
				Hashtable<CM_PROJECT, Object> rowHash = DerbyUtils.getRowData(rs);
				String newChecksum = checksumHash.get(rowHash.get(CM_PROJECT.NAME).toString());
				if( null != newChecksum && newChecksum.length() > 0 )
				{
					rs.updateString(CM_PROJECT.CHECKSUM.toString(), newChecksum);
					rs.updateRow();
				}
			}
			
			// Commit the updates
			db.commit();
		}
		finally
		{
			// Release the result set
			if( null != rs ){ rs.close(); }
			
			// Release the statement
			if( null != checksumSelect ){ checksumSelect.close(); }
			
			// Close project db connections
			if( null != db ){ db.close(); }
		}
	}
	
	/**
	 * Compares this version of the project to a previous/new version to determine what are the updates and what was deleted
	 * @param baselineProjectDB The previous baseline (build) for this Integrity CM Project
	 * @param api The current Integrity API Session to obtain the author information
	 * @param return The total number of changes found in the comparison
	 * @throws SQLException 
	 * @throws IOException 
	 */
	public int compareBaseline(File baselineProjectDB, APISession api) throws SQLException, IOException
	{
		// Re-initialize our return variable
		changeCount = 0;
		
		// Open connections to the embedded Integrity SCM Project cache databases
		Connection baselineDB = DerbyUtils.createDBConnection(baselineProjectDB);
		Connection db = openProjectDB();
		Statement baselineSelect = null;
		Statement pjSelect = null;
		ResultSet baselineRS = null;
		ResultSet rs = null;
		
		try
		{			
			// Create the select statement for the previous baseline
			baselineSelect = baselineDB.createStatement();
			Logger.debug("Attempting to execute query " + DerbyUtils.BASELINE_SELECT);
			baselineRS = baselineSelect.executeQuery(DerbyUtils.BASELINE_SELECT);
		
			// Create a hashtable to hold the old baseline for easy comparison
			Hashtable<String, Hashtable<CM_PROJECT,Object>> baselinePJ = new Hashtable<String, Hashtable<CM_PROJECT,Object>>();
			while( baselineRS.next() )
			{
				Hashtable<CM_PROJECT, Object> rowHash = DerbyUtils.getRowData(baselineRS);
				Hashtable<CM_PROJECT, Object> memberInfo = new Hashtable<CM_PROJECT, Object>();
				memberInfo.put(CM_PROJECT.MEMBER_ID, (null == rowHash.get(CM_PROJECT.MEMBER_ID) ? "" : rowHash.get(CM_PROJECT.MEMBER_ID).toString()));
				memberInfo.put(CM_PROJECT.TIMESTAMP, (null == rowHash.get(CM_PROJECT.TIMESTAMP) ? "" : (Date)rowHash.get(CM_PROJECT.TIMESTAMP)));
				memberInfo.put(CM_PROJECT.DESCRIPTION, (null == rowHash.get(CM_PROJECT.DESCRIPTION) ? "" : rowHash.get(CM_PROJECT.DESCRIPTION).toString()));
				memberInfo.put(CM_PROJECT.AUTHOR, (null == rowHash.get(CM_PROJECT.AUTHOR) ? "" : rowHash.get(CM_PROJECT.AUTHOR).toString()));
				memberInfo.put(CM_PROJECT.CONFIG_PATH, (null == rowHash.get(CM_PROJECT.CONFIG_PATH) ? "" : rowHash.get(CM_PROJECT.CONFIG_PATH).toString()));
				memberInfo.put(CM_PROJECT.REVISION, (null == rowHash.get(CM_PROJECT.REVISION) ? "" : rowHash.get(CM_PROJECT.REVISION).toString()));
				memberInfo.put(CM_PROJECT.RELATIVE_FILE, (null == rowHash.get(CM_PROJECT.RELATIVE_FILE) ? "" : rowHash.get(CM_PROJECT.RELATIVE_FILE).toString()));
				memberInfo.put(CM_PROJECT.CHECKSUM, (null == rowHash.get(CM_PROJECT.CHECKSUM) ? "" : rowHash.get(CM_PROJECT.CHECKSUM).toString()));
				baselinePJ.put(rowHash.get(CM_PROJECT.NAME).toString(), memberInfo);
			}
			
			// Create the select statement for the current project
			pjSelect = db.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			Logger.debug("Attempting to execute query " + DerbyUtils.DELTA_SELECT);
			rs = pjSelect.executeQuery(DerbyUtils.DELTA_SELECT);
			
			// Now we will compare the adds and updates between the current project and the baseline
			for( int i = 1; i <= DerbyUtils.getRowCount(rs); i++ )
			{
				// Move the cursor to the current record
				rs.absolute(i);
				Hashtable<CM_PROJECT, Object> rowHash = DerbyUtils.getRowData(rs);
				// Obtain the member we're working with
				String memberName = rowHash.get(CM_PROJECT.NAME).toString();
				// Get the baseline project information for this member
				Logger.debug("Comparing file against baseline " + memberName);
				Hashtable<CM_PROJECT, Object> baselineMemberInfo = baselinePJ.get(memberName);
				// This file was in the previous baseline as well...
				if( null != baselineMemberInfo )
				{
					// Did it change? Either by an update or roll back (update member revision)?
					String oldRevision = baselineMemberInfo.get(CM_PROJECT.REVISION).toString();
					if( ! rowHash.get(CM_PROJECT.REVISION).toString().equals(oldRevision) )
					{
						// Initialize the prior revision
						rs.updateString(CM_PROJECT.OLD_REVISION.toString(), oldRevision);
						// Initialize the author information as requested
						if( ! skipAuthorInfo ){ rs.updateString(CM_PROJECT.AUTHOR.toString(), 
													IntegrityCMMember.getAuthor(api, 
													rowHash.get(CM_PROJECT.CONFIG_PATH).toString(),
													rowHash.get(CM_PROJECT.MEMBER_ID).toString(),
													rowHash.get(CM_PROJECT.REVISION).toString())); }
						// Initialize the delta flag for this member
						rs.updateShort(CM_PROJECT.DELTA.toString(), (short)2);
						changeCount++;
					}
					else
					{
						// This member did not change, so lets copy its old author information
						if( null != baselineMemberInfo.get(CM_PROJECT.AUTHOR) )
						{
							rs.updateString(CM_PROJECT.AUTHOR.toString(), baselineMemberInfo.get(CM_PROJECT.AUTHOR).toString());
						}
						// Also, lets copy over the previous MD5 checksum
						if( null != baselineMemberInfo.get(CM_PROJECT.CHECKSUM) )
						{
							rs.updateString(CM_PROJECT.CHECKSUM.toString(), baselineMemberInfo.get(CM_PROJECT.CHECKSUM).toString());
						}
						// Initialize the delta flag
						rs.updateShort(CM_PROJECT.DELTA.toString(), (short)0);
					}
					
					// Remove this member from the baseline project hashtable, so we'll be left with items that are dropped
					baselinePJ.remove(memberName);
				}
				else // We've found a new file
				{
					// Initialize the author information as requested
					if( ! skipAuthorInfo ){ rs.updateString(CM_PROJECT.AUTHOR.toString(), 
												IntegrityCMMember.getAuthor(api, 
												rowHash.get(CM_PROJECT.CONFIG_PATH).toString(),
												rowHash.get(CM_PROJECT.MEMBER_ID).toString(),
												rowHash.get(CM_PROJECT.REVISION).toString())); }				
					// Initialize the delta flag for this member
					rs.updateShort(CM_PROJECT.DELTA.toString(), (short)1);
					changeCount++;
				}
				
				// Update this row in the data source
				rs.updateRow();				
			}
			
			// Now, we should be left with the drops.  Exist only in the old baseline and not the current one.
			Enumeration<String> deletedMembers = baselinePJ.keys();
			while( deletedMembers.hasMoreElements() )
			{
				changeCount++;
				String memberName = deletedMembers.nextElement();
				Hashtable<CM_PROJECT, Object> memberInfo = baselinePJ.get(memberName);
				
				// Add the deleted members to the database
				rs.moveToInsertRow();
				rs.updateShort(CM_PROJECT.TYPE.toString(), (short)0);
				rs.updateString(CM_PROJECT.NAME.toString(), memberName);
				rs.updateString(CM_PROJECT.MEMBER_ID.toString(), memberInfo.get(CM_PROJECT.MEMBER_ID).toString());
				if( memberInfo.get(CM_PROJECT.TIMESTAMP) instanceof java.util.Date )
				{
					Timestamp ts = new Timestamp(((Date)memberInfo.get(CM_PROJECT.TIMESTAMP)).getTime());
					rs.updateTimestamp(CM_PROJECT.TIMESTAMP.toString(), ts);
				}
				rs.updateString(CM_PROJECT.DESCRIPTION.toString(), memberInfo.get(CM_PROJECT.DESCRIPTION).toString());
				rs.updateString(CM_PROJECT.AUTHOR.toString(), memberInfo.get(CM_PROJECT.AUTHOR).toString());
				rs.updateString(CM_PROJECT.CONFIG_PATH.toString(), memberInfo.get(CM_PROJECT.CONFIG_PATH).toString());
				rs.updateString(CM_PROJECT.REVISION.toString(), memberInfo.get(CM_PROJECT.REVISION).toString());
				rs.updateString(CM_PROJECT.RELATIVE_FILE.toString(), memberInfo.get(CM_PROJECT.RELATIVE_FILE).toString());
				rs.updateShort(CM_PROJECT.DELTA.toString(), (short)3);
				rs.insertRow();
				rs.moveToCurrentRow();
			}

			// Commit changes to the database...
			db.commit();
		}
		finally
		{
			// Close the result set and select statements
			if( null != baselineRS ){ baselineRS.close(); }
			if( null != rs ){ rs.close(); }			
			if( null != baselineSelect ){ baselineSelect.close(); }
			if( null != pjSelect ){ pjSelect.close(); }			
			
			// Close DB connections
			if( null != baselineDB ){ baselineDB.close(); }
			if( null != db ){ db.close(); }
			
			// Shutdown the baseline project DB
			DerbyUtils.shutdownDB(baselineProjectDB);
		}
		
		return changeCount;
	}		
	
	/**
	 * Project access function that returns the state of the current project
	 * NOTE: For maximum efficiency, this should be called only once and after the compareBasline() has been invoked!
	 * @return A List containing every member in this project, including any dropped artifacts
	 * @throws SQLException
	 * @throws IOException
	 */
	public List<Hashtable<CM_PROJECT, Object>> viewProject() throws SQLException, IOException
	{
		// Initialize our return variable
		List<Hashtable<CM_PROJECT, Object>> projectMembersList = new ArrayList<Hashtable<CM_PROJECT, Object>>();
		
		// Initialize our db connection
		Connection db = openProjectDB();
		Statement stmt = null;
		ResultSet rs = null;
		
		try
		{
			stmt = db.createStatement();
			rs = stmt.executeQuery(DerbyUtils.PROJECT_SELECT);
			while( rs.next() )
			{
				projectMembersList.add(DerbyUtils.getRowData(rs));
			}
		}
		finally
		{
			// Close the database resources
			if( null != rs ){ rs.close(); }
			if( null != stmt ){ stmt.close(); }
			if( null != db ){ db.close(); }
		}
		
		return projectMembersList;
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

			// Add the change log details, if the project has changed 
			if( changeCount > 0 )
			{
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
			Logger.warn("Caught Parser Configuration Exception while generating Change Log!");
			Logger.warn(pce.getMessage());			
		}
		catch(TransformerException tfe)
		{
			Logger.warn("Caught Transformer Exception while generating Change Log!");
			Logger.warn(tfe.getMessage());			
		}
		catch(IOException ioe)
		{
			Logger.warn("Caught IO Exception while generating Change Log!");
			Logger.warn(ioe.getMessage());			
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
			Logger.warn("Caught Unsupported Encoding Exception while generating Integrity Source links!");
			Logger.warn(uee.getMessage());			
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
	 * Returns a string list of relative paths to all directories in this project
	 * @return
	 * @throws SQLException 
	 * @throws IOException 
	 */
	public List<String> getDirList() throws SQLException, IOException
	{
		// Initialize our return variable
		List<String> dirList = new ArrayList<String>();
		
		// Initialize our db connection
		Connection db = openProjectDB();
		Statement stmt = null;
		ResultSet rs = null;
		
		try
		{
			stmt = db.createStatement();
			rs = stmt.executeQuery(DerbyUtils.DIR_SELECT);
			while( rs.next() )
			{
				Hashtable<CM_PROJECT, Object> rowData = DerbyUtils.getRowData(rs); 
				dirList.add(rowData.get(CM_PROJECT.RELATIVE_FILE).toString());
			}
		}
		finally
		{
			// Close the database resources
			if( null != rs ){ rs.close(); }
			if( null != stmt ){ stmt.close(); }
			if( null != db ){ db.close(); }
		}
		
		return dirList;
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

