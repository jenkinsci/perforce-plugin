package hudson.plugins.perforce;

import java.util.Map;

import com.tek42.perforce.process.ExecutorFactory;
import hudson.FilePath;
import hudson.Launcher;

/**
 * Implementation of P4Java's ExecutorFactory to create new HudsonP4Executors
 */
public class HudsonP4ExecutorFactory implements ExecutorFactory {

	/**
	 * Launcher and FilePath have to be transient as they are NOT seriazable,
	 * whereas Hudson will attempt to serizalize this class via:
	 * PerforceSCM->Depot->HudsonP4ExectorFactory.
	 */
	transient Launcher hudsonLauncher;
	transient Map<String, String> env;
	transient FilePath filePath;

	/**
	 * Hudson specific constructor to deal with launching P4 remotely.
	 *
	 * @param hudsonLauncher
	 * @param filePath
	 */
	HudsonP4ExecutorFactory(Launcher hudsonLauncher, FilePath filePath) {
		this.hudsonLauncher = hudsonLauncher;
		this.filePath = filePath;
	}
	
	public HudsonP4Executor newExecutor() {
		return new HudsonP4Executor(hudsonLauncher, env, filePath);
	}

	public void setEnv(Map<String, String> env) {
		this.env = env;
	}

}
