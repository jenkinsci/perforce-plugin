/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.perforce;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.FastPipedInputStream;
import hudson.remoting.FastPipedOutputStream;
import hudson.remoting.RemoteOutputStream;
import hudson.util.StreamTaskListener;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author rpetti
 */
public class QuickCleaner {

    private Launcher hudsonLauncher;
    private String[] env;
    private FilePath filePath;
    private String p4exe;
    private FileFilter filter;
    private String p4ticket;

    QuickCleaner(String p4exe, String p4ticket, Launcher hudsonLauncher, Depot depot, FilePath filePath, FileFilter filter) {
        this.hudsonLauncher = hudsonLauncher;
        this.env = getEnvFromDepot(depot, filePath.getRemote());
        this.filePath = filePath;
        this.p4exe = p4exe;
        this.filter = filter;
        this.p4ticket = p4ticket;
    }

    public void doClean() throws PerforceException {
        call(new QuickCleanerCall());
    }
    
    public void doRestore() throws PerforceException {
        call(new QuickRestoreCall());
    }
    
    public void exec() throws PerforceException {
        call(new QuickCleanerCall());
    }

    public void call(RemoteCall remoteCall) throws PerforceException {
        try {
            // ensure we actually have a valid hudson launcher
            if (null == hudsonLauncher) {
                hudsonLauncher = Hudson.getInstance().createLauncher(new StreamTaskListener(System.out));
            }
            TaskListener listener = hudsonLauncher.getListener();

            // hudsonOut->p4in->reader
            FastPipedOutputStream hudsonOut = new FastPipedOutputStream();
            FastPipedInputStream p4in = new FastPipedInputStream(hudsonOut);
            //input = p4in;

            final OutputStream out = hudsonOut == null ? null : new RemoteOutputStream(hudsonOut);

            remoteCall.setEnv(env);
            remoteCall.setP4exe(p4exe);
            remoteCall.setOut(out);
            remoteCall.setWorkDir(filePath.getRemote());
            remoteCall.setListener(listener);
            remoteCall.setFilter(filter);
            remoteCall.setP4Ticket(p4ticket);
            LogPrinter logPrinter = new LogPrinter(listener.getLogger(), p4in);
            logPrinter.start();
            filePath.act(remoteCall);
            logPrinter.join();

        } catch (Exception e) {
            throw new PerforceException("Could not run quick clean.", e);
        }
    }
    
    private class LogPrinter extends Thread {

        private PrintStream log;
        private InputStream input;

        LogPrinter(PrintStream log, InputStream input) {
            this.log = log;
            this.input = input;
        }

        @Override
        public void run() {
            try {
                IOUtils.copy(input, log);
            } catch (IOException ex) {
                Logger.getLogger(QuickCleaner.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private static String[] getEnvFromDepot(Depot depot, String workDir) {
        String[] keys = {
            "P4USER",
            "P4PASSWD",
            "P4PORT",
            "P4COMMANDCHARSET",
            "P4CHARSET",
            "P4CLIENT",
            "PATH",
            "SystemDrive",
            "SystemRoot"
        };

        ArrayList<String> result = new ArrayList<String>();
        for (int i = 0; i < keys.length; i++) {
            String value = depot.getProperty(keys[i]);
            if (value != null && !value.trim().isEmpty()) {
                result.add(keys[i] + "=" + value);
            }
        }
        try {
            result.add("PWD=" + new File(workDir).getCanonicalPath());
            result.add("CD=" + new File(workDir).getCanonicalPath());
        } catch (IOException ex) {
            Logger.getLogger(QuickCleaner.class.getName()).log(Level.SEVERE, null, ex);
        }
        result.add("P4CONFIG=");
        return result.toArray(new String[result.size()]);
    }

    public interface RemoteCall extends Callable<Integer, IOException> {

        Integer call() throws IOException;

        void setEnv(String[] env);

        void setFilter(FileFilter filter);

        void setListener(TaskListener listener);

        void setOut(OutputStream out);

        void setP4exe(String p4exe);

        void setWorkDir(String workDir);

        void setP4Ticket(String p4ticket);
        
    }
    
    public static class PerforceCall extends Thread {

        private String[] env;
        private InputStream input;
        private OutputStream output;
        private String workDir;
        private TaskListener listener;
        private String[] cmdList;
        private boolean closePipes;

        PerforceCall(String[] env, String[] cmdList, InputStream input, OutputStream output, String workDir, TaskListener listener, boolean closeOutputPipe) {
            this.input = input;
            this.output = output;
            this.env = env;
            this.workDir = workDir;
            this.listener = listener;
            this.cmdList = cmdList;
            this.closePipes = closeOutputPipe;
        }

        @Override
        public void run() {
            Launcher.ProcStarter ps = new Launcher.LocalLauncher(listener).launch();
            ps.envs(env).stdin(input).stdout(output).cmds(cmdList);
            if (workDir != null) {
                ps.pwd(workDir);
            }
            Proc p;
            try {
                p = ps.start();
                Integer ret = p.join();
                //return ret;
            } catch (InterruptedException e) {
                if (output != null && closePipes) {
                    IOUtils.closeQuietly(output);
                }
                //return -1;
            } catch (IOException e) {
                if (output != null && closePipes) {
                    IOUtils.closeQuietly(output);
                }
            } finally {
                if (closePipes) {
                    IOUtils.closeQuietly(output);
                }
                IOUtils.closeQuietly(input);
            }
        }
    }
}
