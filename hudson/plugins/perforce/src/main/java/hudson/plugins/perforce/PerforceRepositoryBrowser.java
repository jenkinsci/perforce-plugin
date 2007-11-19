package hudson.plugins.perforce;

import hudson.scm.*;

import java.io.IOException;
import java.net.URL;

import com.tek42.perforce.model.Changelist;

/**
 * {@link RepositoryBrowser} for Perforce.
 *
 * @author Mike wille
 */
public abstract class PerforceRepositoryBrowser extends RepositoryBrowser<PerforceChangeLogEntry> {
    /**
     * Determines the link to the diff between the version.
     * in the {@link PerforceChangeLogEntry.Change.File} to its previous version.
     *
     * @return
     *      null if the browser doesn't have any URL for diff.
     */
    public abstract URL getDiffLink(Changelist.FileEntry file) throws IOException;

    /**
     * Determines the link to a single file under Perforce.
     * This page should display all the past revisions of this file, etc. 
     *
     * @return
     *      null if the browser doesn't have any suitable URL.
     */
    public abstract URL getFileLink(Changelist.FileEntry file) throws IOException;

    private static final long serialVersionUID = 1L;
}
