/*******************************************************************************
 * Contributors: PTC 2016
 *******************************************************************************/
package hudson.scm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.BooleanUtils;

import com.mks.api.response.APIException;
import com.mks.api.response.Field;
import com.mks.api.response.InterruptedException;
import com.mks.api.response.Item;
import com.mks.api.response.Response;
import com.mks.api.response.WorkItem;
import com.mks.api.response.WorkItemIterator;

import hudson.AbortException;
import hudson.FilePath;
import hudson.scm.IntegritySCM.DescriptorImpl;
import hudson.scm.api.APIUtils;
import hudson.scm.api.ExceptionHandler;
import hudson.scm.api.command.CommandFactory;
import hudson.scm.api.command.IAPICommand;
import hudson.scm.api.option.APIOption;
import hudson.scm.api.option.FileAPIOption;
import hudson.scm.api.option.IAPIFields;
import hudson.scm.api.option.IAPIFields.CP_MEMBER_OPERATION;
import hudson.scm.api.option.IAPIOption;
import hudson.scm.api.session.ISession;

/**
 * This class is intended to represent an Integrity CM Member However, due to scalability
 * constraints, the bulk of the member metadata will be stored in an embedded database. The purpose
 * of this class is to statically assist with various Integrity member operations, like co.
 */
public final class IntegrityCMMember
{
  private static final Logger LOGGER = Logger.getLogger(IntegritySCM.class.getSimpleName());
  private static final String ENCODING = "UTF-8";

  /**
   * Returns only the file name portion for this full server-side member path
   * 
   * @param memberID The full server side path for the Integrity member
   * @return
   */
  public static final String getName(String memberID)
  {
    if (memberID.indexOf('/') > 0)
    {
      return memberID.substring(memberID.lastIndexOf('/') + 1);
    } else if (memberID.indexOf('\\') > 0)
    {
      return memberID.substring(memberID.lastIndexOf('\\') + 1);
    } else
    {
      return memberID;
    }
  }

  /**
   * Returns an URL encoded string representation for invoking this Integrity member's annotated
   * view
   * 
   * @param configPath Full server side path for this Integrity member's project/subproject
   * @param memberID Full server side path for this Integrity member
   * @param memberRev Member revision string for this Integrity member
   * @return
   * @throws UnsupportedEncodingException
   */
  public static final String getAnnotatedLink(String configPath, String memberID, String memberRev)
      throws UnsupportedEncodingException
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
   * Returns an URL encoded string representation for invoking this Integrity member's differences
   * view This assumes that IntegrityCMProject.compareBaseline() was invoked!
   * 
   * @param configPath Full server side path for this Integrity member's project/subproject
   * @param memberID Full server side path for this Integrity member
   * @param memberRev Member revision string for this Integrity member
   * @param oldMemberRev Revision string representing the previous or next revision
   * @return
   */
  public static final String getDifferencesLink(String configPath, String memberID,
      String memberRev, String oldMemberRev) throws UnsupportedEncodingException
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
   * Returns an URL encoded string representation for invoking this Integrity CP view This assumes
   * that IntegrityCMProject.compareBaseline() was invoked!
   * 
   * @param cpid
   * @return
   */
  public static String getViewCP(String cpid)
  {
    StringBuilder sb = new StringBuilder();
    sb.append("viewcp?selection=");
    sb.append(cpid);
    return sb.toString();
  }

