/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.perforce;

import com.tek42.perforce.model.Changelist;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 *
 * @author rpetti
 */
public class PerforceChangeLogParserTest extends HudsonTestCase {
    
    public void testSaveAndLoadChangeLogSet() throws Exception {
        PerforceChangeLogParser parser = new PerforceChangeLogParser();
        List<PerforceChangeLogEntry> entries = new ArrayList<PerforceChangeLogEntry>();
        PerforceChangeLogSet originalSet = new PerforceChangeLogSet(null,entries);
        
        
        List<Changelist> changes = new ArrayList<Changelist>();
        Changelist cl = new Changelist();
        cl.setChangeNumber(1000);
        cl.setDescription("test change <this is broken XML&>");
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
        fileEntry = new Changelist.FileEntry();
        fileEntry.setAction(Changelist.FileEntry.Action.ADD);
        fileEntry.setChangenumber("1001");
        fileEntry.setRevision("3");
        fileEntry.setWorkspacePath("some/workspace/path2");
        fileEntry.setFilename("file2");
        files.add(fileEntry);
        cl.setFiles(files);
        List<Changelist.JobEntry> jobs = new ArrayList<Changelist.JobEntry>();
        Changelist.JobEntry jobEntry = new Changelist.JobEntry();
        jobEntry.setDescription("test job");
        jobEntry.setJob("test-job");
        jobEntry.setStatus("submitted");
        jobs.add(jobEntry);
        jobEntry = new Changelist.JobEntry();
        jobEntry.setDescription("test job2 <!--Contains some nonsense-->\n<[[ like, really broken ]]>\n");
        jobEntry.setJob("test-job2");
        jobEntry.setStatus("rejected");
        jobs.add(jobEntry);
        cl.setJobs(jobs);
        changes.add(cl);
        PerforceChangeLogEntry entry = new PerforceChangeLogEntry(originalSet);
        entry.setChange(cl);
        entries.add(entry);

        cl = new Changelist();
        cl.setChangeNumber(1003);
        cl.setDescription("test change2");
        cl.setUser("test.user2");
        cl.setWorkspace("test_workspace2");
        files = new ArrayList<Changelist.FileEntry>();
        fileEntry = new Changelist.FileEntry();
        fileEntry.setAction(Changelist.FileEntry.Action.ADD);
        fileEntry.setChangenumber("1003");
        fileEntry.setRevision("1");
        fileEntry.setWorkspacePath("some/workspace/path5");
        fileEntry.setFilename("file5");
        files.add(fileEntry);
        fileEntry = new Changelist.FileEntry();
        fileEntry.setAction(Changelist.FileEntry.Action.ADD);
        fileEntry.setChangenumber("1003");
        fileEntry.setRevision("3");
        fileEntry.setWorkspacePath("some/workspace/path3");
        fileEntry.setFilename("file3");
        files.add(fileEntry);
        cl.setFiles(files);
        jobs = new ArrayList<Changelist.JobEntry>();
        jobEntry = new Changelist.JobEntry();
        jobEntry.setDescription("test job3");
        jobEntry.setJob("test-job3");
        jobEntry.setStatus("submitted");
        jobs.add(jobEntry);
        jobEntry = new Changelist.JobEntry();
        jobEntry.setDescription("test job4");
        jobEntry.setJob("test-job4");
        jobEntry.setStatus("rejected");
        jobs.add(jobEntry);
        cl.setJobs(jobs);
        changes.add(cl);
        entry = new PerforceChangeLogEntry(originalSet);
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
                originalSet.getHistory().get(1).getChange().toString(),
                reloadedSet.getHistory().get(1).getChange().toString());
        assertEquals(
                originalSet.getHistory().get(0).getChange().getFiles().get(0).toString(),
                reloadedSet.getHistory().get(0).getChange().getFiles().get(0).toString());
        assertEquals(
                originalSet.getHistory().get(0).getChange().getUser(),
                reloadedSet.getHistory().get(0).getChange().getUser());
        assertEquals(
                originalSet.getHistory().get(0).getChange().getJobs().get(0).toString(),
                reloadedSet.getHistory().get(0).getChange().getJobs().get(0).toString());
    }
    
}
