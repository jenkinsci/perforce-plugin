package hudson.plugins.perforce;

import hudson.Launcher;
import hudson.Proc;
import hudson.Util;
import hudson.model.TaskListener;
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
public class QuickCleanerCall implements Callable<Integer, IOException> {

    private final String[] env;
    private final OutputStream out;
    private final String workDir;
    private final TaskListener listener;
    private final String p4exe;
    private final FileFilter filter;

    QuickCleanerCall(String p4exe, String[] env, OutputStream out, String workDir, TaskListener listener, FileFilter filter) {
        this.env = env;
        this.out = out;
        this.workDir = workDir;
        this.listener = listener;
        this.p4exe = p4exe;
        this.filter = filter;
    }

    public Integer call() throws IOException {
        PipedOutputStream dsOutput = new PipedOutputStream();
        PipedInputStream p4Input = new PipedInputStream();
        PipedOutputStream p4Output = new PipedOutputStream();
        PipedInputStream cleanerInput = new PipedInputStream();

        DirectoryScanner directoryScanner = new DirectoryScanner(workDir, dsOutput, filter);
        ProcessByPerforce p4Processor = new ProcessByPerforce(env, p4exe, p4Input, p4Output, workDir);
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
    private class ProcessByPerforce extends Thread {

        private String[] env;
        private String p4exe;
        private InputStream input;
        private OutputStream output;
        private String workDir;

        ProcessByPerforce(String[] env, String p4exe, InputStream input, OutputStream output, String workDir) {
            this.input = input;
            this.output = output;
            this.env = env;
            this.p4exe = p4exe;
            this.workDir = workDir;
        }

        @Override
        public void run() {
            ArrayList<String> cmdList = new ArrayList<String>();
            //cmdList.add("env");
            cmdList.add(p4exe);
            cmdList.add("-x-");
            cmdList.add("have");
            //cmdList.add("info");
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
                if (output != null) {
                    IOUtils.closeQuietly(output);
                }
                //return -1;
            } catch (IOException e) {
                if (output != null) {
                    IOUtils.closeQuietly(output);
                }
            } finally {
                IOUtils.closeQuietly(input);
                IOUtils.closeQuietly(output);
            }
        }
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
                            log("Error deleting file: " + filename);
                        }
                    }
                }
            } catch (IOException e) {
                // TODO: Handle IO errors
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
                    return file.delete();
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
