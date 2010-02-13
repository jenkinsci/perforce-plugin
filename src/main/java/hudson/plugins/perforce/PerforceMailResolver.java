package hudson.plugins.perforce;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.Project;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.tasks.MailAddressResolver;
import hudson.util.StreamTaskListener;
import java.io.File;

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
    	
        for (AbstractProject p : u.getProjects()) {
            if (p.isDisabled()) continue;
            if (p.getScm() instanceof PerforceSCM) {
                PerforceSCM pscm = (PerforceSCM) p.getScm();
                TaskListener listener = new StreamTaskListener(System.out);
                try {
                    Node node = p.getLastBuiltOn();
                    // If the node is offline, skip the project.
                    // The node needs to be online for us to execute commands.
                    if (node.getChannel() == null) continue;
                    // TODO: replace this with p.getLastBuild().getWorkspace()
                    // which is the way it should be, but doesn't work with this version of hudson.
                    FilePath workspace = node.createPath(p.getLastBuiltOn().getRootPath().getRemote());
                    // I'm not sure if this is the standard way to create an ad-hoc Launcher, I just
                    // copied it from HudsonP4Executor.exec
                    Launcher launcher = Hudson.getInstance().createLauncher(listener);
                    com.tek42.perforce.model.User pu = pscm.getDepot(launcher, workspace).getUsers().getUser(u.getId());
                    if (pu.getEmail() != null && !pu.getEmail().equals(""))
                        return pu.getEmail();

                } catch (Exception e) {
                    listener.getLogger().println("Could not get email address from Perforce: "+e.getMessage());
                }
            }
        }
        return null;
    }

}
