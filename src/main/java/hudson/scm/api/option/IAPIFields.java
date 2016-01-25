/*******************************************************************************
 * Contributors: PTC 2016
 *******************************************************************************/


package hudson.scm.api.option;

/**
 * Constants interface
 * 
 * @author Author: vlambat
 * @version $Revision: $
 */
public interface IAPIFields
{
  public static final String id = "id";
  public static final String ID = "ID";
  public static final String TYPE = "type";
  public static final String NAME = "name";
  public static final String USER = "user";
  public static final String CP_ID = "cpid";
  public static final String PARENT = "parent";
  public static final String CP_STATE = "state";
  public static final String CONTEXT = "context";
  public static final String PROJECT = "project";
  public static final String CP_MEMBER = "member";
  public static final String REVISION = "revision";
  public static final String FIELD_SEPARATOR = ",";
  public static final String RESULTANT = "resultant";
  public static final String MEMBER_REV = "memberrev";
  public static final String CP_ENTRIES = "CPEntries";
  public static final String MKS_ENTRIES = "MKSEntries";
  public static final String PROJECT_NAME = "projectName";
  public static final String PROJECT_TYPE = "projectType";
  public static final String LAST_CHECKPOINT = "lastCheckpoint";
  public static final String MEMBER_TIMESTAMP = "membertimestamp";
  public static final String FULL_CONFIG_SYNTAX = "fullConfigSyntax";
  public static final String MEMBER_DESCRIPTION = "memberdescription";
  public static final String DELETE_OPERATION = "delete";
  public static final String ADD_OPERATION = "add";
  public static final String GET_OPERATION = "get";
  public static final String LOCATION = "location";
  public static final String CLOSED_DATE = "closeddate";
  public static final String CONFIG_PATH = "configpath";

  public enum CP_MEMBER_OPERATION
  {
    ADD, DROP, MOVEMEMBER, ADDFROMARCHIVE, UPDATEREVISION, RENAME, UPDATE, CREATESUBPROJECT;

    public String toString()
    {
      switch (this) {
        case ADD:
          return "ADD";
        case DROP:
          return "DROP";
        case MOVEMEMBER:
          return "MOVEMEMBER";
        case UPDATE:
          return "UPDATE";
        case UPDATEREVISION:
          return "UPDATEREVISION";
        case ADDFROMARCHIVE:
          return "ADDFROMARCHIVE";
        case RENAME:
          return "RENAME";
        case CREATESUBPROJECT:
          return "CREATESUBPROJECT";
      }
      return null;
    }

    public static CP_MEMBER_OPERATION searchEnum(String operation)
    {
      for (CP_MEMBER_OPERATION each : CP_MEMBER_OPERATION.class.getEnumConstants())
      {
        if (each.toString().compareToIgnoreCase(operation) == 0)
        {
          return each;
        }
      }
      return null;
    }
  }
}
