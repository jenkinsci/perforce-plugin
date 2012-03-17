package hudson.plugins.perforce;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;
import com.tek42.perforce.model.Changelist;
import com.tek42.perforce.model.Counter;
import com.tek42.perforce.model.Label;
import com.tek42.perforce.model.Workspace;
import com.tek42.perforce.parse.Counters;
import com.tek42.perforce.parse.Workspaces;
import com.tek42.perforce.model.Changelist.FileEntry;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import static hudson.Util.fixNull;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.*;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.util.FormValidation;
import hudson.util.LogTaskListener;

import hudson.util.StreamTaskListener;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Extends {@link SCM} to provide integration with Perforce SCM repositories.
 *
 * @author Mike Wille
 * @author Brian Westrich
 * @author Victor Szoltysek
 */
public class PerforceSCM extends SCM {

    private Long configVersion;
    
    String p4User;
    String p4Passwd;
    String p4Port;
    String p4Client;
	String clientSpec;
    String projectPath;
    String projectOptions;
    String p4Label;
    String p4Counter;
    String p4Stream;

    /**
     * Transient so that old XML data will be read but not saved.
     * @deprecated Replaced by {@link #p4Tool}
     */
    transient String p4Exe;
    String p4SysDrive = "C:";
    String p4SysRoot = "C:\\WINDOWS";

    PerforceRepositoryBrowser browser;

    private static final Logger LOGGER = Logger.getLogger(PerforceSCM.class.getName());

    private static final int MAX_CHANGESETS_ON_FIRST_BUILD = 50;

    /**
     * Name of the p4 tool installation
     */
    String p4Tool;

	/**
     * Use ClientSpec text file from depot to prepare the workspace view
     */
    boolean useClientSpec = false;
    /**
     * True if stream depot is used, false otherwise
     */
    boolean useStreamDepot = false;
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
     * Always force sync the workspace when running a build
     */
    boolean alwaysForceSync = false;
    /**
     * Don't update the 'have' database on the server when syncing.
     */
    boolean dontUpdateServer = false;
    /**
     * Disable Workspace pre-build automatic sync and changelog retrieval
     * This should be renamed if we can implement upgrade logic to handle old configs
     */
    boolean disableAutoSync = false;
    /**
     * Disable Workspace syncing
     */
    boolean disableSyncOnly = false;
    /**
     * This is to allow the client to use the old naming scheme
     * @deprecated As of 1.0.25, replaced by {@link #clientSuffixType}
     */
    @Deprecated
    boolean useOldClientName = false;
    /**
     * If true, we will create the workspace view within the plugin.  If false, we will not.
     */
    Boolean createWorkspace = true;
    /**
     * If true, we will manage the workspace view within the plugin.  If false, we will leave the
     * view alone.
     */
    boolean updateView = true;
    /**
     * If false we add the slave hostname to the end of the client name when
     * running on a slave.  Defaulting to true so as not to change the behavior
     * for existing users.
     * @deprecated As of 1.0.25, replaced by {@link #clientSuffixType}
     */
    @Deprecated
    boolean dontRenameClient = true;
    /**
     * If true we update the named counter to the last changelist value after the sync operation.
     * If false the counter will be used as the changelist value to sync to.
     * Defaulting to false since the counter name is not set to begin with.
     */
    boolean updateCounterValue = false;
    /**
     * If true, we will never update the client workspace spec on the perforce server.
     */
    boolean dontUpdateClient = false;
    /**
     * If true the environment value P4PASSWD will be set to the value of p4Passwd.
     */
    boolean exposeP4Passwd = false;

    /**
     * If true, the workspace will be deleted before the checkout commences.
     */
    boolean wipeBeforeBuild = false;

    /**
     * If true, the ,repository will be deleted before the checkout commences in addition to the workspace.
     */
    boolean wipeRepoBeforeBuild = false;

    /**
     * If > 0, then will override the changelist we sync to for the first build.
     */
    int firstChange = -1;

    /**
     * P4 user name(s) or regex user pattern to exclude from SCM poll to prevent build trigger.
     * Multiple user names are deliminated by space.
     */
    String excludedUsers;

    /**
     * P4 file(s) or regex file pattern to exclude from SCM poll to prevent build trigger.
     */
    String excludedFiles;

    /**
     * If a ticket was issued we can use it instead of the password in the environment.
     */
    private String p4Ticket = null;

    /**
     * Determines what to append to the end of the client workspace names on slaves
     * Possible values:
     *  None
     *  Hostname
     *  Hash
     */
    String slaveClientNameFormat = null;

    /**
     * We need to store the changelog file name for the build so that we can expose
     * it to the build environment
     */
    transient private String changelogFilename = null;

    /**
     * The value of the LineEnd field in the perforce Client spec.
     */
    private String lineEndValue = "local";

    /**
     * View mask settings for polling and/or syncing against a subset
     * of files in the client workspace.
     */
    private boolean useViewMask = false;
    private String viewMask = null;
    private boolean useViewMaskForPolling = true;
    private boolean useViewMaskForSyncing = false;

    /**
     * Sync only on master option.
     */
    private boolean pollOnlyOnMaster = false;

    /**
     * charset options
     */
    private String p4Charset = null;
    private String p4CommandCharset = null;

    @DataBoundConstructor
    public PerforceSCM(
            String p4User,
            String p4Passwd,
            String p4Client,
            String p4Port,
            String projectOptions,
            String p4Tool,
            String p4SysRoot,
            String p4SysDrive,
            String p4Label,
            String p4Counter,
            String lineEndValue,
            String p4Charset,
            String p4CommandCharset,
            boolean updateCounterValue,
            boolean forceSync,
            boolean dontUpdateServer,
            boolean alwaysForceSync,
            boolean createWorkspace,
            boolean updateView,
            boolean disableAutoSync,
            boolean disableSyncOnly,
            boolean wipeBeforeBuild,
            boolean wipeRepoBeforeBuild,
            boolean dontUpdateClient,
            boolean exposeP4Passwd,
            boolean pollOnlyOnMaster,
            String slaveClientNameFormat,
            int firstChange,
            PerforceRepositoryBrowser browser,
            String excludedUsers,
            String excludedFiles
            ) {

        this.configVersion = 0L;
        
        this.p4User = p4User;
        this.setP4Passwd(p4Passwd);
        this.exposeP4Passwd = exposeP4Passwd;
        this.p4Client = p4Client;
        this.p4Port = p4Port;
        this.p4Tool = p4Tool;
        this.pollOnlyOnMaster = pollOnlyOnMaster;
        this.projectOptions = (projectOptions != null)
                ? projectOptions
                : "noallwrite clobber nocompress unlocked nomodtime rmdir";

        if (this.p4Label != null && p4Label != null) {
            Logger.getLogger(PerforceSCM.class.getName()).warning(
                    "Label found in views and in label field.  Using: "
                    + p4Label);
        }
        this.p4Label = Util.fixEmptyAndTrim(p4Label);

        this.p4Counter = Util.fixEmptyAndTrim(p4Counter);
        this.updateCounterValue = updateCounterValue;

        this.projectPath = Util.fixEmptyAndTrim(projectPath);

        if (p4SysRoot != null && p4SysRoot.length() != 0)
            this.p4SysRoot = Util.fixEmptyAndTrim(p4SysRoot);

        if (p4SysDrive != null && p4SysDrive.length() != 0)
            this.p4SysDrive = Util.fixEmptyAndTrim(p4SysDrive);

        // Get systemDrive,systemRoot computer environment variables from
        // the current machine.
        String systemDrive = null;
        String systemRoot = null;
        if (Hudson.isWindows()) {
            try {
                EnvVars envVars = Computer.currentComputer().getEnvironment();
                systemDrive = envVars.get("SystemDrive");
                systemRoot = envVars.get("SystemRoot");
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, ex.getMessage(), ex);
            }
        }
        if (p4SysRoot != null && p4SysRoot.length() != 0) {
            this.p4SysRoot = Util.fixEmptyAndTrim(p4SysRoot);
        } else {
            if (systemRoot != null && !systemRoot.trim().equals("")) {
                this.p4SysRoot = Util.fixEmptyAndTrim(systemRoot);
            }
        }
        if (p4SysDrive != null && p4SysDrive.length() != 0) {
            this.p4SysDrive = Util.fixEmptyAndTrim(p4SysDrive);
        } else {
            if (systemDrive != null && !systemDrive.trim().equals("")) {
                this.p4SysDrive = Util.fixEmptyAndTrim(systemDrive);
            }
        }

        this.lineEndValue = lineEndValue;
        this.forceSync = forceSync;
        this.dontUpdateServer = dontUpdateServer;
        this.alwaysForceSync = alwaysForceSync;
        this.disableAutoSync = disableAutoSync;
        this.disableSyncOnly = disableSyncOnly;
        this.browser = browser;
        this.wipeBeforeBuild = wipeBeforeBuild;
        this.wipeRepoBeforeBuild = wipeRepoBeforeBuild;
        this.createWorkspace = Boolean.valueOf(createWorkspace);
        this.updateView = updateView;
        this.dontUpdateClient = dontUpdateClient;
        this.slaveClientNameFormat = slaveClientNameFormat;
        this.firstChange = firstChange;
        this.dontRenameClient = false;
        this.useOldClientName = false;
        this.p4Charset = Util.fixEmptyAndTrim(p4Charset);
        this.p4CommandCharset = Util.fixEmptyAndTrim(p4CommandCharset);
        this.excludedUsers = Util.fixEmptyAndTrim(excludedUsers);
        this.excludedFiles = Util.fixEmptyAndTrim(excludedFiles);
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
     *
     * @param node the value of node
     */
    protected Depot getDepot(Launcher launcher, FilePath workspace, AbstractProject project, AbstractBuild build, Node node) {

        HudsonP4ExecutorFactory p4Factory = new HudsonP4ExecutorFactory(launcher,workspace);

        Depot depot = new Depot(p4Factory);
        depot.setUser(p4User);

        depot.setPort(p4Port);
        if(build != null){
            depot.setClient(substituteParameters(p4Client, build));
            depot.setPassword(getDecryptedP4Passwd(build));
        }
        else if(project != null)
        {
            depot.setClient(substituteParameters(p4Client, getDefaultSubstitutions(project)));
            depot.setPassword(getDecryptedP4Passwd(project));
        } else {
            depot.setClient(p4Client);
            depot.setPassword(getDecryptedP4Passwd());
        }

        if(node == null)
            depot.setExecutable(getP4Executable(p4Tool));
        else
            depot.setExecutable(getP4Executable(p4Tool,node,TaskListener.NULL));
        depot.setSystemDrive(p4SysDrive);
        depot.setSystemRoot(p4SysRoot);

        depot.setCharset(p4Charset);
        depot.setCommandCharset(p4CommandCharset);

        return depot;
    }

