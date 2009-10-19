package hudson.plugins.perforce;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.tasks.MailAddressResolver;
import hudson.util.StreamTaskListener;

/**
 * Implementation of {@link MailAddressResolver} for looking up the email address of a user in the Perforce repository.
 *
 * @author Mike
 *         Date: Apr 22, 2008 2:01:37 PM
 */
@Extension
public class PerforceMailResolver extends MailAddressResolver {
	
    @SuppressWarnings("unchecked")
	public String findMailAddressFor(User u) {
    	
    	/*
		The previous implementation only generated NPEs for me, and this one just hangs.  So I'm
		just going to leave it as a no-op on the theory that it's no worse than I found it.
		<http://www.nabble.com/PerforceMailResolver-is-broken--td25934348.html>
		*/
//        for (AbstractProject p : u.getProjects()) {
//
//            if (p.getScm() instanceof PerforceSCM) {
//                PerforceSCM pscm = (PerforceSCM) p.getScm();
//                TaskListener listener = new StreamTaskListener(System.out);
//                try {
//                    // I'm not sure if this is the standard way to create an ad-hoc Launcher, I just
//                	// copied it from HudsonP4Executor.exec
//                	Launcher launcher = Hudson.getInstance().createLauncher(listener);
//                    com.tek42.perforce.model.User pu = pscm.getDepot(launcher, p.getWorkspace()).getUsers().getUser(u.getId());
//                    if (pu.getEmail() != null && !pu.getEmail().equals(""))
//                        return pu.getEmail();
//
//                } catch (Exception e) {
//                    listener.getLogger().println("Could not get email address from Perforce: "+e.getMessage());
//                }
//            }
//        }
        return null;
    }

}
