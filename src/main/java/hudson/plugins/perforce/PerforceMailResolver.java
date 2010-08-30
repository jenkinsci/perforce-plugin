package hudson.plugins.perforce;

import com.tek42.perforce.PerforceException;
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
import java.util.logging.Logger;

/**
 * Implementation of {@link MailAddressResolver} for looking up the email address of a user in the Perforce repository.
 *
 * @author Mike
 *         Date: Apr 22, 2008 2:01:37 PM
 */
@Extension
public class PerforceMailResolver extends MailAddressResolver {
    private static final Logger LOGGER = Logger.getLogger(PerforceMailResolver.class.getName());

    @SuppressWarnings("unchecked")
    public String findMailAddressFor(User u) {
    	LOGGER.fine("Email address for " + u.getId() + " requested.");
        for (AbstractProject p : u.getProjects()) {
            if (p.isDisabled()) continue;
            if (p.getScm() instanceof PerforceSCM) {
                LOGGER.finer("Checking " + p.getName() + "'s SCM for " + u.getId() + "'s address.");
                PerforceSCM pscm = (PerforceSCM) p.getScm();
                TaskListener listener = new StreamTaskListener(System.out);
                Node node = p.getLastBuiltOn();

                // If the node is offline, skip the project.
                // The node needs to be online for us to execute commands.
                if (node == null) {
                    LOGGER.finer("Build doesn't seem to have been run before. Cannot resolve email address using this project.");
                    continue;
                }
                if (node.getChannel() == null) {
                    LOGGER.finer("Node " + node.getDisplayName() + " is not up, cannot resolve email address using this project.");
                    continue;
                }
                // TODO: replace this with p.getLastBuild().getWorkspace()
                // which is the way it should be, but doesn't work with this version of hudson.
                for (int tries = 0; tries < 5; tries++) {
                    FilePath workspace = p.getLastBuiltOn().getRootPath();
                    Launcher launcher = p.getLastBuiltOn().createLauncher(listener);
                    com.tek42.perforce.model.User pu = null;
                    try {
                        LOGGER.finer("Trying to get email address from perforce for " + u.getId());
                        pu = pscm.getDepot(launcher, workspace, p).getUsers().getUser(u.getId());
                    } catch (Exception e) {
                        LOGGER.fine("Could not get email address from Perforce: " + e.getMessage());
                        e.printStackTrace(listener.getLogger());
                    }
                    if (pu != null && pu.getEmail() != null && !pu.getEmail().equals("")) {
                        LOGGER.fine("Got email (" + pu.getEmail() + ") from perforce for " + u.getId());
                        return pu.getEmail();
                    }
                    try {
                        //gradually increase sleep time
                        Thread.sleep(tries*300);
                    } catch (InterruptedException e){
                        return null;
                    }
                }
            }
        }
        return null;
    }

}
