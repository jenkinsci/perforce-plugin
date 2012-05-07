
package hudson.plugins.perforce;

import hudson.Launcher;
import hudson.Proc;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
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
public class QuickCleaner implements Callable<Integer, IOException>{
        private final String[] env;
        private final OutputStream out;
        private final String workDir;
        private final TaskListener listener;
        private final String p4exe;

        QuickCleaner(String p4exe, String[] env, OutputStream out, String workDir, TaskListener listener) {
            this.env = env;
            this.out = out;
            this.workDir = workDir;
            this.listener = listener;
            this.p4exe = p4exe;
        }
        public Integer call() throws IOException {
            PipedOutputStream dsOutput = new PipedOutputStream();
            PipedInputStream p4Input = new PipedInputStream();
            PipedOutputStream p4Output = new PipedOutputStream();
            PipedInputStream cleanerInput = new PipedInputStream();
            
            DirectoryScanner directoryScanner = new DirectoryScanner(workDir, dsOutput);
            ProcessByPerforce p4Processor = new ProcessByPerforce(env, p4exe, p4Input, p4Output);
            Cleaner cleaner = new Cleaner(workDir, cleanerInput, out);
            
            dsOutput.connect(p4Input);
            p4Output.connect(cleanerInput);
            
            cleaner.start();
            p4Processor.start();
            directoryScanner.start();
            
            try{
                directoryScanner.join();
                p4Processor.join();
                cleaner.join();
            } catch (InterruptedException e){
                
            }
            return 0;
        }
        
        //Scans the specified path for all files
        private class DirectoryScanner extends Thread{
            private File workDir;
            private BufferedWriter output;
            DirectoryScanner(String workDir, OutputStream os){
                this.workDir = new File(workDir);
                this.output = new BufferedWriter(new OutputStreamWriter(os));
            }

            @Override
            public void run() {
                try {
                    scanDirForFiles(workDir);
                } catch (IOException ex) {
                    Logger.getLogger(QuickCleaner.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            
            private void scanDirForFiles(File dir) throws IOException {
                for(File file : dir.listFiles()) {
                    if(Util.isSymlink(file)) continue;
                    if(file.isFile()){
                        outputFilePath(file);
                    } else if(file.isDirectory()){
                        scanDirForFiles(file);
                    }
                }
            }
            
            private void outputFilePath(File file){
                String path = file.getPath();
                if(path.startsWith(workDir.getPath()))
                    path = path.substring(workDir.getPath().length());
                else
                    return;
                if(path.startsWith(File.separator))
                    path = path.substring(1);
                try {
                    output.write(path);
                    output.newLine();
                    output.flush();
                } catch (IOException e) {
                    //TODO handle io errors
                }
            }
            
        }
        
        //Ask perforce if they are tracked        
        private class ProcessByPerforce extends Thread{
            private String[] env;
            private String p4exe;
            private InputStream input;
            private OutputStream output;
            ProcessByPerforce(String[] env, String p4exe, InputStream input, OutputStream output){
                this.input = input;
                this.output = output;
                this.env = env;
                this.p4exe = p4exe;
            }

            @Override
            public void run() {
                ArrayList<String> cmdList = new ArrayList<String>();
                cmdList.add(p4exe);
                cmdList.add("-x-");
                cmdList.add("have");
                Launcher.ProcStarter ps = new Launcher.LocalLauncher(listener).launch();
                ps.envs(env).stdin(input).stdout(out).cmds(cmdList);
                if(workDir!=null) ps.pwd(workDir);
                Proc p;
                try {
                    p = ps.start();
                    Integer ret = p.join();
                    if(out!=null) out.close();
                    //return ret;
                } catch (InterruptedException e) {
                    if(out!=null) IOUtils.closeQuietly(out);
                    //return -1;
                } catch (IOException e) {
                    if(out!=null) IOUtils.closeQuietly(out);
                }
            }
        }
        
        //Deletes untracked files
        private class Cleaner extends Thread{
            private InputStream input;
            private OutputStream err;
            private String workDir;
            Cleaner(String workDir, InputStream input, OutputStream err){
                this.workDir = workDir;
                this.input = input;
                this.err = err;
            }
            @Override
            public void run() {
                BufferedReader in = new BufferedReader(new InputStreamReader(input));
                BufferedWriter error = new BufferedWriter(new OutputStreamWriter(err));
                String line;
                try{
                    while((line = in.readLine()) != null){
                        if(line.contains("- file(s) not on client.")){
                            String filename = line.replace("- file(s) not on client.", "").trim();
                            File file = new File(workDir,filename);
                            if(!file.delete()){
                                error.write("Error deleting file: "+line.trim());
                                error.newLine();
                                error.flush();
                            }
                        }
                    }
                }catch(IOException e){
                    //TODO Handle IO errors
                }
            }
        }
}
