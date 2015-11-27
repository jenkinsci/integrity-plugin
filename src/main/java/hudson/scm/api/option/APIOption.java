// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm.api.option;

import com.mks.api.MultiValue;
import com.mks.api.Option;

/**
 *
 * @author Author: asen
 * @version $Revision: $
 */
public class APIOption extends Option implements IAPIOption
{

  public APIOption(String name)
  {
    super(name);
  }

  public APIOption(String param1, String param2)
  {
    super(param1, param2);
  }

  public APIOption(String string, MultiValue mvFields)
  {
    super(string, mvFields);
  }

}
