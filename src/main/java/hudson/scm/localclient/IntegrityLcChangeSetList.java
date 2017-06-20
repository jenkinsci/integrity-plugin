package hudson.scm.localclient;

import hudson.model.Run;
import hudson.scm.ChangeLogSet;
import hudson.scm.RepositoryBrowser;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Created by asen on 19-06-2017.
 */
public class IntegrityLcChangeSetList
		extends ChangeLogSet<IntegrityLcChangeSet>{
    private final List<IntegrityLcChangeSet> changeSets;

    public IntegrityLcChangeSetList(Run run, RepositoryBrowser<?> browser,
		    List<IntegrityLcChangeSet> logs)
    {
	super(run,browser);
	this.changeSets = Collections.unmodifiableList(logs);
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
}
