package hudson.plugins.perforce;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;
import com.tek42.perforce.model.Changelist;
import com.tek42.perforce.model.Workspace;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import static hudson.Util.fixNull;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Messages;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.FormFieldValidator;
import hudson.util.FormValidation;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extends {@link SCM} to provide integration with Perforce SCM repositories.
 *
 * @author Mike Wille
 * @author Brian Westrich
 * @author Victor Szoltysek
 */
public class PerforceSCM extends SCM {

	String p4User;
	String p4Passwd;
	String p4Port;
	String p4Client;	
	String projectPath;
	String p4Label;

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
	 * If false we add the slave hostname to then end of the client name when 
	 * running on a slave
	 */
	boolean dontRenameClient = false;	
		
	/**
	 * If > 0, then will override the changelist we sync to for the first build.
	 */
	int firstChange = -1;
	

    @DataBoundConstructor
	public PerforceSCM(String p4User, String p4Passwd, String p4Client, String p4Port, String projectPath,
						String p4Exe, String p4SysRoot, String p4SysDrive, String p4Label, boolean forceSync,
						boolean updateView, boolean dontRenameClient, int firstChange, PerforceRepositoryBrowser browser) {
    	
    	this.p4User = p4User;
		this.setP4Passwd(p4Passwd);
		this.p4Client = p4Client;
		this.p4Port = p4Port;
				
		//make it backwards compatible with the old way of specifying a label
		Matcher m = Pattern.compile("(@\\S+)\\s*").matcher(projectPath);
		if (m.find()) {
			p4Label = m.group(1);					
			projectPath = projectPath.substring(0,m.start(1)) 
				+ projectPath.substring(m.end(1)); 			
		}
		
		if (this.p4Label != null && p4Label != null) {
			Logger.getLogger(PerforceSCM.class.getName()).warning(
					"Label found in views and in label field.  Using:" 
					+ p4Label);
		}
		this.p4Label = Util.fixEmptyAndTrim(p4Label);		

		this.projectPath = projectPath;

		if(p4Exe != null)
			this.p4Exe = p4Exe;
		
		if(p4SysRoot != null && p4SysRoot != "")
			this.p4SysRoot = p4SysRoot;

		if(p4SysDrive != null && p4SysDrive != "")
			this.p4SysDrive = p4SysDrive;

		this.forceSync = forceSync;
		this.browser = browser;
		this.updateView = updateView;
		this.dontRenameClient = dontRenameClient;
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
                PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();

		depot.setPassword(encryptor.decryptString(p4Passwd));
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
		 PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();

		depot.setPassword(encryptor.decryptString(p4Passwd));
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
	 * Override of SCM.buildEnvVars() in order to setup the last change we have 
	 * sync'd to as a Hudson
	 * environment variable: P4_CHANGELIST
	 *
	 * @param build
	 * @param env
	 */
	public void buildEnvVars(AbstractBuild build, Map<String, String> env) {
		super.buildEnvVars(build, env);
		int lastChange = getLastDepotChange(build);
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
	public boolean checkout(AbstractBuild build, Launcher launcher, 
			FilePath workspace, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
		
		PrintStream log = listener.getLogger();
		
		//keep projectPath local so any modifications for slaves don't get saved
		String projectPath = this.projectPath;
		depot = getDepot(launcher,workspace);
		
		
		//this is a work around for issue 2062
		//https://hudson.dev.java.net/issues/show_bug.cgi?id=2062
		//we don't why but sometimes the connection drops when communicating 
		//with perforce retrying seems to work for now but this really needs
		//to be fixed properly
		int RETRY_COUNT = 6;
		int WAIT_PERIOD = 10000;
		for (int retryAttempt = 0; retryAttempt < RETRY_COUNT; retryAttempt++){		
		try {
			

			boolean creatingNewWorkspace = false;

			//make sure each slave has a unique client name by adding it's 
			//hostname to the end of the client spec
			String nodeSuffix = "";
			if ( !dontRenameClient &&
					build.getBuiltOnStr() != null && 
					build.getBuiltOnStr() != "") {

				//use the 1st part of the hostname as the node suffix
				String host = workspace.act(new GetHostname());
				if (host.contains(".")) {
					nodeSuffix = "-" + host.subSequence(0, host.indexOf('.'));	
				} else {
					nodeSuffix = "-" + host;
				}
				
				log.println("Changing client to " + p4Client + nodeSuffix);
				depot.setClient(p4Client + nodeSuffix);
				creatingNewWorkspace = true;
			}			
					
			Workspace p4workspace = 
				depot.getWorkspaces().getWorkspace(p4Client + nodeSuffix);
			
			//if the client doesn't exist set the name so it will be created
			p4workspace.setName(p4Client + nodeSuffix);
			assert p4workspace != null;
			creatingNewWorkspace = p4workspace.getAccess() == null || p4workspace.getAccess().length() == 0;

			//update the root to where hudson builds from
			String localPath = getLocalPathName(workspace,launcher.isUnix());
			log.println("Changing P4 Client Root to: " + localPath);
			p4workspace.setRoot(localPath);

			//optionally rewrite the views field in the client spec
			if(updateView || creatingNewWorkspace) {
				projectPath = fixProjectPath(projectPath, nodeSuffix);
				log.println("Changing P4 Client View to: " + projectPath);				
				p4workspace.clearViews();				
				for (String view : projectPath.split("\n")) {
					p4workspace.addView(" " + view);
				}				
			} 	
			
			//if we don't use the same client for each slave, erase the host 
			//field
			if (dontRenameClient) {			 
				p4workspace.setHost("");
			}	
			
			//save the client for use when sync'ing in a few...
			depot.getWorkspaces().saveWorkspace(p4workspace);

			//Get the list of changes since the last time we looked...
			int lastChange = getLastChange((Run)build.getPreviousBuild());
			log.println("Last sync'd change: " + lastChange);

			List<Changelist> changes;
			if(p4Label != null) {
				changes = new ArrayList<Changelist>(0);
			} else {
				changes = depot.getChanges().getChangelistsFromNumbers(
						depot.getChanges().getChangeNumbersTo(getChangesPaths(
								p4workspace), lastChange + 1));
			}

			if(changes.size() > 0) {
				// save the last change we sync'd to for use when polling...
				lastChange = changes.get(0).getChangeNumber();
				PerforceChangeLogSet.saveToChangeLog(
						new FileOutputStream(changelogFile), changes);

			} else if(p4Label != null) {
				createEmptyChangeLog(changelogFile, listener, "changelog");

			} else if(!forceSync) {
				log.println("No changes since last build.  Syncing workspace.");
				for (String view : projectPath.split("\n")) {
					
					depot.getWorkspaces().syncTo(
							view.split(" ")[0]+"@"+lastChange, forceSync);
				}
				return createEmptyChangeLog(
						changelogFile, listener, "changelog");
			}

			// Now we can actually do the sync process...
			long startTime = System.currentTimeMillis();
			log.println("Sync'ing workspace to depot.");

			if(forceSync)
				log.println("Forcing: p4 sync " + projectPath);

			//sync to the last changeset or a label if specified
			if(p4Label == null) {		
				
				for (String view : projectPath.split("\n")) {
					depot.getWorkspaces().syncTo(
							view.split(" ")[0]+"@"+lastChange, forceSync);
				}	
				
			} else {	
				
				listener.getLogger().println("Syncing to Label " + 
						p4Label + " instead of last change");
				for (String view : projectPath.split("\n")) {
					depot.getWorkspaces().syncTo(
							view.split(" ")[0]+"@"+p4Label, forceSync);
				}						
			}

			// reset one time use variables...
			forceSync = false;
			firstChange = -1;

			listener.getLogger().println("Sync complete, took " + 
					(System.currentTimeMillis() - startTime) + " MS");

			// Add tagging action unless using labels. You can't label a label
			if(p4Label == null) {
				build.addAction(new PerforceTagAction(
						build, depot, lastChange, projectPath));
			}

			//save the one time use variables...
			build.getParent().save();  

			return true;

		} catch(PerforceException e) {
			
			
			log.print("Caught Exception communicating with perforce. " + 
					e.getMessage());
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw, true);
			e.printStackTrace(pw);
			pw.flush();
			sw.flush();
			log.print(sw.toString());
			
			if (retryAttempt < RETRY_COUNT){
				try { Thread.sleep(WAIT_PERIOD); }
				catch (InterruptedException exception) {}
			} else {			
				throw new IOException(
						"Unable to communicate with perforce. " + 
						e.getMessage());
			}
		} catch (InterruptedException e) {
			retryAttempt = RETRY_COUNT;
			
			throw new IOException(
					"Unable to get hostname from slave. " + e.getMessage());
		} 
		}
		return false;		
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
		changesPath = PerforceSCMHelper.computePathFromViews(p4workspace.getViews());		
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
	
	public int getLastDepotChange(Run build){
		Depot depot = getDepot();
		int returnValue = -1;
		try{
			returnValue = depot.getChanges().getChangeNumbers("//...", -1, 1).get(0);
		}
		catch(PerforceException pe)
		{
			System.out.println("Problem: " + pe.getMessage());
			pe.printStackTrace();
		}
		return returnValue;
	}	

	/* (non-Javadoc)
	 * @see hudson.scm.SCM#pollChanges(hudson.model.AbstractProject, hudson.Launcher, hudson.FilePath, hudson.model.TaskListener)
	 */
	@Override
	public boolean pollChanges(AbstractProject project, Launcher launcher, 
			FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
		
		try {
			int lastChange = getLastChange(project.getLastBuild());
			listener.getLogger().println("Looking for changes...");

			Workspace p4workspace = getDepot(
					launcher,workspace).getWorkspaces().getWorkspace(p4Client);
			
			//make sure we're polling the specified paths
			if (p4workspace.getAccess() == null || 
					p4workspace.getAccess().length() == 0) {
				if (updateView) {
					this.projectPath = fixProjectPath(projectPath, "");
					listener.getLogger().println(
							"Changing P4 Client View to: " + projectPath);					
					p4workspace.clearViews();
					for (String view : projectPath.split("\n")) {
						//add a default workspace path if necessary
						if (projectPath.contains(p4Client)) {
							p4workspace.addView(view);
						} else {
							p4workspace.addView(view + 
									" //" + p4Client + "/...\n");
						}
					}				
				} else {
					listener.getLogger().println(
							Messages.AbstractProject_NoWorkspace());
				}
			}
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
		//log.println("Found last change: " + action.getChangeNumber());
		return action.getChangeNumber();
	}

    @Extension
	public static final class PerforceSCMDescriptor extends SCMDescriptor<PerforceSCM> {
        public PerforceSCMDescriptor() {
            super(PerforceSCM.class, PerforceRepositoryBrowser.class);
            load();
        }

        public String getDisplayName() {
            return "Perforce";
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
            
            if(port.length()==0 || exe.length() == 0 || user.length() == 0 ) {// nothing entered yet
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
    	public FormValidation doValidateP4Client(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {    		
    		
        	String workspace = Util.fixEmptyAndTrim(req.getParameter("client"));        	
        	Depot depot = getDepotFromRequest(req);
        	if (depot == null) {
        		return FormValidation.error(
        				"Unable to check workspace against depot");
        	}
        	if (workspace == null) {
        		return FormValidation.error("You must enter a workspaces name");
        	}        	
        	try {
        		Workspace p4Workspace = 
        			depot.getWorkspaces().getWorkspace(workspace); 
        		
        		if (p4Workspace.getAccess() == null || 
        				p4Workspace.getAccess() == "")		
					return FormValidation.warning("Workspace does not exist. " +
							"If \"Let Hudson Manage Workspace View\" is check" +
							" the workspace will be automatically created.");
			} catch (PerforceException e) {
				return FormValidation.error(
						"Error accessing perforce while checking workspace");
			} 
			
			return FormValidation.ok();
    	}
    	
        /**
         * Performs syntactical check on the P4Label
          */
        public FormValidation doValidateP4Label(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        	
        	String label = Util.fixEmptyAndTrim(req.getParameter("label"));
        	
        	if (label == null) 
        		return FormValidation.ok();
        	
        	Depot depot = getDepotFromRequest(req);
        	try {        		
            	com.tek42.perforce.model.Label p4Label = depot.getLabels().getLabel(label);        		
				if (p4Label.getAccess() == null || p4Label.getAccess() == "")		
					return FormValidation.error("Label does not exist");
			} catch (PerforceException e) {
				return FormValidation.error(
						"Error accessing perforce while checking label");
			}
            	
            return FormValidation.ok();
        }    	

        /**
         * Checks if the value is a valid Perforce project path.
         */
        public FormValidation doCheckProjectPath(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        	String views = Util.fixEmptyAndTrim(req.getParameter("value"));
        	for(String view : views.split("\n")) {
        		if (Pattern.matches("\\/\\/\\S+ \\/\\/\\S+", view))
        			return FormValidation.error("Invalid view:" + view);
        	}
        	return FormValidation.ok();
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
    
	private String fixProjectPath(String projectPath, String nodeSuffix) {	
				
		String newPath = "";
		for (String line : projectPath.split("\n")) {

			Matcher depotOnly = 
				Pattern.compile("^\\s*\\/\\/\\S+(\\/\\S+)\\s*$").matcher(line); 
			Matcher depotAndWorkspace =
				Pattern.compile(
						"\\s*(\\/\\/\\S+?\\/\\S+)\\s*\\/\\/\\S+?(\\/\\S+)"
						).matcher(line); 

			if (depotOnly.find()) {
				
				//add a default workspace path
				line = line + " //" + p4Client + nodeSuffix + "/...\n";
				
			} else if (depotAndWorkspace.find()) {
				
				line = depotAndWorkspace.group(1) + 
					" //" + p4Client + nodeSuffix + depotAndWorkspace.group(2);
			}
			newPath = newPath + line + "\n";
		}
		return newPath;
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
                PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
		p4Passwd = encryptor.encryptString(passwd);
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
	 * @return the p4Label
	 */
	public String getP4Label() {
		return p4Label;
	}

	/**
	 * @param exe the p4Label to set
	 */
	public void setP4Label(String label) {
		p4Label = label;
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
	 * @return	True if we are using a label
	 */
	public boolean isUseLabel() {
		return p4Label != null;
	}

	/**
	 * @param force	True to perform a one time force sync, false to perform normal sync
	 */
//	public void setForceSync(boolean force) {
//		this.forceSync = force;
//	}	
	
	/**
	 * @param dontRenameClient	False if the client will rename the client spec for each 
	 * slave
	 */
	public void setDontRenameClient(boolean dontRenameClient) {
		this.dontRenameClient = dontRenameClient;
	}

	/**
	 * @return 	True if the client will rename the client spec for each slave
	 */
	public boolean isDontRenameClient() {
		return dontRenameClient;
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
	
	/**
	 * Get the hostname of the client to use as the node suffix
	 */	
    private static final class GetHostname implements FileCallable<String> {
        public String invoke(File f, VirtualChannel channel) throws IOException {
                return InetAddress.getLocalHost().getHostName();
        }
        private static final long serialVersionUID = 1L;
    }	
    
    
    /**
     * With Perforce the server keeps track of files in the workspace.  We never
     * want files deleted without the knowledge of the server so we disable the 
     * cleanup process.
     * 
     * @param project
     *      The project that owns this {@link SCM}. This is always the same 
     *      object for a particular instanceof {@link SCM}. Just passed in here 
     *      so that {@link SCM} itself doesn't have to remember the value.
     * @param workspace
     *      The workspace which is about to be deleted. Never null. This can be 
     *      a remote file path.
     * @param node
     *      The node that hosts the workspace. SCM can use this information to 
     *      determine the course of action.
     *
     * @return
     *      true if {@link SCM} is OK to let Hudson proceed with deleting the 
     *      workspace.
     *      False to veto the workspace deletion.
     */
    @Override
    public boolean processWorkspaceBeforeDeletion(AbstractProject<?,?> project, FilePath workspace, Node node) {
    	Logger.getLogger(PerforceSCM.class.getName()).info(
    			"Veto workspace cleanup");
        return false;
    }    
}

