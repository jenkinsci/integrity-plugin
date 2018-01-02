package hudson.scm.localclient;

import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import hudson.model.Run;
import hudson.scm.ChangeLogSet;
import hudson.scm.RepositoryBrowser;

/**
 * Created by asen on 19-06-2017.
 */
public class IntegrityLcChangeSetList
		extends ChangeLogSet<IntegrityLcChangeSet> implements
		Serializable
{

    private static final long serialVersionUID = -5571987574672621692L;
    private final List<IntegrityLcChangeSet> changeSets;
    private String url;

    public IntegrityLcChangeSetList(Run<?, ?> run, RepositoryBrowser<?> browser,
		    String integrityUrl, List<IntegrityLcChangeSet> logs)
    {
	super(run,browser);
	this.changeSets = Collections.unmodifiableList(logs);
	this.url = integrityUrl;
	for (IntegrityLcChangeSet log : logs)
	    log.setParent(this);
    }

    @Override
    public boolean isEmptySet()
    {
	return changeSets.isEmpty();
    }

    @Override
    public Iterator<IntegrityLcChangeSet> iterator()
    {
	return changeSets.iterator();
    }
    
    /**
     * Provides the Integrity URL for this Change Log Set
     * 
     * @return
     */
    public String getIntegrityURL()
    {
      return url;
    }
    
    /**
     * Returns the list of Change Logs
     */
    public List<IntegrityLcChangeSet> getLogs()
    {
      return changeSets;
    }

    @Override
    public String getKind()
    {
	return "integrity";
    }
}
