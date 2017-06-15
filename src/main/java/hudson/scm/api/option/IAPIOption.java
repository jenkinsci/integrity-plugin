/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/


package hudson.scm.api.option;

/**
 * Constants interface
 * 
 * @author Author: asen
 * @version $Revision: $
 */
public interface IAPIOption
{
  public static final String OVER_WRITE_EXISTING = "overwriteExisting";
  public static final String NO_LOCK = "nolock";
  public static final String PROJECT = "project";
  public static final String TARGET_FILE = "targetFile";
  public static final String LINE_TERMINATOR = "lineTerminator";
  public static final String REVISION = "revision";
  public static final String RESTORE_TIMESTAMP = "restoreTimestamp";
  public static final String NORESTORE_TIMESTAMP = "norestoreTimestamp";
  public static final String MEMBER_TIMESTAMP = "memberTimestamp";
  public static final String RECURSE = "recurse";
  public static final String FIELDS = "fields";
  public static final String FILTER = "filter";
  public static final String MEMBER_ID = "memberID";
  public static final String AUTHOR = "author";
  public static final String CP_ID = "cpid";
  public static final String SOURCE_FILE = "sourceFile";
  public static final String DESCRIPTION = "description";
  public static final String SAVE_TIMESTAMP = "saveTimestamp";
  public static final String NO_CLOSE_CP = "nocloseCP";
  public static final String NO_DIFFERENT_NAMES = "nodifferentNames";
  public static final String BRANCH_VARIANT = "branchVariant";
  public static final String NO_CHECKIN_UNCHANGED = "nocheckinUnchanged";
  public static final String ON_EXISTING_ARCHIVE = "onExistingArchive";
  public static final String SHARE_ARCHIVE = "sharearchive";
  public static final String YES = "yes";
  public static final String ACTION = "action";
  public static final String REMOVE = "remove";
  public static final String SUMMARY = "summary";
  public static final String ITEM_ID = "issueId";
  public static final String RELEASE_LOCKS = "releaseLocks";
  public static final String CLOSE_CP = "closeCP";
  public static final String COMMIT = "commit";
  public static final String CHECKPOINT_LABEL = "chkptLabel";
  public static final String LABEL = "label";
  public static final String PROJECT_REVISION = "projectRevision";
  public static final String MOVE_LABEL = "moveLabel";
  public static final String CHECKPOINT_DESCRIPTION = "checkpointDesc";
  public static final String RICH_CONTENT_FIELD = "richContentField";
  public static final String FIELD = "field";
  public static final String TRAVERSE_FIELDS = "traverseFields";
  public static final String QUERY_DEFINITION = "queryDefinition";
  public static final String ANNOTATION = "annotation";
  public static final String VERDICT = "verdict";
  public static final String FORCE_CREATE = "forceCreate";
  public static final String SESSION_ID = "sessionID";
  public static final String REV = "r";
  public static final String ASOF_REVISION_PREFIX = "asof:";
  public static final String TIMESTAMP_PREFIX = "ts=";
  public static final String SANDBOX = "sandbox";
}
