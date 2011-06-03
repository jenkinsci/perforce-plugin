package hudson.plugins.perforce;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

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
        User author = User.get(change.getUser());
        // Need to store the actual perforce user id for later retrieval
        // because Jenkins does not support all the same characters that
        // perforce does in the userID.
        PerforceUserProperty puprop = author.getProperty(PerforceUserProperty.class);
        if ( puprop == null || puprop.getPerforceId() == null || puprop.getPerforceId().equals("")){
            puprop = new PerforceUserProperty();
            try {
                author.addProperty(puprop);
            } catch (IOException ex) {
                Logger.getLogger(PerforceChangeLogEntry.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        puprop.setPerforceId(change.getUser());
        return author;
    }

    public String getUser() {
        return getAuthor().getDisplayName();
    }

    public Collection<Changelist.FileEntry> getAffectedFiles() {
        return change.getFiles();
    }

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
     * {@inheritDoc}
     */
    public String getCurrentRevision() {
        return getChangeNumber();
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
