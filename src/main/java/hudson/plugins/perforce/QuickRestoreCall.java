/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.perforce;

import hudson.model.TaskListener;
import hudson.plugins.perforce.QuickCleaner.PerforceCall;
import hudson.plugins.perforce.QuickCleaner.RemoteCall;
import java.io.*;
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

    public QuickRestoreCall() {
    }

    public Integer call() throws IOException {
        PipedInputStream forceSyncInput = new PipedInputStream();
        PipedOutputStream diffOutput = new PipedOutputStream();
        
        forceSyncInput.connect(diffOutput);
        
        PerforceCall forceSync = new PerforceCall(env, new String[]{p4exe, "-x-", "sync", "-f"}, forceSyncInput, out, workDir, listener, true);
        PerforceCall findChangedFiles = new PerforceCall(env, new String[]{p4exe, "diff", "-se"}, new ClosedInputStream(), diffOutput, workDir, listener, false);
        PerforceCall findDeletedFiles = new PerforceCall(env, new String[]{p4exe, "diff", "-sd"}, new ClosedInputStream(), diffOutput, workDir, listener, false);    

        try {
            forceSync.start();
            //find changed files
            findChangedFiles.start();
            findChangedFiles.join();
            diffOutput.flush();
            //find deleted files
            findDeletedFiles.start();
            findDeletedFiles.join();
            diffOutput.flush();
            diffOutput.close();
            //finish up syncing
            forceSync.join();
        } catch (InterruptedException e) {
            forceSync.interrupt();
            findChangedFiles.interrupt();
            findDeletedFiles.interrupt();
        }
        
        return 0;
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
}
