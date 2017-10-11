/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/
package hudson.scm.api.session;

import com.mks.api.Command;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;

public interface ISession
{

  public Response runCommand(Command cmd) throws APIException;

  public Response runCommandWithInterim(Command cmd) throws APIException;

  public void terminate();

  public String getUserName();

  public void refreshAPISession() throws APIException;

  void ping() throws APIException;

}
