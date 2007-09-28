package hudson.plugins.perforce;

import hudson.Plugin;
import hudson.scm.SCM;
import hudson.scm.SCMS;
import hudson.tasks.BuildStep;

/**
 * 
 * @author Mike Wille
 */
public class PluginImpl extends Plugin {
    public void start() throws Exception {
        // plugins normally extend Hudson by providing custom implementations
        // of 'extension points'. In this example, we'll add one builder.
        //BuildStep.BUILDERS.add(HelloWorldBuilder.DESCRIPTOR);
        SCMS.SCMS.add(PerforceSCM.DESCRIPTOR);
        super.start();
    }
}
