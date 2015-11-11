/**
 * 
 */
package hudson.scm.api.command;

import com.mks.api.response.APIException;

/**
 * @author asen
 *
 */
public class APICommandException extends APIException {

    private static final long serialVersionUID = -7355644672829917075L;

    public APICommandException()
    {
	super();
    }
        
    public APICommandException(final String message)
    {
	 super(message);
    }
    
    public APICommandException(final Throwable error)
    {
	 super(error);
    }
    
}
