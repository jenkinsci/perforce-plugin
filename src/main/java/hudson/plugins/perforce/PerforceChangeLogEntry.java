package hudson.plugins.perforce;

import java.util.*;

import hudson.scm.*;
import hudson.model.User;

import com.tek42.perforce.model.Changelist;

/**
 * Perforce Implementation of {@link ChangeLogSet.Entry}.  This is a 1 to 1 mapping of
 * Perforce changelists.
 * <p>
 * Note: Internally, within the plugin we use an actual Perforce Change object in place of this.
 * 
 * @author Mike Wille
 *
 */
public class PerforceChangeLogEntry extends ChangeLogSet.Entry {
	Changelist change;

	public PerforceChangeLogEntry(PerforceChangeLogSet parent) {
		super();
		setParent(parent);
	}
	
	@Override
	public User getAuthor() {
		return User.get(change.getUser());
	}

	@Override
	public Collection<String> getAffectedPaths() {
		List<String> paths = new ArrayList<String>(change.getFiles().size());
		for(Changelist.FileEntry entry : change.getFiles()) {
			paths.add(entry.getFilename());
		}
		return paths;
	}

	@Override
	public String getMsg() {
		return change.getDescription();
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
