//  
//   $Id: FakeResponse.java 1.6 2015/03/30 20:19:13IST Reid, Randall (rreid) Exp  $
//
//   Copyright 2011 by PTC Inc. All rights reserved.
//
//   This Software is unpublished, valuable, confidential property of
//   PTC Inc.   Any use or disclosure of this Software
//   without the express written permission of PTC Inc.
//   is strictly prohibited.
//

package hudson.scm;

import com.mks.api.VersionNumber;
import com.mks.api.response.*;
import com.mks.api.response.InterruptedException;

/**
 * This class allows for a Api Response object to be used when testing.
 *
 * @since Purple
 * @version $Revision: 1.6 $
 */
public class FakeResponse extends FakeObject implements Response
{
    public final static String GET_API_EXCEPTION = "getApiException";
    public final static String GET_RESULT = "getResult";
    public final static String GET_WORKITEMS = "getWorkItems";
    public final static String GET_EXITCODE = "getExitCode";
    public final static String GET_WORKITEM_LIST_SIZE = "getWorkItemListSize";
    
    public FakeResponse()
    {
	setExitCode( 0 );
    }
    
    /* (non-Javadoc)
     * @see com.mks.api.response.Response#getAPIVersion()
     */
    public VersionNumber getAPIVersion()
    {
	return null;
    }


    /* (non-Javadoc)
     * @see com.mks.api.response.Response#getInvocationID()
     */
    public String getInvocationID()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.WorkItemContainer#getWorkItemListSize()
     */
    public int getWorkItemListSize()
    {
	return (Integer) getValueByKey( GET_WORKITEM_LIST_SIZE );
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.WorkItemContainer#getWorkItems()
     */
    public WorkItemIterator getWorkItems()
    {
	WorkItemIterator it = (WorkItemIterator) getValueByKey(GET_WORKITEMS);
	return it;
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.WorkItemContainer#getWorkItem(java.lang.String)
     */
    public WorkItem getWorkItem(String id)
    {
	return (WorkItem) getValueByKey( id );
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.WorkItemContainer#getWorkItem(java.lang.String, java.lang.String)
     */
    public WorkItem getWorkItem(String id, String context)
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.WorkItemContainer#containsWorkItem(java.lang.String)
     */
    public boolean containsWorkItem(String id)
    {
	return false;
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.WorkItemContainer#containsWorkItem(java.lang.String, java.lang.String)
     */
    public boolean containsWorkItem(String id, String context)
    {
	return false;
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.SubRoutineContainer#getSubRoutineListSize()
     */
    public int getSubRoutineListSize()
    {
	return 0;
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.SubRoutineContainer#getSubRoutines()
     */
    public SubRoutineIterator getSubRoutines()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.SubRoutineContainer#getSubRoutine(java.lang.String)
     */
    public SubRoutine getSubRoutine(String name)
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.SubRoutineContainer#containsSubRoutine(java.lang.String)
     */
    public boolean containsSubRoutine(String name)
    {
	return false;
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.Response#getExitCode()
     */
    public int getExitCode() throws InterruptedException
    {
	 Object value = getValueByKey(GET_EXITCODE);
	 if (value instanceof Exception)
	     throw (com.mks.api.response.InterruptedException)value;
	return (Integer) value;
    }

    public void setExitCode(int exitCode)
    {
	put(GET_EXITCODE,exitCode);
    }
    
    /* (non-Javadoc)
     * @see com.mks.api.response.Response#getApplicationName()
     */
    public String getApplicationName()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.Response#getCommandName()
     */
    public String getCommandName()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.Response#getCommandString()
     */
    public String getCommandString()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.Response#getConnectionHostname()
     */
    public String getConnectionHostname()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.Response#getConnectionPort()
     */
    public int getConnectionPort()
    {
	return 0;
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.Response#getConnectionUsername()
     */
    public String getConnectionUsername()
    {
	return null;
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.Response#getResult()
     */
    public Result getResult() throws InterruptedException
    {
	Object result = getValueByKey(GET_RESULT);
	if (result instanceof Exception)
	    throw (InterruptedException)result;
	return (Result) result;
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.Response#getCacheContents()
     */
    public boolean getCacheContents()
    {
	return false;
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.Response#interrupt()
     */
    public void interrupt()
    {
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.Response#getAPIException()
     */
    public APIException getAPIException() throws InterruptedException
    {
	Object object = getValueByKey(GET_API_EXCEPTION);
	if (object instanceof InterruptedException)
	    throw (InterruptedException)object;
	return (APIException) object;
    }

    /* (non-Javadoc)
     * @see com.mks.api.response.Response#release()
     */
    public void release() throws APIException
    {
    }
}
