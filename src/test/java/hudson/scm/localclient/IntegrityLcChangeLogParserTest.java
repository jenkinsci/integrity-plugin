package hudson.scm.localclient;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import static org.junit.Assert.assertEquals;

/**
 * Created by asen on 19-06-2017.
 */
public class IntegrityLcChangeLogParserTest
{
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    private String changeLogFileName = "test.xml";
    private File changeLogFile;
    private String lineMsgFile = ("msg: checked out revision 1.1, file: a.txt");

   	private String dummyData = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>"+
			"<changelog>"+
			"<items>"+
			"<item>"+
			"<file>src/22.txt</file>"+
			"<msg>checked out revision 1.1</msg>"+
			"<context>/p1/project.pj</context>"+
			"</item>"+
			"</items>"+
			"</changelog>";
   	private String dummyDataWithOnlyMsg = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>"+
			"<changelog>"+
			"<items>"+
			"<item>"+
			"<msg>checked out revision 1.1</msg>"+
			"</item>"+
			"</items>"+
			"</changelog>";
   	private String dummyDataWithComma = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>"+
			"<changelog>"+
			"<items>"+
			"<item>"+
			"<file>src/22.txt</file>"+
			"<msg>checked out revision 1.1,</msg>"+
			"<context>/p1/project.pj</context>"+
			"</item>"+
			"</items>"+
			"</changelog>";
 	private String invalidData = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>"+
			"<changelog>"+
			"<items>"+
			"<item>"+
			"<invalid>Invalid data</invalid>" +
			"</item>"+
			"</items>"+
			"</changelog>";

    @Before
    public void setUp() throws Exception {
        changeLogFile = testFolder.newFile(changeLogFileName);
    }

    @Test
    public void testIntegrityLcChangeSetMsgFile(){
        IntegrityLcChangeSet changeSet = new IntegrityLcChangeSet(lineMsgFile);
        changeSet.getMsg().equals("checked out revision 1.1");
        changeSet.getFile().equals("a.txt");
    }

    @Test
    public void parseCorrectChangeLogMsgFile() throws Exception
    {
 
        try(PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(changeLogFile), "UTF-8"))) {
        	writer.print(dummyData);
        }
        IntegrityLcChangeLogParser parser = new IntegrityLcChangeLogParser("");
        IntegrityLcChangeSetList list = parser.parse(null, null, changeLogFile);
        for(IntegrityLcChangeSet set : list){
            assertEquals("checked out revision 1.1", set.getMsg());
            assertEquals("src/22.txt", set.getFile());
        }
    }

    @Test
    public void parseCorrectChangeLogMsg() throws Exception
    {
        try(PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(changeLogFile), "UTF-8"))) {
            writer.print(dummyDataWithOnlyMsg);
        }
        IntegrityLcChangeLogParser parser = new IntegrityLcChangeLogParser("");
        IntegrityLcChangeSetList list = parser.parse(null, null, changeLogFile);
        for(IntegrityLcChangeSet set : list){
            assertEquals("checked out revision 1.1", set.getMsg());
        }
    }

    @Test
    public void parseCorrectChangeLogMsgWithComma() throws Exception
    {
        try(PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(changeLogFile), "UTF-8"))) {
            writer.print(dummyDataWithComma);
        }
        IntegrityLcChangeLogParser parser = new IntegrityLcChangeLogParser("");
        IntegrityLcChangeSetList list = parser.parse(null, null, changeLogFile);
        for(IntegrityLcChangeSet set : list){
            assertEquals("checked out revision 1.1", set.getMsg());
        }
    }

    @Test
    public void parseInvalidChangeLogToken() throws Exception
    {
        try(PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(changeLogFile), "UTF-8"))) {
            writer.print(invalidData);
        }
        IntegrityLcChangeLogParser parser = new IntegrityLcChangeLogParser("");
        IntegrityLcChangeSetList list = parser.parse(null, null, changeLogFile);
        for(IntegrityLcChangeSet set : list){
            assertEquals(null, set.getMsg());
        }
    }
}