/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/


package hudson.scm.api;

import com.mks.api.MultiValue;
import com.mks.api.response.APIException;
import com.mks.api.response.Response;
import com.mks.api.response.WorkItem;

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
    for (String param : params)
    {
      mvFields.add(param);
    }

    return mvFields;
  }

  public static WorkItem getWorkItem(Response response) throws APIException
  {
    return response.getWorkItems().next();
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
