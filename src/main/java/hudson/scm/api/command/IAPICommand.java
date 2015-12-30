package hudson.scm.api.command;

import com.mks.api.response.APIException;
import com.mks.api.response.Response;

import hudson.AbortException;
import hudson.scm.api.option.IAPIOption;
import hudson.scm.api.session.ISession;

/**
 * All Jenkins Integrity API command calls must extend this interface. A command is essentially a
 * request that makes up what is logically an Integrity API call. Commands correspond to operations
 * the user can perform with Integrity, for example checking out a project or applying a change
 * package etc.
 * 
 * @author asen
 * 
 **/

public interface IAPICommand
{
  // List of commonly used plugin API commands
  static String PROJECT_CHECKOUT_COMMAND = "projectco";
  static String PROJECT_CHECKIN_COMMAND = "projectci";
  static String PROJECT_INFO_COMMAND = "projectinfo";
  static String PROJECT_CPDIFF_COMMAND = "projectcpdiff";
  static String VIEW_PROJECT_COMMAND = "viewproject";
  static String REVISION_INFO_COMMAND = "revisioninfo";
  static String LOCK_COMMAND = "lock";
  static String PROJECT_ADD_COMMAND = "projectadd";
  static String UNLOCK_COMMAND = "unlock";
  static String CREATE_CP_COMMAND = "createcp";
  static String CLOSE_CP_COMMAND = "closecp";
  static String SUBMIT_CP_COMMAND = "submitcp";
  static String VIEW_CP_COMMAND = "viewcp";
  static String CHECKPOINT_COMMAND = "checkpoint";
  static String ADD_PROJECT_LABEL_COMMAND = "addprojectlabel";
  static String EDIT_ISSUE_COMMAND = "editissue";
  static String RELATIONSHIPS_COMMAND = "relationships";
  static String ISSUES_COMMAND = "issues";
  static String EDIT_RESULT_COMMAND = "editresult";

  /**
   * Execute the command using Integrity Session API
   * 
   * @param api
   */
  public Response execute(ISession api) throws APIException;

  /**
   * Default way to execute the command using an auto-generated Integrity Session API
   * 
   * @return
   * @throws APICommandException
   * @throws AbortException
   */
  public Response execute() throws APIException, AbortException;

  /**
   * Do actions post the Integrity API call specifically for Jenkins functionality
   */
  public void doPostAction();

  /**
   * Do actions pre the Integrity API call specifically for Jenkins functionality
   */
  public void doPreAction();

  /**
   * Objects required for command pre and post processing.
   * 
   * @param objects
   */
  public void addAdditionalParameters(String paramName, Object param);

  /**
   * @param option
   */
  public void addOption(IAPIOption option);

  /**
   * @param param
   */
  public void addSelection(String param);

  /**
   * Function to explicitly terminate/return sessions to Session Pool for WITH_INTERIM commands
   * 
   * @throws Exception
   */
  public void terminateAPI() throws Exception;

}
