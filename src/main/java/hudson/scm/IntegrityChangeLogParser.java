package hudson.scm;

import hudson.model.AbstractBuild;
import hudson.util.IOException2;
import hudson.scm.IntegrityChangeLogSet.IntegrityChangeLog;
import hudson.scm.IntegrityChangeLogSet.IntegrityChangeLogPath;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.digester3.Digester;
import org.xml.sax.SAXException;

/**
 * This class provides the parser to read the changelog.xml
 * that is generated at the end of a build listing the SCM changes 
 */
public class IntegrityChangeLogParser extends ChangeLogParser 
{
	private final String integrityURL;
	
	/**
	 * Provides a mechanism to obtain the host/port information
	 * for constructing links to annotations and differences views
	 */
	public IntegrityChangeLogParser(String url)
	{
		integrityURL = url;
	}
	
	/**
	 * Overridden ChangeLogParser
	 * @see hudson.scm.ChangeLogParser#parse(hudson.model.AbstractBuild, java.io.File)
	 */
	@Override
	public IntegrityChangeLogSet parse(@SuppressWarnings("rawtypes") AbstractBuild build, File changeLogFile) throws IOException, SAXException 
	{
		List<IntegrityChangeLog> changeSetList = new ArrayList<IntegrityChangeLog>();
		Digester digester = new Digester();
		digester.push(changeSetList);

		// When digester reads a {{<items>}} child node of {{<changelog>}} it will create a {{IntegrityChangeLog}} object
		digester.addObjectCreate("*/items/item", IntegrityChangeLog.class);
		digester.addSetProperties("*/items/item");
		digester.addBeanPropertySetter("*/items/item/file");
		digester.addBeanPropertySetter("*/items/item/user");
		digester.addBeanPropertySetter("*/items/item/rev");
		digester.addBeanPropertySetter("*/items/item/date");
		digester.addBeanPropertySetter("*/items/item/annotation");
		digester.addBeanPropertySetter("*/items/item/differences");
		digester.addBeanPropertySetter("*/items/item/msg");		
		// The digested node/item is added to the change set through {{java.util.List.add()}}
		digester.addSetNext("*/items/item", "add");
		// Additional information about the affected paths
		digester.addObjectCreate("*/items/item/paths/path", IntegrityChangeLogPath.class);
		digester.addSetProperties("*/items/item/paths/path");
		digester.addBeanPropertySetter("*/items/item/paths/path", "value");	
		digester.addSetNext("*/items/item/paths/path", "addPath");
		
		// Do the actual parsing
        try 
        {
            digester.parse(changeLogFile);
        }
        catch( IOException e ) 
        {
            throw new IOException2("Failed to parse " + changeLogFile, e);
        } 
        catch( SAXException e ) 
        {
            throw new IOException2("Failed to parse " + changeLogFile, e);
        }
        		
        // Create a new Integrity Change Log Set populated with a list of Entries...
        return new IntegrityChangeLogSet(build, changeSetList, integrityURL);
	}

}
