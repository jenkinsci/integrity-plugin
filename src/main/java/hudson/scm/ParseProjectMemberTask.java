// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mks.api.response.Field;
import com.mks.api.response.WorkItem;

import hudson.AbortException;
import hudson.scm.IntegritySCM.DescriptorImpl;


/**
 *
 * @author Author: asen
 * @version $Revision: $
 */
public class ParseProjectMemberTask implements Callable<Void>
{
  private static final Logger LOGGER = Logger.getLogger(IntegritySCM.class.getSimpleName());
  private final WorkItem wi;
  private PreparedStatement insert = null;
  private final String projectRoot;
  private final Map<String, String> pjConfigHash;
  private Connection db = null;

  public ParseProjectMemberTask(WorkItem wi, final Map<String, String> pjConfigHash,
      IntegrityCMProject siProject) throws SQLException
  {
    // Get a connection from our pool
    db = DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource().getPooledConnection().getConnection();
    String insertSQL = DerbyUtils.INSERT_MEMBER_RECORD.replaceFirst("CM_PROJECT",
        siProject.getProjectCacheTable());
    this.insert = db.prepareStatement(insertSQL);
    this.wi = wi;
    // Compute the project root directory
    projectRoot =
        siProject.getProjectName().substring(0, siProject.getProjectName().lastIndexOf('/'));
    this.pjConfigHash = Collections.unmodifiableMap(pjConfigHash);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.concurrent.Callable#call()
   */
  @Override
  public Void call() throws AbortException, SQLException
  {
    LOGGER.log(Level.INFO, Thread.currentThread().getName() + " :: Parse member task begin for : "
        + wi.getField("name").getValueAsString());
    String entryType = (null != wi.getField("type") ? wi.getField("type").getValueAsString() : "");

    // Ignore certain pending operations
    if (entryType.endsWith("in-pending-sub") || entryType.equalsIgnoreCase("pending-add")
        || entryType.equalsIgnoreCase("pending-move-to-update")
        || entryType.equalsIgnoreCase("pending-rename-update"))
    {
      LOGGER.log(Level.WARNING, Thread.currentThread().getName()
          + " :: Parse Member Task: Skipping " + entryType + " " + wi.getId());
    } else
    {
      // Figure out this member's parent project's canonical path name
      String parentProject = wi.getField("parent").getValueAsString();
      // Save this member entry
      String memberName = wi.getField("name").getValueAsString();
      // Figure out the full member path
      LOGGER.log(Level.FINE, Thread.currentThread().getName()
          + " :: Parse Member Task: Member context: " + wi.getContext());
      LOGGER.log(Level.FINE, Thread.currentThread().getName()
          + " :: Parse Member Task: Member parent: " + parentProject);
      LOGGER.log(Level.FINE,
          Thread.currentThread().getName() + " :: Parse Member Task: Member name: " + memberName);

      // Process this member only if we can figure out where to put it in the workspace
      if (memberName.startsWith(projectRoot))
      {
        String description = "";
        // Per JENKINS-19791 some users are getting an exception when attempting
        // to read the 'memberdescription' field in the API response. This is an
        // attempt to catch the exception and ignore it...!
        try
        {
          if (null != wi.getField("memberdescription")
              && null != wi.getField("memberdescription").getValueAsString())
          {
            description =
                DerbyUtils.fixDescription(wi.getField("memberdescription").getValueAsString());
          }
        } catch (NoSuchElementException e)
        {
          // Ignore exception
          LOGGER.log(Level.WARNING,
              Thread.currentThread().getName()
                  + " :: Parse Member Task: Cannot obtain the value for 'memberdescription' in API response for member: "
                  + memberName);
          LOGGER.log(Level.INFO, Thread.currentThread().getName()
              + " :: Parse Member Task: API Response has the following fields available: ");
          for (@SuppressWarnings("unchecked")
          final Iterator<Field> fieldsIterator = wi.getFields(); fieldsIterator.hasNext();)
          {
            Field apiField = fieldsIterator.next();
            LOGGER.log(Level.INFO,
                Thread.currentThread().getName() + " :: Parse Member Task: Name: "
                    + apiField.getName() + ", Value: " + apiField.getValueAsString());
          }
        }

        Date timestamp = new Date();
        // Per JENKINS-25068 some users are getting a null pointer exception when attempting
        // to read the 'membertimestamp' field in the API response. This is an attempt to work
        // around it!
        try
        {
          Field timeFld = wi.getField("membertimestamp");
          if (null != timeFld && null != timeFld.getDateTime())
          {
            timestamp = timeFld.getDateTime();
          }
        } catch (Exception e)
        {
          // Ignore exception
          LOGGER.log(Level.WARNING,
              Thread.currentThread().getName()
                  + " :: Parse Member Task: Cannot obtain the value for 'membertimestamp' in API response for member: "
                  + memberName);
          LOGGER.log(Level.WARNING, Thread.currentThread().getName()
              + " :: Parse Member Task: Defaulting 'membertimestamp' to now - " + timestamp);
        }

        try
        {
          insert.clearParameters();
          insert.setShort(1, (short) 0); // Type
          insert.setString(2, memberName); // Name
          LOGGER.log(Level.FINE, Thread.currentThread().getName()
              + " :: Parse Member Task: Member Name: " + memberName);
          insert.setString(3, wi.getId()); // MemberID
          LOGGER.log(Level.FINE,
              Thread.currentThread().getName() + " :: Parse Member Task: MemberID: " + wi.getId());
          insert.setTimestamp(4, new Timestamp(timestamp.getTime())); // Timestamp
          insert.setClob(5, new StringReader(description)); // Description
          LOGGER.log(Level.FINE, Thread.currentThread().getName()
              + " :: Parse Member Task: Description: " + description);
          insert.setString(6, pjConfigHash.get(parentProject)); // ConfigPath
          LOGGER.log(Level.FINE, Thread.currentThread().getName()
              + " :: Parse Member Task: ConfigPath : " + pjConfigHash.get(parentProject));
          insert.setString(7, wi.getField("memberrev").getItem().getId()); // Revision
          LOGGER.log(Level.FINE, Thread.currentThread().getName()
              + " :: Parse Member Task: Revision: " + wi.getField("memberrev").getItem().getId());
          insert.setString(8, memberName.substring(projectRoot.length())); // RelativeFile (for
                                                                           // workspace)
          LOGGER.log(Level.FINE,
              Thread.currentThread().getName() + " :: Parse Member Task: RelativeFile: "
                  + memberName.substring(projectRoot.length()));
          LOGGER.log(Level.INFO, "Attempting to execute query " + insert);
          insert.executeUpdate();

          db.commit();
        } finally
        {
          // Close the insert statement
          if (null != insert)
            insert.close();

          // Close the database connection
          if (null != db)
            db.close();

        }
      } else
      {
        // Issue warning...
        LOGGER.log(Level.WARNING,
            Thread.currentThread().getName() + " :: Parse Member Task: Skipping " + memberName
                + " it doesn't appear to exist within this project " + projectRoot + "!");
      }
    }
    LOGGER.log(Level.INFO, Thread.currentThread().getName() + " :: Parse member task end for : "
        + wi.getField("name").getValueAsString());
    return null;
  }

}
