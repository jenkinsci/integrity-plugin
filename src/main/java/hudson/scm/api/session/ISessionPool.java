// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm.api.session;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.KeyedObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;

import com.mks.api.response.APIException;
import com.mks.api.response.InterruptedException;

import hudson.AbortException;
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
  // Max APIsessions in the pool, per IntegrityConfigurable. Note that this has to be higher than
  // the checkout thread count to prevent CO threads from blocking
  private int maxTotalPerKey = 10;
  // Max idle APIsession objects in the pool, per IntegrityConfigurable
  private int maxIdlePerKey = 2;
  // 3 mins before idle Sessions are checked for eviction
  private long minEvictableIdleTimeMillis = 600000;

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
    LOGGER.log(Level.FINE, "Starting Integrity Session Pool");
    startPool();
  }

  private void startPool()
  {
    GenericKeyedObjectPoolConfig config = new GenericKeyedObjectPoolConfig();
    config.setMaxTotalPerKey(maxTotalPerKey);
    config.setMaxIdlePerKey(maxIdlePerKey);
    config.setTestOnBorrow(true);
    config.setTestOnCreate(true);
    // config.setTestOnReturn(true);
    config.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
    config.setEvictionPolicyClassName("hudson.scm.api.session.SessionPoolEvictionPolicy");
    pool =
        new GenericKeyedObjectPool<IntegrityConfigurable, ISession>(new ISessionFactory(), config);
    LOGGER.log(Level.FINEST,
        "Session Pool started with configuration : MaxTotalPerConfig : " + maxTotalPerKey
            + " , MaxIdlePerKey : " + maxIdlePerKey + " , MinEvictableTimeinMillis : "
            + config.getMinEvictableIdleTimeMillis());
  }

  /**
   * @return the {@link KeyedObjectPool}
   */
  public KeyedObjectPool<IntegrityConfigurable, ISession> getPool()
  {
    return pool;
  }

  /**
   *
   * @author Author: asen
   * @version $Revision: $
   */
  private class ISessionFactory
      extends BaseKeyedPooledObjectFactory<IntegrityConfigurable, ISession>
  {
    /*
     * (non-Javadoc)
     * 
     * @see org.apache.commons.pool2.BaseKeyedPooledObjectFactory#create(java.lang.Object)
     */
    @Override
    public ISession create(IntegrityConfigurable settings) throws Exception
    {
      LOGGER.log(Level.FINE, "Creating a new Integrity Session for the Session Pool :"
          + settings.getUserName() + "@" + settings.getHostName() + ":" + settings.getPort());
      ISession api = APISession.create(settings);
      if (null == api)
      {
        LOGGER.log(Level.SEVERE, "An Integrity API Session could not be established :"
            + settings.getUserName() + "@" + settings.getHostName() + ":" + settings.getPort());
        throw new AbortException("An Integrity API Session could not be established :"
            + settings.getUserName() + "@" + settings.getHostName() + ":" + settings.getPort());
      }
      return api;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.commons.pool2.BaseKeyedPooledObjectFactory#wrap(java.lang.Object)
     */
    @Override
    public PooledObject<ISession> wrap(ISession value)
    {
      return new DefaultPooledObject<ISession>(value);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.commons.pool2.BaseKeyedPooledObjectFactory#destroyObject(java.lang.Object,
     * org.apache.commons.pool2.PooledObject)
     */
    @Override
    public void destroyObject(IntegrityConfigurable key, PooledObject<ISession> p) throws Exception
    {
      LOGGER.log(Level.FINEST, "Terminating Integrity Session Pool object : " + key.toString());
      p.getObject().terminate();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.commons.pool2.BaseKeyedPooledObjectFactory#validateObject(java.lang.Object,
     * org.apache.commons.pool2.PooledObject)
     */
    @Override
    public boolean validateObject(IntegrityConfigurable key, PooledObject<ISession> p)
    {
      LOGGER.log(Level.FINEST, "Validating Integrity Session Pool object : " + key.toString());
      ISession session = p.getObject();
      if (null != session)
      {
        try
        {
          session.ping();
        } catch (InterruptedException e)
        {
          LOGGER.log(Level.FINEST,
              "Failed to ping Integrity Session Pool object : " + key.toString(), e);
          return false;
        } catch (APIException e)
        {
          LOGGER.log(Level.FINEST,
              "Failed to ping Integrity Session Pool object : " + key.toString(), e);
          return false;
        }
      } else
        return false;

      return true;
    }
  }
}
