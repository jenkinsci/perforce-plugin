package hudson.plugins.perforce;

import hudson.model.AbstractProject;
import hudson.model.User;
import hudson.tasks.MailAddressResolver;

/**
 * Implementation of {@link MailAddressResolver} for looking up the email address of a user in the Perforce repository.
 *
 * @author Mike
 *         Date: Apr 22, 2008 2:01:37 PM
 */
public class PerforceMailResolver extends MailAddressResolver {
	public String findMailAddressFor(User u) {

		for(AbstractProject p : u.getProjects()) {

			if(p.getScm() instanceof PerforceSCM) {
				PerforceSCM pscm = (PerforceSCM) p.getScm();
				try {
					// couldn't resist the name pu...
					com.tek42.perforce.model.User pu = pscm.getDepot().getUsers().getUser(u.getId());
					if(pu.getEmail() != null && !pu.getEmail().equals(""))
						return pu.getEmail();

				} catch(Exception e) {
					// where are we supposed to log this errror?
				}
			}
		}
		return null;
	}

}
