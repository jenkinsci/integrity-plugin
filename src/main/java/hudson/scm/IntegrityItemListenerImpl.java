package hudson.scm;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.AbstractProject;
import hudson.model.listeners.ItemListener;
import hudson.scm.IntegritySCM.DescriptorImpl;

/**
 * This class implements the onCopied event when a Job is copied from another Job
 * The sole purpose is to ensure that the Configuration Name parameter is unique.
 * In addition, this is also used to clean up Integrity SCM cache tables when a Job is purged.
 */
@Extension
public class IntegrityItemListenerImpl extends ItemListener 
{
	private static final Logger LOGGER = Logger.getLogger(IntegritySCM.class.getSimpleName());
	
	@DataBoundConstructor
	public IntegrityItemListenerImpl() 
	{
	}
	
	/**
	 * This overridden event ensures the Integrity CM's Configuration Name parameter is unique
	 */
	@Override
	public void onCopied(Item src, Item tgt)
	{
		LOGGER.fine("ItemListenerImpl.onCopied() invoked");
		super.onCopied(src, tgt);
		
		// Ensure the objects being copied are Jobs configured with Integrity CM as the SCM
		if (src instanceof AbstractProject && tgt instanceof AbstractProject)
		{
			final AbstractProject<?, ?> srcProject = (AbstractProject<?, ?>) src;
			final AbstractProject<?, ?> tgtProject = (AbstractProject<?, ?>) tgt;
			if ( srcProject.getScm() instanceof IntegritySCM && tgtProject.getScm() instanceof IntegritySCM )
			{
				final IntegritySCM srcSCM = (IntegritySCM) srcProject.getScm();
				final IntegritySCM tgtSCM = (IntegritySCM) tgtProject.getScm();
				if( srcSCM.getConfigurationName().equals(tgtSCM.getConfigurationName()) )
				{
					LOGGER.fine("Current configuration name is not unique - " + tgtSCM.getConfigurationName());
					tgtSCM.setConfigurationName(((IntegritySCM.DescriptorImpl)tgtSCM.getDescriptor()).getConfigurationName());
					LOGGER.info("Resetting Integrity 'Configuration Name' to - " + tgtSCM.getConfigurationName());
				}
			}
		}
	}
	
	/**
	 * Cleans up the Integrity SCM plugin database's cache tables, if appropriate
	 */
	@Override
	public void onDeleted(Item item)
	{
		LOGGER.fine("ItemListenerImpl.onDeleted() invoked");
		super.onDeleted(item);		
		
		LOGGER.fine("Preparing to clean up IntegritySCM cache tables for item - " + item.getName());
		try
		{
			List<String> jobsList = DerbyUtils.getDistinctJobNames(DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource());
			if( jobsList.contains(item.getName()))
			{
				DerbyUtils.deleteProjectCache(DescriptorImpl.INTEGRITY_DESCRIPTOR.getDataSource(), item.getName());
			}

		}
		catch (SQLException sqlex)
		{
			LOGGER.log(Level.SEVERE, "SQLException", sqlex);
		}
		
		LOGGER.fine("IntegritySCM cache table cleanup complete!");
	}
	
}
