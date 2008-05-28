package hudson.plugins.perforce;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;
import com.tek42.perforce.model.Label;
import static hudson.Util.fixEmpty;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Hudson;
import hudson.scm.AbstractScmTagAction;
import hudson.util.FormFieldValidator;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link Action} that lets people create tag for the given build.
 * 
 * @author Mike Wille
 */
public class PerforceTagAction extends AbstractScmTagAction {
	private int changeNumber;
	private Depot depot;
	private String tag;
	private String desc;
	private String view;
    
    public PerforceTagAction(AbstractBuild build, Depot depot, int changeNumber, String view) {
        super(build);
        this.depot = depot;
        this.changeNumber = changeNumber;
        this.view = view;
    }

    public int getChangeNumber() {
    	return changeNumber;
    }
    
    public String getIconFileName() {
        if(tag == null && !Hudson.isAdmin())
            return null;
        return "save.gif";
    }

    public String getDisplayName() {
        if(isTagged())
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
    
    /**
     * Returns true if this build has already been tagged at least once.
     */
    public boolean isTagged() {
        if(tag == null)
        	return false;
        return true;
    }

    /**
     * Checks to see if the user entered tag matches any Perforce restrictions.
     */
    public String isInvalidTag(String tag) {
    	Pattern spaces = Pattern.compile("\\s{1,}");

    	Matcher m = spaces.matcher(tag);
    	if(m.find()) {
    		return "Spaces are not allowed.";
    	}
    	
    	return null;
    }
    
    /**
     * Checks if the value is a valid Perforce tag (label) name.
     */
    public synchronized void doCheckTag(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        new FormFieldValidator(req,rsp,false) {
            protected void check() throws IOException, ServletException {
                String tag = fixEmpty(request.getParameter("value")).trim();
                if(tag == null) {// nothing entered yet
                    ok();
                    return;
                }

                error(isInvalidTag(tag));
            }
        }.check();
    }
    
    /**
     * Invoked to actually tag the workspace.
     */
    public synchronized void doSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        tag = req.getParameter("name");
        desc = req.getParameter("desc");
        
        Label label = new Label();
        label.setName(tag);
        label.setDescription(desc);
        label.setRevision(new Integer(changeNumber).toString());
        label.addView(view);
        try {
        	depot.getLabels().saveLabel(label);
        } catch(PerforceException e) {
        	tag = null;
        	desc = null;
        	e.printStackTrace();
        	throw new IOException("Failed to issue perforce label." + e.getMessage());
        }
        build.save();
        rsp.sendRedirect(".");
    }
}
