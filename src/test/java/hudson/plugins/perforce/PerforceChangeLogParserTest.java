/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.perforce;

import java.util.ArrayList;
import java.util.List;
import org.jvnet.hudson.test.HudsonTestCase;

import com.tek42.perforce.model.Changelist;
import java.io.*;

/**
 *
 * @author rpetti
 */
public class PerforceChangeLogParserTest extends HudsonTestCase {
    
    public void testSaveAndLoadChangeLogSet() throws Exception {
        PerforceChangeLogParser parser = new PerforceChangeLogParser();
        List<PerforceChangeLogEntry> entries = new ArrayList<PerforceChangeLogEntry>();
        List<Changelist> changes = new ArrayList<Changelist>();
        Changelist cl = new Changelist();
        cl.setChangeNumber(1000);
        cl.setDescription("test change");
        cl.setUser("test.user");
        cl.setWorkspace("test_workspace");
        List<Changelist.FileEntry> files = new ArrayList<Changelist.FileEntry>();
        Changelist.FileEntry fileEntry = new Changelist.FileEntry();
        fileEntry.setAction(Changelist.FileEntry.Action.ADD);
        fileEntry.setChangenumber("1000");
        fileEntry.setRevision("1");
        fileEntry.setWorkspacePath("some/workspace/path");
        fileEntry.setFilename("file");
        files.add(fileEntry);
        cl.setFiles(files);
        changes.add(cl);
        PerforceChangeLogSet originalSet = new PerforceChangeLogSet(null,entries);
        PerforceChangeLogEntry entry = new PerforceChangeLogEntry(originalSet);
        entry.setChange(cl);
        entries.add(entry);
        
        File tempFile = File.createTempFile(getClass().getName(),".tmp");
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile));
        PerforceChangeLogSet.saveToChangeLog(out, changes);
        out.flush();
        out.close();
        
        PerforceChangeLogSet reloadedSet = (PerforceChangeLogSet)parser.parse(null, tempFile);
        
        assertEquals(
                originalSet.getHistory().get(0).getChange().toString(),
                reloadedSet.getHistory().get(0).getChange().toString());
        assertEquals(
                originalSet.getHistory().get(0).getChange().getFiles().get(0).toString(),
                reloadedSet.getHistory().get(0).getChange().getFiles().get(0).toString());
    }
    
}
