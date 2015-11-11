package hudson.scm.api.command;

import java.io.File;
import java.sql.Timestamp;
import java.util.HashMap;

import com.mks.api.Command;

import hudson.scm.api.option.APIOption;
import hudson.scm.api.option.IAPIOption;

/**
 * Project Checkout command for the Integrity Jenkins Plugin
 * 
 * @author Author: asen
 * @version $Revision: $
 */
public class ProjectCheckoutCommand extends BasicAPICommand
{

    public ProjectCheckoutCommand()
    {
	cmd = new Command(Command.SI, PROJECT_CHECKOUT_COMMAND);
	commandHelperObjects = new HashMap<String, Object>();
	
	// Initialize defaults
	cmd.addOption(new APIOption(IAPIOption.OVER_WRITE_EXISTING));
	cmd.addOption(new APIOption(IAPIOption.NO_LOCK));
    }
    
    @Override
    public void doPostAction()
    {
	boolean restoreTimestamp = Boolean.valueOf(commandHelperObjects.get(IAPIOption.RESTORE_TIMESTAMP).toString());
	File targetFile = (File)commandHelperObjects.get(IAPIOption.TARGET_FILE);
	Timestamp memberTimestamp = (Timestamp)commandHelperObjects.get(IAPIOption.MEMBER_TIMESTAMP);
	if(restoreTimestamp)
	{
	    targetFile.setLastModified(memberTimestamp.getTime());
	}	
    }

    @Override
    public void doPreAction()
    {
	// Make sure the directory is created
	File targetFile = (File)commandHelperObjects.get(IAPIOption.TARGET_FILE);
	
	if( ! targetFile.getParentFile().isDirectory() )
	{
	    targetFile.getParentFile().mkdirs();
	}
    }

}
