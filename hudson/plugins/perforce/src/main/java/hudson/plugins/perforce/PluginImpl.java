package hudson.plugins.perforce;

import hudson.Plugin;
import hudson.scm.SCM;
import hudson.scm.SCMS;
import hudson.scm.RepositoryBrowsers;
import hudson.tasks.BuildStep;

import hudson.plugins.perforce.browsers.*;

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
        RepositoryBrowsers.LIST.add(P4Web.DESCRIPTOR);
        RepositoryBrowsers.LIST.add(FishEyePerforce.DESCRIPTOR);
        super.start();
    }
}
