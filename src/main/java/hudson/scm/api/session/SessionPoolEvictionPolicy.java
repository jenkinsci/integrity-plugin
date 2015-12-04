// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm.api.session;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultEvictionPolicy;
import org.apache.commons.pool2.impl.EvictionConfig;

/**
 *
 * @author Author: asen
 * @version $Revision: $
 */
public class SessionPoolEvictionPolicy extends DefaultEvictionPolicy<ISession>
{

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
    return super.evict(config, underTest, idleCount) || (null == session) || (!session.isAlive());
  }
}
