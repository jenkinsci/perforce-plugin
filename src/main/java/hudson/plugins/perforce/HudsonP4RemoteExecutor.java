/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package hudson.plugins.perforce;

import java.io.*;
import java.util.Map;
import java.util.Set;

import com.tek42.perforce.PerforceException;
import com.tek42.perforce.process.Executor;
import hudson.CloseProofOutputStream;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.LocalLauncher;
import hudson.Launcher.RemoteLauncher;
import hudson.Launcher.RemoteLauncher.ProcImpl;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.Proc.RemoteProc;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.Channel.Listener;
import hudson.remoting.FastPipedInputStream;
import hudson.remoting.FastPipedOutputStream;
import hudson.remoting.Future;
import hudson.remoting.RemoteInputStream;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.VirtualChannel;
import hudson.util.StreamTaskListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/* Implementation of the P4Java Executor interface that provides support for
 * remotely executing Perforce commands.
 * <p/>
 *
 * This differs from HudsonP4Executor in that it doesn't use the native hudson
 * launcher. We need to use our own callable in order to gain better control over
 * the remote streams.
 *
 * <p/>
 *
 * User contract: Use this class only once to execute a command. ,to execute
 * another command, spawn another Exector using the Exector Factory
 *
 * @author rpetti
 */
public class HudsonP4RemoteExecutor implements HudsonP4Executor {

    private BufferedReader reader;
    private BufferedWriter writer;

    private InputStream input;
    private OutputStream output;

    private Launcher hudsonLauncher;
    private String[] env;
    private FilePath filePath;
    
    private Proc currentProcess;

    /**
     * Constructor that takes Hudson specific details for launching the
     * Perforce commands.
     *
     * @param hudsonLauncher
     * @param envMap
     * @param filePath
     */
    HudsonP4RemoteExecutor(Launcher hudsonLauncher, Map<String, String> envMap, FilePath filePath) {
        this.hudsonLauncher = hudsonLauncher;
        this.env = convertEnvMaptoArray(envMap);
        this.filePath = filePath;
    }

    public void close() {
        // Need to close writer
        // (reader gets closed by remote process)
        try {
            output.close();
        } catch(IOException e) {
            // Do nothing
        }
    }

    public void exec(String[] cmd) throws PerforceException {
        try {
            
            // hudsonOut->p4in->reader
            HudsonPipedOutputStream hudsonOut = new HudsonPipedOutputStream();
            FastPipedInputStream p4in = new FastPipedInputStream(hudsonOut);
            input = p4in;

            // hudsonIn<-p4Out<-writer
            FastPipedInputStream hudsonIn = new FastPipedInputStream();
            FastPipedOutputStream p4out = new FastPipedOutputStream(hudsonIn);
            output = p4out;

            final OutputStream out   = new RemoteOutputStream(hudsonOut);
            final InputStream  in    = new RemoteInputStream(hudsonIn);

            String remotePath = filePath.getRemote();

            ProcStarter ps = hudsonLauncher.new ProcStarter();
            ps.cmds(Arrays.asList(cmd));
            ps.envs(env);
            ps.stdin(in);
            ps.stdout(out); // stderr is bundled into stdout
            ps.pwd(remotePath);
            
            currentProcess = hudsonLauncher.launch(ps);
            
            // Required to close hudsonOut stream
            hudsonOut.closeOnProcess(currentProcess);
        } catch(IOException e) {
            //try to close all the pipes before throwing an exception
            closeBuffers();

            throw new PerforceException("Could not run perforce command.", e);
	}

    }

    /**
     * Function for converting map of environment variables to a String
     * array as hudson does not provide a launcher method that takes
     * <p/>
     * (1) Environment Map (2) InputStream (3) OutputStream
     * <p> .. at the same time
     *
     * @param envMap
     *
     * @return
     */
    private String[] convertEnvMaptoArray(Map<String, String> envMap) {
        Set<String> keySet = envMap.keySet();
        String[] keys = keySet.toArray(new String[0]);

        String[] result = new String[keys.length];
        for (int i = 0; i < keys.length; i++)
            result[i] = keys[i] + "=" + envMap.get(keys[i]);

        return result;
    }

    public BufferedWriter getWriter() {
        if(writer==null){
            writer = new BufferedWriter(new OutputStreamWriter(output));
        }
        return writer;
    }

    public BufferedReader getReader() {
        if(reader==null){
            reader = new BufferedReader(new InputStreamReader(input));
        }
        return reader;
    }

    private void closeBuffers(){
        try {
            input.close();
        } catch(IOException ignoredException) {};
        try {
            output.close();
        } catch(IOException ignoredException) {};
    }

    public InputStream getInputStream() {
        return input;
    }

    public OutputStream getOutputStream() {
        return output;
    }

    public void kill() {
        closeBuffers();
        
        try {       	
            currentProcess.kill();
        }
        catch(IOException ignoredException) {}
        catch(InterruptedException ignoredException) {}
    }
}
