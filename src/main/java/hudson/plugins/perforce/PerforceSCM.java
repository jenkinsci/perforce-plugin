package hudson.plugins.perforce;

import static hudson.Util.fixNull;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.RepositoryBrowsers;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.FormFieldValidator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.io.ObjectInputStream;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;
import com.tek42.perforce.model.Changelist;
import com.tek42.perforce.model.Workspace;

/**
 * Extends {@link SCM} to provide integration with Perforce SCM repositories.
 *
 * @author Mike Wille
 * @author Brian Westrich
 * @author Victor Szoltysek
 */
public class PerforceSCM extends SCM {

	public static final PerforceSCM.PerforceSCMDescriptor DESCRIPTOR = new PerforceSCM.PerforceSCMDescriptor();

	String p4User;
	String p4Passwd;
	String p4Port;
	String p4Client;
	String projectPath;

	String p4Exe = "C:\\Program Files\\Perforce\\p4.exe";
	String p4SysDrive = "C:";
	String p4SysRoot = "C:\\WINDOWS";

	transient Depot depot;

	PerforceRepositoryBrowser browser;

	/**
	 * This is being removed, including it as transient to fix exceptions on startup.
	 */
	transient int lastChange;
	/**
	 * force sync is a one time trigger from the config area to force a sync with the depot.
	 * it is reset to false after the first checkout.
	 */
	boolean forceSync = false;
	/**
	 * If true, we will manage the workspace view within the plugin.  If false, we will leave the
	 * view alone.
	 */
	boolean updateView = true;
	/**
	 * If > 0, then will override the changelist we sync to for the first build.
	 */
	int firstChange = -1;

	public PerforceSCM(String p4User, String p4Pass, String p4Client, String p4Port, String projectPath,
						String p4Exe, String p4SysRoot, String p4SysDrive, boolean forceSync,
						boolean updateView, int firstChange, PerforceRepositoryBrowser browser) {

		this.p4User = p4User;
		this.p4Passwd = p4Pass;
		this.p4Client = p4Client;
		this.p4Port = p4Port;
		this.projectPath = projectPath;

		if(p4Exe != null)
			this.p4Exe = p4Exe;

		if(p4SysRoot != null)
			this.p4SysRoot = p4SysRoot;

		if(p4SysDrive != null)
			this.p4SysDrive = p4SysDrive;

		this.forceSync = forceSync;
		this.browser = browser;
		this.updateView = updateView;
		this.firstChange = firstChange;
	}

	/**
	 * This only exists because we need to do initialization after we have been brought
	 * back to life.  I'm not quite clear on stapler and how all that works.
	 * At any rate, it doesn't look like we have an init() method for setting up our Depot
	 * after all of the setters have been called.  Someone correct me if I'm wrong...
	 *
	 * UPDATE: With the addition of PerforceMailResolver, we now have need to share the depot object.  I'm making
	 * this protected to enable that.
	 *
	 * Always create a new Depot to reflect any changes to the machines that
	 * P4 actions will be performed on.
	 */
	protected Depot getDepot(Launcher launcher, FilePath workspace) {

		HudsonP4ExecutorFactory p4Factory = new HudsonP4ExecutorFactory(launcher,workspace);
		depot = new Depot(p4Factory);
		depot.setUser(p4User);
		depot.setPassword(p4Passwd);
		depot.setPort(p4Port);
		depot.setClient(p4Client);
		depot.setExecutable(p4Exe);
		depot.setSystemDrive(p4SysDrive);
		depot.setSystemRoot(p4SysRoot);

		return depot;
	}

	/**
	 * Used for MailResolver
	 */
	protected Depot getDepot() {
	    return depot;
	}

	/**
	 * Depot is transient, so we need to create a new one on start up
	 * specifically for the getDepot() method.
	 */
	private void readObject(ObjectInputStream is){
	    try{
		is.defaultReadObject();

		depot = new Depot();
		depot.setUser(p4User);
		depot.setPassword(p4Passwd);
		depot.setPort(p4Port);
		depot.setClient(p4Client);
		depot.setExecutable(p4Exe);
		depot.setSystemDrive(p4SysDrive);
		depot.setSystemRoot(p4SysRoot);

	    } catch (IOException exception){
		//DO nothing
	    } catch (ClassNotFoundException exception){
		//DO nothing
	    }

	}

	/**
	 * Override of SCM.buildEnvVars() in order to setup the last change we have sync'd to as a Hudson
	 * environment variable: P4_CHANGELIST
	 *
	 * @param build
	 * @param env
	 */
	public void buildEnvVars(AbstractBuild build, Map<String, String> env) {
		super.buildEnvVars(build, env);
		int lastChange = getLastChange(build.getPreviousBuild());
		env.put("P4_CHANGELIST", Integer.toString(lastChange));
	}

