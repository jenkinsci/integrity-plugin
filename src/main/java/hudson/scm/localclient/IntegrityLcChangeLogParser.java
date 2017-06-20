package hudson.scm.localclient;

import hudson.model.Run;
import hudson.scm.ChangeLogParser;
import hudson.scm.RepositoryBrowser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by asen on 19-06-2017.
 */
public class IntegrityLcChangeLogParser extends ChangeLogParser
{
    private final String integrityUrl;

    public IntegrityLcChangeLogParser(String url)
    {
        super();
        integrityUrl = url;
    }

    @Override
    public IntegrityLcChangeSetList parse(Run build,
                    RepositoryBrowser<?> browser, File changelogFile)
                    throws IOException, SAXException
    {
        // Parse the log file into IntegrityLcChangeSetList items
        LineIterator lineIterator = null;
        try {
            lineIterator = FileUtils.lineIterator(changelogFile,"UTF-8");
            return new IntegrityLcChangeSetList(build, browser, parse(lineIterator));
        } finally {
            LineIterator.closeQuietly(lineIterator);
        }
    }

    private List<IntegrityLcChangeSet> parse(Iterator<String> changelog) {
        Set<IntegrityLcChangeSet> r = new LinkedHashSet<>();
        while (changelog.hasNext()) {
            String line = changelog.next();
            r.add(parseLines(line));
        }
        return new ArrayList<>(r);
    }

    private IntegrityLcChangeSet parseLines(String line) {
        return new IntegrityLcChangeSet(line);
    }
}