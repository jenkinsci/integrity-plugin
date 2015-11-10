package hudson.scm;

import com.mks.api.Command;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;

public interface ISession {

	public Response runCommand(Command cmd) throws APIException;

}
