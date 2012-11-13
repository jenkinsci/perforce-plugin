/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.perforce;

import hudson.model.TaskListener;
import hudson.plugins.perforce.QuickCleaner.PerforceCall;
import hudson.plugins.perforce.QuickCleaner.RemoteCall;
import java.io.*;
import java.util.ArrayList;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ClosedInputStream;

/**
 *
 * @author rpetti
 */
class QuickRestoreCall implements RemoteCall {
    private String[] env;
    private OutputStream out;
    private String workDir;
    private TaskListener listener;
    private String p4exe;
    private FileFilter filter;
    private String p4ticket;

    public QuickRestoreCall() {
    }

    public Integer call() throws IOException {
        try {
            forceSyncUsingP4DiffOption("-se");
            forceSyncUsingP4DiffOption("-sd");
        } finally {
            IOUtils.closeQuietly(out);
        }
        return 0;
    }
    
    private void forceSyncUsingP4DiffOption(String option) throws IOException {
        PipedInputStream forceSyncInput = new PipedInputStream();
        PipedOutputStream diffOutput = new PipedOutputStream();
        
        forceSyncInput.connect(diffOutput);
        
        ArrayList<String> forceSyncCmdList = new ArrayList<String>();
        ArrayList<String> findDiffFilesCmdList = new ArrayList<String>();
        
        //build force sync command
        forceSyncCmdList.add(p4exe);
        if(p4ticket != null && !p4ticket.trim().isEmpty()){
            forceSyncCmdList.add("-P");
            forceSyncCmdList.add(p4ticket);
        }
        forceSyncCmdList.add("-d");
        forceSyncCmdList.add(workDir);
        forceSyncCmdList.add("-x-");
        forceSyncCmdList.add("sync");
        forceSyncCmdList.add("-f");
        
        PerforceCall forceSync = new PerforceCall(env, forceSyncCmdList.toArray(new String[forceSyncCmdList.size()]), forceSyncInput, out, workDir, listener, false);
        
        //build diff command
        findDiffFilesCmdList.add(p4exe);
        if(p4ticket != null && !p4ticket.trim().isEmpty()){
            findDiffFilesCmdList.add("-P");
            findDiffFilesCmdList.add(p4ticket);
        }
        findDiffFilesCmdList.add("-d");
        findDiffFilesCmdList.add(workDir);
        findDiffFilesCmdList.add("diff");
        findDiffFilesCmdList.add(option);
        
        PerforceCall findDiffFiles = new PerforceCall(env, findDiffFilesCmdList.toArray(new String[findDiffFilesCmdList.size()]), new ClosedInputStream(), diffOutput, workDir, listener, true);    
        
        try {
            forceSync.start();
            //find changed files
            findDiffFiles.start();
            findDiffFiles.join();
            forceSync.join();
        } catch (InterruptedException e) {
            forceSync.interrupt();
            findDiffFiles.interrupt();
        } finally {
            IOUtils.closeQuietly(forceSyncInput);
            IOUtils.closeQuietly(diffOutput);
        }
    }
    
    public void setEnv(String[] env) {
        this.env = env;
    }

    public void setFilter(FileFilter filter) {
        this.filter = filter;
    }

    public void setListener(TaskListener listener) {
        this.listener = listener;
    }

    public void setOut(OutputStream out) {
        this.out = out;
    }

    public void setP4exe(String p4exe) {
        this.p4exe = p4exe;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public void setP4Ticket(String p4ticket) {
        this.p4ticket = p4ticket;
    }
}
