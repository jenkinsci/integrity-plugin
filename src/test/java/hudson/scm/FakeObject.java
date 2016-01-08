/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/

package hudson.scm;

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
