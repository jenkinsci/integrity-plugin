package hudson.scm;

import hudson.model.AbstractBuild;
import hudson.util.Digester2;
import hudson.util.IOException2;
import hudson.scm.IntegrityChangeLogSet.IntegrityChangeLog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.digester.Digester;
import org.xml.sax.SAXException;

/**
 * At this point, this 
 *
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
		Digester digester = new Digester2();
		digester.push(changeSetList);

		// When digester reads a {{<items>}} child node of {{<changelog>}} it will create a {{IntegrityChangeLog}} object
		digester.addObjectCreate("*/items/item", IntegrityChangeLog.class);
		digester.addSetProperties("*/items/item");
		digester.addBeanPropertySetter("*/items/item/file");
		digester.addBeanPropertySetter("*/items/item/user");
		digester.addBeanPropertySetter("*/items/item/revision");
		digester.addBeanPropertySetter("*/items/item/date");
		digester.addBeanPropertySetter("*/items/item/annotation");
		digester.addBeanPropertySetter("*/items/item/differences");
		digester.addBeanPropertySetter("*/items/item/msg");		
		// The digested node/item is added to the change set through {{java.util.List.add()}}
		digester.addSetNext("*/items/item", "add");
		        
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
