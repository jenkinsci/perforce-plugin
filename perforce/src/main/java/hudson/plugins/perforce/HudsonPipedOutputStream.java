package hudson.plugins.perforce;

import java.io.IOException;
import java.io.PipedOutputStream;

import hudson.Proc;

/**
 * Extended class of PipedOutputStream, used specifically for passing into
 * hudson launcher.launch() calls.  The extra closeOnProcess method is
 * required as launcher.launch() does NOT close its output stream, which will
 * cause a deadlock if using read calls (that block) to read data.
 * <p/>
 * The Hudson method in question that doesn't close it's output stream is
 * StreamCopyThread.class.
 * <p/>
 * User contract: After calling launcher.launch(), pass its process into
 * closeOnProcess().
 * <p/>
 * Example:
 * HudsonPipedOutputStream hudsonOut = new HudsonPipedOutputStream();
 * Proc process = hudsonLauncher.launch(cmd,env,hudsonOut,filePath);
 * hudsonOut.closeOnProcess(process);
 *
 * @author Victor Szoltysek
 */
public class HudsonPipedOutputStream extends PipedOutputStream {

	// Close stream, when hudson process finishes.
	public void closeOnProcess(final Proc process) {
		Runnable runnable = new Runnable() {
			public void run() {
				try {
					process.join();
				} catch(IOException e) {
					// Do nothing
				} catch(InterruptedException e) {
					// Do nothing
				}
				finally {
					try {
						close();
					} catch(IOException e) {
						// Do nothing
					}
				}
			}
		};

		new Thread(runnable).start();
	}

}
