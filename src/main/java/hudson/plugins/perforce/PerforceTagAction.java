package hudson.plugins.perforce;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;
import com.tek42.perforce.model.Label;
import static hudson.Util.fixNull;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Hudson;
import hudson.scm.AbstractScmTagAction;
import hudson.security.Permission;
import hudson.util.FormValidation;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import javax.annotation.CheckForNull;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * {@link Action} that lets people create tag for the given build.
 *
 * @author Mike Wille
 */
@ExportedBean
public class PerforceTagAction extends AbstractScmTagAction {
    private final int changeNumber;
    private Depot depot;
    private List<PerforceTag> tags = new ArrayList<PerforceTag>();
    @Deprecated
    private transient String tag;
    @Deprecated
    private transient String desc;
    private String view;
    private String owner;

    public PerforceTagAction(AbstractBuild build, Depot depot, int changeNumber, String views, String owner) {
        super(build);
        this.depot = depot;
        this.changeNumber = changeNumber;
        this.view = views;
        this.owner = owner;
    }

    public PerforceTagAction(AbstractBuild build, Depot depot, String label, String views, String owner) {
        super(build);
        this.depot = depot;
        this.changeNumber = -1;
        this.tag = label;
        this.tags.add(new PerforceTag(label,""));
        this.view = views;
        this.owner = owner;
    }

    public PerforceTagAction(PerforceTagAction tga) {
        super(tga.build);
        this.depot = tga.depot;
        this.changeNumber = tga.changeNumber;
        this.tag = tga.tag;
        this.tags.addAll(tga.getTags());
        this.view = tga.view;
        this.owner = tga.owner;
    }

    @Exported
    public int getChangeNumber() {
        return changeNumber;
    }

    public String getView() {
        return view;
    }

    public String getIconFileName() {
        if (!getACL().hasPermission(getPermission()))
            return null;
        return "save.gif";
    }

    public String getDisplayName() {
        if (isTagged())
            return "Perforce Label";
        else
            return "Label This Build";
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getDescription() {
        return desc;
    }

    public void setDescription(String desc) {
        this.desc = desc;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public Depot getDepot() {
        return depot;
    }

    public List<PerforceTag> getTags() {
        return tags;
    }

    public void setTags(List<PerforceTag> tags) {
        this.tags = tags;
    }

    /**
     * Returns true if this build has already been tagged at least once.
     */
    public boolean isTagged() {
        return tags != null && !tags.isEmpty();
    }

    /**
     * Checks to see if the user entered tag matches any Perforce restrictions.
     */
    public String isInvalidTag(String tag) {
        Pattern spaces = Pattern.compile("\\s{1,}");
        Matcher m = spaces.matcher(tag);
        if (m.find()) {
            return "Spaces are not allowed.";
        }
        return null;
    }
    
    @CheckForNull
    public PerforceSCM getSCM() {
        if(this.getBuild().getProject().getScm() instanceof PerforceSCM){
            return (PerforceSCM)this.getBuild().getProject().getScm();
        }
        return null;
    }

    /**
     * Checks if the value is a valid Perforce tag (label) name.
     */
    public synchronized FormValidation doCheckTag(@QueryParameter String value) {
        value = fixNull(value).trim();
        String error = value.length() > 0 ? isInvalidTag(value) : null;
        if (error != null) return FormValidation.error(error);
        return FormValidation.ok();
    }

    /**
     * Invoked to actually tag the workspace.
     */
    public synchronized void doSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        getACL().checkPermission(getPermission());

        String tag = req.getParameter("name");
        String desc = req.getParameter("desc");
        String owner = req.getParameter("owner");

        tagBuild(tag, desc, owner);

        rsp.sendRedirect(".");
    }

    public void tagBuild(String tagname, String description, String owner) throws IOException {
        Label label = new Label();
        label.setName(tagname);
        label.setDescription(description);
        label.setRevision(new Integer(changeNumber).toString());
        if(owner!=null && !owner.equals("")) label.setOwner(owner);

        PerforceSCM scm = getSCM();
        if(scm != null){
            depot.setPassword(scm.getDecryptedP4Passwd(this.getBuild().getProject(), this.getBuild().getBuiltOn()));
        }

        //Only take the depot paths and add them to the view.
        List<String> viewPairs = PerforceSCM.parseProjectPath(view, "workspace");
        for (int i=0; i < viewPairs.size(); i+=2){
            String depotPath = viewPairs.get(i);
            label.addView(depotPath);
        }
        try {
            depot.getLabels().saveLabel(label);
        } catch(PerforceException e) {
            e.printStackTrace();
            throw new IOException("Failed to issue perforce label." + e.getMessage());
        }
        tags.add(new PerforceTag(tagname,description));
        build.save();
    }
    
    @SuppressWarnings( "deprecation" )
    public Object readResolve() {
        if (tags == null)
        {
            tags = new ArrayList<PerforceTag>();
            if (tag != null)
            {
                tags.add(new PerforceTag(tag,desc));
            }
        }
        
        return this;
    }
    
    public static class PerforceTag {
        private String name;
        private String desc;

        public PerforceTag(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
        
    }

    @Override
    protected Permission getPermission() {
        return PerforceSCM.TAG;
    }
    
}
