package hudson.plugins.perforce;

import java.util.Map;

import com.tek42.perforce.process.ExecutorFactory;
import hudson.FilePath;
import hudson.Launcher;

/*
 * Implementation of P4Java's ExecutorFactory to create new HudsonP4Executors
 *
 * @author Victor Szoltysek
 */
public class HudsonP4ExecutorFactory implements ExecutorFactory {

	/**
	 * FilePath, and Launcher are Hudson classes that are not serializable
	 * They need to be set as transient, or Hudson will throw exceptions.
	 * Make sure to create a new HudsonP4ExecutorFactory after de-serialization
	 */
	transient private Launcher hudsonLauncher;
	transient private Map<String, String> env;
	transient private FilePath filePath;

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
