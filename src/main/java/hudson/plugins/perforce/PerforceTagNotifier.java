package hudson.plugins.perforce;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.model.BuildListener;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.Launcher;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.plugins.perforce.utils.MacroStringHelper;
import hudson.plugins.perforce.utils.ParameterSubstitutionException;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * PerforceTagNotifier
 * Adds the option to automatically tag a build upon build completion.
 *
 * @author rpetti
 */
public class PerforceTagNotifier extends Notifier {
    protected static final Logger LOGGER = Logger.getLogger(PerforceTagNotifier.class.getName());

    public String rawLabelName;
    public String rawLabelOwner;
    public String rawLabelDesc;
    public boolean onlyOnSuccess = true;

    public boolean isOnlyOnSuccess() {
        return onlyOnSuccess;
    }

    public void setOnlyOnSuccess(boolean onlyOnSuccess) {
        this.onlyOnSuccess = onlyOnSuccess;
    }

    public String getRawLabelName() {
        return rawLabelName;
    }

    public void setRawLabelName(String rawLabelName) {
        this.rawLabelName = rawLabelName;
    }

    public String getRawLabelDesc() {
        return rawLabelDesc;
    }

    public void setRawLabelDesc(String rawLabelDesc) {
        this.rawLabelDesc = rawLabelDesc;
    }

    public String getRawLabelOwner() {
        return rawLabelOwner;
    }

    public void setRawLabelOwner(String rawLabelOwner) {
        this.rawLabelOwner = rawLabelOwner;
    }
    
    @DataBoundConstructor
    public PerforceTagNotifier(String rawLabelName, String rawLabelDesc, String rawLabelOwner, boolean onlyOnSuccess){
        this.rawLabelName = rawLabelName;
        this.rawLabelDesc = rawLabelDesc;
        this.rawLabelOwner = rawLabelOwner;
        this.onlyOnSuccess = onlyOnSuccess;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
        if(!onlyOnSuccess || build.getResult() == Result.SUCCESS){

            EnvVars environment;
            try{
                environment = build.getEnvironment(listener);
            } catch (IOException e) {
                listener.getLogger().println("Could not load build environment.");
                return false;
            }

            PerforceTagAction tagAction = (PerforceTagAction) build.getAction(PerforceTagAction.class);

            if(tagAction == null){
                //look for Promoted build
                listener.getLogger().println("Could not find tag information, checking if this is a promotion job.");
                String jobName = environment.get("PROMOTED_JOB_NAME");
                String buildNumber = environment.get("PROMOTED_NUMBER");
                if(jobName == null || jobName.isEmpty() || buildNumber == null || buildNumber.isEmpty()){
                    listener.getLogger().println("Not a promotion job.");
                } else {
                    AbstractProject project = (AbstractProject) Hudson.getInstance().getItemByFullName(jobName, AbstractProject.class);
                    int buildNum = Integer.parseInt(buildNumber);
                    build = (AbstractBuild<?, ?>) project.getBuildByNumber(buildNum);
                    tagAction = (PerforceTagAction) build.getAction(PerforceTagAction.class);   
                }
                if(tagAction == null){
                    listener.getLogger().println("Could not label build in perforce; is it a valid perforce job?");
                    return false;
                }
            }

            listener.getLogger().println("Labelling Build in Perforce using " + rawLabelName);

            String labelName, labelDesc, labelOwner;
            try {
                labelName = MacroStringHelper.substituteParameters(rawLabelName, build, null);
                labelDesc = MacroStringHelper.substituteParameters(rawLabelDesc, build, null);
                labelOwner = MacroStringHelper.substituteParameters(rawLabelOwner, build, null);
            } catch (ParameterSubstitutionException ex) {
                listener.getLogger().println("Parameter substitution error in label items. "+ex.getMessage());
                return false;
            }
            
            if (labelName == null || labelName.equals("")) {
                listener.getLogger().println("Label Name is empty, cannot label.");
                return false;
            }
            
            if (labelDesc == null || labelDesc.equals("")) {
                labelDesc = "Label automatically generated by Perforce Plugin.";
            }
            

            try {
                tagAction.tagBuild(labelName, labelDesc, labelOwner);
            } catch (IOException e) {
                listener.getLogger().println(e.getMessage());
                return false;
            }

            listener.getLogger().println("Label '" + labelName + "' successfully generated.");
        }

        return true;
    }

    public static DescriptorImpl descriptor() {
        return Hudson.getInstance().getDescriptorByType(PerforceTagNotifier.DescriptorImpl.class);
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Create or Update Label in Perforce";
        }
        
    }

}
