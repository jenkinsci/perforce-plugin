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
import hudson.Proc;
import hudson.Proc.RemoteProc;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.FastPipedInputStream;
import hudson.remoting.FastPipedOutputStream;
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

    private Launcher hudsonLauncher;
    private String[] env;
    private FilePath filePath;

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
            writer.close();
        } catch(IOException e) {
            // Do nothing
        }
    }

    public void exec(String[] cmd) throws PerforceException {
        try {
            // ensure we actually have a valid hudson launcher
            if (null == hudsonLauncher) {
                hudsonLauncher = Hudson.getInstance().createLauncher(new StreamTaskListener(System.out));
            }
            VirtualChannel channel = hudsonLauncher.getChannel();

            // hudsonOut->p4in->reader
            FastPipedOutputStream hudsonOut = new FastPipedOutputStream();
            FastPipedInputStream p4in = new FastPipedInputStream(hudsonOut);
            reader = new BufferedReader(new InputStreamReader(p4in));

            // hudsonIn<-p4Out<-writer
            FastPipedInputStream hudsonIn = new FastPipedInputStream();
            FastPipedOutputStream p4out = new FastPipedOutputStream(hudsonIn);
            writer = new BufferedWriter(new OutputStreamWriter(p4out));

            final OutputStream out = hudsonOut == null ? null : new RemoteOutputStream(hudsonOut);
            final InputStream  in  = hudsonIn ==null ? null : new RemoteInputStream(hudsonIn);
            Proc proc = new RemoteProc(channel.callAsync(new RemoteCall(Arrays.asList(cmd), env, in, out, null, filePath.getRemote(), hudsonLauncher.getListener())));

        } catch(IOException e) {
            //try to close all the pipes before throwing an exception
            closeBuffers();

            throw new PerforceException("Could not run perforce command.", e);
        }
    }

    private static class RemoteCall implements Callable<Integer,IOException> {
        private final List<String> cmd;
        private final String[] env;
        private final InputStream in;
        private final OutputStream out;
        private final OutputStream err;
        private final String workDir;
        private final TaskListener listener;

        RemoteCall(List<String> cmd, String[] env, InputStream in, OutputStream out, OutputStream err, String workDir, TaskListener listener) {
            this.cmd = new ArrayList<String>(cmd);
            this.env = env;
            this.in = in;
            this.out = out;
            this.err = err;
            this.workDir = workDir;
            this.listener = listener;
        }
        public Integer call() throws IOException {
            Launcher.ProcStarter ps = new LocalLauncher(listener).launch();
            ps.cmds(cmd).envs(env).stdin(in).stdout(out);
            if(err != null) ps.stderr(err);
            if(workDir!=null)   ps.pwd(workDir);

            Proc p = ps.start();
            try {
                Integer ret = p.join();
                if(out!=null) out.close();
                if(err!=null) err.close();
                return ret;
            } catch (InterruptedException e) {
                if(out!=null) out.close();
                if(err!=null) err.close();
                return -1;
            }
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

    public BufferedReader getReader() {
        return reader;
    }

    public BufferedWriter getWriter() {
        return writer;
    }

    private void closeBuffers(){
        try {
            reader.close();
        } catch(IOException ignoredException) {};
        try {
            writer.close();
        } catch(IOException ignoredException) {};
    }

}
