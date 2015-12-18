// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm.api.option;

/**
 * Constants interface
 * 
 * @author Author: asen
 * @version $Revision: $
 */
public interface IAPIOption
{
  public final String OVER_WRITE_EXISTING = "overwriteExisting";
  public final String NO_LOCK = "nolock";
  public final String PROJECT = "project";
  public final String TARGET_FILE = "targetFile";
  public final String LINE_TERMINATOR = "lineTerminator";
  public final String REVISION = "revision";
  public final String RESTORE_TIMESTAMP = "restoreTimestamp";
  public final String NORESTORE_TIMESTAMP = "norestoreTimestamp";
  public final String MEMBER_TIMESTAMP = "memberTimestamp";
  public final String RECURSE = "recurse";
  public final String FIELDS = "fields";
  public final String FILTER = "filter";
  public final String MEMBER_ID = "memberID";
  public final String AUTHOR = "author";
  public final String CP_ID = "cpid";
  public final String SOURCE_FILE = "sourceFile";
  public final String DESCRIPTION = "description";
  public final String SAVE_TIMESTAMP = "saveTimestamp";
  public final String NO_CLOSE_CP = "nocloseCP";
  public final String NO_DIFFERENT_NAMES = "nodifferentNames";
  public final String BRANCH_VARIANT = "branchVariant";
  public final String NO_CHECKIN_UNCHANGED = "nocheckinUnchanged";
  public final String ON_EXISTING_ARCHIVE = "onExistingArchive";
  public final String SHARE_ARCHIVE = "sharearchive";
  public final String YES = "yes";
  public final String ACTION = "action";
  public final String REMOVE = "remove";
  public final String SUMMARY = "summary";
  public final String ITEM_ID = "issueId";
  public final String RELEASE_LOCKS = "releaseLocks";
  public final String CLOSE_CP = "closeCP";
  public final String COMMIT = "commit";
  public final String CHECKPOINT_LABEL = "chkptLabel";
  public final String LABEL = "label";
  public final String PROJECT_REVISION = "projectRevision";
  public final String MOVE_LABEL = "moveLabel";
  public final String CHECKPOINT_DESCRIPTION = "checkpointDesc";
  public final String RICH_CONTENT_FIELD = "richContentField";
  public final String FIELD = "field";
  public final String TRAVERSE_FIELDS = "traverseFields";
  public final String QUERY_DEFINITION = "queryDefinition";
  public final String ANNOTATION = "annotation";
  public final String VERDICT = "verdict";
  public final String FORCE_CREATE = "forceCreate";
  public final String SESSION_ID = "sessionID";
  public final String REV = "r";
  public final String ASOF = "asof:";
}
