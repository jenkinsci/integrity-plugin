/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/


package hudson.scm;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mks.api.response.WorkItem;
import com.mks.api.si.SIModelTypeName;

import hudson.AbortException;
import hudson.scm.IntegritySCM.DescriptorImpl;
import hudson.scm.api.option.IAPIFields;

/**
 *
 * @author Author: asen
 * @version $Revision: $
 */
public class ParseProjectFolderTask implements Callable<Map<String, String>>
{

  private static final Logger LOGGER = Logger.getLogger(IntegritySCM.class.getSimpleName());
  private final WorkItem wi;
  private PreparedStatement insert = null;
  private Map<String, String> pjConfigHash = new HashMap<String, String>();
  private final String projectRoot;
  private Connection db = null;

  public ParseProjectFolderTask(WorkItem wi, IntegrityCMProject siProject) throws SQLException
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
  }


  @Override
  public Map<String, String> call() throws AbortException, SQLException
  {
    LOGGER.log(Level.FINE, Thread.currentThread().getName()
        + " :: Parse project folder task begin for : " + wi.getField(IAPIFields.NAME).getValueAsString());
    String entryType = (null != wi.getField(IAPIFields.TYPE) ? wi.getField(IAPIFields.TYPE).getValueAsString() : "");

    if (wi.getModelType().equals(SIModelTypeName.SI_SUBPROJECT))
    {
      // Ignore pending subprojects in the tree...
      if (entryType.equalsIgnoreCase("pending-sharesubproject"))
      {
        LOGGER.warning("Parse Folder Task: Skipping " + entryType + " " + wi.getId());
      } else
      {
        try(StringReader reader = new StringReader(""))
        {
          // Save the configuration path for the current subproject, using the canonical path name
          pjConfigHash.put(wi.getField(IAPIFields.NAME).getValueAsString(), wi.getId());
          // Save the relative directory path for this subproject
          String pjDir = wi.getField(IAPIFields.NAME).getValueAsString().substring(projectRoot.length());
          pjDir = pjDir.substring(0, pjDir.lastIndexOf('/'));
          // Save this directory entry
          insert.clearParameters();
          insert.setShort(1, (short) 1); // Type
          insert.setString(2, wi.getField(IAPIFields.NAME).getValueAsString()); // Name
          LOGGER.log(Level.FINEST, Thread.currentThread().getName()
              + " :: Parse Folder Task: Member: " + wi.getField(IAPIFields.NAME).getValueAsString());
          insert.setString(3, wi.getId()); // MemberID
          LOGGER.log(Level.FINEST,
              Thread.currentThread().getName() + " :: Parse Folder Task: MemberID: " + wi.getId());
          insert.setTimestamp(4, new Timestamp(Calendar.getInstance().getTimeInMillis())); // Timestamp
          insert.setClob(5, reader); // Description
          insert.setString(6, wi.getId()); // ConfigPath
          LOGGER.log(Level.FINEST, Thread.currentThread().getName()
              + " :: Parse Folder Task: ConfigPath: " + wi.getId());

          String subProjectRev = "";
          if (wi.contains(IAPIFields.MEMBER_REV))
          {
            subProjectRev = wi.getField(IAPIFields.MEMBER_REV).getItem().getId();
          }
          insert.setString(7, subProjectRev); // Revision
          LOGGER.log(Level.FINEST, Thread.currentThread().getName()
              + " :: Parse Folder Task: Revision: " + subProjectRev);
          insert.setString(8, pjDir); // RelativeFile
          LOGGER.log(Level.FINEST,
              Thread.currentThread().getName() + " :: Parse Folder Task: RelativeFile: " + pjDir);
          LOGGER.log(Level.FINEST, "Attempting to execute query " + insert);
          insert.setString(9, ""); // Cpid
          insert.setShort(10, (short) 0); // Delta defaulted to "No change" for CP mode
          insert.executeUpdate();
        } finally
        {
          // Close the insert statement
          if (null != insert)
            insert.close();

          // Close the database connection
          if (null != db)
            db.close();
        }
      }
    }
    LOGGER.log(Level.FINE, Thread.currentThread().getName()
        + " :: Parse project folder task end for : " + wi.getField(IAPIFields.NAME).getValueAsString());
    return pjConfigHash;
  }

}