	/**
	 * Perform some manipulation on the workspace URI to get a valid local path
	 * <p>
	 * Is there an issue doing this?  What about remote workspaces?  does that happen?
	 *
	 * @param path
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private String getLocalPathName(FilePath path, boolean isUnix) throws IOException, InterruptedException {
		String uriString = path.toURI().toString();
		// Get rid of URI prefix
		// NOTE: this won't handle remote files, is that a problem?
		uriString = uriString.replaceAll("file:/", "");
		// It seems there is a /./ to denote the root in the path on my test instance.
		// I don't know if this is in production, or how it works on other platforms (non win32)
		// but I am removing it here because perforce doesn't like it.
		uriString = uriString.replaceAll("/./", "/");
		// The URL is also escaped.  We need to unescape it because %20 in path names isn't cool for perforce.
		uriString = URLDecoder.decode(uriString, "UTF-8");

		// Last but not least, we need to convert this to local path separators.
		if(isUnix) {
		    // on unixen we need to prepend with /
		    uriString = "/" + uriString;

		} else {
		    //just replace with sep doesn't work because java's foobar regexp replaceAll
		    uriString = uriString.replaceAll("/", "\\\\");
		}

		return uriString;
	}

	/* (non-Javadoc)
	 * @see hudson.scm.SCM#checkout(hudson.model.AbstractBuild, hudson.Launcher, hudson.FilePath, hudson.model.BuildListener, java.io.File)
	 */
	@Override
	public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile) throws IOException, InterruptedException {

		try {
			listener.getLogger().println("Performing sync with Perforce for: " + projectPath);

			// Check to make sure our client is mapped to the local hudson directory...
		    // The problem is that perforce assumes a local directory on your computer maps
		    // directly to the remote depot.  Unfortunately, this doesn't work with they way
		    // Hudson sets up workspaces.  Not to worry!  What we do here is manipulate the
		    // perforce client spec before we do a checkout.
		    // An alternative would be to setup a single client for each build project.  While,
		    // that is possible, I think its wasteful makes setup time for the user longer as
		    // they have to go and create a workspace in perforce for each new project.

		    // 1. Retrieve the client specified, throw an exception if we are configured wrong and the
		    // client spec doesn't exist.
			//Update Hudson Exec Factory with new values:

			Workspace p4workspace = getDepot(launcher,workspace).getWorkspaces().getWorkspace(p4Client);
			assert p4workspace != null;
			boolean creatingNewWorkspace = p4workspace.getAccess() == null || p4workspace.getAccess().length() == 0;
			boolean usingLabel = projectPath.contains("@");

			// 2. Before we sync, we need to update the client spec (we do this on every build).
			// Here we are getting the local file path to the workspace.  We will use this as the "Root"
			// config property of the client spec. This tells perforce where to store the files on our local disk
			String localPath = getLocalPathName(workspace,launcher.isUnix());
			listener.getLogger().println("Changing P4 Client Root to: " + localPath);
			p4workspace.setRoot(localPath);

			// 3. Optionally regenerate the workspace view.
			// We tell perforce to map the project contents directly (this drops off the project
			// name from the workspace. So instead of:
			//	[Hudson]/[job]/workspace/[Project]/contents
			// we get:
			//	[Hudson]/[job]/workspace/contents
			if(updateView || creatingNewWorkspace) {
				String view = projectPath + " //" + p4workspace.getName() + "/...";
				listener.getLogger().println("Changing P4 Client View to: " + view);
				p4workspace.clearViews();
				p4workspace.addView(view);
			}

			// 3b. There is a slight chance of failure with sync'ing to head.  I've experienced
			// a problem where a sync does not happen and there is no error message.  It is when
			// the host value of the workspace does not match up with the host hudson is working on.
			// Perforce reports an error like: "Client 'hudson' can only be used from host 'workstation'."
			// but this does not show up in P4Java as an error.  Until P4Java is fixed, the workaround is
			// to clear the host value.
			p4workspace.setHost("");

			// 3c. Validate the workspace. Currently this only involves making sure project path is set to //...
			// if more than one workspace view exists (mostly because we don't know when you'd want to use any
			// project path other than that with multiple views, so haven't designed support for it.
			if (!updateView && p4workspace.getViews().size() > 1 && !PerforceSCMHelper.projectPathIsValidForMultiviews(projectPath)) {
				throw new PerforceException("Unless you are using a label, " +
						"the only project path currently supported when you have " +
						"multiple workspace views is '//...'. Please revise your project path or P4 workspace " +
						"accordingly.");
			}

			// 4. Go and save the client for use when sync'ing in a few...
			depot.getWorkspaces().saveWorkspace(p4workspace);

			// 5. Get the list of changes since the last time we looked...
			int lastChange = getLastChange((Run)build.getPreviousBuild());
			listener.getLogger().println("Last sync'd change: " + lastChange);

			List<Changelist> changes;
			if(usingLabel) {
				changes = new ArrayList<Changelist>(0);
			} else {
				changes = depot.getChanges().getChangelistsFromNumbers(depot.getChanges().getChangeNumbersTo(getChangesPaths(p4workspace), lastChange + 1));
			}

			if(changes.size() > 0) {
				// save the last change we sync'd to for use when polling...
				lastChange = changes.get(0).getChangeNumber();
				PerforceChangeLogSet.saveToChangeLog(new FileOutputStream(changelogFile), changes);

			} else if(usingLabel) {
				createEmptyChangeLog(changelogFile, listener, "changelog");

			} else if(!forceSync) {
				listener.getLogger().println("No changes since last build.");
				return createEmptyChangeLog(changelogFile, listener, "changelog");
			}

			// 7. Now we can actually do the sync process...
			long startTime = System.currentTimeMillis();
			listener.getLogger().println("Sync'ing workspace to depot.");

			if(forceSync)
				listener.getLogger().println("ForceSync flag is set, forcing: p4 sync " + projectPath);

			// Here we are testing to see if there is a label we are sync'ing to.  If so, we can't use the standard
			// sync to head method.  Note: this assumes that there are no @ in the project path.  If there are,
			// configure the plugin with %40 in place of @.  This is what P4V and P4Win do internally.
			if(projectPath.contains("@")) {
				listener.getLogger().println("Label found in projectPath, NOT sync'ing to the head.");
				depot.getWorkspaces().syncTo(projectPath, forceSync);
			} else {
				depot.getWorkspaces().syncToHead(projectPath, forceSync);
			}

			// reset one time use variables...
			forceSync = false;
			firstChange = -1;

			listener.getLogger().println("Sync complete, took " + (System.currentTimeMillis() - startTime) + " MS");

			// Add tagging action... (Only if we are not using labels.  You can't label a label...)
			if(!usingLabel) {
				build.addAction(new PerforceTagAction(build, depot, lastChange, projectPath));
			}

			// And I'm spent...
			build.getParent().save();  // The pertinent things we want to save are the one time use variables...

			return true;

		} catch(PerforceException e) {
			listener.getLogger().print("Caught Exception communicating with perforce. " + e.getMessage());
			e.printStackTrace();
			throw new IOException("Unable to communicate with perforce. " + e.getMessage());
		} finally {
			//Utils.cleanUp();
		}
	}

	/**
	 * compute the path(s) that we search on to detect whether the project
	 * has any unsynched changes
	 *
	 * @param p4workspace the workspace
	 * @return a string of path(s), e.g. //mymodule1/... //mymodule2/...
	 */
	private String getChangesPaths(Workspace p4workspace) {
		String changesPath;
		if (p4workspace.getViews().size() > 1) {
			changesPath = PerforceSCMHelper.computePathFromViews(p4workspace.getViews());
		} else {
			changesPath = projectPath;
		}
		return changesPath;
	}

	@Override
	public PerforceRepositoryBrowser getBrowser() {
	   return browser;
	}


	/* (non-Javadoc)
	 * @see hudson.scm.SCM#createChangeLogParser()
	 */
	@Override
	public ChangeLogParser createChangeLogParser() {
		return new PerforceChangeLogParser();
	}

	/* (non-Javadoc)
	 * @see hudson.scm.SCM#getDescriptor()
	 */
	@Override
	public SCMDescriptor<?> getDescriptor() {
		return DESCRIPTOR;
	}

	/* (non-Javadoc)
	 * @see hudson.scm.SCM#pollChanges(hudson.model.AbstractProject, hudson.Launcher, hudson.FilePath, hudson.model.TaskListener)
	 */
	@Override
	public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {

		try {
			int lastChange = getLastChange(project.getLastBuild());
			listener.getLogger().println("Looking for changes...");

			Workspace p4workspace = getDepot(launcher,workspace).getWorkspaces().getWorkspace(p4Client);

			// List<Changelist> changes = getDepot().getChanges().getChangelists(getChangesPaths(p4workspace), -1, 1);
			// the above call is slightly more efficient, but doesn't support multiple paths.
			// May not be worth optimizing (by implementing multiple path version of getChangelists) since after the first
			// build we rarely have more than a couple of changelists per build.
			List<Integer> changes = depot.getChanges().getChangeNumbersTo(getChangesPaths(p4workspace), lastChange + 1);

			listener.getLogger().println("Last sync'd change is : " + lastChange);
			if (changes.size() > 0) {
				listener.getLogger().println("New changes detected, triggering a build.");
				return true;
			}
			listener.getLogger().println("We have nothing to do.");
			return false;
		} catch(PerforceException e) {
			System.out.println("Problem: " + e.getMessage());
			listener.getLogger().println("Caught Exception communicating with perforce." + e.getMessage());
			e.printStackTrace();
			throw new IOException("Unable to communicate with perforce.  Check log file for: " + e.getMessage());
		}

	}

	public int getLastChange(Run build) {
		// If we are starting a new hudson project on existing work and want to skip the prior history...
		if(firstChange > 0)
			return firstChange;

		// If anything is broken, we will default to 0.
		if(build == null)
			return 0;

		PerforceTagAction action = build.getAction(PerforceTagAction.class);

		// if build had no actions, keep going back until we find one that does.
		if(action == null) {
			return getLastChange(build.getPreviousBuild());
		}

		return action.getChangeNumber();
	}

	public static final class PerforceSCMDescriptor extends SCMDescriptor<PerforceSCM> {

        private PerforceSCMDescriptor() {
            super(PerforceSCM.class, PerforceRepositoryBrowser.class);
            load();
        }

        public String getDisplayName() {
            return "Perforce";
        }

        public SCM newInstance(StaplerRequest req) throws FormException {
        	String value = req.getParameter("p4.forceSync");
        	boolean force = false;
        	if(value != null && !value.equals(""))
        		force = new Boolean(value);

        	value = req.getParameter("p4.updateView");
        	boolean update = false;
        	if(value != null && !value.equals(""))
        		update = new Boolean(value);

        	value = req.getParameter("p4.firstChange");
        	int firstChange = -1;
        	if(value != null && !value.equals(""))
        		firstChange = new Integer(value);

            return new PerforceSCM(
                req.getParameter("p4.user"),
                req.getParameter("p4.passwd"),
                req.getParameter("p4.client"),
                req.getParameter("p4.port"),
                req.getParameter("projectPath"),
                req.getParameter("p4.exe"),
                req.getParameter("p4.sysRoot"),
                req.getParameter("p4.sysDrive"),
                force,
                update,
                firstChange,
                RepositoryBrowsers.createInstance(PerforceRepositoryBrowser.class, req, "p4.browser"));
        }

    	public String isValidProjectPath(String path) {
    		if(!path.startsWith("//")) {
    			return "Path must start with '//' (Example: //depot/ProjectName/...)";
    		}
    		if(!path.endsWith("/...")) {
				if(!path.contains("@")) {
					return "Path must end with Perforce wildcard: '/...'  (Example: //depot/ProjectName/...)";
				}
			}
    		return null;
    	}

    	protected Depot getDepotFromRequest(StaplerRequest request) {
    		String port = fixNull(request.getParameter("port")).trim();
        	String exe = fixNull(request.getParameter("exe")).trim();
        	String user = fixNull(request.getParameter("user")).trim();
            String pass = fixNull(request.getParameter("pass")).trim();

            if(port.length()==0 || exe.length() == 0 || user.length() == 0 || pass.length() == 0) {// nothing entered yet
                return null;
            }
            Depot depot = new Depot();
            depot.setUser(user);
			depot.setPassword(pass);
			depot.setPort(port);
			depot.setExecutable(exe);

			return depot;
    	}

    	/**
    	 * Checks if the perforce login credentials are good.
    	 */
    	public void doValidatePerforceLogin(StaplerRequest request, StaplerResponse rsp) throws IOException, ServletException {
    		new FormFieldValidator(request, rsp, false) {
                protected void check() throws IOException, ServletException {
                	Depot depot = getDepotFromRequest(request);
        			if(depot != null) {
	        			try {
	        				depot.getStatus().isValid();
	        			} catch(PerforceException e) {
	        				error(e.getMessage());
	        			}
        			}
        			ok();
        			return;
                }
            }.check();
    	}

    	/**
    	 * Checks to see if the specified workspace is valid.
    	 */
    	public void doValidateWorkspace(StaplerRequest request, StaplerResponse rsp) throws IOException, ServletException {
    		new FormFieldValidator(request, rsp, false) {
                protected void check() throws IOException, ServletException {
                	Depot depot = getDepotFromRequest(request);
                	String workspace = request.getParameter("workspace");
        			if(depot != null) {
	        			try {
	        				depot.getWorkspaces().getWorkspace(workspace);
	        			} catch(PerforceException e) {
	        				error(e.getMessage());
	        			}
        			}
        			ok();
        			return;
                }
            }.check();
    	}

        /**
         * Checks if the value is a valid Perforce project path.
         */
        public void doCheckProjectPath(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new FormFieldValidator(req, rsp, false) {
                protected void check() throws IOException, ServletException {
                    String path = fixNull(request.getParameter("value")).trim();
                    if(path.length() == 0) {// nothing entered yet
                        ok();
                        return;
                    }
                    // TODO: Check against depot if the path actually exists via: p4 fstat -m 1 [projectPath]
                    error(isValidProjectPath(path));
                }
            }.check();
        }

        /**
         * Checks if the change list entered exists
         */
        public void doCheckChangeList(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        	new FormFieldValidator(req, rsp, false) {
                protected void check() throws IOException, ServletException {
                	Depot depot = getDepotFromRequest(request);
                    String change = fixNull(request.getParameter("change")).trim();

                    if(change.length() == 0) {// nothing entered yet
                        ok();
                        return;
                    }
        			if(depot != null) {
	        			try {
	        				int number = new Integer(change);
	        				Changelist changelist = depot.getChanges().getChangelist(number);
	        				if(changelist.getChangeNumber() != number)
	        					throw new PerforceException("broken");
	        			} catch(Exception e) {
	        				error("Changelist: " + change + " does not exist.");
	        			}
        			}
        			ok();
        			return;
                }
            }.check();
        }

	}

	/**
	 * @return the projectPath
	 */
	public String getProjectPath() {
		return projectPath;
	}

	/**
	 * @param projectPath the projectPath to set
	 */
	public void setProjectPath(String projectPath) {
		this.projectPath = projectPath;
	}

	/**
	 * @return the p4User
	 */
	public String getP4User() {
		return p4User;
	}

	/**
	 * @param user the p4User to set
	 */
	public void setP4User(String user) {
		p4User = user;
	}

	/**
	 * @return the p4Passwd
	 */
	public String getP4Passwd() {
		return p4Passwd;
	}

	/**
	 * @param passwd the p4Passwd to set
	 */
	public void setP4Passwd(String passwd) {
		p4Passwd = passwd;
	}

	/**
	 * @return the p4Port
	 */
	public String getP4Port() {
		return p4Port;
	}

	/**
	 * @param port the p4Port to set
	 */
	public void setP4Port(String port) {
		p4Port = port;
	}

	/**
	 * @return the p4Client
	 */
	public String getP4Client() {
		return p4Client;
	}

	/**
	 * @param client the p4Client to set
	 */
	public void setP4Client(String client) {
		p4Client = client;
	}

	/**
	 * @return the p4SysDrive
	 */
	public String getP4SysDrive() {
		return p4SysDrive;
	}

	/**
	 * @param sysDrive the p4SysDrive to set
	 */
	public void setP4SysDrive(String sysDrive) {
		p4SysDrive = sysDrive;
	}

	/**
	 * @return the p4SysRoot
	 */
	public String getP4SysRoot() {
		return p4SysRoot;
	}

	/**
	 * @param sysRoot the p4SysRoot to set
	 */
	public void setP4SysRoot(String sysRoot) {
		p4SysRoot = sysRoot;
	}

	/**
	 * @return the p4Exe
	 */
	public String getP4Exe() {
		return p4Exe;
	}

	/**
	 * @param exe the p4Exe to set
	 */
	public void setP4Exe(String exe) {
		p4Exe = exe;
	}

	/**
	 * @param update	True to let the plugin manage the view, false to let the user manage it
	 */
	public void setUpdateView(boolean update) {
		this.updateView = update;
	}

	/**
	 * @return 	True if the plugin manages the view, false if the user does.
	 */
	public boolean isUpdateView() {
		return updateView;
	}

	/**
	 * @return	True if we are performing a one-time force sync
	 */
	public boolean isForceSync() {
		return forceSync;
	}

	/**
	 * @param force	True to perform a one time force sync, false to perform normal sync
	 */
	public void setForceSync(boolean force) {
		this.forceSync = force;
	}

	/**
	 * This is only for the config screen.  Also, it returns a string and not an int.
	 * This is because we want to show an empty value in the config option if it is not being
	 * used.  The default value of -1 is not exactly empty.  So if we are set to default of
	 * -1, we return an empty string.  Anything else and we return the actual change number.
	 *
	 * @return	The one time use variable, firstChange.
	 */
	public String getFirstChange() {
		if(firstChange < 0)
			return "";
		return new Integer(firstChange).toString();
	}
}

