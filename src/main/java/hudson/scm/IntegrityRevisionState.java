/*******************************************************************************
 * Contributors:
 *     PTC 2016
 *******************************************************************************/
package hudson.scm;

import java.io.Serializable;
import java.util.logging.Logger;

/**
 * Contains the state of the current Integrity Configuration Management Project
 */
public final class IntegrityRevisionState extends SCMRevisionState implements Serializable
{
  private static final long serialVersionUID = 1838332506014398677L;
  private static final Logger LOGGER = Logger.getLogger(IntegritySCM.class.getSimpleName());
  private final String projectCacheTable;

  public IntegrityRevisionState(String jobName, String configurationName, String projectCacheTable)
  {
    LOGGER.fine("IntegrityRevisionState() invoked!");
    this.projectCacheTable = projectCacheTable;
  }

  public String getProjectCache()
  {
    return projectCacheTable;
  }
}
