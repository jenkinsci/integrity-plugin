// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm.api.session;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultEvictionPolicy;
import org.apache.commons.pool2.impl.EvictionConfig;

import com.mks.api.response.APIException;
import com.mks.api.response.InterruptedException;

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
      } catch (InterruptedException e)
      {
        LOGGER.log(Level.FINEST,
            "Eviction Thread: Failed to ping Integrity Session Pool object : " + session.toString(),
            e);
        return true;
      } catch (APIException e)
      {
        LOGGER.log(Level.FINEST,
            "Eviction Thread: Failed to ping Integrity Session Pool object : " + session.toString(),
            e);
        return true;
      }
    } else
      return true;

    return super.evict(config, underTest, idleCount);
  }
}
