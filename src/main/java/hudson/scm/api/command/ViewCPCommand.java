/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/
package hudson.scm.api.command;

import com.mks.api.Command;
import com.mks.api.MultiValue;

import hudson.scm.IntegrityConfigurable;
import hudson.scm.api.APIUtils;
import hudson.scm.api.option.APIOption;
import hudson.scm.api.option.IAPIFields;
import hudson.scm.api.option.IAPIOption;

public class ViewCPCommand extends BasicAPICommand {
	
	protected ViewCPCommand(final IntegrityConfigurable serverConfig)
    {
		super(serverConfig);
		cmd = new Command(Command.SI, VIEW_CP_COMMAND);
    MultiValue mv = APIUtils.createMultiValueField(IAPIFields.FIELD_SEPARATOR, IAPIFields.CP_MEMBER,
        IAPIFields.CP_STATE, IAPIFields.PROJECT, IAPIFields.id, IAPIFields.TYPE,
        IAPIFields.REVISION, IAPIFields.LOCATION, IAPIFields.CLOSED_DATE, IAPIFields.USER, IAPIFields.CONFIG_PATH);
	    cmd.addOption(new APIOption(IAPIOption.FIELDS, mv));
    }
}
