package hudson.scm.api.command;

import hudson.scm.IntegrityConfigurable;
import hudson.scm.api.APIUtils;
import hudson.scm.api.option.APIOption;
import hudson.scm.api.option.IAPIFields;
import hudson.scm.api.option.IAPIOption;

import com.mks.api.Command;
import com.mks.api.MultiValue;

public class ViewCPCommand extends BasicAPICommand {
	
	protected ViewCPCommand(final IntegrityConfigurable serverConfig)
    {
		super(serverConfig);
		cmd = new Command(Command.SI, VIEW_CP_COMMAND);
	    MultiValue mv = APIUtils.createMultiValueField(IAPIFields.FIELD_SEPARATOR, IAPIFields.CP_MEMBER, IAPIFields.CP_STATE, IAPIFields.PROJECT);
	    cmd.addOption(new APIOption(IAPIOption.FIELDS, mv));
    }
}
