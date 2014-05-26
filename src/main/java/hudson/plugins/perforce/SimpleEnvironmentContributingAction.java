package hudson.plugins.perforce;

import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.AbstractBuild;

import java.util.Hashtable;
import java.util.Map;

/**
 * Store extra environment variables into the action, and participate in
 * populating he build environment once added into the build.
 * 
 */
public class SimpleEnvironmentContributingAction implements
        EnvironmentContributingAction {
    private Map<String, String> env = new Hashtable<String, String>();

    /**
     * Contribute an environment variable.
     * 
     * @param key
     *            The name of the variable.
     * @param value
     *            The value of the variable.
     */
    public void put(String key, String value) {
        if (key != null && value != null) {
            this.env.put(key, value);
        }
    }

    public String getIconFileName() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getDisplayName() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getUrlName() {
        // TODO Auto-generated method stub
        return null;
    }

    public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
        // TODO Auto-generated method stub
        env.putAll(this.env);
    }

}
