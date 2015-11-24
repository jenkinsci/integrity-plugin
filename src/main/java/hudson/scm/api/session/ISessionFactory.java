// $Id: $
// (c) Copyright 2015 by PTC Inc. All rights reserved.
//
// This Software is unpublished, valuable, confidential property of
// PTC Inc. Any use or disclosure of this Software without the express
// written permission of PTC Inc. is strictly prohibited.

package hudson.scm.api.session;

import java.util.logging.Logger;

import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import hudson.AbortException;
import hudson.scm.IntegrityConfigurable;
import hudson.scm.IntegritySCM;

/**
 *  This class is used to handle Sessions inside the {@link ISessionPool}
 *
 * @author Author: asen
 * @version $Revision: $
 */
public class ISessionFactory extends BaseKeyedPooledObjectFactory<IntegrityConfigurable, ISession>
{
  private static final Logger LOGGER = Logger.getLogger(IntegritySCM.class.getSimpleName());

  /* (non-Javadoc)
   * @see org.apache.commons.pool2.BaseKeyedPooledObjectFactory#create(java.lang.Object)
   */
  @Override
  public ISession create(IntegrityConfigurable settings) throws Exception
  {
    LOGGER.info("Creating a new Integrity Session for the Session Pool");
    ISession api = APISession.create(settings);
    if( null == api )
    {
        LOGGER.severe("An Integrity API Session could not be established!");
        throw new AbortException("An Integrity API Session could not be established!");
    }
    return api;
  }

  /* (non-Javadoc)
   * @see org.apache.commons.pool2.BaseKeyedPooledObjectFactory#wrap(java.lang.Object)
   */
  @Override
  public PooledObject<ISession> wrap(ISession value)
  {
    return new DefaultPooledObject<ISession>(value);
  }
  
  /* (non-Javadoc)
   * @see org.apache.commons.pool2.BaseKeyedPooledObjectFactory#destroyObject(java.lang.Object, org.apache.commons.pool2.PooledObject)
   */
  @Override
  public void destroyObject(IntegrityConfigurable key, PooledObject<ISession> p) throws Exception
  {
    LOGGER.info("Terminating Integrity Session Pool object");
    p.getObject().terminate();
  }
  
}
