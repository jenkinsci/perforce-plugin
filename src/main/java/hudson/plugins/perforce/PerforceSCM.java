package hudson.plugins.perforce;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;
import com.tek42.perforce.model.Changelist;
import com.tek42.perforce.model.Counter;
import com.tek42.perforce.model.Label;
import com.tek42.perforce.model.Workspace;
import com.tek42.perforce.parse.Counters;
import com.tek42.perforce.parse.Workspaces;

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
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.JobProperty;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Node;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.FormValidation;

import hudson.util.StreamTaskListener;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
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
    String projectOptions;
    String p4Label;
    String p4Counter;

    String p4Exe = "C:\\Program Files\\Perforce\\p4.exe";
    String p4SysDrive = "C:";
    String p4SysRoot = "C:\\WINDOWS";

    PerforceRepositoryBrowser browser;

    private static final Logger LOGGER = Logger.getLogger(PerforceSCM.class.getName());

    private static final int MAX_CHANGESETS_ON_FIRST_BUILD = 50;

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
     * Disable Workspace pre-build automatic sync
     */
    boolean disableAutoSync = false;
    /**
     * This is to allow the client to use the old naming scheme
     * @deprecated As of 1.0.25, replaced by {@link #clientSuffixType}
     */
    @Deprecated
    boolean useOldClientName = false;
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
     * If > 0, then will override the changelist we sync to for the first build.
     */
    int firstChange = -1;

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
            String projectPath,
            String projectOptions,
            String p4Exe,
            String p4SysRoot,
            String p4SysDrive,
            String p4Label,
            String p4Counter,
            String lineEndValue,
            String p4Charset,
            String p4CommandCharset,
            boolean updateCounterValue,
            boolean forceSync,
            boolean alwaysForceSync,
            boolean updateView,
            boolean disableAutoSync,
            boolean wipeBeforeBuild,
            boolean dontUpdateClient,
            boolean exposeP4Passwd,
            String slaveClientNameFormat,
            int firstChange,
            PerforceRepositoryBrowser browser/*,
            String viewMask,
            boolean useViewMaskForPolling,
            boolean useViewMaskForSyncing*/
            ) {

        this.p4User = p4User;
        this.setP4Passwd(p4Passwd);
        this.exposeP4Passwd = exposeP4Passwd;
        this.p4Client = p4Client;
        this.p4Port = p4Port;
        this.projectOptions = (projectOptions != null)
                ? projectOptions
                : "noallwrite clobber nocompress unlocked nomodtime rmdir";

        // Make it backwards compatible with the old way of specifying a label
        Matcher m = Pattern.compile("(@\\S+)\\s*").matcher(projectPath);
        if (m.find()) {
            p4Label = m.group(1);
            projectPath = projectPath.substring(0,m.start(1))
                + projectPath.substring(m.end(1));
        }

        if (this.p4Label != null && p4Label != null) {
            Logger.getLogger(PerforceSCM.class.getName()).warning(
                    "Label found in views and in label field.  Using: "
                    + p4Label);
        }
        this.p4Label = Util.fixEmptyAndTrim(p4Label);

        this.p4Counter = Util.fixEmptyAndTrim(p4Counter);
        this.updateCounterValue = updateCounterValue;

        this.projectPath = Util.fixEmptyAndTrim(projectPath);

        if (p4Exe != null)
            this.p4Exe = Util.fixEmptyAndTrim(p4Exe);

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
        this.alwaysForceSync = alwaysForceSync;
        this.disableAutoSync = disableAutoSync;
        this.browser = browser;
        this.wipeBeforeBuild = wipeBeforeBuild;
        this.updateView = updateView;
        this.dontUpdateClient = dontUpdateClient;
        this.slaveClientNameFormat = slaveClientNameFormat;
        this.firstChange = firstChange;
        this.dontRenameClient = false;
        this.useOldClientName = false;
        this.p4Charset = Util.fixEmptyAndTrim(p4Charset);
        this.p4CommandCharset = Util.fixEmptyAndTrim(p4CommandCharset);
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
    protected Depot getDepot(Launcher launcher, FilePath workspace, AbstractProject project) {

        HudsonP4ExecutorFactory p4Factory = new HudsonP4ExecutorFactory(launcher,workspace);

        Depot depot = new Depot(p4Factory);
        depot.setUser(p4User);

        PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
        depot.setPassword(encryptor.decryptString(p4Passwd));

        depot.setPort(p4Port);
        if(project != null){
            depot.setClient(substituteParameters(p4Client, getDefaultSubstitutions(project)));
        } else {
            depot.setClient(p4Client);
        }

        depot.setExecutable(p4Exe);
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
                env.put("P4PASSWD", p4Ticket);
            } else {
                PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
                env.put("P4PASSWD", encryptor.decryptString(p4Passwd));
            }
        }

        env.put("P4CLIENT", getEffectiveClientName(build));
        PerforceTagAction pta = getMostRecentTagAction(build);
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

    private Hashtable<String, String> getDefaultSubstitutions(AbstractProject project) {
        Hashtable<String, String> subst = new Hashtable<String, String>();
        subst.put("JOB_NAME", project.getFullName());
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
        String pathName = new String(path);
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

    /*
     * @see hudson.scm.SCM#checkout(hudson.model.AbstractBuild, hudson.Launcher, hudson.FilePath, hudson.model.BuildListener, java.io.File)
     */
    @Override
    public boolean checkout(AbstractBuild build, Launcher launcher,
            FilePath workspace, BuildListener listener, File changelogFile) throws IOException, InterruptedException {

        PrintStream log = listener.getLogger();
        changelogFilename = changelogFile.getAbsolutePath();

        boolean wipeBeforeBuild = this.wipeBeforeBuild;
        boolean forceSync = this.forceSync;

        if(build.getBuildVariables() != null){
            Object p4clean;
            if((p4clean = build.getBuildVariables().get("P4CLEANWORKSPACE")) != null){
                String p4cleanString = p4clean.toString();
                if(p4cleanString.toUpperCase().equals("TRUE") || p4cleanString.equals("1")){
                    wipeBeforeBuild = true;
                } else {
                    wipeBeforeBuild = false;
                }
            }
            Object p4force;
            if((p4force = build.getBuildVariables().get("P4FORCESYNC")) != null){
                String p4forceString = p4force.toString();
                if(p4forceString.toUpperCase().equals("TRUE") || p4forceString.equals("1")){
                    forceSync = true;
                } else {
                    forceSync = false;
                }
            }
        }

        if(wipeBeforeBuild){
            log.println("Clearing workspace...");
            if(processWorkspaceBeforeDeletion(build.getProject(), workspace, build.getBuiltOn())){
                workspace.deleteContents();
                log.println("Cleared workspace.");
            } else {
                log.println("Could not clear workspace. See hudson.perforce.PerforceSCM logger for details.");
            }
				forceSync = true;
        }

        //keep projectPath local so any modifications for slaves don't get saved
        String projectPath = substituteParameters(this.projectPath, build);
        String p4Label = substituteParameters(this.p4Label, build);
        String viewMask = substituteParameters(this.viewMask, build);
        Depot depot = getDepot(launcher,workspace, build.getProject());

        //If we're doing a matrix build, we should always force sync.
        if((Object)build instanceof MatrixBuild || (Object)build instanceof MatrixRun){
            if(!alwaysForceSync && !wipeBeforeBuild)
                log.println("This is a matrix build; It is HIGHLY recommended that you enable the " +
                            "'Always Force Sync' or 'Clean Workspace' options. " +
                            "Failing to do so will likely result in child builds not being synced properly.");
        }

        try {
            Workspace p4workspace = getPerforceWorkspace(build.getProject(), projectPath, depot, build.getBuiltOn(), build, launcher, workspace, listener, false);

            saveWorkspaceIfDirty(depot, p4workspace, log);

            //If we're not managing the view, populate the projectPath with the current view from perforce
            //This is both for convenience, and so the labelling mechanism can operate correctly
            if(!updateView){
                projectPath = p4workspace.getViewsAsString();
            }
            
            //Get the list of changes since the last time we looked...
            String p4WorkspacePath = "//" + p4workspace.getName() + "/...";
            int lastChange = getLastChange((Run)build.getPreviousBuild());
            log.println("Last sync'd change: " + lastChange);

            int newestChange = lastChange;
            
            if(!disableAutoSync)
            {
                List<Changelist> changes;
                if (p4Label != null) {
                    changes = new ArrayList<Changelist>(0);
                } else {
                    String counterName;
                    if (p4Counter != null && !updateCounterValue)
                        counterName = p4Counter;
                    else
                        counterName = "change";

                    Counter counter = depot.getCounters().getCounter(counterName);
                    newestChange = counter.getValue();

                    if (lastChange == 0){
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
                }

                if (changes.size() > 0) {
                    // Save the changes we discovered.
                    PerforceChangeLogSet.saveToChangeLog(
                            new FileOutputStream(changelogFile), changes);
                    newestChange = changes.get(0).getChangeNumber();
                }
                else {
                    // No new changes discovered (though the definition of the workspace or label may have changed).
                    createEmptyChangeLog(changelogFile, listener, "changelog");
                    // keep the newestChange to the same value except when changing
                    // definitions from label builds to counter builds
                    if (lastChange != -1)
                        newestChange = lastChange;
                }
            }

            // Now we can actually do the sync process...
            StringBuilder sbMessage = new StringBuilder("Sync'ing workspace to ");
            StringBuilder sbSyncPath = new StringBuilder(p4WorkspacePath);
            StringBuilder sbSyncPathSuffix = new StringBuilder();
            sbSyncPathSuffix.append("@");

            if (p4Label != null) {
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

            if(!disableAutoSync)
            {
                if(useViewMaskForSyncing && useViewMask){
                    for(String path : viewMask.replaceAll("\r", "").split("\n")){
                        StringBuilder sbMaskPath = new StringBuilder(path);
                        sbMaskPath.append(sbSyncPathSuffix);
                        String maskPath = sbMaskPath.toString();
                        depot.getWorkspaces().syncTo(maskPath, forceSync || alwaysForceSync);
                    }
                } else {
                    depot.getWorkspaces().syncTo(syncPath, forceSync || alwaysForceSync);
                }
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            log.println("Sync complete, took " + duration + " ms");

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

            if (p4Label != null) {
                // Add tagging action that indicates that the build is already
                // tagged (you can't label a label).
                build.addAction(new PerforceTagAction(
                        build, depot, p4Label, projectPath));
            }
            else {
                // Add tagging action that enables the user to create a label
                // for this build.
                build.addAction(new PerforceTagAction(
                        build, depot, newestChange, projectPath));
            }

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

    /*
     * @see hudson.scm.SCM#pollChanges(hudson.model.AbstractProject, hudson.Launcher, hudson.FilePath, hudson.model.TaskListener)
     *
     * When *should* this method return true?
     *
     * 1) When there is no previous build (might be the first, or all previous
     *    builds might have been deleted).
     *
     * 2) When the previous build did not use Perforce, in which case we can't
     *    be "sure" of the state of the files.
     *
     * 3) If the clientspec's views have changed since the last build; we don't currently
     *    save that info, but we should!  I (James Synge) am not sure how to save it;
     *    should it be:
     *         a) in the build.xml file, and if so, how do we save it there?
     *         b) in the change log file (which actually makes a fair amount of sense)?
     *         c) in a separate file in the build directory (not workspace),
     *            along side the change log file?
     *
     * 4) p4Label has changed since the last build (either from unset to set, or from
     *    one label to another).
     *
     * 5) p4Label is set AND unchanged AND the set of file-revisions selected
     *    by the label in the p4 workspace has changed.  Unfortunately, I don't
     *    know of a cheap way to do this.
     *
     * There may or may not have been a previous build.  That build may or may not
     * have been done using Perforce, and if with Perforce, may have been done
     * using a label or latest, and may or may not be for the same view as currently
     * defined.  If any change has occurred, we'll treat that as a reason to build.
     *
     * Note that the launcher and workspace may operate remotely (as of 2009-06-21,
     * they correspond to the node where the last build occurred, if any; if none,
     * then the master is used).
     *
     * Note also that this method won't be called while the workspace (not job)
     * is in use for building or some other polling thread.
     */
    @Override
    public boolean pollChanges(AbstractProject project, Launcher launcher,
            FilePath workspace, TaskListener listener) throws IOException, InterruptedException {

        PrintStream logger = listener.getLogger();
        logger.println("Looking for changes...");
        
        Hashtable<String, String> subst = getDefaultSubstitutions(project);

        Depot depot = getDepot(launcher,workspace,project);
        
        try {
            Workspace p4workspace = getPerforceWorkspace(project, substituteParameters(projectPath, subst), depot, project.getLastBuiltOn(), null, launcher, workspace, listener, false);
            if (p4workspace.isNew())
                return true;

            saveWorkspaceIfDirty(depot, p4workspace, logger);
            
            Boolean needToBuild = needToBuild(p4workspace, project, depot, logger);
            if (needToBuild == null) {
                needToBuild = wouldSyncChangeWorkspace(project, depot, logger);
            }

            if (needToBuild == Boolean.FALSE) {
                return false;
            }
            else {
                logger.println("Triggering a build.");
                return true;
            }
        } catch (PerforceException e) {
            System.out.println("Problem: " + e.getMessage());
            logger.println("Caught Exception communicating with perforce." + e.getMessage());
            e.printStackTrace();
            throw new IOException("Unable to communicate with perforce.  Check log file for: " + e.getMessage());
        }
    }

    private Boolean needToBuild(Workspace p4workspace, AbstractProject project, Depot depot,
            PrintStream logger) throws IOException, InterruptedException, PerforceException {

        /*
         * Don't bother polling if we're already building, or soon will.
         * Ideally this would be a policy exposed to the user, perhaps for all
         * jobs with all types of scm, not just those using Perforce.
         */
//        if (project.isBuilding() || project.isInQueue()) {
//            logger.println("Job is already building or in the queue; skipping polling.");
//            return Boolean.FALSE;
//        }

        Run lastBuild = project.getLastBuild();
        if (lastBuild == null) {
            logger.println("No previous build exists.");
            //Don't trigger a build when the job has never run,
            //this is to prevent the build from triggering while configuring
            //a job that has been duplicated from another.
            return Boolean.FALSE;
        }

        PerforceTagAction action = lastBuild.getAction(PerforceTagAction.class);
        if (action == null) {
            logger.println("Previous build doesn't have Perforce info.");
            return null;
        }

        int lastChangeNumber = action.getChangeNumber();
        String lastLabelName = action.getTag();

        if (lastChangeNumber <= 0 && lastLabelName != null) {
            logger.println("Previous build was based on label " + lastLabelName);
            // Last build was based on a label, so we want to know if:
            //      the definition of the label was changed;
            //      or the view has been changed;
            //      or p4Label has been changed.
            if (p4Label == null) {
                logger.println("Job configuration changed to build from head, not a label.");
                return Boolean.TRUE;
            }

            if (!lastLabelName.equals(p4Label)) {
                logger.println("Job configuration changed to build from label " + p4Label + ", not from head");
                return Boolean.TRUE;
            }

            // No change in job definition (w.r.t. p4Label).  Don't currently
            // save enough info about the label to determine if it changed.
            logger.println("Assuming that the workspace and label definitions have not changed.");
            return Boolean.FALSE;
        }

        if (lastChangeNumber > 0) {
            logger.println("Last sync'd change was " + lastChangeNumber);
            if (p4Label != null) {
                logger.println("Job configuration changed to build from label " + p4Label + ", not from head.");
                return Boolean.TRUE;
            }

            int highestSelectedChangeNumber;

            if (p4Counter != null && !updateCounterValue) {

                // If this is a downstream build that triggers by polling the set counter
                // use the counter as the value for the newest change instead of the workspace view

                Counter counter = depot.getCounters().getCounter(p4Counter);
                highestSelectedChangeNumber = counter.getValue();
                logger.println("Latest submitted change selected by named counter is " + highestSelectedChangeNumber);
            } else {

                // Has any new change been submitted since then (that is selected
                // by this workspace).

                List<Integer> changeNumbers;
                if(useViewMaskForPolling && useViewMask){
                    Integer newestChange;
                    Counter counter = depot.getCounters().getCounter("change");
                    newestChange = counter.getValue();

                    changeNumbers = depot.getChanges().getChangeNumbersInRange(p4workspace, lastChangeNumber, newestChange, substituteParameters(viewMask, getDefaultSubstitutions(project)));
                } else {
                    String root = "//" + p4workspace.getName() + "/...";
                    changeNumbers = depot.getChanges().getChangeNumbers(root, -1, 2);
                }
                if (changeNumbers.isEmpty()) {
                    // Wierd, this shouldn't be!  I suppose it could happen if the
                    // view selects no files (e.g. //depot/non-existent-branch/...).
                    // This can also happen when using view masks with polling.
                    logger.println("No changes found.");
                    return Boolean.FALSE;
                } else {
                    highestSelectedChangeNumber = changeNumbers.get(0).intValue();
                    logger.println("Latest submitted change selected by workspace is " + highestSelectedChangeNumber);
                }
            }

            if (lastChangeNumber >= highestSelectedChangeNumber) {
                // Note, can't determine with currently saved info
                // whether the workspace definition has changed.
                logger.println("Assuming that the workspace definition has not changed.");
                return Boolean.FALSE;
            }
            else {
                return Boolean.TRUE;
            }
        }

        return null;
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

        String p4Client = this.p4Client;
        if (build != null) {
            p4Client = getEffectiveClientName(build);
        } else {
            p4Client = getEffectiveClientName(project, buildNode, workspace);
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

        // Get the clientspec (workspace) from perforce

        Workspace p4workspace = depot.getWorkspaces().getWorkspace(p4Client);
        assert p4workspace != null;
        boolean creatingNewWorkspace = p4workspace.isNew();

        // If the client workspace doesn't exist, and we're not managing the clients,
        // Then terminate the build with an error
        if(!updateView && creatingNewWorkspace){
            log.println("*** Perforce client workspace '" + p4Client +"' doesn't exist.");
            log.println("*** Please create it, or allow hudson to manage clients on it's own.");
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
        
        if (!localPath.equals(p4workspace.getRoot()) && !dontChangeRoot && !dontUpdateClient) {
            log.println("Changing P4 Client Root to: " + localPath);
            forceSync = true;
            p4workspace.setRoot(localPath);
        }

        // If necessary, rewrite the views field in the clientspec;

        // TODO If dontRenameClient==false, and updateView==false, user
        // has a lot of work to do to maintain the clientspecs.  Seems like
        // we could copy from a master clientspec to the slaves.

        if (updateView || creatingNewWorkspace) {
            List<String> mappingPairs = parseProjectPath(projectPath, p4Client);
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
        try {
            p4Client = getEffectiveClientName(build.getProject(), buildNode, workspace);
        } catch (Exception e){
            new StreamTaskListener(System.out).getLogger().println(
                    "Could not get effective client name: " + e.getMessage());
        } finally {
            p4Client = substituteParameters(p4Client, build);
            return p4Client;
        }
    }

    private String getEffectiveClientName(AbstractProject project, Node buildNode, FilePath workspace)
            throws IOException, InterruptedException {

        String nodeSuffix = "";
        String p4Client = substituteParameters(this.p4Client, getDefaultSubstitutions(project));
        String basename = p4Client;
        if (workspace == null){
            workspace = buildNode.getRootPath();
        }

        if (nodeIsRemote(buildNode) && !getSlaveClientNameFormat().equals("")) {
            String host = "UNKNOWNHOST";
            try{
                host = workspace.act(new GetHostname());
            } catch (Exception e) {
                LOGGER.warning("Could not get hostname for slave " + buildNode.getDisplayName());
                Writer stackTrace = new StringWriter();
                PrintWriter stackTracePrinter = new PrintWriter(stackTrace);
                e.printStackTrace(stackTracePrinter);
                LOGGER.info(stackTrace.toString());
            }

            if (host.contains(".")) {
                host = String.valueOf(host.subSequence(0, host.indexOf('.')));
            }
            //use hashcode of the nodename to get a unique, slave-specific client name
            String hash = String.valueOf(buildNode.getNodeName().hashCode());

            Map<String, String> substitutions = new Hashtable<String,String>();
            substitutions.put("hostname", host);
            substitutions.put("hash", hash);
            substitutions.put("basename", basename);

            p4Client = substituteParameters(getSlaveClientNameFormat(), substitutions);
        }
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
            newInstance.setUseViewMask(req.getParameter("p4.useViewMask") != null);
            newInstance.setViewMask(Util.fixEmptyAndTrim(req.getParameter("p4.viewMask")));
            newInstance.setUseViewMaskForPolling(req.getParameter("p4.useViewMaskForPolling") != null);
            newInstance.setUseViewMaskForSyncing(req.getParameter("p4.useViewMaskForSyncing") != null);
            return newInstance;
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
            String exe = fixNull(request.getParameter("exe")).trim();
            String user = fixNull(request.getParameter("user")).trim();
            String pass = fixNull(request.getParameter("pass")).trim();

            if (port.length() == 0 || exe.length() == 0) { // Not enough entered yet
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
                    depot.getWorkspaces().getWorkspace(workspace);

                if (p4Workspace.getAccess() == null ||
                        p4Workspace.getAccess().equals(""))
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

        public List<String> getAllLineEndChoices(){
            List<String> allChoices = Arrays.asList(new String[]{
                "local",
                "unix",
                "mac",
                "win",
                "share",
            });
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
    static List<String> parseProjectPath(String projectPath, String p4Client) {
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
                            }
                        }
                        // Assume anything else is a comment and ignore it
                    }
                }
            }
        }
        return parsed;
    }

    static String substituteParameters(String string, AbstractBuild build) {
        Hashtable<String,String> subst = new Hashtable<String,String>();
        subst.put("JOB_NAME", build.getProject().getFullName());
        subst.put("BUILD_TAG", "hudson-" + build.getProject().getName() + "-" + String.valueOf(build.getNumber()));
        subst.put("BUILD_ID", build.getId());
        subst.put("BUILD_NUMBER", String.valueOf(build.getNumber()));
        String result = substituteParameters(string, build.getBuildVariables());
        result = substituteParameters(result, subst);
        return result;
    }

    static String substituteParameters(String string, Map<String,String> subst) {
        if(string == null) return null;
        String newString = new String(string);
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
            if (!line.trim().equals(p1 + " " + p2))
                return false;
        }
        return !pi.hasNext(); // equals iff there are no more pairs
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
            "Workspace is being deleted; enabling one-time force sync.");
        forceSync = true;
        return true;
    }

    @Override public boolean requiresWorkspaceForPolling() {
	// If slaveClientNameFormat is empty - not using a host specific name, so can always
	// use 'master' to calculate poll changes without a local workspace
	// This allows slaves to be thrown away and still allow polling to work

	if(isSlaveClientNameStatic()) {
		//Logger.getLogger(PerforceSCM.class.getName()).info(
		//	"No SlaveClientName supplied - assuming shared clientname - so no Workspace required for Polling");
		return false;
	}
      return true;
    }

    public boolean isSlaveClientNameStatic() {
        Map<String,String> testSub1 = new Hashtable<String,String>();
        testSub1.put("hostname", "HOSTNAME1");
        testSub1.put("hash", "HASH1");
        testSub1.put("basename", this.p4Client);
        String result1 = substituteParameters(getSlaveClientNameFormat(), testSub1);

        Map<String,String> testSub2 = new Hashtable<String,String>();
        testSub2.put("hostname", "HOSTNAME2");
        testSub2.put("hash", "HASH2");
        testSub2.put("basename", this.p4Client);
        String result2 = substituteParameters(getSlaveClientNameFormat(), testSub2);

        return result1.equals(result2);
    }

}
