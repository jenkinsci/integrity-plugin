package hudson.scm;

import hudson.scm.IntegrityChangeLogSet.IntegrityChangeLog;

import java.io.IOException;
import java.net.URL;

public abstract class IntegrityRepositoryBrowser extends RepositoryBrowser<IntegrityChangeLog> 
{
	private static final long serialVersionUID = 4745105100520040559L;

    /**
     * Constructs the differences link between two Integrity Member Revisions.
     * @return
     * 		URL containing the link to difference two revisions 
     */
    public abstract URL getDiffLink(IntegrityChangeLog logEntry) throws IOException;

}