    /**
     * Override of SCM.buildEnvVars() in order to setup the last change we have
     * sync'd to as a Hudson
     * environment variable: P4_CHANGELIST
     *
     * @param build
     * @param env
     */
    @Override
    public void buildEnvVars(AbstractBuild build, Map<String, String> env) {
        super.buildEnvVars(build, env);
        env.put("P4PORT", p4Port);
        env.put("P4USER", p4User);

        // if we want to allow p4 commands in script steps this helps
        if (exposeP4Passwd) {
            // this may help when tickets are used since we are
            // not storing the ticket on the client during login
            if (p4Ticket != null) {
                env.put("P4TICKET", p4Ticket);
            }
            PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
            env.put("P4PASSWD", encryptor.decryptString(p4Passwd));
        }

        env.put("P4CLIENT", getEffectiveClientName(build));
        PerforceTagAction pta = build.getAction(PerforceTagAction.class);
        if (pta != null) {
            if (pta.getChangeNumber() > 0) {
                int lastChange = pta.getChangeNumber();
                env.put("P4_CHANGELIST", Integer.toString(lastChange));
            }
            else if (pta.getTag() != null) {
                String label = pta.getTag();
                env.put("P4_LABEL", label);
            }
        }

        if(changelogFilename != null)
        {
            env.put("HUDSON_CHANGELOG_FILE", changelogFilename);
        }
    }

    /**
     * Get the path to p4 executable from a Perforce tool installation. 
     * 
     * @param tool the p4 tool installation name
     * @return path to p4 tool path or an empty string if none is found
     */
    public String getP4Executable(String tool) {
        PerforceToolInstallation toolInstallation = getP4Tool(tool);
        if(toolInstallation == null)
            return "p4";
        return toolInstallation.getP4Exe();
    }
    
    public String getP4Executable(String tool, Node node, TaskListener listener) {
        PerforceToolInstallation toolInstallation = getP4Tool(tool);
        if(toolInstallation == null)
            return "p4";
        String p4Exe="p4";
        try {
            p4Exe = toolInstallation.forNode(node, listener).getP4Exe();
        }catch(IOException e){
            listener.getLogger().println(e);
        }catch(InterruptedException e){
            listener.getLogger().println(e);
        }
        return p4Exe;
    }
    
    /**
     * Get the path to p4 executable from a Perforce tool installation. 
     * 
     * @param tool the p4 tool installation name
     * @return path to p4 tool installation or null
     */
    public PerforceToolInstallation getP4Tool(String tool) {
        PerforceToolInstallation[] installations = ((hudson.plugins.perforce.PerforceToolInstallation.DescriptorImpl)Hudson.getInstance().
                getDescriptorByType(PerforceToolInstallation.DescriptorImpl.class)).getInstallations();
        for(PerforceToolInstallation i : installations) {
            if(i.getName().equals(tool)) {
                return i;
            }
        }
        return null;
    }
    
    /**
     * Use the old job configuration data. This method is called after the object is read by XStream.
     * We want to create tool installations for each individual "p4Exe" path as field "p4Exe" has been removed.
     * 
     * @return the new object which is an instance of PerforceSCM
     */
    @SuppressWarnings( "deprecation" )
    public Object readResolve() {
        if(createWorkspace == null) {
            createWorkspace = Boolean.TRUE;
        }
        
        if(p4Exe != null) {
            PerforceToolInstallation.migrateOldData(p4Exe);
            p4Tool = p4Exe;
        }
        
        if(configVersion == null) {
            configVersion = 0L;
        }
        
        return this;
    }
    
    private Hashtable<String, String> getDefaultSubstitutions(AbstractProject project) {
        Hashtable<String, String> subst = new Hashtable<String, String>();
        subst.put("JOB_NAME", getSafeJobName(project));
        for (NodeProperty nodeProperty: Hudson.getInstance().getGlobalNodeProperties()) {
            if (nodeProperty instanceof EnvironmentVariablesNodeProperty) {
                subst.putAll( ((EnvironmentVariablesNodeProperty)nodeProperty).getEnvVars() );
            }
        }
        ParametersDefinitionProperty pdp = (ParametersDefinitionProperty) project.getProperty(hudson.model.ParametersDefinitionProperty.class);
        if(pdp != null) {
            for (ParameterDefinition pd : pdp.getParameterDefinitions()) {
                try {
                    ParameterValue defaultValue = pd.getDefaultParameterValue();
                    if(defaultValue != null) {
                        String name = defaultValue.getName();
                        String value = defaultValue.createVariableResolver(null).resolve(name);
                        subst.put(name, value);
                    }
                } catch (Exception e) {
                }
            }
        }

        return subst;
    }

    private String getEffectiveProjectPathFromFile(AbstractBuild build, AbstractProject project, PrintStream log, Depot depot) throws PerforceException {
        String projectPath;
        String clientSpec;
        if(build!=null){
            clientSpec = substituteParameters(this.clientSpec, build);
        } else {
            clientSpec = substituteParameters(this.clientSpec, getDefaultSubstitutions(project));
        }
        log.println("Read ClientSpec from: " + clientSpec);
        com.tek42.perforce.parse.File f = depot.getFile(clientSpec);
        projectPath = f.read();
        if(build!=null){
            projectPath = substituteParameters(projectPath, build);
        } else {
            projectPath = substituteParameters(projectPath, getDefaultSubstitutions(project));
        }
        return projectPath;
    }

    private int getLastBuildChangeset(AbstractProject project) {
        Run lastBuild = project.getLastBuild();
        return getLastChange(lastBuild);
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
        return processPathName(path.getRemote(), isUnix);
    }

    public static String processPathName(String path, boolean isUnix){
        String pathName = path;
        pathName = pathName.replaceAll("\\\\+", "\\\\");
        pathName = pathName.replaceAll("/\\./", "/");
        pathName = pathName.replaceAll("\\\\\\.\\\\", "\\\\");
        pathName = pathName.replaceAll("/+", "/");
        if(isUnix){
            pathName = pathName.replaceAll("\\\\", "/");
        } else {
            pathName = pathName.replaceAll("/", "\\\\");
        }
        return pathName;
    }

