//  
//   $Id: FakeObject.java 1.4 2013/02/27 19:49:45IST alivogiannis Exp  $
//
//   Copyright 2011 by PTC Inc. All rights reserved.
//
//   This Software is unpublished, valuable, confidential property of
//   PTC Inc.   Any use or disclosure of this Software
//   without the express written permission of PTC Inc.
//   is strictly prohibited.
//

package hudson.scm.test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for all Fake objects used for testing.
 *
 * @since Purple
 * @version $Revision: 1.4 $
 */
public class FakeObject
{
    private Map<String,Object> values = new LinkedHashMap<String, Object>();
    
    public void put(String key, Object value)
    {
	values.put(key,value);
    }

    public Object getValueByKey(String key)
    {
	Object value = getValues().get(key);
	return value;
    }

    public Map<String, Object> getValues()
    {
	return values;
    }
    
    public Exception getException(String key)
    {
	Object value = getValueByKey( key );
	if (value instanceof Exception)
	    return (Exception)value;
	return null;
    }
    
    protected boolean getBooleanValueByKey(String key)
    {
	Boolean value = (Boolean)getValueByKey(key);
	if (value == null)
	    return false;
	return value.booleanValue();
    }
}
