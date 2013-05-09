package hudson.plugins.perforce;

import hudson.Launcher;
import hudson.Proc;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.plugins.perforce.QuickCleaner.PerforceCall;
import hudson.remoting.Callable;
import java.io.*;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author rpetti
 */
public class QuickCleanerCall implements QuickCleaner.RemoteCall {

    private String[] env;
    private OutputStream out;
    private String workDir;
    private TaskListener listener;
    private String p4exe;
    private FileFilter filter;
    private String p4ticket;
    
    QuickCleanerCall() {
        
    }

    @Override
    public void setEnv(String[] env) {
        this.env = env;
    }

    @Override
    public void setFilter(FileFilter filter) {
        this.filter = filter;
    }

    @Override
    public void setListener(TaskListener listener) {
        this.listener = listener;
    }

    @Override
    public void setOut(OutputStream out) {
        this.out = out;
    }

    @Override
    public void setP4exe(String p4exe) {
        this.p4exe = p4exe;
    }

    @Override
    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }
    
    @Override
    public Integer call() throws IOException {
        PipedOutputStream dsOutput = new PipedOutputStream();
        PipedInputStream p4Input = new PipedInputStream();
        PipedOutputStream p4Output = new PipedOutputStream();
        PipedInputStream cleanerInput = new PipedInputStream();

        DirectoryScanner directoryScanner = new DirectoryScanner(workDir, dsOutput, filter);
        PerforceCall p4Processor = createHaveCall(env, p4exe, p4ticket, p4Input, p4Output, workDir, listener);
        Cleaner cleaner = new Cleaner(workDir, cleanerInput, out);

        dsOutput.connect(p4Input);
        p4Output.connect(cleanerInput);

        cleaner.start();
        p4Processor.start();
        directoryScanner.start();

        try {
            directoryScanner.join();
            p4Processor.join();
            cleaner.join();
        } catch (InterruptedException e) {
            directoryScanner.interrupt();
            p4Processor.interrupt();
            cleaner.interrupt();
        }
        return 0;
    }

    public void setP4Ticket(String p4ticket) {
        this.p4ticket = p4ticket;
    }

    //Scans the specified path for all files
    private class DirectoryScanner extends Thread {

        private File workDir;
        private BufferedWriter output;
        private FileFilter filter;

        DirectoryScanner(String workDir, OutputStream os, FileFilter filter) {
            this.workDir = new File(workDir);
            this.output = new BufferedWriter(new OutputStreamWriter(os));
            this.filter = filter;
        }

        @Override
        public void run() {
            try {
                scanDirForFiles(workDir);
            } catch (IOException ex) {
                Logger.getLogger(QuickCleanerCall.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                IOUtils.closeQuietly(output);
            }
        }

        private void scanDirForFiles(File dir) throws IOException {
            for (File file : dir.listFiles()) {
                if (Util.isSymlink(file)) {
                    continue;
                }
                if (filter == null || filter.accept(file)) {
                    if (file.isFile()) {
                        outputFilePath(file);
                    } else if (file.isDirectory()) {
                        scanDirForFiles(file);
                    }
                }
            }
        }

        private void outputFilePath(File file) throws IOException {
            String path = file.getPath();
            if (path.startsWith(workDir.getPath())) {
                path = path.substring(workDir.getPath().length());
            } else {
                return;
            }
            if (path.startsWith(File.separator)) {
                path = path.substring(1);
            }

            output.write(path);
            output.newLine();
            output.flush();

        }
    }

    //Ask perforce if they are tracked        
    private PerforceCall createHaveCall (String[] env, String p4exe, String p4ticket, InputStream input, OutputStream output, String workDir, TaskListener listener) {
        ArrayList<String> cmdlist = new ArrayList<String>();
        cmdlist.add(p4exe);
        if(p4ticket != null && !p4ticket.trim().isEmpty()){
            cmdlist.add("-P");
            cmdlist.add(p4ticket);
        }
        cmdlist.add("-d");
        cmdlist.add(workDir);
        cmdlist.add("-x-");
        cmdlist.add("have");
        return new PerforceCall(env, cmdlist.toArray(new String[cmdlist.size()]), input, output, workDir, listener, true);
    }

    //Deletes untracked files
    private class Cleaner extends Thread {

        private BufferedReader in;
        private BufferedWriter log;
        private String workDir;

        Cleaner(String workDir, InputStream input, OutputStream err) {
            this.workDir = workDir;
            this.in = new BufferedReader(new InputStreamReader(input));
            this.log = new BufferedWriter(new OutputStreamWriter(err));
        }

        @Override
        public void run() {
            String line;
            try {
                while ((line = in.readLine()) != null) {
                    if (line.contains("- file(s) not on client.")) {
                        String filename = line.replace("- file(s) not on client.", "").trim();
                        File file = new File(workDir, filename);
                        if (!safelyDelete(file)) {
                            log("WARNING: Problem deleting file during quick clean: " + filename);
                        }
                    }
                }
            } catch (IOException e) {
                try {
                    log("Exception occurred while cleaning files: " + e.getMessage());
                } catch (IOException ignored) {
                    // can't do anything about this
                }
            } finally {
                IOUtils.closeQuietly(in);
                IOUtils.closeQuietly(log);
            }
        }

        private boolean safelyDelete(File file) throws IOException {
            File parent = (new File(workDir)).getCanonicalFile();
            File testPath = file.getCanonicalFile();
            while ((testPath = testPath.getParentFile()) != null) {
                if (testPath.equals(parent)) {
                    Util.deleteFile(file);
                    if(!file.exists()) {
                        return true;
                    } else {
                        return false;
                    }
                }
            }
            log("Warning, file outside workspace not cleaned: " + file.getPath());
            return false;
        }

        private void log(String string) throws IOException {
            log.write(string);
            log.newLine();
            log.flush();
        }
    }
}