  /**
   * Performs a checkout of this Integrity Source File to a working file location on the build
   * server represented by targetFile
   * 
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
  public static final boolean checkout(ISession api, String configPath, String memberID,
      String memberRev, Timestamp memberTimestamp, File targetFile, boolean restoreTimestamp,
      String lineTerminator) throws APIException
  {
    IAPICommand command = CommandFactory.createCommand(IAPICommand.PROJECT_CHECKOUT_COMMAND, null);

    command.addOption(new APIOption(IAPIOption.PROJECT, configPath));
    command.addOption(new FileAPIOption(IAPIOption.TARGET_FILE, targetFile));
    command.addOption(new APIOption(
        restoreTimestamp ? IAPIOption.RESTORE_TIMESTAMP : IAPIOption.NORESTORE_TIMESTAMP));
    command.addOption(new APIOption(IAPIOption.LINE_TERMINATOR, lineTerminator));
    command.addOption(new APIOption(IAPIOption.REVISION, memberRev));
    // Add the member selection
    command.addSelection(memberID);

    command.addAdditionalParameters(IAPIOption.MEMBER_TIMESTAMP, memberTimestamp);
    command.addAdditionalParameters(IAPIOption.RESTORE_TIMESTAMP, restoreTimestamp);
    command.addAdditionalParameters(IAPIOption.TARGET_FILE, targetFile);

    Response response = command.execute(api);
    return BooleanUtils.toBoolean(response.getExitCode(), 0, 1);
  }

  /**
   * Performs a revision info on this Integrity Source File
   * 
   * @param configPath Full project configuration path
   * @param memberID Member ID for this file
   * @param memberRev Member Revision for this file
   * @return User responsible for making this change
   * @throws AbortException
   * @throws APICommandException
   */
  public static String getAuthorFromRevisionInfo(String serverConfigId, String configPath,
      String memberID, String memberRev) throws AbortException
  {
    String author = "unknown";

    // Construct the revision-info command
    IAPICommand command = CommandFactory.createCommand(IAPICommand.REVISION_INFO_COMMAND,
        DescriptorImpl.INTEGRITY_DESCRIPTOR.getConfiguration(serverConfigId));
    command.addOption(new APIOption(IAPIOption.PROJECT, configPath));
    command.addOption(new APIOption(IAPIOption.REVISION, memberRev));
    command.addSelection(memberID);

    Response response;
    try
    {
      response = command.execute();
      author = APIUtils.getAuthorInfo(response, memberID);

    } catch (APIException aex)
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
   * 
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
      fis = new FileInputStream(targetFile);
      result = DigestUtils.md5Hex(fis);
    } catch (FileNotFoundException fnfe)
    {
      result = "";
    } finally
    {
      if (null != fis)
      {
        fis.close();
      }
    }
    return result;
  }

  /**
   * Performs a lock and subsequent project checkin for the specified member
   * 
   * @param ciSettings Integrity API Session
   * @param configPath Full project configuration path
   * @param member Member name for this file
   * @param relativePath Workspace relative file path
   * @param cpid Change Package ID
   * @param desc Checkin description
   * @throws AbortException
   * @throws APIException
   */
  public static final void updateMember(IntegrityConfigurable ciSettings, String configPath,
      FilePath member, String relativePath, String cpid, String desc)
          throws AbortException, APIException
  {
    // Construct the lock command
    IAPICommand command = CommandFactory.createCommand(IAPICommand.LOCK_COMMAND, ciSettings);
    command.addOption(new APIOption(IAPIOption.PROJECT, configPath));
    command.addOption(new APIOption(IAPIOption.CP_ID, cpid));
    command.addSelection(relativePath);

    try
    {
      // Execute the lock command
      command.execute();
      // If the lock was successful, check-in the updates
      LOGGER.fine("Attempting to checkin file: " + member);

      IAPICommand cmd =
          CommandFactory.createCommand(IAPICommand.PROJECT_CHECKIN_COMMAND, ciSettings);
      cmd.addOption(new APIOption(IAPIOption.PROJECT, configPath));
      cmd.addOption(new APIOption(IAPIOption.CP_ID, cpid));
      cmd.addOption(new FileAPIOption(IAPIOption.SOURCE_FILE, new File("" + member)));
      cmd.addOption(new APIOption(IAPIOption.DESCRIPTION, desc));

      cmd.addSelection(relativePath);

      cmd.execute();
    } catch (APIException ae)
    {
      // If the command fails, add only if the error indicates a missing member
      ExceptionHandler eh = new ExceptionHandler(ae);
      String exceptionString = eh.getMessage();

      // Ensure exception is due to member does not exist
      if (exceptionString.indexOf("is not a current or destined or pending member") > 0)
      {
        LOGGER.fine("Lock failed: " + exceptionString);
        LOGGER.fine("Attempting to add file: " + member);

        // Construct the project add command
        IAPICommand addCommand =
            CommandFactory.createCommand(IAPICommand.PROJECT_ADD_COMMAND, ciSettings);
        addCommand.addOption(new APIOption(IAPIOption.PROJECT, configPath));
        addCommand.addOption(new APIOption(IAPIOption.CP_ID, cpid));
        addCommand.addOption(new FileAPIOption(IAPIOption.SOURCE_FILE, new File("" + member)));
        addCommand.addOption(new APIOption(IAPIOption.DESCRIPTION, desc));

        addCommand.addSelection(relativePath);
        // Execute the add command
        addCommand.execute();
      } else
      {
        // Re-throw the error as we need to troubleshoot
        throw ae;
      }
    }
  }

  /**
   * Performs a recursive unlock on all current user's locked members
   * 
   * @param integrityConfig
   * @param configPath Full project configuration path
   * @throws AbortException
   * @throws APIException
   */
  public static final void unlockMembers(IntegrityConfigurable integrityConfig, String configPath)
      throws AbortException, APIException
  {
    // Construct the unlock command
    IAPICommand command = CommandFactory.createCommand(IAPICommand.UNLOCK_COMMAND, integrityConfig);
    command.addOption(new APIOption(IAPIOption.PROJECT, configPath));
    command.execute();
  }

  /**
   * Creates a Change Package for updating Integrity SCM projects
   * 
   * @param Integrity API Session
   * @param itemID Integrity Lifecycle Manager Item ID
   * @param desc Change Package Description
   * @return
   * @throws APIException
   * @throws AbortException
   * @throws InterruptedException
   */
  public static final String createCP(IntegrityConfigurable ciSettings, String itemID, String desc)
      throws APIException, AbortException, InterruptedException
  {
    // Return the generated CP ID
    String cpid = ":none";

    // Check to see if the Item ID contains the magic keyword
    if (":bypass".equalsIgnoreCase(itemID) || "bypass".equalsIgnoreCase(itemID))
    {
      return ":bypass";
    }

    // First figure out what Integrity Item to use for the Change Package
    try
    {
      int intItemID = Integer.parseInt(itemID);
      if (intItemID <= 0)
      {
        LOGGER.fine("Couldn't determine Integrity Item ID, defaulting cpid to ':none'!");
        return cpid;
      }
    } catch (NumberFormatException nfe)
    {
      LOGGER.fine("Couldn't determine Integrity Item ID, defaulting cpid to ':none'!");
      return cpid;
    }

    IAPICommand command = CommandFactory.createCommand(IAPICommand.CREATE_CP_COMMAND, ciSettings);
    command.addOption(new APIOption(IAPIOption.DESCRIPTION, desc));
    command.addOption(new APIOption(IAPIOption.SUMMARY, desc));
    command.addOption(new APIOption(IAPIOption.ITEM_ID, itemID));

    Response res = command.execute();

    // Process the response object
    if (null != res)
    {
      // Parse the response object to extract the CP ID
      if (res.getExitCode() == 0)
      {
        cpid = res.getResult().getPrimaryValue().getId();
      } else // Abort the script is the command failed
      {
        LOGGER.severe("An error occured creating Change Package to check-in build updates!");
      }
    } else
    {
      LOGGER.severe("An error occured creating Change Package to check-in build updates!");
    }

    return cpid;
  }

  /**
   * Submits the change package used for updating the Integrity SCM project
   * 
   * @param ciSettings Integrity API Session
   * @param cpid Change Package ID
   * @throws AbortException
   * @throws APIException
   */
  public static final void submitCP(IntegrityConfigurable ciSettings, String cpid)
      throws APIException, AbortException
  {
    LOGGER.fine("Submitting Change Package: " + cpid);

    IAPICommand command = CommandFactory.createCommand(IAPICommand.CLOSE_CP_COMMAND, ciSettings);
    command.addSelection(cpid);

    // First we'll attempt to close the cp to release locks on files that haven't changed,
    // next we will submit the cp which will submit it for review or
    // it will get automatically closed in the case of transactional cps
    try
    {
      command.execute();
    } catch (APIException ae)
    {
      ExceptionHandler eh = new ExceptionHandler(ae);
      String exceptionString = eh.getMessage();

      // Ensure exception is due to member does not exist
      if (exceptionString.indexOf("has pending entries and can not be closed") > 0)
      {
        LOGGER.fine("Close cp failed: " + exceptionString);
        LOGGER.fine("Attempting to submit cp: " + cpid);

        // Construct the submit cp command
        IAPICommand submitcpcmd =
            CommandFactory.createCommand(IAPICommand.SUBMIT_CP_COMMAND, ciSettings);
        submitcpcmd.addSelection(cpid);

        submitcpcmd.execute();
      } else
      {
        // Re-throw the error as we need to troubleshoot
        throw ae;
      }

    }
  }

  /**
   * View the change package
   * 
   * @param ciSettings Integrity API Session
   * @param projectCPIDs List of Change Package ID
   * @param cpCacheTable
   * @param membersInCP
   * @throws AbortException
   * @throws APIException
   * @throws SQLException
   */
  public static final Map<CPInfo, List<CPMember>> viewCP(IntegrityConfigurable ciSettings,
      Set<String> projectCPIDs, String cpCacheTable, Map<CPInfo, List<CPMember>> membersInCP)
          throws APIException, AbortException, SQLException
  {
    LOGGER.log(Level.FINE, "Retrieving cached CPs.");

    DerbyUtils.getCPCacheTable(DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource(), cpCacheTable);
    projectCPIDs
        .addAll(DerbyUtils.doCPCacheOperations(cpCacheTable, null, null, IAPIFields.GET_OPERATION));

    LOGGER.fine("Viewing Change Package List :" + projectCPIDs.toString());
    if (projectCPIDs.isEmpty())
      return membersInCP;

    IAPICommand command = CommandFactory.createCommand(IAPICommand.VIEW_CP_COMMAND, ciSettings);

    for (Iterator<String> projectCPID = projectCPIDs.iterator(); projectCPID.hasNext();)
      command.addSelection(projectCPID.next());
    Response res = command.execute();

    // Process the response object
    if (null != res && res.getExitCode() == 0)
    {
      for (WorkItemIterator itWrokItem = res.getWorkItems(); itWrokItem.hasNext();)
      {
        WorkItem workItem = itWrokItem.next();
        Field stateField = workItem.getField(IAPIFields.CP_STATE);
        Field idField = workItem.getField(IAPIFields.id);
        String cp = idField.getValueAsString();
        String cpState = stateField.getValueAsString();
        String user = workItem.getField(IAPIFields.USER).getValueAsString();
        LOGGER.fine("CP ID: " + cp + ", State :" + cpState);
        if (cpState.equalsIgnoreCase("Closed"))
        {
          Date closedDate = workItem.getField(IAPIFields.CLOSED_DATE).getDateTime();
          List<CPMember> memberList = new ArrayList<CPMember>();
          Field entriesField = workItem.getField(IAPIFields.MKS_ENTRIES);
          LOGGER.fine("Iterating entries of Change Package " + workItem.toString());

          for (Iterator<Item> it = entriesField.getList().iterator(); it.hasNext();)
          {
            Item entriesInfo = it.next();

            Field memberField = entriesInfo.getField(IAPIFields.CP_MEMBER);
            String member = memberField.getValueAsString();
            Field projectField = entriesInfo.getField(IAPIFields.PROJECT);
            String operationType = entriesInfo.getField(IAPIFields.TYPE).getValueAsString();
            String revision = entriesInfo.getField(IAPIFields.REVISION).getValueAsString();
            String project = projectField.getValueAsString();
            String configpath = entriesInfo.getField(IAPIFields.CONFIG_PATH).getValueAsString();
            String location =
                entriesInfo.getField(IAPIFields.CONFIG_PATH).getValueAsString().replace("#", "");
            if (project.lastIndexOf('/') > 0)
              member = project.substring(0, project.lastIndexOf('/') + 1) + member;

            CPMember cpMember = new CPMember(member, CP_MEMBER_OPERATION.searchEnum(operationType),
                revision, location, configpath, user);

            if (memberList.contains(cpMember))
            {
              boolean removed = false;
              for (Iterator<CPMember> memIt = memberList.iterator(); memIt.hasNext();)
              {
                CPMember inListCpMem = memIt.next();
                if (inListCpMem.getMemberName().equals(cpMember.getMemberName()))
                {
                  String inListCPMemRev = inListCpMem.getRevision().replace(".", "");
                  String incomingRevision = revision.replace(".", "");
                  if (Integer.parseInt(inListCPMemRev) < Integer.parseInt(incomingRevision))
                  {
                    // Consider the latest revision only and consider that revision's operation
                    // for build
                    memIt.remove();
                    removed = true;
                  }
                }
              }
              if (removed)
                memberList.add(cpMember);
            } else
              memberList.add(cpMember);
            LOGGER.fine("Change Package entry:" + member.toString());
          }

          LOGGER.log(Level.FINE,
              "Adding CP members for CP: " + cp + "with Closed Date :" + closedDate);
          membersInCP.put(new CPInfo(cp, closedDate), memberList);

          // Delete the cached CPID, if exists
          LOGGER.log(Level.FINE, "Deleting cached CP : " + cp);
          DerbyUtils.doCPCacheOperations(cpCacheTable, cp, cpState, IAPIFields.DELETE_OPERATION);
        } else
        {
          // Insert CPID with State into Derby
          LOGGER.log(Level.FINE,
              "CP State not closed. Caching CP : " + cp + " with State : " + cpState);
          DerbyUtils.doCPCacheOperations(cpCacheTable, cp, cpState, IAPIFields.ADD_OPERATION);
        }
      }
    } else
    {
      LOGGER.severe("An error occured viewing Change Package!");
    }

    // Sort by closed date
    membersInCP = new TreeMap<CPInfo, List<CPMember>>(membersInCP);
    return membersInCP;
  }

  /**
   *
   * @author Author: asen
   * @version $Revision: $
   */
  public static class CPMember implements Comparable<CPMember>
  {
    private final String memberName;
    private final CP_MEMBER_OPERATION operationType;
    private final String revision;
    private final String location;
    private final String user;
    private final String configpath;

    public CPMember(final String memberName, final CP_MEMBER_OPERATION operation,
        final String revision, final String location, final String configpath, final String user)
    {
      this.memberName = memberName;
      this.operationType = operation;
      this.revision = revision;
      this.location = location;
      this.user = user;
      this.configpath = configpath;
    }

    public String getMemberName()
    {
      return memberName;
    }

    public CP_MEMBER_OPERATION getOperationType()
    {
      return operationType;
    }

    public String getRevision()
    {
      return revision;
    }

    public String getLocation()
    {
      return location;
    }

    public String getUser()
    {
      return user;
    }

    public String getConfigpath()
    {
      return configpath;
    }

    @Override
    public int compareTo(CPMember o)
    {
      return this.memberName.compareTo(o.getMemberName());
    }

    @Override
    public boolean equals(Object obj)
    {
      return this.memberName.equals(((CPMember) obj).getMemberName());
    }
    
    @Override
    public int hashCode() {
    	return Objects.hash(
                super.hashCode(),
                this.memberName);
    }
  }

  /**
   *
   * @author Author: asen
   * @version $Revision: $
   */
  public static class CPInfo implements Comparable<CPInfo>
  {
    private final String id;
    private final Date closedDate;

    public CPInfo(final String id, final Date closedDate)
    {
      this.id = id;
      this.closedDate = closedDate;
    }

    public String getId()
    {
      return id;
    }

    public Date getClosedDate()
    {
      return closedDate;
    }

    @Override
    public int compareTo(CPInfo o)
    {
      return this.closedDate.compareTo(o.getClosedDate());
    }

    @Override
    public boolean equals(Object obj)
    {
      return this.id.equals(((CPInfo) obj).getId());
    }

    @Override
    public int hashCode()
    {
      return this.id.hashCode();
    }
  }

}
