package hudson.plugins.perforce;

import java.io.File;
import java.io.IOException;
import java.io.*;
import java.net.*;
import java.util.Date;
import java.util.List;

import org.apache.xml.utils.URI;
import org.kohsuke.stapler.StaplerRequest;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.*;
import hudson.scm.SCMDescriptor;

import com.tek42.perforce.*;
import com.tek42.perforce.model.*;

/**
 * Extends {@link SCM} to provide integration with Perforce SCM.
 * 
 * @author Mike Wille
 * 
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
	
	int lastChange = 0;
	
	Depot depot;
		
	public PerforceSCM(String p4User, String p4Pass, String p4Client, String p4Port, String projectPath, 
						String p4Exe, String p4SysRoot, String p4SysDrive) {
		
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
	}
	
	/**
	 * This only exists because we need to do initialization after we have been brought
	 * back to life.  I'm not quite clear on stapler and how all that works.
	 * At any rate, it doesn't look like we have an init() method for setting up our Depot
	 * after all of the setters have been called.  Someone correct me if I'm wrong...
	 */
	private Depot getDepot() {
		if(depot == null) {
			depot = new Depot();
			depot.setUser(p4User);
			depot.setPassword(p4Passwd);
			depot.setPort(p4Port);
			depot.setClient(p4Client);
			depot.setExecutable(p4Exe);
			depot.setSystemDrive(p4SysDrive);
			depot.setSystemRoot(p4SysRoot);
		}
		return depot;
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
	private String getLocalPathName(FilePath path) throws IOException, InterruptedException {
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
		String sep = System.getProperty("file.separator");
		if(sep.equals("\\")) {
			// just replace with sep doesn't work because java's foobar regexp replaceAll
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
			Workspace p4workspace = getDepot().getWorkspaces().getWorkspace(p4Client);
			if(p4workspace == null) {
				throw new PerforceException("Workspace: " + p4Client + " doesn't exist.");
			}

			// 2. Before we sync, we need to update the client spec. (we do this on every build)
			// Here we are getting the local file path to the workspace.  We will use this as the "Root"
			// config property of the client spec. This tells perforce where to store the files on our local disk
			String localPath = getLocalPathName(workspace);
			listener.getLogger().println("Changing P4 Client Root to: " + localPath);
							
			// 3. We tell perforce to map the project contents directly (this drops off the project 
			// name from the workspace. So instead of: 
			//	[Hudson]/[job]/workspace/[Project]/contents
			// we get:
			//	[Hudson]/[job]/workspace/contents
			String view = projectPath + " //" + p4workspace.getName() + "/...";
			listener.getLogger().println("Changing P4 Client View to: " + view);
			
			// 4. Go and save the client using our custom method and not the Perforce API...
			p4workspace.setRoot(localPath);
			p4workspace.clearViews();
			p4workspace.addView(view);
			depot.getWorkspaces().saveWorkspace(p4workspace);
			
			// 5. Get the list of changes since the last time we looked...
			listener.getLogger().println("Last sync'd change: " + lastChange);
			List<Changelist> changes = depot.getChanges().getChangelistsFromNumbers(depot.getChanges().getChangeNumbersTo(projectPath, lastChange + 1));
			if(changes.size() > 0) {
				// save the last change we sync'd to for use when polling...
				lastChange = changes.get(0).getChangeNumber();
				System.out.println("Changelog file: " + changelogFile);
				PerforceChangeLogSet.saveToChangeLog(new FileOutputStream(changelogFile), changes);
			} else {
				listener.getLogger().println("No changes since last build.");
				createEmptyChangeLog(changelogFile, listener, "changelog");
				return false;
			}
						
			// 7. Now we can actually do the sync process...
			long startTime = System.currentTimeMillis();
			listener.getLogger().println("Sync'ing workspace to depot.");
			
			// XXX: There is a potential issue here.  If a user goes and deletes their workspace files they
			// will never be downloaded from perforce again.  Perforce tracks what files you have on your local
			// dev instance.  To get around this we would do a "force" sync.  Unfortunately, the API doesn't
			// provide that option.  The workaround is to go into perforce and change the client to be sync'd
			// to revision 0.
			depot.getWorkspaces().syncToHead(projectPath);
			
			listener.getLogger().println("Sync complete, took " + (System.currentTimeMillis() - startTime) + " MS");
			
			// And I'm spent...
			
			return true;
			
		} catch(PerforceException e) {
			
			listener.getLogger().print("Caught Exception communicating with perforce." + e.getMessage());
			e.printStackTrace();
			throw new IOException("Unable to communicate with perforce.  Check log file for: " + e.getMessage());
		} finally {
			//Utils.cleanUp();
		}
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
			listener.getLogger().println("Looking for changes...");
			List<Changelist> changes = getDepot().getChanges().getChangelists(projectPath, -1, 1);
			listener.getLogger().println("Latest change in depot is: " + changes.get(0).getChangeNumber());
			listener.getLogger().println(changes.get(0).toString());
			listener.getLogger().println("Last sync'd change is : " + lastChange);
			if(lastChange != changes.get(0).getChangeNumber()) {
				return true;
			}
			return false;
		} catch(PerforceException e) {
			System.out.println("Problem: " + e.getMessage());
			listener.getLogger().println("Caught Exception communicating with perforce." + e.getMessage());
			e.printStackTrace();
			throw new IOException("Unable to communicate with perforce.  Check log file for: " + e.getMessage());
		} finally {
			//Utils.cleanUp();
		}
		
		//return false;
	}
	
	public static final class PerforceSCMDescriptor extends SCMDescriptor<PerforceSCM> {
        
        private PerforceSCMDescriptor() {
            super(PerforceSCM.class, null);
            load();
        }

        public String getDisplayName() {
            return "Perforce";
        }

        public SCM newInstance(StaplerRequest req) throws FormException {
            return new PerforceSCM(
                req.getParameter("p4.user"),
                req.getParameter("p4.passwd"),
                req.getParameter("p4.client"),
                req.getParameter("p4.port"),
                req.getParameter("projectPath"),
                req.getParameter("p4.exe"),
                req.getParameter("p4.sysRoot"),
                req.getParameter("p4.sysDrive"));
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
	
	public void setLastChange(int change) {
		lastChange = change;
	}
	
	public int getLastChange() {
		return lastChange;
	}
	
}

