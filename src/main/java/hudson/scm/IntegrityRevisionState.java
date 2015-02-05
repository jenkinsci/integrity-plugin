package hudson.scm;

import hudson.scm.IntegritySCM.DescriptorImpl;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Contains the state of the current Integrity Configuration Management Project
 */
public final class IntegrityRevisionState extends SCMRevisionState implements Serializable 
{
	private static final long serialVersionUID = 1838332506014398677L;
	private static final Logger LOGGER = Logger.getLogger("IntegritySCM");
	private final String projectCacheTable;

	public IntegrityRevisionState(String jobName, String configurationName, String projectCacheTable) 
	{
		LOGGER.fine("IntegrityRevisionState() invoked!");
		// Perform some clean up on old cache tables
		try
		{
			DerbyUtils.cleanupProjectCache(DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource(), jobName, configurationName);
		}
		catch (SQLException sqlex)
		{
	    	LOGGER.severe("SQL Exception caught...");
    		LOGGER.log(Level.SEVERE, "SQLException", sqlex);
		}
		
		this.projectCacheTable = projectCacheTable;
	}
	
	public String getProjectCache()
	{
		return projectCacheTable;
	}
}
