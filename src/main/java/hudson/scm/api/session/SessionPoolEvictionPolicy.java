/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/


package hudson.scm.api.session;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultEvictionPolicy;
import org.apache.commons.pool2.impl.EvictionConfig;
import com.mks.api.response.APIException;
import hudson.scm.IntegritySCM;

/**
 *
 * @author Author: asen
 * @version $Revision: $
 */
public class SessionPoolEvictionPolicy extends DefaultEvictionPolicy<ISession>
{
  private static final Logger LOGGER = Logger.getLogger(IntegritySCM.class.getSimpleName());

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.commons.pool2.impl.DefaultEvictionPolicy#evict(org.apache.commons.pool2.impl.
   * EvictionConfig, org.apache.commons.pool2.PooledObject, int)
   */
  @Override
  public boolean evict(EvictionConfig config, PooledObject<ISession> underTest, int idleCount)
  {
    ISession session = underTest.getObject();
    if (null != session)
    {
      try
      {
        session.ping();
      } catch (APIException e)
      {
        LOGGER.log(Level.FINEST,
            "Eviction Thread: Failed to ping PTC RV&S Session Pool object : " + session.toString(),
            e);
        return true;
      }
    } else
      return true;

    return super.evict(config, underTest, idleCount);
  }
}
