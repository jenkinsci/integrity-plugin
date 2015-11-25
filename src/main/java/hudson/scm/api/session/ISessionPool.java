// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm.api.session;

import java.util.logging.Logger;

import org.apache.commons.pool2.KeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;

import hudson.scm.IntegrityConfigurable;
import hudson.scm.IntegritySCM;

/**
 * APISession Pool Controller class
 * 
 * @author Author: asen
 * @version $Revision: $
 */
public class ISessionPool
{

  private static final Logger LOGGER = Logger.getLogger(IntegritySCM.class.getSimpleName());
  private KeyedObjectPool<IntegrityConfigurable, ISession> pool;

  private static class SingletonHolder
  {
    public static final ISessionPool INSTANCE = new ISessionPool();
  }

  /**
   * @return a singleton instance of the Integrity API Session Pool
   */
  public static ISessionPool getInstance()
  {
    return SingletonHolder.INSTANCE;
  }

  private ISessionPool()
  {
    startPool();
  }

  private void startPool()
  {
    pool = new GenericKeyedObjectPool<IntegrityConfigurable, ISession>(new ISessionFactory());
    LOGGER.info("Session Pool started");
  }

  /**
   * @return the {@link KeyedObjectPool}
   */
  public KeyedObjectPool<IntegrityConfigurable, ISession> getPool()
  {
    return pool;
  }
}
