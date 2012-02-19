package hudson.plugins.perforce;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;
import com.tek42.perforce.model.Label;
import static hudson.Util.fixNull;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Hudson;
import hudson.scm.AbstractScmTagAction;
import hudson.util.FormValidation;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import org.kohsuke.stapler.QueryParameter;

/**
 * {@link Action} that lets people create tag for the given build.
 *
 * @author Mike Wille
 */
public class PerforceTagAction extends AbstractScmTagAction {
    private final int changeNumber;
    private Depot depot;
    private String tag;
    private String desc;
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
        this.view = views;
        this.owner = owner;
    }

    public PerforceTagAction(PerforceTagAction tga) {
        super(tga.build);
        this.depot = tga.depot;
        this.changeNumber = tga.changeNumber;
        this.tag = tga.tag;
        this.view = tga.view;
        this.owner = tga.owner;
    }

    public int getChangeNumber() {
        return changeNumber;
    }

    public String getView() {
        return view;
    }

    public String getIconFileName() {
        if (tag == null && !Hudson.getInstance().hasPermission(Hudson.ADMINISTER))
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

    /**
     * Returns true if this build has already been tagged at least once.
     */
    public boolean isTagged() {
        return tag != null;
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
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);

        String tag = req.getParameter("name");
        String desc = req.getParameter("desc");
        owner = req.getParameter("owner");

        tagBuild(tag, desc);

        rsp.sendRedirect(".");
    }

    public void tagBuild(String tagname, String description) throws IOException {
        tag = tagname;
        desc = description;
        Label label = new Label();
        label.setName(tag);
        label.setDescription(desc);
        label.setRevision(new Integer(changeNumber).toString());
        if(owner!=null && !owner.equals("")) label.setOwner(owner);

        if(this.getBuild().getProject().getScm() instanceof PerforceSCM){
            PerforceSCM scm = (PerforceSCM)this.getBuild().getProject().getScm();
            depot.setPassword(scm.getDecryptedP4Passwd(this.getBuild().getProject()));
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
            tag = null;
            desc = null;
            e.printStackTrace();
            throw new IOException("Failed to issue perforce label." + e.getMessage());
        }
        build.save();
    }
    
}
