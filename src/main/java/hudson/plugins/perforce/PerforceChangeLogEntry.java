package hudson.plugins.perforce;

import java.util.*;

import org.kohsuke.stapler.export.Exported;

import hudson.scm.*;
import hudson.model.User;

import com.tek42.perforce.model.Changelist;
import java.text.SimpleDateFormat;

/**
 * Perforce Implementation of {@link ChangeLogSet.Entry}.  This is a 1 to 1 mapping of
 * Perforce changelists.
 * <p>
 * Note: Internally, within the plugin we use an actual Perforce Change object in place of this.
 * 
 * @author Mike Wille
 */
public class PerforceChangeLogEntry extends ChangeLogSet.Entry {
    Changelist change;

    public PerforceChangeLogEntry(PerforceChangeLogSet parent) {
        super();
        setParent(parent);
    }
    
    @Override
    @Exported
    public User getAuthor() {
        return User.get(change.getUser());
    }

    @Override
    @Exported
    public Collection<String> getAffectedPaths() {
        List<String> paths = new ArrayList<String>(change.getFiles().size());
        for (Changelist.FileEntry entry : change.getFiles()) {
            paths.add(entry.getFilename());
        }
        return paths;
    }

    @Override
    @Exported
    public String getMsg() {
        return change.getDescription();
    }

    @Exported
    public String getChangeNumber() {
        return new Integer(getChange().getChangeNumber()).toString();
    }

    @Exported
    public String getChangeTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(getChange().getDate());
    }

    /**
     * @return the change
     */
    public Changelist getChange() {
        return change;
    }

    /**
     * @param change the change to set
     */
    public void setChange(Changelist change) {
        this.change = change;
    }    
    
}
