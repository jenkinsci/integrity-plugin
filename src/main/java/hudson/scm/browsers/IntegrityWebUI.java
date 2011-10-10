package hudson.scm.browsers;

import java.io.IOException;
import java.net.URL;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.scm.IntegrityRepositoryBrowser;
import hudson.scm.IntegrityChangeLogSet.IntegrityChangeLog;
import hudson.scm.RepositoryBrowser;

public class IntegrityWebUI extends IntegrityRepositoryBrowser 
{
    /**
     * The URL of the Integrity Configuration Management Server
     * For example: <tt>http://hostname:7001</tt>
     * 
     * NOTE: This is optional and is used as an override to the URL
     * found within the IntegrityChangeLogSet.getIntegrityURL() 
     */
    public final String url;	
    @Extension
    public static final IntegrityWebUIDescriptorImpl WEBUI_DESCRIPTOR = new IntegrityWebUIDescriptorImpl();    
	private static final long serialVersionUID = 6861438752583904309L;

	/**
	 * Provides a mechanism to override the URL for the Change Log Set
	 * @param url
	 */
    @DataBoundConstructor
    public IntegrityWebUI(String url) 
    {
    	// Initialize our url override
        this.url = url;
    }

	/**
	 * Returns the Descriptor<RepositoryBrowser<?>> for the IntegrityWebUI object. 
	 * The IntegrityWebUIDescriptorImpl is used to create new instances of the IntegrityWebUI.
	 */    
    public Descriptor<RepositoryBrowser<?>> getDescriptor() 
    {
        return WEBUI_DESCRIPTOR;
    }

    /**
     * Returns an Integrity differences link for a specific file
     */
	@Override
	public URL getDiffLink(IntegrityChangeLog logEntry) throws IOException 
	{
		URL context = null;
		// Check to see if a URL has been overridden
		if( null != url && url.length() > 0 )
		{
			if( url.endsWith("/") )
			{
				context = new URL(url + "si/");
			}
			else
			{
				context = new URL(url + "/si/");
			}
			return new URL(context, logEntry.getDifferences());
		}
		else // Use the URL from the Change Log Set
		{
			context = new URL(logEntry.getParent().getIntegrityURL() + "/si/");
			return new URL(context, logEntry.getDifferences());
		}
	}

	/**
	 * At this point we don't have a separate link for the Change Set itself
	 * Each entry essentially is a change set within itself
	 * Returns an Integrity annotation view link for a specific file
	 */
	@Override
	public URL getChangeSetLink(IntegrityChangeLog logEntry) throws IOException 
	{
		URL context = null;
		// Check to see if a URL has been overridden
		if( null != url && url.length() > 0 )
		{
			if( url.endsWith("/") )
			{
				context = new URL(url + "si/");
			}
			else
			{
				context = new URL(url + "/si/");
			}
			return new URL(context, logEntry.getAnnotation());
		}
		else // Use the URL from the Change Log Set
		{
			context = new URL(logEntry.getParent().getIntegrityURL() + "/si/");
			return new URL(context, logEntry.getAnnotation());
		}
	}
	
	/**
	 * The relationship of Descriptor and Browser (the describable) is akin to class and object.
	 * This means the descriptor is used to create instances of the describable.
	 * This Descriptor is an internal class in the Browser class named DescriptorImpl. 
	 */	
    public static class IntegrityWebUIDescriptorImpl extends Descriptor<RepositoryBrowser<?>> 
    {
        protected IntegrityWebUIDescriptorImpl() 
        {
            super(IntegrityWebUI.class);
        }
    	
    	/**
    	 * Returns the name for this Repository Browser
    	 */
    	@Override
        public String getDisplayName() 
        {
            return "Integrity CM - Web Interface";
        }
    }	
}
