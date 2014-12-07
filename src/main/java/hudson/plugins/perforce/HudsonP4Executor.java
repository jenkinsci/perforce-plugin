/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package hudson.plugins.perforce;

import com.tek42.perforce.PerforceException;
import com.tek42.perforce.process.Executor;
import java.io.InputStream;
import java.io.OutputStream;

/**
 *
 * @author rpetti
 */
public interface HudsonP4Executor extends Executor {

    void close();

    void exec(String[] cmd) throws PerforceException;

    InputStream getInputStream();

    OutputStream getOutputStream();

}
