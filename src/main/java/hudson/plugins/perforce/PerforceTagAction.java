package hudson.plugins.perforce;

import hudson.scm.*;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.model.LargeText;
import hudson.scm.SubversionSCM.SvnInfo;
import hudson.util.CopyOnWriteMap;
import hudson.util.FormFieldValidator;
import static hudson.Util.fixEmpty;
import static hudson.Util.fixNull;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Map.Entry;
import java.lang.ref.WeakReference;

import com.tek42.perforce.*;
import com.tek42.perforce.model.*;

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
        	throw new IOException("Failed to issue perforce label.", e);
        }
        
        rsp.sendRedirect(".");
    }
}
