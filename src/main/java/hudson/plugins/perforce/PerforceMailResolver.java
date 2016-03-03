package hudson.plugins.perforce;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.MailAddressResolver;
import hudson.util.StreamTaskListener;
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

    public String findMailAddressFor(User u) {
        String email = findPerforceMailAddressFor(u);
        if (email == null){
            return null;
        } else if (email.matches(".+@.+")){
            return email;
        } else {
            LOGGER.fine("Rejecting invalid email ("+ email +") retrieved from perforce.");
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    public String findPerforceMailAddressFor(User u) {
        LOGGER.fine("Email address for " + u.getId() + " requested.");
        String perforceId = u.getId();
        PerforceUserProperty puprop = u.getProperty(PerforceUserProperty.class);
        if (puprop != null){
            if(puprop.getPerforceId() != null){
                LOGGER.fine("Using perforce user id '" + perforceId + "' from " + u.getId() + "'s properties.");
                perforceId = puprop.getPerforceId();
            }
            if(puprop.getPerforceEmail() != null){
                LOGGER.fine("Got email ("+puprop.getPerforceEmail()+") from " + u.getId() +"'s P4 properties.");
                return puprop.getPerforceEmail();
            }
        }
        for (AbstractProject p : Hudson.getInstance().getAllItems(AbstractProject.class)) {
            if (!(p instanceof TopLevelItem)) continue;
            if (p.isDisabled()) continue;
            if (p.getScm() instanceof PerforceSCM) {
                LOGGER.finer("Checking " + p.getName() + "'s Perforce SCM for " + perforceId + "'s address.");
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
                    com.tek42.perforce.model.User pu;
                    try {
                        LOGGER.finer("Trying to get email address from perforce for " + perforceId);
                        pu = pscm.getDepot(launcher, workspace, p, null, node).getUsers().getUser(perforceId);
                        if (pu != null && pu.getEmail() != null && !pu.getEmail().equals("")) {
                            LOGGER.fine("Got email (" + pu.getEmail() + ") from perforce for " + perforceId);
                            return pu.getEmail();
                        } else {
                            //operation succeeded, but no email address was found for this user
                            return null;
                        }
                    } catch (Exception e) {
                        LOGGER.fine("Could not get email address from Perforce: " + e.getMessage());
                        e.printStackTrace(listener.getLogger());
                    }
                    try {
                        //gradually increase sleep time
                        Thread.sleep(tries);
                    } catch (InterruptedException e){
                        return null;
                    }
                }
            }
        }
        return null;
    }

}
