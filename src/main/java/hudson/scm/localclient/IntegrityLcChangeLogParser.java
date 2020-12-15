package hudson.scm.localclient;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.digester3.Digester;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.xml.sax.SAXException;

import hudson.model.Run;
import hudson.scm.ChangeLogParser;
import hudson.scm.RepositoryBrowser;

/**
 * Created by asen on 19-06-2017.
 */
public class IntegrityLcChangeLogParser extends ChangeLogParser implements
                Serializable
{
    private static final long serialVersionUID = 3380552685713306298L;
    private final String integrityUrl;

    public IntegrityLcChangeLogParser(String url)
    {
        super();
        integrityUrl = url;
    }

	@Override
	public IntegrityLcChangeSetList parse(@SuppressWarnings("rawtypes") Run build, RepositoryBrowser<?> browser,
			File changelogFile) throws IOException, SAXException {
		try {
			List<IntegrityLcChangeSet>  changeSetList = parseXML(build, browser, integrityUrl, changelogFile);
			return new IntegrityLcChangeSetList(build, browser, integrityUrl, changeSetList);
		} catch (SAXException e) {
			LineIterator lineIterator = null;
			try {
				lineIterator = FileUtils.lineIterator(changelogFile, "UTF-8");
				return new IntegrityLcChangeSetList(build, browser, integrityUrl, parse(lineIterator));
			} finally {
				LineIterator.closeQuietly(lineIterator);
			}
			
		}
	}

	private List<IntegrityLcChangeSet> parseXML(@SuppressWarnings("rawtypes") Run build, 
    RepositoryBrowser<?> browser, String integrityUrl, File changelogFile) 
            throws IOException, SAXException
    {
		List<IntegrityLcChangeSet> changeSetList = new ArrayList<IntegrityLcChangeSet>();
		Digester digester = new Digester();
		digester.push(changeSetList);

		// When digester reads a {{<items>}} child node of {{<changelog>}} it will
		// create a {{IntegrityChangeLog}} object
		digester.addObjectCreate("*/items/item", IntegrityLcChangeSet.class);
		digester.addSetProperties("*/items/item");
		digester.addBeanPropertySetter("*/items/item/file");
		digester.addBeanPropertySetter("*/items/item/msg");
		digester.addBeanPropertySetter("*/items/item/context");

		// The digested node/item is added to the change set through
		// {{java.util.List.add()}}
		digester.addSetNext("*/items/item", "add");

		// Do the actual parsing
		try
        {
			digester.parse(changelogFile);
		} catch (IOException e)
        {
			throw new IOException("Failed to parse " + changelogFile, e);
		} catch (SAXException e)
        {
			throw new SAXException("Failed to parse " + changelogFile, e);
		}

		return changeSetList;
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