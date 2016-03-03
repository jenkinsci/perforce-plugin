package hudson.plugins.perforce;

import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.EnvironmentSpecific;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Information about Perforce installation. This includes the path to p4 executable.
 *
 */
public final class PerforceToolInstallation extends ToolInstallation implements NodeSpecific<PerforceToolInstallation>, EnvironmentSpecific<PerforceToolInstallation> {

    //To store migrated data as all the tool installations are created on one go.
    static transient ArrayList<PerforceToolInstallation> p4Tools = new ArrayList<PerforceToolInstallation>();

    /**
     * Default constructor.
     * 
     * @param name Installation name
     * @param home Path to p4.exe
     * @param properties Additional tool installation data
     */
    @DataBoundConstructor
    public PerforceToolInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    /**
     * Get the path to p4.exe
     * 
     * @return Path to p4.exe
     */
    public String getP4Exe() {
        return getHome();
    }

    /**
     * Migrate old data into new tool installations if needed.
     */
    @Initializer(after=InitMilestone.JOB_LOADED)
    public static void onLoaded() {
        DescriptorImpl descriptor = (DescriptorImpl) Hudson.getInstance().getDescriptor(PerforceToolInstallation.class);
        PerforceToolInstallation[] installations = getInstallations(descriptor);

        //Allow only one migration round. Old "p4Exe" field is kept in job configuration until the job is saved.
        if (installations.length > 0) {
            return;
        }

        if (!p4Tools.isEmpty()) {
            Hudson.getInstance().getDescriptorByType(DescriptorImpl.class).setInstallations(p4Tools.toArray(new PerforceToolInstallation[p4Tools.size()]));
        }
    }

    private static PerforceToolInstallation[] getInstallations(DescriptorImpl descriptor) {
        PerforceToolInstallation[] installations;
        try {
            installations = descriptor.getInstallations();
        } catch (NullPointerException e) {
            installations = new PerforceToolInstallation[0];
        }
        return installations;
    }
    
    /**
     * Migrate data from old job specific "p4Exe" field. Create a tool installation for each
     * individual path with the path as the tool name.
     * 
     * @param exe The path to p4 executable
     */
    public static synchronized void migrateOldData(String exe) {
        for (PerforceToolInstallation tool : p4Tools) {
            //Tool installation already exists, Unix case
            if (File.separatorChar == '/' && tool.getName().equals(exe)) {
                return;
            }
            //Tool installation already exists, Windows case
            if (File.separatorChar != '/' && tool.getName().equalsIgnoreCase(exe)) {
                return;
            }
        }
        p4Tools.add(new PerforceToolInstallation(exe, exe, Collections.<ToolProperty<?>>emptyList()));
    }

    public PerforceToolInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new PerforceToolInstallation(getName(), translateFor(node, log), Collections.<ToolProperty<?>>emptyList());
    }

    public PerforceToolInstallation forEnvironment(EnvVars environment) {
        return new PerforceToolInstallation(getName(), environment.expand(getHome()), Collections.<ToolProperty<?>>emptyList());
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Hudson.getInstance().getDescriptor(PerforceToolInstallation.class);
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<PerforceToolInstallation> {
        @CopyOnWrite
        private volatile PerforceToolInstallation[] installations = new PerforceToolInstallation[0];

        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public String getDisplayName() {
            return "Perforce";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            super.configure(req, json);
            save();
            return true;
        }

        @Override
        public PerforceToolInstallation[] getInstallations() {
            return installations;
        }

        @Override
        public void setInstallations(PerforceToolInstallation... installations) {
            this.installations = installations;
            save();
        }

        /**
         * Checks if the path to p4 executable exists.
         */
        public FormValidation doCheckHome(@QueryParameter File value)
            throws IOException, ServletException {

            Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
            String path = value.getPath();

            return FormValidation.validateExecutable(path);
        }
    }

}