    private static void retrieveUserInformation(Depot depot, List<Changelist> changes) throws PerforceException {
        // uniqify in order to reduce number of calls to P4.
        HashSet<String> users = new HashSet<String>();
        for(Changelist change : changes){
            users.add(change.getUser());
        }
        for(String user : users){
            com.tek42.perforce.model.User pu;
            try{
                 pu = depot.getUsers().getUser(user);
            }catch(Exception e){
                throw new PerforceException("Problem getting user information for " + user,e);
            }
            User author = User.get(user);
            // Need to store the actual perforce user id for later retrieval
            // because Jenkins does not support all the same characters that
            // perforce does in the userID.
            PerforceUserProperty puprop = author.getProperty(PerforceUserProperty.class);
            if ( puprop == null || puprop.getPerforceId() == null || puprop.getPerforceId().equals("")){
                puprop = new PerforceUserProperty();
                try {
                    author.addProperty(puprop);
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
            puprop.setPerforceEmail(pu.getEmail());
            puprop.setPerforceId(user);
        }
    }

    private static class WipeWorkspaceExcludeFilter implements FileFilter, Serializable {
        
        private List<String> excluded = new ArrayList<String>();
        
        public WipeWorkspaceExcludeFilter(String... args){
            for(String arg : args){
                excluded.add(arg);
            }
        }
        
        public void exclude(String arg){
            excluded.add(arg);
        }
        
        public boolean accept(File arg0) {
            for(String exclude : excluded){
                if(arg0.getName().equals(exclude)){
                    return false;
                }
            }
            return true;
        }
    }

    private static boolean overrideWithBooleanParameter(String paramName, AbstractBuild build, boolean dflt) {
        boolean value = dflt;
        Object param;
        if(build.getBuildVariables() != null){
            if((param = build.getBuildVariables().get(paramName)) != null){
                String paramString = param.toString();
                if(paramString.toUpperCase().equals("TRUE") || paramString.equals("1")){
                    value = true;
                } else {
                    value = false;
                }
            }
        }
        return value;
    }
    
    /*
     * @see hudson.scm.SCM#checkout(hudson.model.AbstractBuild, hudson.Launcher, hudson.FilePath, hudson.model.BuildListener, java.io.File)
     */
    @Override
    public boolean checkout(AbstractBuild build, Launcher launcher,
            FilePath workspace, BuildListener listener, File changelogFile) throws IOException, InterruptedException {

        PrintStream log = listener.getLogger();
        changelogFilename = changelogFile.getAbsolutePath();

        boolean wipeBeforeBuild = overrideWithBooleanParameter(
                "P4CLEANWORKSPACE", build, this.wipeBeforeBuild);
        boolean wipeRepoBeforeBuild = overrideWithBooleanParameter(
                "P4CLEANREPOINWORKSPACE", build, this.wipeRepoBeforeBuild);
        boolean forceSync = overrideWithBooleanParameter(
                "P4FORCESYNC", build, this.forceSync);
        boolean disableAutoSync = overrideWithBooleanParameter(
                "P4DISABLESYNC", build, this.disableAutoSync);
        boolean disableSyncOnly = overrideWithBooleanParameter(
                "P4DISABLESYNCONLY", build, this.disableSyncOnly);

        
        //Use local variables so that substitutions are not saved
        String p4Label = substituteParameters(this.p4Label, build);
        String viewMask = substituteParameters(this.viewMask, build);
        Depot depot = getDepot(launcher,workspace, build.getProject(), build, build.getBuiltOn());
        String p4Stream = substituteParameters(this.p4Stream, build);
        
        
        //If we're doing a matrix build, we should always force sync.
        if((Object)build instanceof MatrixBuild || (Object)build instanceof MatrixRun){
            if(!alwaysForceSync && !wipeBeforeBuild)
                log.println("This is a matrix build; It is HIGHLY recommended that you enable the " +
                            "'Always Force Sync' or 'Clean Workspace' options. " +
                            "Failing to do so will likely result in child builds not being synced properly.");
        }

        try {
            //keep projectPath local so any modifications for slaves don't get saved
            String projectPath;
            if(useClientSpec){
                projectPath = getEffectiveProjectPathFromFile(build, build.getProject(), log, depot);
            } else {
                projectPath = substituteParameters(this.projectPath, build);
            }
        
            Workspace p4workspace = getPerforceWorkspace(build.getProject(), projectPath, depot, build.getBuiltOn(), build, launcher, workspace, listener, false);

            boolean dirtyWorkspace = p4workspace.isDirty();
            saveWorkspaceIfDirty(depot, p4workspace, log);

            if(wipeBeforeBuild){
                log.println("Clearing workspace...");
                String p4config = substituteParameters("${P4CONFIG}", build);
                WipeWorkspaceExcludeFilter wipeFilter = new WipeWorkspaceExcludeFilter(".p4config",p4config);
        	if(wipeRepoBeforeBuild){
                    log.println("Clear workspace includes .repository ...");                    
                } else {
                    log.println("Note: .repository directory in workspace (if exists) is skipped.");
                    wipeFilter.exclude(".repository");
                }
                List<FilePath> workspaceDirs = workspace.list(wipeFilter);
                for(FilePath dir : workspaceDirs){
                    dir.deleteRecursive();
                }
                log.println("Cleared workspace.");
                forceSync = true;
            }
            
            //In case of a stream depot, we want Perforce to handle the client views. So let's re-initialize
            //the p4workspace object if it was changed since the last build. Also, populate projectPath with
            //the current view from Perforce. We need it for labeling.
            if (useStreamDepot) {
                if (dirtyWorkspace) {
                    p4workspace = depot.getWorkspaces().getWorkspace(getEffectiveClientName(build), p4Stream);
                }
                projectPath = p4workspace.getTrimmedViewsAsString();
            }
            //If we're not managing the view, populate the projectPath with the current view from perforce
            //This is both for convenience, and so the labeling mechanism can operate correctly
            if(!updateView){
                projectPath = p4workspace.getTrimmedViewsAsString();
            }

            //Get the list of changes since the last time we looked...
            String p4WorkspacePath = "//" + p4workspace.getName() + "/...";
            int lastChange = getLastChange((Run)build.getPreviousBuild());
            log.println("Last build changeset: " + lastChange);

            int newestChange = lastChange;

            if(!disableAutoSync)
            {
                List<Changelist> changes;
                if (p4Label != null && !p4Label.trim().isEmpty()) {
                    newestChange = depot.getChanges().getHighestLabelChangeNumber(p4workspace, p4Label.trim(), p4WorkspacePath);
                } else {
                    String counterName;
                    if (p4Counter != null && !updateCounterValue)
                    	counterName = substituteParameters(this.p4Counter, build);
                    else
                        counterName = "change";

                    Counter counter = depot.getCounters().getCounter(counterName);
                    newestChange = counter.getValue();
                }
                
                if(build instanceof MatrixRun) {
                    newestChange = getOrSetMatrixChangeSet(build, depot, newestChange, projectPath, log);
                }

                if (lastChange <= 0){
                    lastChange = newestChange - MAX_CHANGESETS_ON_FIRST_BUILD;
                    if (lastChange < 0){
                        lastChange = 0;
                    }
                }
                if (lastChange >= newestChange) {
                    changes = new ArrayList<Changelist>(0);
                } else {
                    List<Integer> changeNumbersTo;
                    if(useViewMaskForSyncing && useViewMask){
                        changeNumbersTo = depot.getChanges().getChangeNumbersInRange(p4workspace, lastChange+1, newestChange, viewMask);
                    } else {
                        changeNumbersTo = depot.getChanges().getChangeNumbersInRange(p4workspace, lastChange+1, newestChange);
                    }
                    changes = depot.getChanges().getChangelistsFromNumbers(changeNumbersTo);
                }


                if (changes.size() > 0) {
                    // Save the changes we discovered.
                    PerforceChangeLogSet.saveToChangeLog(
                            new FileOutputStream(changelogFile), changes);
                    newestChange = changes.get(0).getChangeNumber();
                    // Get and store information about committers
                    retrieveUserInformation(depot, changes);
                }
                else {
                    // No new changes discovered (though the definition of the workspace or label may have changed).
                    createEmptyChangeLog(changelogFile, listener, "changelog");
                }

                if(!disableSyncOnly){
                    // Now we can actually do the sync process...
                    StringBuilder sbMessage = new StringBuilder("Sync'ing workspace to ");
                    StringBuilder sbSyncPath = new StringBuilder(p4WorkspacePath);
                    StringBuilder sbSyncPathSuffix = new StringBuilder();
                    sbSyncPathSuffix.append("@");

                    if (p4Label != null && !p4Label.trim().isEmpty()) {
                        sbMessage.append("label ");
                        sbMessage.append(p4Label);
                        sbSyncPathSuffix.append(p4Label);
                    }
                    else {
                        sbMessage.append("changelist ");
                        sbMessage.append(newestChange);
                        sbSyncPathSuffix.append(newestChange);
                    }

                    sbSyncPath.append(sbSyncPathSuffix);

                    if (forceSync || alwaysForceSync)
                        sbMessage.append(" (forcing sync of unchanged files).");
                    else
                        sbMessage.append(".");

                    log.println(sbMessage.toString());
                    String syncPath = sbSyncPath.toString();

                    long startTime = System.currentTimeMillis();

                    if(useViewMaskForSyncing && useViewMask){
                        for(String path : viewMask.replaceAll("\r", "").split("\n")){
                            StringBuilder sbMaskPath = new StringBuilder(path);
                            sbMaskPath.append(sbSyncPathSuffix);
                            String maskPath = sbMaskPath.toString();
                            depot.getWorkspaces().syncTo(maskPath, forceSync || alwaysForceSync, dontUpdateServer);
                        }
                    } else {
                        depot.getWorkspaces().syncTo(syncPath, forceSync || alwaysForceSync, dontUpdateServer);
                    }
                    long endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;

                    log.println("Sync complete, took " + duration + " ms");
                }
            }

            boolean doSaveProject = false;
            // reset one time use variables...
            if(this.forceSync == true || this.firstChange != -1){
                this.forceSync = false;
                this.firstChange = -1;
                //save the one time use variables...
                doSaveProject = true;
            }
            //If we aren't managing the client views, update the current ones
            //with those from perforce, and save them if they have changed.
            if(!this.updateView && !projectPath.equals(this.projectPath)){
                this.projectPath = projectPath;
                doSaveProject = true;
            }
            if(doSaveProject){
                build.getParent().save();
            }

            // Add tagging action that enables the user to create a label
            // for this build.
            build.addAction(new PerforceTagAction(
                build, depot, newestChange, projectPath, p4User));

            build.addAction(new PerforceSCMRevisionState(newestChange));

            if (p4Counter != null && updateCounterValue) {
                // Set or create a counter to mark this change
                Counter counter = new Counter();
                counter.setName(p4Counter);
                counter.setValue(newestChange);
                log.println("Updating counter " + p4Counter + " to " + newestChange);
                depot.getCounters().saveCounter(counter);
            }

            // remember the p4Ticket if we were issued one
            p4Ticket = depot.getP4Ticket();

            return true;

        } catch (PerforceException e) {
            log.print("Caught exception communicating with perforce. " + e.getMessage());
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw, true);
            e.printStackTrace(pw);
            pw.flush();
            log.print(sw.toString());
            throw new AbortException(
                    "Unable to communicate with perforce. " + e.getMessage());

        } catch (InterruptedException e) {
            throw new IOException(
                    "Unable to get hostname from slave. " + e.getMessage());
        }
    }

    private synchronized int getOrSetMatrixChangeSet(AbstractBuild build, Depot depot, int newestChange, String projectPath, PrintStream log) {
        int lastChange = 0;
        //special consideration for matrix builds
        if (build instanceof MatrixRun) {
            log.println("This is a matrix run, trying to use change number from parent/siblings...");
            AbstractBuild parentBuild = ((MatrixRun) build).getParentBuild();
            if (parentBuild != null) {
                int parentChange = getLastChange(parentBuild);
                if (parentChange > 0) {
                    //use existing changeset from parent
                    log.println("Latest change from parent is: "+Integer.toString(parentChange));
                    lastChange = parentChange;
                } else {
                    //no changeset on parent, set it for other
                    //matrixruns to use
                    log.println("No change number has been set by parent/siblings. Using latest.");
                    parentBuild.addAction(new PerforceTagAction(build, depot, newestChange, projectPath, p4User));
                }
            }
        }
        return lastChange;
    }

    /**
     * compute the path(s) that we search on to detect whether the project
     * has any unsynched changes
     *
     * @param p4workspace the workspace
     * @return a string of path(s), e.g. //mymodule1/... //mymodule2/...
     */
    private String getChangesPaths(Workspace p4workspace) {
        return PerforceSCMHelper.computePathFromViews(p4workspace.getViews());
    }

    @Override
    public PerforceRepositoryBrowser getBrowser() {
       return browser;
    }

    /*
     * @see hudson.scm.SCM#createChangeLogParser()
     */
    @Override
    public ChangeLogParser createChangeLogParser() {
        return new PerforceChangeLogParser();
    }
    /**
     * Part of the new polling routines. This determines the state of the build specified.
     * @param ab
     * @param lnchr
     * @param tl
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> ab, Launcher lnchr, TaskListener tl) throws IOException, InterruptedException {
        //This shouldn't be getting called, but in case it is, let's calculate the revision anyways.
        PerforceTagAction action = (PerforceTagAction)ab.getAction(PerforceTagAction.class);
        if(action==null){
            //something went wrong...
            return null;
        }
        return new PerforceSCMRevisionState(action.getChangeNumber());
    }

    /**
     * Part of the new polling routines. This compares the specified revision state with the repository,
     * and returns a polling result.
     * @param ap
     * @param lnchr
     * @param fp
     * @param tl
     * @param scmrs
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    protected PollingResult compareRemoteRevisionWith(AbstractProject<?, ?> project, Launcher launcher, FilePath workspace, TaskListener listener, SCMRevisionState scmrs) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();
        logger.println("Looking for changes...");
        final PerforceSCMRevisionState baseline;

        if (scmrs instanceof PerforceSCMRevisionState) {
            baseline = (PerforceSCMRevisionState)scmrs;
        }
        else if (project.getLastBuild()!=null) {
            baseline = (PerforceSCMRevisionState)calcRevisionsFromBuild(project.getLastBuild(), launcher, listener);
        }
        else {
            baseline = new PerforceSCMRevisionState(-1);
        }

        if (project.getLastBuild() == null || baseline == null) {
            listener.getLogger().println("No previous builds to use for comparison.");
            return PollingResult.BUILD_NOW;
        }

        Hashtable<String, String> subst = getDefaultSubstitutions(project);

        Depot depot;

        try {
            Node buildNode = getPollingNode(project);
            if (buildNode == null){
                depot = getDepot(launcher,workspace,project,null,buildNode);
                logger.println("Using master");
            } else {
                depot = getDepot(buildNode.createLauncher(listener),buildNode.getRootPath(),project,null, buildNode);
                logger.println("Using node: " + buildNode.getDisplayName());
            }

            Workspace p4workspace = getPerforceWorkspace(project, substituteParameters(projectPath, subst), depot, buildNode, null, launcher, workspace, listener, false);
            saveWorkspaceIfDirty(depot, p4workspace, logger);

            int lastChangeNumber = baseline.getRevision();
            SCMRevisionState repositoryState = getCurrentDepotRevisionState(p4workspace, project, depot, logger, lastChangeNumber);

            PollingResult.Change change;
            if(repositoryState.equals(baseline)){
                change = PollingResult.Change.NONE;
            } else {
                change = PollingResult.Change.SIGNIFICANT;
            }

            return new PollingResult(baseline, repositoryState, change);

        } catch (PerforceException e) {
            System.out.println("Problem: " + e.getMessage());
            logger.println("Caught Exception communicating with perforce." + e.getMessage());
            throw new IOException("Unable to communicate with perforce.  Check log file for: " + e.getMessage());
        }
    }

    private Node getPollingNode(AbstractProject project) {
        Node buildNode = project.getLastBuiltOn();
        if (pollOnlyOnMaster) {
            buildNode = null;
        } else {
            //try to get an active node that the project is configured to use
            buildNode = project.getLastBuiltOn();
            if (!isNodeOnline(buildNode)) {
                buildNode = null;
            }
            if (buildNode == null && !pollOnlyOnMaster) {
                buildNode = getOnlineConfiguredNode(project);
            }
            if (pollOnlyOnMaster) {
                buildNode = null;
            }
        }
        return buildNode;
    }

    private Node getOnlineConfiguredNode(AbstractProject project) {
        Node buildNode = null;
        for (Node node : Hudson.getInstance().getNodes()) {
            hudson.model.Label l = project.getAssignedLabel();
            if (l != null && !l.contains(node)) {
                continue;
            }
            if (l == null && node.getMode() == hudson.model.Node.Mode.EXCLUSIVE) {
                continue;
            }
            if (!isNodeOnline(node)) {
                continue;
            }
            buildNode = node;
            break;
        }
        return buildNode;
    }

    private boolean isNodeOnline(Node node) {
        return node != null && node.toComputer() != null && node.toComputer().isOnline();
    }

    private SCMRevisionState getCurrentDepotRevisionState(Workspace p4workspace, AbstractProject project, Depot depot,
        PrintStream logger, int lastChangeNumber) throws IOException, InterruptedException, PerforceException {

        int highestSelectedChangeNumber;
        List<Integer> changeNumbers;

        if (p4Counter != null && !updateCounterValue) {

            // If this is a downstream build that triggers by polling the set counter
            // use the counter as the value for the newest change instead of the workspace view

            Counter counter = depot.getCounters().getCounter(p4Counter);
            highestSelectedChangeNumber = counter.getValue();
            logger.println("Latest submitted change selected by named counter is " + highestSelectedChangeNumber);
            String root = "//" + p4workspace.getName() + "/...";
            changeNumbers = depot.getChanges().getChangeNumbersInRange(p4workspace, lastChangeNumber+1, highestSelectedChangeNumber, root);
        } else {
            // General Case

            // Has any new change been submitted since then (that is selected
            // by this workspace).

            Integer newestChange;
            String p4Label = substituteParameters(this.p4Label, getDefaultSubstitutions(project));
            if (p4Label != null && !p4Label.trim().isEmpty()) {
                //In case where we are using a rolling label.
                String root = "//" + p4workspace.getName() + "/...";
                newestChange = depot.getChanges().getHighestLabelChangeNumber(p4workspace, p4Label.trim(), root);
            } else {
                Counter counter = depot.getCounters().getCounter("change");
                newestChange = counter.getValue();
            }

            if(useViewMaskForPolling && useViewMask){
                changeNumbers = depot.getChanges().getChangeNumbersInRange(p4workspace, lastChangeNumber+1, newestChange, substituteParameters(viewMask, getDefaultSubstitutions(project)));
            } else {
                String root = "//" + p4workspace.getName() + "/...";
                changeNumbers = depot.getChanges().getChangeNumbersInRange(p4workspace, lastChangeNumber+1, newestChange, root);
            }
            if (changeNumbers.isEmpty()) {
                // Wierd, this shouldn't be!  I suppose it could happen if the
                // view selects no files (e.g. //depot/non-existent-branch/...).
                // This can also happen when using view masks with polling.
                logger.println("No changes found.");
                return new PerforceSCMRevisionState(lastChangeNumber);
            } else {
                highestSelectedChangeNumber = changeNumbers.get(0).intValue();
                logger.println("Latest submitted change selected by workspace is " + highestSelectedChangeNumber);
            }
        }

        if (lastChangeNumber >= highestSelectedChangeNumber) {
            // Note, can't determine with currently saved info
            // whether the workspace definition has changed.
            logger.println("Assuming that the workspace definition has not changed.");
            return new PerforceSCMRevisionState(lastChangeNumber);
        }
        else {
            for (int changeNumber : changeNumbers) {
                if (isChangelistExcluded(depot.getChanges().getChangelist(changeNumber), project, logger)) {
                    logger.println("Changelist "+changeNumber+" is composed of file(s) and/or user(s) that are excluded.");
                } else {
                    return new PerforceSCMRevisionState(changeNumber);
                }
            }
            return new PerforceSCMRevisionState(lastChangeNumber);
        }
    }

    /**
     * Determines whether or not P4 changelist should be excluded and ignored by the polling trigger.
     * Exclusions include files, regex patterns of files, and/or changelists submitted by a specific user(s).
     *
     * @param changelist the p4 changelist
     * @return  True if changelist only contains user(s) and/or file(s) that are denoted to be excluded
     */
    private boolean isChangelistExcluded(Changelist changelist, AbstractProject project, PrintStream logger) {
        if (changelist == null){
            return false;
        }

        if (excludedUsers != null && !excludedUsers.trim().equals(""))
        {
            List<String> users = Arrays.asList(substituteParameters(excludedUsers,getDefaultSubstitutions(project)).split("\n"));

            if ( users.contains(changelist.getUser()) ) {
                logger.println("Excluded User ["+changelist.getUser()+"] found in changelist.");
                return true;
            }

            // no literal match, try regex
            Matcher matcher;

            for (String regex : users)
            {
                try {
                    matcher = Pattern.compile(regex).matcher(changelist.getUser());
                    if (matcher.find()) {
                        logger.println("Excluded User ["+changelist.getUser()+"] found in changelist.");
                        return true;
                    }
                }
                catch (PatternSyntaxException pse) {
                    break;  // should never occur since we validate regex input before hand, but just be safe
                }
            }
        }

        if (excludedFiles != null && !excludedFiles.trim().equals(""))
        {
            List<String> files = Arrays.asList(substituteParameters(excludedFiles,getDefaultSubstitutions(project)).split("\n"));
            StringBuffer buff = null;
            Matcher matcher = null;
            boolean matchFound;

            if (files.size() > 0 && changelist.getFiles().size() > 0)
            {
                for (FileEntry f : changelist.getFiles()) {
                    if (!doesFilenameMatchAnyP4Pattern(f.getFilename(),files)) {
                        return false;
                    }

                    if (buff == null) {
                        buff = new StringBuffer("Exclude file(s) found:\n");
                    }
                    buff.append("\t").append(f.getFilename());
                }

                logger.println(buff.toString());
                return true;    // get here means changelist contains only file(s) to exclude
            }
        }

        return false;
    }

    private static boolean doesFilenameMatchAnyP4Pattern(String filename, List<String> patternStrings){
        for(String patternString : patternStrings){
            if(patternString.trim().equals("")) continue;
            if(doesFilenameMatchP4Pattern(filename, patternString)){
                return true;
            }
        }
        return false;
    }

    public static boolean doesFilenameMatchP4Pattern(String filename, String patternString) throws PatternSyntaxException {
        patternString = patternString.trim();
        filename = filename.trim();
        patternString = patternString.replaceAll("\\*", "[^/]*");
        patternString = patternString.replaceAll("\\.\\.\\.", ".*");
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(filename);
        if(matcher.matches()){
            return true;
        } else {
            return false;
        }
    }

    private void flushWorkspaceTo0(Depot depot, Workspace p4workspace, PrintStream log) throws PerforceException {
        saveWorkspaceIfDirty(depot, p4workspace, log);
        depot.getWorkspaces().flushTo("//" + p4workspace.getName() + "/...#0");
    }

    // TODO Handle the case where p4Label is set.
    private boolean wouldSyncChangeWorkspace(AbstractProject project, Depot depot,
            PrintStream logger) throws IOException, InterruptedException, PerforceException {

        Workspaces workspaces = depot.getWorkspaces();
        String result = workspaces.syncDryRun().toString();

        if (result.startsWith("File(s) up-to-date.")) {
            logger.println("Workspace up-to-date.");
            return false;
        }
        else {
            logger.println("Workspace not up-to-date.");
            return true;
        }
    }

    public int getLastChange(Run build) {
        // If we are starting a new hudson project on existing work and want to skip the prior history...
        if (firstChange > 0)
            return firstChange;

        // If we can't find a PerforceTagAction, we will default to 0.

        PerforceTagAction action = getMostRecentTagAction(build);
        if (action == null)
            return 0;

        //log.println("Found last change: " + action.getChangeNumber());
        return action.getChangeNumber();
    }

    private PerforceTagAction getMostRecentTagAction(Run build) {
        if (build == null)
            return null;

        PerforceTagAction action = build.getAction(PerforceTagAction.class);
        if (action != null)
            return action;

        // if build had no actions, keep going back until we find one that does.
        return getMostRecentTagAction(build.getPreviousBuild());
    }
    
    private Workspace getPerforceWorkspace(AbstractProject project, String projectPath,
            Depot depot, Node buildNode, AbstractBuild build,
            Launcher launcher, FilePath workspace, TaskListener listener, boolean dontChangeRoot)
        throws IOException, InterruptedException, PerforceException
    {
        PrintStream log = listener.getLogger();

        // If we are building on a slave node, and each node is supposed to have
        // its own unique client, then adjust the client name accordingly.
        // make sure each slave has a unique client name by adding it's
        // hostname to the end of the client spec

        String p4Client;
        if (build != null) {
            p4Client = getEffectiveClientName(build);
        } else {
            p4Client = getDefaultEffectiveClientName(project, buildNode, workspace);
        }

        if (!nodeIsRemote(buildNode)) {
            log.print("Using master perforce client: ");
            log.println(p4Client);
        }
        else if (dontRenameClient) {
            log.print("Using shared perforce client: ");
            log.println(p4Client);
        }
        else {
            log.println("Using remote perforce client: " + p4Client);
        }


        depot.setClient(p4Client);
        String p4Stream = (build == null ? substituteParameters(this.p4Stream, getDefaultSubstitutions(project)) : substituteParameters(this.p4Stream, build));

        // Get the clientspec (workspace) from perforce

        Workspace p4workspace = depot.getWorkspaces().getWorkspace(p4Client, p4Stream);
        assert p4workspace != null;
        boolean creatingNewWorkspace = p4workspace.isNew();

        // If the client workspace doesn't exist, and we're not managing the clients,
        // Then terminate the build with an error
        if(!createWorkspace && creatingNewWorkspace){
            log.println("*** Perforce client workspace '" + p4Client +"' doesn't exist.");
            log.println("*** Please create it, or allow Jenkins to manage clients on it's own.");
            log.println("*** If the client name mentioned above is not what you expected, ");
            log.println("*** check your 'Client name format for slaves' advanced config option.");
            throw new AbortException("Error accessing perforce workspace.");
        }

        // Ensure that the clientspec (workspace) name is set correctly
        // TODO Examine why this would be necessary.

        p4workspace.setName(p4Client);

        // Set the workspace options according to the configuration
        if (projectOptions != null)
            p4workspace.setOptions(projectOptions);

        // Set the line ending option according to the configuration
        if (lineEndValue != null && getAllLineEndChoices().contains(lineEndValue)){
            p4workspace.setLineEnd(lineEndValue);
        }

        // Ensure that the root is appropriate (it might be wrong if the user
        // created it, or if we previously built on another node).

        // Both launcher and workspace can be null if requiresWorkspaceForPolling returns true
        // So provide 'reasonable' default values.
        boolean isunix = true;
        if (launcher!= null)
        	isunix=launcher.isUnix();

        String localPath = p4workspace.getRoot();

        if (workspace!=null)
        	localPath = getLocalPathName(workspace, isunix);
        else if (localPath.trim().equals(""))
                localPath = project.getRootDir().getAbsolutePath();

        if (!localPath.equals(p4workspace.getRoot()) && !dontChangeRoot && !dontUpdateClient) {
            log.println("Changing P4 Client Root to: " + localPath);
            forceSync = true;
            p4workspace.setRoot(localPath);
        }

        if (updateView || creatingNewWorkspace) {
            // Switch to another stream view if necessary
            if (useStreamDepot) {
                p4workspace.setStream(p4Stream);
            }
            // If necessary, rewrite the views field in the clientspec. Also, clear the stream.
            // TODO If dontRenameClient==false, and updateView==false, user
            // has a lot of work to do to maintain the clientspecs.  Seems like
            // we could copy from a master clientspec to the slaves.
            else {
                p4workspace.setStream("");
                if (useClientSpec) {
                    projectPath = getEffectiveProjectPathFromFile(build, project, log, depot);
                }
                List<String> mappingPairs = parseProjectPath(projectPath, p4Client, log);
                if (!equalsProjectPath(mappingPairs, p4workspace.getViews())) {
                    log.println("Changing P4 Client View from:\n" + p4workspace.getViewsAsString());
                    log.println("Changing P4 Client View to: ");
                    p4workspace.clearViews();
                    for (int i = 0; i < mappingPairs.size(); ) {
                        String depotPath = mappingPairs.get(i++);
                        String clientPath = mappingPairs.get(i++);
                        p4workspace.addView(" " + depotPath + " " + clientPath);
                        log.println("  " + depotPath + " " + clientPath);
                    }
                }
            }
        }
        // Clean host field so the client can be used on other slaves
        // such as those operating with the workspace on a network share
        p4workspace.setHost("");

        // NOTE: The workspace is not saved.
        return p4workspace;
    }

    private String getEffectiveClientName(AbstractBuild build) {
        Node buildNode = build.getBuiltOn();
        FilePath workspace = build.getWorkspace();
        String p4Client = this.p4Client;
        p4Client = substituteParameters(p4Client, build);
        try {
            p4Client = getEffectiveClientName(p4Client, buildNode);
        } catch (Exception e){
            new StreamTaskListener(System.out).getLogger().println(
                    "Could not get effective client name: " + e.getMessage());
        }
        return p4Client;
    }

    private String getDefaultEffectiveClientName(AbstractProject project, Node buildNode, FilePath workspace)
            throws IOException, InterruptedException {
        String basename = substituteParameters(this.p4Client, getDefaultSubstitutions(project));
        return getEffectiveClientName(basename, buildNode);
    }

    private String getEffectiveClientName(String basename, Node buildNode)
            throws IOException, InterruptedException {

		String p4Client = basename;

        if (nodeIsRemote(buildNode) && !getSlaveClientNameFormat().equals("")) {
            String host=null;

            Computer c = buildNode.toComputer();
            if (c!=null)
                host = c.getHostName();

            if (host==null) {
                LOGGER.log(Level.WARNING,"Could not get hostname for slave " + buildNode.getDisplayName());
                host = "UNKNOWNHOST";
            }

            if (host.contains(".")) {
                host = String.valueOf(host.subSequence(0, host.indexOf('.')));
            }
            //use hashcode of the nodename to get a unique, slave-specific client name
            String hash = String.valueOf(buildNode.getNodeName().hashCode());

            Map<String, String> substitutions = new Hashtable<String,String>();
            substitutions.put("nodename", buildNode.getNodeName());
            substitutions.put("hostname", host);
            substitutions.put("hash", hash);
            substitutions.put("basename", basename);

            p4Client = substituteParameters(getSlaveClientNameFormat(), substitutions);    
        }
        //eliminate spaces, just in case
        p4Client = p4Client.replaceAll(" ", "_");
        return p4Client;
    }

    public String getSlaveClientNameFormat() {
        if(this.slaveClientNameFormat == null || this.slaveClientNameFormat.equals("")){
            if(this.dontRenameClient){
                slaveClientNameFormat = "${basename}";
            } else if(this.useOldClientName) {
                slaveClientNameFormat = "${basename}-${hostname}";
            } else {
                //Hash should be the new default
                slaveClientNameFormat = "${basename}-${hash}";
            }
        }
        return slaveClientNameFormat;
    }

    private boolean nodeIsRemote(Node buildNode) {
        return buildNode != null && buildNode.getNodeName().length() != 0;
    }

    private void saveWorkspaceIfDirty(Depot depot, Workspace p4workspace, PrintStream log) throws PerforceException {
        if (dontUpdateClient) {
            log.println("'Don't update client' is set. Not saving the client changes.");
            return;
        }
        if (p4workspace.isNew()) {
            log.println("Saving new client " + p4workspace.getName());
            depot.getWorkspaces().saveWorkspace(p4workspace);
        } else if (p4workspace.isDirty()) {
            log.println("Saving modified client " + p4workspace.getName());
            depot.getWorkspaces().saveWorkspace(p4workspace);
        }
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

        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            PerforceSCM newInstance = (PerforceSCM)super.newInstance(req, formData);
            String depotType = req.getParameter("p4.depotType");
            boolean useStreamDepot = depotType.equals("stream");
            boolean useClientSpec = depotType.equals("file");
            newInstance.setUseStreamDepot(useStreamDepot);
            if (useStreamDepot) {
                newInstance.setP4Stream(req.getParameter("p4Stream"));
            }
            else {
                newInstance.setUseClientSpec(useClientSpec);
                if (useClientSpec) {
                    newInstance.setClientSpec(req.getParameter("clientSpec"));
                }
                else {
                    newInstance.setProjectPath(req.getParameter("projectPath"));
                }
            }
            newInstance.setUseViewMask(req.getParameter("p4.useViewMask") != null);
            newInstance.setViewMask(Util.fixEmptyAndTrim(req.getParameter("p4.viewMask")));
            newInstance.setUseViewMaskForPolling(req.getParameter("p4.useViewMaskForPolling") != null);
            newInstance.setUseViewMaskForSyncing(req.getParameter("p4.useViewMaskForSyncing") != null);
            return newInstance;
        }

        /**
         * List available tool installations.
         * 
         * @return list of available p4 tool installations
         */
        public List<PerforceToolInstallation> getP4Tools() {
            PerforceToolInstallation[] p4ToolInstallations = Hudson.getInstance().getDescriptorByType(PerforceToolInstallation.DescriptorImpl.class).getInstallations();
            return Arrays.asList(p4ToolInstallations);
        }

        public String isValidProjectPath(String path) {
            if (!path.startsWith("//")) {
                return "Path must start with '//' (Example: //depot/ProjectName/...)";
            }
            if (!path.endsWith("/...")) {
                if (!path.contains("@")) {
                    return "Path must end with Perforce wildcard: '/...'  (Example: //depot/ProjectName/...)";
                }
            }
            return null;
        }

        protected Depot getDepotFromRequest(StaplerRequest request) {
            String port = fixNull(request.getParameter("port")).trim();
            String tool = fixNull(request.getParameter("tool")).trim();
            String user = fixNull(request.getParameter("user")).trim();
            String pass = fixNull(request.getParameter("pass")).trim();

            if (port.length() == 0 || tool.length() == 0) { // Not enough entered yet
                return null;
            }
            Depot depot = new Depot();
            depot.setUser(user);
            PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
            if (encryptor.appearsToBeAnEncryptedPassword(pass)) {
                depot.setPassword(encryptor.decryptString(pass));
            }
            else {
                depot.setPassword(pass);
            }
            depot.setPort(port);

            String exe = "";
            PerforceToolInstallation[] installations = ((hudson.plugins.perforce.PerforceToolInstallation.DescriptorImpl)Hudson.getInstance().
                    getDescriptorByType(PerforceToolInstallation.DescriptorImpl.class)).getInstallations();
            for(PerforceToolInstallation i : installations) {
                if(i.getName().equals(tool)) {
                    exe = i.getP4Exe();
                }
            }
            depot.setExecutable(exe);

            try {
                Counter counter = depot.getCounters().getCounter("change");
                if (counter != null)
                    return depot;
            } catch (PerforceException e) {
            }

            return null;
        }

        /**
         * Checks if the perforce login credentials are good.
         */
        public FormValidation doValidatePerforceLogin(StaplerRequest req) {
            Depot depot = getDepotFromRequest(req);
            if (depot != null) {
                try {
                    depot.getStatus().isValid();
                } catch (PerforceException e) {
                    return FormValidation.error(e.getMessage());
                }
            }
            return FormValidation.ok();
        }

        /**
         * Checks to see if the specified workspace is valid.
         */
        public FormValidation doValidateP4Client(StaplerRequest req) {
            Depot depot = getDepotFromRequest(req);
            if (depot == null) {
                return FormValidation.error(
                        "Unable to check workspace against depot");
            }
            String workspace = Util.fixEmptyAndTrim(req.getParameter("client"));
            if (workspace == null) {
                return FormValidation.error("You must enter a workspaces name");
            }
            try {
                Workspace p4Workspace =
                    depot.getWorkspaces().getWorkspace(workspace, "");

                if (p4Workspace.getAccess() == null ||
                        p4Workspace.getAccess().equals(""))
                    return FormValidation.warning("Workspace does not exist. " +
                            "If \"Let Hudson/Jenkins Manage Workspace View\" is check" +
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
        public FormValidation doValidateP4Label(StaplerRequest req, @QueryParameter String label) throws IOException, ServletException {
            label = Util.fixEmptyAndTrim(label);
            if (label == null)
                return FormValidation.ok();

            Depot depot = getDepotFromRequest(req);
            if (depot != null) {
                try {
                    Label p4Label = depot.getLabels().getLabel(label);
                    if (p4Label.getAccess() == null || p4Label.getAccess().equals(""))
                        return FormValidation.error("Label does not exist");
                } catch (PerforceException e) {
                    return FormValidation.error(
                            "Error accessing perforce while checking label");
                }
            }
            return FormValidation.ok();
        }

        /**
         * Performs syntactical and permissions check on the P4Counter
          */
        public FormValidation doValidateP4Counter(StaplerRequest req, @QueryParameter String counter) throws IOException, ServletException {
            counter= Util.fixEmptyAndTrim(counter);
            if (counter == null)
                return FormValidation.ok();

            Depot depot = getDepotFromRequest(req);
            if (depot != null) {
                try {
                    Counters counters = depot.getCounters();
                    Counter p4Counter = counters.getCounter(counter);
                    // try setting the counter back to the same value to verify permissions
                    counters.saveCounter(p4Counter);
                } catch (PerforceException e) {
                    return FormValidation.error(
                            "Error accessing perforce while checking counter: " + e.getLocalizedMessage());
                }
            }
            return FormValidation.ok();
        }

		/**
         * Checks to see if the specified ClientSpec is valid.
         */
		public FormValidation doValidateClientSpec(StaplerRequest req) throws IOException, ServletException {
            Depot depot = getDepotFromRequest(req);
            if (depot == null) {
                return FormValidation.error(
                        "Unable to check ClientSpec against depot");
            }

            String clientspec = Util.fixEmptyAndTrim(req.getParameter("clientSpec"));
            if (clientspec == null) {
                return FormValidation.error("You must enter a path to a ClientSpec file");
            }

            if (!DEPOT_ONLY.matcher(clientspec).matches() &&
                !DEPOT_ONLY_QUOTED.matcher(clientspec).matches()){
                return FormValidation.error("Invalid depot path:" + clientspec);
            }

            String workspace = Util.fixEmptyAndTrim(req.getParameter("client"));
            try {
                if (!depot.getStatus().exists(clientspec)) {
                    return FormValidation.error("ClientSpec does not exist");
                }

                Workspace p4Workspace = depot.getWorkspaces().getWorkspace(workspace, "");
                // Warn if workspace exists and is associated with a stream
                if (p4Workspace.getAccess() != null && !p4Workspace.getAccess().equals("") &&
                        p4Workspace.getStream() != null && !p4Workspace.getStream().equals("")) {
                    return FormValidation.warning("Workspace '" + workspace + "' already exists and is associated with a stream. " +
                        "If Jenkins is allowed to manage the workspace view, this workspace will be switched to a local workspace.");
                }
            }
            catch (PerforceException e) {
                return FormValidation.error(
                        "Error accessing perforce while checking ClientSpec: " + e.getLocalizedMessage());
            }

            return FormValidation.ok();
        }

	      /**
         * Checks if the specified stream is valid.
         */
        public FormValidation doValidateStream(StaplerRequest req) throws IOException, ServletException {
            Depot depot = getDepotFromRequest(req);
            if (depot == null) {
                return FormValidation.error(
                        "Unable to check stream against depot");
            }

            String stream = Util.fixEmptyAndTrim(req.getParameter("stream"));
            if (stream == null) {
                return FormValidation.error("You must enter a stream");
            }
            if (!stream.endsWith("/...")) {
                stream += "/...";
            }

            if (!DEPOT_ONLY.matcher(stream).matches() &&
                !DEPOT_ONLY_QUOTED.matcher(stream).matches()){
                return FormValidation.error("Invalid depot path:" + stream);
            }

            String workspace = Util.fixEmptyAndTrim(req.getParameter("client"));
            try {
                if (!depot.getStatus().exists(stream)) {
                    return FormValidation.error("Stream does not exist");
                }

                Workspace p4Workspace = depot.getWorkspaces().getWorkspace(workspace, "");
                // Warn if workspace exists and is not associated with a stream
                if (p4Workspace.getAccess() != null && !p4Workspace.getAccess().equals("") &&
                        (p4Workspace.getStream() == null || p4Workspace.getStream().equals(""))) {
                    return FormValidation.warning("Workspace '" + workspace + "' already exists and is not associated with a stream. " +
                        "If Jenkins is allowed to manage the workspace view, this workspace will be switched to a stream workspace.");
                }
            }
            catch (PerforceException e) {
                return FormValidation.error(
                        "Error accessing perforce while checking stream: " + e.getLocalizedMessage());
            }

            return FormValidation.ok();
        }

        /**
         * Checks if the value is a valid Perforce project path.
         */
        public FormValidation doCheckProjectPath(@QueryParameter String value) throws IOException, ServletException {
            String view = Util.fixEmptyAndTrim(value);
            if (view != null) {
                for (String mapping : view.replace("\r","").split("\n")) {
                    if (!DEPOT_ONLY.matcher(mapping).matches() &&
                        !DEPOT_AND_WORKSPACE.matcher(mapping).matches() &&
                        !DEPOT_ONLY_QUOTED.matcher(mapping).matches() &&
                        !DEPOT_AND_WORKSPACE_QUOTED.matcher(mapping).matches() &&
                        !DEPOT_AND_QUOTED_WORKSPACE.matcher(mapping).matches() &&
                        !COMMENT.matcher(mapping).matches())
                        return FormValidation.error("Invalid mapping:" + mapping);
                }
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckViewMask(StaplerRequest req) {
            String view = Util.fixEmptyAndTrim(req.getParameter("viewMask"));
            if (view != null) {
                for (String path : view.replace("\r","").split("\n")) {
                    if (path.startsWith("-") || path.startsWith("\"-"))
                        return FormValidation.error("'-' not yet supported in view mask:" + path);
                    if (!DEPOT_ONLY.matcher(path).matches() &&
                        !DEPOT_ONLY_QUOTED.matcher(path).matches())
                        return FormValidation.error("Invalid depot path:" + path);
                }
            }
            return FormValidation.ok();
        }

        /**
         * Checks if the change list entered exists
         */
        public FormValidation doCheckChangeList(StaplerRequest req) {
            Depot depot = getDepotFromRequest(req);
            String change = fixNull(req.getParameter("change")).trim();

            if (change.length() == 0) { // nothing entered yet
                return FormValidation.ok();
            }
            if (depot != null) {
                try {
                    int number = Integer.parseInt(change);
                    Changelist changelist = depot.getChanges().getChangelist(number);
                    if (changelist.getChangeNumber() != number)
                        throw new PerforceException("broken");
                } catch (Exception e) {
                    return FormValidation.error("Changelist: " + change + " does not exist.");
                }
            }
            return FormValidation.ok();
        }

        /**
         * Checks if the value is a valid user name/regex pattern.
         */
        public FormValidation doValidateExcludedUsers(StaplerRequest req) {
            String excludedUsers = fixNull(req.getParameter("excludedUsers")).trim();
            List<String> users = Arrays.asList(excludedUsers.split("\n"));

            for (String regex : users) {
                regex = regex.trim();

                if(regex.equals("")) continue;

                try {
                    regex = regex.replaceAll("\\$\\{[^\\}]*\\}","SOMEVARIABLE");
                    Pattern.compile(regex);
                }
                catch (PatternSyntaxException pse) {
                    return FormValidation.error("Invalid regular express ["+regex+"]: " + pse.getMessage());
                }
            }
            return FormValidation.ok();
        }

        /**
         * Checks if the value is a valid file path/regex file pattern.
         */
        public FormValidation doValidateExcludedFiles(StaplerRequest req) {
            String excludedFiles = fixNull(req.getParameter("excludedFiles")).trim();
            List<String> files = Arrays.asList(excludedFiles.split("\n"));
            for (String file : files) {
                // splitting with \n can still leave \r on some OS/browsers
                // trimming should eliminate it.
                file = file.trim();
                // empty line? lets ignore it.
                if(file.equals("")) continue;
                // check to make sure it's a valid file spec
                if( !DEPOT_ONLY.matcher(file).matches() && !DEPOT_ONLY_QUOTED.matcher(file).matches() ){
                    return FormValidation.error("Invalid file spec ["+file+"]: Not a perforce file spec.");
                }
                // check to make sure the globbing regex will work
                // (ie, in case there are special characters that the user hasn't escaped properly)
                try {
                    file = file.replaceAll("\\$\\{[^\\}]*\\}","SOMEVARIABLE");
                    doesFilenameMatchP4Pattern("somefile", file);
                }
                catch (PatternSyntaxException pse) {
                    return FormValidation.error("Invalid file spec ["+file+"]: " + pse.getMessage());
                }
            }
            return FormValidation.ok();
        }

        public FormValidation doValidateForceSync(StaplerRequest req) {
            Boolean forceSync = Boolean.valueOf(fixNull(req.getParameter("forceSync")).trim());
            Boolean alwaysForceSync = Boolean.valueOf(fixNull(req.getParameter("alwaysForceSync")).trim());
            Boolean dontUpdateServer = Boolean.valueOf(fixNull(req.getParameter("dontUpdateServer")).trim());
            
            if((forceSync || alwaysForceSync) && dontUpdateServer){
                return FormValidation.error("Don't Update Server Database (-p) option is incompatible with force syncing! Either disable -p, or disable force syncing.");
            }
            return FormValidation.ok();
        }
        
        public List<String> getAllLineEndChoices(){
            List<String> allChoices = Arrays.asList(
                "local",
                "unix",
                "mac",
                "win",
                "share"
            );
            ArrayList<String> choices = new ArrayList<String>();
            //Order choices so that the current one is first in the list
            //This is required in order for tests to work, unfortunately
            //choices.add(lineEndValue);
            for(String choice : allChoices){
                //if(!choice.equals(lineEndValue)){
                    choices.add(choice);
                //}
            }
            return choices;
        }

        public String getAppName() {
            return Hudson.getInstance().getDisplayName();
        }

    }

    /* Regular expressions for parsing view mappings.
     */
    private static final Pattern COMMENT = Pattern.compile("^\\s*$|^#.*$");
    private static final Pattern DEPOT_ONLY = Pattern.compile("^\\s*[+-]?//\\S+?(/\\S+)$");
    private static final Pattern DEPOT_ONLY_QUOTED = Pattern.compile("^\\s*\"[+-]?//\\S+?(/[^\"]+)\"$");
    private static final Pattern DEPOT_AND_WORKSPACE =
            Pattern.compile("^\\s*([+-]?//\\S+?/\\S+)\\s+//\\S+?(/\\S+)$");
    private static final Pattern DEPOT_AND_WORKSPACE_QUOTED =
            Pattern.compile("^\\s*\"([+-]?//\\S+?/[^\"]+)\"\\s+\"//\\S+?(/[^\"]+)\"$");
    private static final Pattern DEPOT_AND_QUOTED_WORKSPACE =
            Pattern.compile("^\\s*([+-]?//\\S+?/\\S+)\\s+\"//\\S+?(/[^\"]+)\"$");

    /**
     * Parses the projectPath into a list of pairs of strings representing the depot and client
     * paths. Even items are depot and odd items are client.
     * <p>
     * This parser can handle quoted or non-quoted mappings, normal two-part mappings, or one-part
     * mappings with an implied right part. It can also deal with +// or -// mapping forms.
     */
    public static List<String> parseProjectPath(String projectPath, String p4Client) {
        PrintStream log = (new LogTaskListener(LOGGER, Level.WARNING)).getLogger();
        return parseProjectPath(projectPath, p4Client, log);
    }
    
    public static List<String> parseProjectPath(String projectPath, String p4Client, PrintStream log) {
        List<String> parsed = new ArrayList<String>();
        for (String line : projectPath.split("\n")) {
            Matcher depotOnly = DEPOT_ONLY.matcher(line);
            if (depotOnly.find()) {
                // add the trimmed depot path, plus a manufactured client path
                parsed.add(line.trim());
                parsed.add("//" + p4Client + depotOnly.group(1));
            } else {
                Matcher depotOnlyQuoted = DEPOT_ONLY_QUOTED.matcher(line);
                if (depotOnlyQuoted.find()) {
                    // add the trimmed quoted depot path, plus a manufactured quoted client path
                    parsed.add(line.trim());
                    parsed.add("\"//" + p4Client + depotOnlyQuoted.group(1) + "\"");
                } else {
                    Matcher depotAndWorkspace = DEPOT_AND_WORKSPACE.matcher(line);
                    if (depotAndWorkspace.find()) {
                        // add the found depot path and the clientname-tweaked client path
                        parsed.add(depotAndWorkspace.group(1));
                        parsed.add("//" + p4Client + depotAndWorkspace.group(2));
                    } else {
                        Matcher depotAndWorkspaceQuoted = DEPOT_AND_WORKSPACE_QUOTED.matcher(line);
                        if (depotAndWorkspaceQuoted.find()) {
                           // add the found depot path and the clientname-tweaked client path
                            parsed.add("\"" + depotAndWorkspaceQuoted.group(1) + "\"");
                            parsed.add("\"//" + p4Client + depotAndWorkspaceQuoted.group(2) + "\"");
                        } else {
                            Matcher depotAndQuotedWorkspace = DEPOT_AND_QUOTED_WORKSPACE.matcher(line);
                            if (depotAndQuotedWorkspace.find()) {
                                // add the found depot path and the clientname-tweaked client path
                                parsed.add(depotAndQuotedWorkspace.group(1));
                                parsed.add("\"//" + p4Client + depotAndQuotedWorkspace.group(2) + "\"");
                            } else {
                                // Assume anything else is a comment and ignore it
                                // Throw a warning anyways.
                                log.println("Warning: Client Spec line invalid, ignoring. ("+line+")");
                            }
                        }
                    }
                }
            }
        }
        return parsed;
    }

    static String substituteParameters(String string, AbstractBuild build) {
        Hashtable<String,String> subst = new Hashtable<String,String>();
        
        boolean useEnvironment = true;
        //get full environment for build from jenkins
        for(StackTraceElement ste : (new Throwable()).getStackTrace()){
            if(ste.getMethodName().equals("buildEnvVars") &&
                    ste.getClassName().equals(PerforceSCM.class.getName())){
                useEnvironment = false;
            }
        }
        if(useEnvironment){
            try {
                EnvVars vars = build.getEnvironment(TaskListener.NULL);
                subst.putAll(vars);
            } catch (IOException ex) {
                Logger.getLogger(PerforceSCM.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InterruptedException ex) {
                Logger.getLogger(PerforceSCM.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        subst.put("JOB_NAME", getSafeJobName(build));
        String hudsonName = Hudson.getInstance().getDisplayName().toLowerCase();
        subst.put("BUILD_TAG", hudsonName + "-" + build.getProject().getName() + "-" + String.valueOf(build.getNumber()));
        subst.put("BUILD_ID", build.getId());
        subst.put("BUILD_NUMBER", String.valueOf(build.getNumber()));
        
        //get global properties
        for (NodeProperty nodeProperty: Hudson.getInstance().getGlobalNodeProperties()) {
            if (nodeProperty instanceof EnvironmentVariablesNodeProperty) {
                subst.putAll( ((EnvironmentVariablesNodeProperty)nodeProperty).getEnvVars() );
            }
        }
        //get node-specific global properties
        for(NodeProperty nodeProperty : build.getBuiltOn().getNodeProperties()){
            if(nodeProperty instanceof EnvironmentVariablesNodeProperty) {
                subst.putAll( ((EnvironmentVariablesNodeProperty)nodeProperty).getEnvVars() );
            }
        }
        String result = substituteParameters(string, subst);
        result = substituteParameters(result, build.getBuildVariables());
        return result;
    }

    static String getSafeJobName(AbstractBuild build){
        return getSafeJobName(build.getProject());
    }

    static String getSafeJobName(AbstractProject project){
        return project.getFullName().replace('/','-').replace('=','-').replace(',','-');
    }

    static String substituteParameters(String string, Map<String,String> subst) {
        if(string == null) return null;
        String newString = string;
        for (Map.Entry<String,String> entry : subst.entrySet()){
            newString = newString.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return newString;
    }

    /**
     * Compares a parsed project path pair list against a list of view
     * mapping lines from a client spec.
     */
     static boolean equalsProjectPath(List<String> pairs, List<String> lines) {
        Iterator<String> pi = pairs.iterator();
        for (String line : lines) {
            if (!pi.hasNext())
                return false;
            String p1 = pi.next();
            String p2 = pi.next();  // assuming an even number of pair items
            if (!line.trim().equals(p1.trim() + " " + p2.trim()))
                return false;
        }
        return !pi.hasNext(); // equals iff there are no more pairs
    }

	/**
     * @return the path to the ClientSpec
     */
    public String getClientSpec() {
        return clientSpec;
    }

    /**
     * @param path the path to the ClientSpec
     */
    public void setClientSpec(String clientSpec) {
        this.clientSpec = clientSpec;
    }

	/**
     * @return True if we are using a ClientSpec file to setup the workspace view
     */
    public boolean isUseClientSpec() {
        return useClientSpec;
    }

	/**
     * @param useClientSpec True if a ClientSpec file should be used to setup workspace view, False otherwise
     */
    public void setUseClientSpec(boolean useClientSpec) {
        this.useClientSpec = useClientSpec;
    }

    /**
     * Check if we are using a stream depot type or a classic depot type.
     * 
     * @return True if we are using a stream depot type, False otherwise
     */
    public boolean isUseStreamDepot() {
        return useStreamDepot;
    }

    /**
     * Control the usage of stream depot.
     * 
     * @param useStreamDepot True if stream depot is used, False otherwise
     */
    public void setUseStreamDepot(boolean useStreamDepot) {
        this.useStreamDepot = useStreamDepot;
    }

    /**
     * Get the stream name.
     * 
     * @return the p4Stream
     */
    public String getP4Stream() {
        return p4Stream;
    }

    /**
     * Set the stream name.
     * 
     * @param stream the stream name
     */
    public void setP4Stream(String stream) {
        p4Stream = stream;
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
		// Make it backwards compatible with the old way of specifying a label
        Matcher m = Pattern.compile("(@\\S+)\\s*").matcher(projectPath);
        if (m.find()) {
            p4Label = m.group(1);
            projectPath = projectPath.substring(0,m.start(1))
                + projectPath.substring(m.end(1));
        }
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

    public String getDecryptedP4Passwd() {
        PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
        return encryptor.decryptString(p4Passwd);
    }

    public String getDecryptedP4Passwd(AbstractBuild build) {
        return substituteParameters(getDecryptedP4Passwd(), build);
    }

    public String getDecryptedP4Passwd(AbstractProject project) {
        return substituteParameters(getDecryptedP4Passwd(), getDefaultSubstitutions(project));
    }

    /**
     * @param passwd the p4Passwd to set
     */
    public void setP4Passwd(String passwd) {
        PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
        if (encryptor.appearsToBeAnEncryptedPassword(passwd))
            p4Passwd = passwd;
        else
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
     * @deprecated Replaced by {@link #getP4Tool()}
     */
    public String getP4Exe() {
        return p4Exe;
    }

    /**
     * @deprecated Replaced by {@link #setP4Tool(String)}
     */
    public void setP4Exe(String exe) {
        p4Exe = exe;
    }
    
    /**
     * @return the p4Tool
     */
    public String getP4Tool() {
        return p4Tool;
    }
    
    /**
     * @param tool the p4 tool installation to set
     */
    public void setP4Tool(String tool) {
        p4Tool = tool;
    }

    /**
     * @return the p4Label
     */
    public String getP4Label() {
        return p4Label;
    }

    /**
     * @param label the p4Label to set
     */
    public void setP4Label(String label) {
        p4Label = label;
    }

    /**
     * @return the p4Counter
     */
    public String getP4Counter() {
        return p4Counter;
    }

    /**
     * @param counter the p4Counter to set
     */
    public void setP4Counter(String counter) {
        p4Counter = counter;
    }

    /**
     * @return True if the plugin should update the counter to the last change
     */
    public boolean isUpdateCounterValue() {
        return updateCounterValue;
    }

    /**
     * @param updateCounterValue True if the plugin should update the counter to the last change
     */
    public void setUpdateCounterValue(boolean updateCounterValue) {
        this.updateCounterValue = updateCounterValue;
    }

    /**
     * @return True if the P4PASSWD value must be set in the environment
     */
    public boolean isExposeP4Passwd() {
        return exposeP4Passwd;
    }

    /**
     * @param exposeP4Passwd True if the P4PASSWD value must be set in the environment
     */
    public void setExposeP4Passwd(boolean exposeP4Passwd) {
        this.exposeP4Passwd = exposeP4Passwd;
    }

    /**
     * The current perforce option set for the view.
     * @return current perforce view options
     */
    public String getProjectOptions() {
        return projectOptions;
    }

    /**
     * Set the perforce options for view creation.
     * @param projectOptions the effective perforce options.
     */
    public void setProjectOptions(String projectOptions) {
        this.projectOptions = projectOptions;
    }

    /**
     * @param createWorkspace    True to let the plugin create the workspace, false to let the user manage it
     */
    public void setCreateWorkspace(boolean val) {
        this.createWorkspace = Boolean.valueOf(val);
    }

    /**
     * @return  True if the plugin manages the view, false if the user does.
     */
    public boolean isCreateWorkspace() {
        return createWorkspace.booleanValue();
    }

    /**
     * @param update    True to let the plugin manage the view, false to let the user manage it
     */
    public void setUpdateView(boolean update) {
        this.updateView = update;
    }

    /**
     * @return  True if the plugin manages the view, false if the user does.
     */
    public boolean isUpdateView() {
        return updateView;
    }

    /**
     * @return  True if we are performing a one-time force sync
     */
    public boolean isForceSync() {
        return forceSync;
    }

    /**
     * @return  True if we are performing a one-time force sync
     */
    public boolean isAlwaysForceSync() {
        return alwaysForceSync;
    }

    /**
     * @return True if auto sync is disabled
     */
    public boolean isDisableAutoSync() {
        return disableAutoSync;
    }

    /**
     * @return True if we are using the old style client names
     */
    public boolean isUseOldClientName() {
        return this.useOldClientName;
    }

    /**
     * @param force True to perform a one time force sync, false to perform normal sync
     */
    public void setForceSync(boolean force) {
        this.forceSync = force;
    }

    /**
     * @param force True to perform a one time force sync, false to perform normal sync
     */
    public void setAlwaysForceSync(boolean force) {
        this.alwaysForceSync = force;
    }

    /**
     * @param disable True to disable the pre-build sync, false to perform pre-build sync
     */
    public void setDisableAutoSync(boolean disable) {
        this.disableAutoSync = disable;
    }

    /**
     * @param use True to use the old style client names, false to use the new style
     */
    public void setUseOldClientName(boolean use) {
        this.useOldClientName = use;
    }

    /**
     * @return  True if we are using a label
     */
    public boolean isUseLabel() {
        return p4Label != null;
    }

    /**
     * @param dontRenameClient  False if the client will rename the client spec for each
     * slave
     */
    public void setDontRenameClient(boolean dontRenameClient) {
        this.dontRenameClient = dontRenameClient;
    }

    /**
     * @return  True if the client will rename the client spec for each slave
     */
    public boolean isDontRenameClient() {
        return dontRenameClient;
    }

    /**
     * @return True if the plugin is to delete the workpsace files before building.
     */
    public boolean isWipeBeforeBuild() {
        return wipeBeforeBuild;
    }

    /**
     * @return True if the plugin is to delete the workpsace including the.repository files before building.
     */
    public boolean isWipeRepoBeforeBuild() {
        return wipeRepoBeforeBuild;
    }

    /**
     * @param clientFormat A string defining the format of the client name for slave workspaces.
     */
    public void setSlaveClientNameFormat(String clientFormat){
        this.slaveClientNameFormat = clientFormat;
    }

    /**
     * @param wipeBeforeBuild True if the client is to delete the workspace files before building.
     */
    public void setWipeBeforeBuild(boolean wipeBeforeBuild) {
        this.wipeBeforeBuild = wipeBeforeBuild;
    }

    public boolean isDontUpdateClient() {
        return dontUpdateClient;
    }

    public void setDontUpdateClient(boolean dontUpdateClient) {
        this.dontUpdateClient = dontUpdateClient;
    }

    public boolean isUseViewMaskForPolling() {
        return useViewMaskForPolling;
    }

    public void setUseViewMaskForPolling(boolean useViewMaskForPolling) {
        this.useViewMaskForPolling = useViewMaskForPolling;
    }

    public boolean isUseViewMaskForSyncing() {
        return useViewMaskForSyncing;
    }

    public void setUseViewMaskForSyncing(boolean useViewMaskForSyncing) {
        this.useViewMaskForSyncing = useViewMaskForSyncing;
    }

    public String getViewMask() {
        return viewMask;
    }

    public void setViewMask(String viewMask) {
        this.viewMask = viewMask;
    }

    public boolean isUseViewMask() {
        return useViewMask;
    }

    public void setUseViewMask(boolean useViewMask) {
        this.useViewMask = useViewMask;
    }

    public String getP4Charset() {
        return p4Charset;
    }

    public void setP4Charset(String p4Charset) {
        this.p4Charset = p4Charset;
    }

    public String getP4CommandCharset() {
        return p4CommandCharset;
    }

    public void setP4CommandCharset(String p4CommandCharset) {
        this.p4CommandCharset = p4CommandCharset;
    }

    public String getLineEndValue() {
        return lineEndValue;
    }

    public void setLineEndValue(String lineEndValue) {
        this.lineEndValue = lineEndValue;
    }

    public boolean isDisableSyncOnly() {
        return disableSyncOnly;
    }

    public void setDisableSyncOnly(boolean disableSyncOnly) {
        this.disableSyncOnly = disableSyncOnly;
	}

    public String getExcludedUsers() {
        return excludedUsers;
    }

    public void setExcludedUsers(String users) {
        excludedUsers = users;
    }

    public String getExcludedFiles() {
        return excludedFiles;
    }

    public void setExcludedFiles(String files) {
        excludedFiles = files;
    }

    public boolean isPollOnlyOnMaster() {
        return pollOnlyOnMaster;
    }

    public void setPollOnlyOnMaster(boolean pollOnlyOnMaster) {
        this.pollOnlyOnMaster = pollOnlyOnMaster;
    }

    public boolean isDontUpdateServer() {
        return dontUpdateServer;
    }

    public void setDontUpdateServer(boolean dontUpdateServer) {
        this.dontUpdateServer = dontUpdateServer;
    }

    public List<String> getAllLineEndChoices(){
        List<String> allChoices = ((PerforceSCMDescriptor)this.getDescriptor()).getAllLineEndChoices();
        ArrayList<String> choices = new ArrayList<String>();
        //Order choices so that the current one is first in the list
        //This is required in order for tests to work, unfortunately
        choices.add(lineEndValue);
        for(String choice : allChoices){
            if(!choice.equals(lineEndValue)){
                choices.add(choice);
            }
        }
        return choices;
    }

    /**
     * This is only for the config screen.  Also, it returns a string and not an int.
     * This is because we want to show an empty value in the config option if it is not being
     * used.  The default value of -1 is not exactly empty.  So if we are set to default of
     * -1, we return an empty string.  Anything else and we return the actual change number.
     *
     * @return  The one time use variable, firstChange.
     */
    public String getFirstChange() {
        if (firstChange <= 0)
            return "";
        return Integer.valueOf(firstChange).toString();
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
        Logger perforceLogger = Logger.getLogger(PerforceSCM.class.getName());
        perforceLogger.info(
            "Workspace '"+workspace.getRemote()+"' is being deleted; flushing workspace to revision 0.");
        TaskListener loglistener = new LogTaskListener(perforceLogger,Level.INFO);
        PrintStream log = loglistener.getLogger();
        TaskListener listener = new StreamTaskListener(log);
        Launcher launcher = node.createLauncher(listener);
        Depot depot = getDepot(launcher, workspace, project, null, node);
        try {
            Workspace p4workspace = getPerforceWorkspace(
                project,
                substituteParameters(projectPath,getDefaultSubstitutions(project)),
                depot,
                node,
                null,
                null,
                workspace,
                listener,
                dontRenameClient);
            flushWorkspaceTo0(depot, p4workspace, log);
        } catch (Exception ex) {
            Logger.getLogger(PerforceSCM.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return true;
    }

    @Override public boolean requiresWorkspaceForPolling() {
	//nodes are allocated and used in the pollChanges() function if available,
        //so we'll just tell jenkins to provide the master's launcher.

	return false;
    }

    public boolean isSlaveClientNameStatic() {
        Map<String,String> testSub1 = new Hashtable<String,String>();
        testSub1.put("hostname", "HOSTNAME1");
        testSub1.put("nodename", "NODENAME1");
        testSub1.put("hash", "HASH1");
        testSub1.put("basename", this.p4Client);
        String result1 = substituteParameters(getSlaveClientNameFormat(), testSub1);

        Map<String,String> testSub2 = new Hashtable<String,String>();
        testSub2.put("hostname", "HOSTNAME2");
        testSub2.put("nodename", "NODENAME2");
        testSub2.put("hash", "HASH2");
        testSub2.put("basename", this.p4Client);
        String result2 = substituteParameters(getSlaveClientNameFormat(), testSub2);

        return result1.equals(result2);
    }

    @Override
    public boolean supportsPolling() {
        return true;
    }

}
