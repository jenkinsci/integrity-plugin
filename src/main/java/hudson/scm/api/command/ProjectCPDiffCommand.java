/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/
package hudson.scm.api.command;

import hudson.scm.IntegrityConfigurable;
import hudson.scm.IntegritySCM;
import hudson.scm.api.APIUtils;
import hudson.scm.api.option.APIOption;
import hudson.scm.api.option.IAPIFields;
import hudson.scm.api.option.IAPIOption;

import com.mks.api.Command;
import com.mks.api.MultiValue;

public class ProjectCPDiffCommand extends BasicAPICommand {
	
	protected ProjectCPDiffCommand(final IntegrityConfigurable serverConfig) {

		super(serverConfig);
		cmd = new Command(Command.SI, PROJECT_CPDIFF_COMMAND);
	    cmd.addOption(new APIOption(IAPIOption.RECURSE));	    
	    MultiValue mv = APIUtils.createMultiValueField(IAPIFields.FIELD_SEPARATOR, IAPIFields.id, IAPIFields.USER);
	    cmd.addOption(new APIOption(IAPIOption.FIELDS, mv));
	}

}
