/*
 *	P4Java - java integration with Perforce SCM
 *	Copyright (C) 2007-,  Mike Wille, Tek42
 *
 *	This library is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU Lesser General Public
 *	License as published by the Free Software Foundation; either
 *	version 2.1 of the License, or (at your option) any later version.
 *
 *	This library is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *	Lesser General Public License for more details.
 *
 *	You should have received a copy of the GNU Lesser General Public
 *	License along with this library; if not, write to the Free Software
 *	Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *	You can contact the author at:
 *
 *	Web:	http://tek42.com
 *	Email:	mike@tek42.com
 *	Mail:	755 W Big Beaver Road
 *			Suite 1110
 *			Troy, MI 48084
 */

package com.tek42.perforce.model;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents a changelist in Perforce.
 * <p>
 * Again Perforce fails us with an imcomplete API. Their change object does not contain a record of files or jobs
 * attached to the change. Grr... I'm forced to create one that is more complete.
 * <p>
 * This class maps the output of p4 describe [ChangeNumber]. However, it does not contain the diffs ouput by that
 * command. If you want those, get them yourself.
 * 
 * @author Mike Wille
 */
public class Changelist implements java.io.Serializable {
	int changeNumber;
	String workspace;
	Date date;
	String user;
	String description;
	List<FileEntry> files;
	List<JobEntry> jobs;

	public Changelist() {
		files = new ArrayList<FileEntry>(0);
		jobs = new ArrayList<JobEntry>(0);
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[Change]: " + changeNumber + "\n");
		sb.append("by " + user + "@" + workspace + "\n");
		sb.append("on " + date + "\n");
		sb.append("Description:\n" + description + "\n");
		if(jobs.size() > 0) {
			sb.append("Jobs: \n");
			for(JobEntry job : jobs) {
				sb.append(job + "\n");
			}
		}
		if(files.size() > 0) {
			sb.append("Files: \n");
			for(FileEntry file : files) {
				sb.append(file + "\n");
			}
		}

		return sb.toString();
	}

	/**
	 * Perforce has multiple files per change. This class represents a single file within a change which includes the
	 * action, filename, and revision.
	 * 
	 * @author Mike Wille
	 */
	public static class FileEntry implements java.io.Serializable {
		public static enum Action {
			ADD, EDIT, DELETE, INTEGRATE, BRANCH, PURGE
		}

		Action action;
		String filename;
		String revision;

		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(action + " '" + filename + "' #" + revision + ".");
			return sb.toString();
		}

		/**
		 * @return the action
		 */
		public Action getAction() {
			return action;
		}

		/**
		 * @param action
		 *            the action to set
		 */
		public void setAction(Action action) {
			this.action = action;
		}

		/**
		 * @return the filename
		 */
		public String getFilename() {
			return filename;
		}

		/**
		 * @param filename
		 *            the filename to set
		 */
		public void setFilename(String filename) {
			this.filename = filename;
		}

		/**
		 * @return the revision
		 */
		public String getRevision() {
			return revision;
		}

		/**
		 * @param revision
		 *            the revision to set
		 */
		public void setRevision(String revision) {
			this.revision = revision;
		}
	}

	/**
	 * Perforce links issues to changes via jobs. This represents a job attached to a change.
	 * 
	 * @author Mike Wille
	 */
	public static class JobEntry implements java.io.Serializable {
		String status;
		String job;
		String description;

		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(job + " " + status + " " + description);
			return sb.toString();
		}

		/**
		 * @return the status
		 */
		public String getStatus() {
			return status;
		}

		/**
		 * @param status
		 *            the status to set
		 */
		public void setStatus(String status) {
			this.status = status;
		}

		/**
		 * @return the job
		 */
		public String getJob() {
			return job;
		}

		/**
		 * @param job
		 *            the job to set
		 */
		public void setJob(String job) {
			this.job = job;
		}

		/**
		 * @return the description
		 */
		public String getDescription() {
			return description;
		}

		/**
		 * @param description
		 *            the description to set
		 */
		public void setDescription(String description) {
			this.description = description;
		}
	}

	/**
	 * @return the changeNumber
	 */
	public int getChangeNumber() {
		return changeNumber;
	}

	/**
	 * @param changeNumber
	 *            the changeNumber to set
	 */
	public void setChangeNumber(int changeNumber) {
		this.changeNumber = changeNumber;
	}

	/**
	 * @return the workspace
	 */
	public String getWorkspace() {
		return workspace;
	}

	/**
	 * @param workspace
	 *            the workspace to set
	 */
	public void setWorkspace(String workspace) {
		this.workspace = workspace;
	}

	/**
	 * @return the date
	 */
	public Date getDate() {
		return date;
	}

	/**
	 * @param date
	 *            the date to set
	 */
	public void setDate(Date date) {
		this.date = date;
	}

	/**
	 * @return the user
	 */
	public String getUser() {
		return user;
	}

	/**
	 * @param user
	 *            the user to set
	 */
	public void setUser(String user) {
		this.user = user;
	}

	/**
	 * @return the description
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * @param description
	 *            the description to set
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * @return the files
	 */
	public List<FileEntry> getFiles() {
		return files;
	}

	/**
	 * @param files
	 *            the files to set
	 */
	public void setFiles(List<FileEntry> files) {
		this.files = files;
	}

	/**
	 * @return the jobs
	 */
	public List<JobEntry> getJobs() {
		return jobs;
	}

	/**
	 * @param jobs
	 *            the jobs to set
	 */
	public void setJobs(List<JobEntry> jobs) {
		this.jobs = jobs;
	}
}
