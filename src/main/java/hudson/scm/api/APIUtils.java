// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm.api;

import com.mks.api.MultiValue;
import com.mks.api.response.APIException;
import com.mks.api.response.InterruptedException;
import com.mks.api.response.Response;
import com.mks.api.response.WorkItem;

import hudson.scm.api.command.APICommandException;
import hudson.scm.api.option.IAPIOption;

/**
 * Utility class for common Integrity API utilities
 * 
 * @author Author: asen
 * @version $Revision: $
 */
public class APIUtils
{

    /**
     * Utility method to create a multi value field object
     * 
     * @param separator
     * @param params
     * @return 
     */
    public static MultiValue createMultiValueField(String separator, String... params)
    {
	MultiValue mvFields = new MultiValue(separator);
	for (String param : params){
	    mvFields.add(param);
	}
	
	return mvFields;
    }
    
    
    /**
     * @param response
     * @return
     * @throws APICommandException
     */
    public static int getResponseExitCode(Response response) throws APICommandException
    {
	try {
	    return response.getExitCode();
	} catch (InterruptedException e) {
	    throw new APICommandException(e);
	}
    }
    
    public static WorkItem getWorkItem(Response response) throws APICommandException
    {
	    try {
		return response.getWorkItems().next();
	    } catch (APIException e) {
		throw new APICommandException(e);
	    }
    }

    /**
     * @param response
     * @param memberID 
     * @return
     */
    public static String getAuthorInfo(Response response, String memberID)
    {
	String author = response.getWorkItem(memberID).getField(IAPIOption.AUTHOR).getValueAsString();
	return author;
    }
}
