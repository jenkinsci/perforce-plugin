package hudson.plugins.perforce;

import hudson.Plugin;
import hudson.scm.SCM;
import hudson.scm.SCMS;
import hudson.scm.RepositoryBrowsers;
import hudson.tasks.BuildStep;
import hudson.tasks.MailAddressResolver;

import hudson.plugins.perforce.browsers.*;

/**
 * 
 * @author Mike Wille
 */
public class PluginImpl extends Plugin {
    public void start() throws Exception {
        
        SCMS.SCMS.add(PerforceSCM.DESCRIPTOR);
        RepositoryBrowsers.LIST.add(P4Web.DESCRIPTOR);
        RepositoryBrowsers.LIST.add(FishEyePerforce.DESCRIPTOR);
		MailAddressResolver.LIST.add(new PerforceMailResolver());
		super.start();
    }
}
