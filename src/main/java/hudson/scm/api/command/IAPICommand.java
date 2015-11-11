package hudson.scm.api.command;

import com.mks.api.Command;
import com.mks.api.response.Response;

import hudson.scm.api.ISession;
import hudson.scm.api.option.IAPIOption;

/**
 * All Jenkins Integrity API command calls must extend this interface. A command is essentially a 
 * request that makes up what is logically an Integrity API call. 
 * Commands correspond to operations the user can perform with
 * Integrity, for example checking out a project or applying a change package etc. 
 * 
 * @author asen
 * 
**/

public interface IAPICommand 
{
    
    static String PROJECT_CHECKOUT_COMMAND  =  "projectco";
    static String PROJECT_CHECKIN_COMMAND =  "projectci";
    static String PROJECT_INFO_COMMAND  =  "projectinfo";
    static String VIEW_PROJECT_COMMAND  =  "viewproject";
    static String REVISION_INFO_COMMAND =  "revisioninfo";
    static String LOCK_COMMAND =  "lock";
    static String PROJECT_ADD_COMMAND =  "projectadd";
    static String UNLOCK_COMMAND =  "unlock";
    static String CREATE_CP_COMMAND =  "createcp";
    static String CLOSE_CP_COMMAND =  "closecp";
    static String SUBMIT_CP_COMMAND =  "submitcp";
    static String CHECKPOINT_COMMAND =  "checkpoint";
    static String ADD_PROJECT_LABEL_COMMAND =  "addprojectlabel";
    
    /**
     * Execute the command using Integrity Session API
     * @param api
     */
    public Response execute(ISession api) throws APICommandException;
    
    /**
     * Execute the command using an auto-generated Integrity Session API
     * @return
     * @throws APICommandException
     */
    public boolean execute() throws APICommandException;
    
    /**
     * @return
     */
    public Command getMKSAPICommand();
    
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
    
}
