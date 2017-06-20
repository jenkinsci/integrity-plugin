package hudson.scm.localclient;

import hudson.scm.IntegrityConfigurable;
import hudson.scm.api.session.APISession;
import hudson.util.StreamTaskListener;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import static org.junit.Assert.*;

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
    private String lineMsg = ("msg: Test Commit");

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
            writer.print(lineMsgFile);
        }
        IntegrityLcChangeLogParser parser = new IntegrityLcChangeLogParser("");
        IntegrityLcChangeSetList list = parser.parse(null, null, changeLogFile);
        for(IntegrityLcChangeSet set : list){
            assertEquals("checked out revision 1.1", set.getMsg());
            assertEquals("a.txt", set.getFile());
        }
    }

    @Test
    public void parseCorrectChangeLogMsg() throws Exception
    {
        try(PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(changeLogFile), "UTF-8"))) {
            writer.print(lineMsg);
        }
        IntegrityLcChangeLogParser parser = new IntegrityLcChangeLogParser("");
        IntegrityLcChangeSetList list = parser.parse(null, null, changeLogFile);
        for(IntegrityLcChangeSet set : list){
            assertEquals("Test Commit", set.getMsg());
        }
    }
}