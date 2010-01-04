package com.perforce.api;

import java.io.*;
import java.util.*;
import java.text.*;

/*
 * Copyright (c) 2001, Perforce Software, All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

/**
 * Representation of a source control file.
 * 
 * @see Hashtable
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2002/06/05 $ $Revision: #8 $
 */
public final class FileEntry extends SourceControlObject {
	private String depot_path = null;

	private String client_path = null;

	private String description = "";

	private String owner = "";

	private FileEntry source = null;

	private int head_change = -1;

	private int head_rev = 0;

	private String head_type = "unknown";

	private long head_time = 0;

	private int have_rev = 0;

	private int other_cnt = 0;

	private String head_action = "";

	private Vector others;

	private static HashDecay fentries;

	private String file_content = "";

	private DateFormat fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);

	/** Default no-argument constructor. */
	public FileEntry() {
		this((Env) null);
	}

	/**
	 * Constructs a file entry using the environment.
	 * 
	 * @param env
	 *            Source control environement to use.
	 */
	public FileEntry(Env env) {
		super(env);
		if(null == others) {
			others = new Vector();
		}
	}

	/**
	 * Constructs a file entry using the environment and path.
	 * 
	 * @param env
	 *            Source control environement to use.
	 * @param p
	 *            Path to the file.
	 */
	public FileEntry(Env env, String p) {
		this(env);
		if(p.startsWith("//")) {
			depot_path = p;
		} else {
			client_path = p;
		}
	}

	/**
	 * Constructs a file entry using the path.
	 * 
	 * @param p
	 *            Path to the file.
	 */
	public FileEntry(String p) {
		this(null, p);
	}

	private static HashDecay setCache() {
		if(null == fentries) {
			fentries = new HashDecay(120000);
			fentries.start();
		}
		return fentries;
	}

	public HashDecay getCache() {
		return setCache();
	}

	/** Sets the decription for this file */
	public void setDescription(String d) {
		description = d;
	}

	/** Returns the decription for this file */
	public String getDescription() {
		return description;
	}

	/** Sets the owner for this file */
	public void setOwner(String o) {
		int pos;
		owner = o;
		if(-1 != (pos = owner.indexOf('@'))) {
			owner = owner.substring(0, pos);
		}
	}

	/** Returns the owner for this file */
	public String getOwner() {
		return owner;
	}

	/** Sets the source file entry associated with this file. */
	public void setSource(FileEntry fent) {
		source = fent;
	}

	/** Returns the source file entry associated with this file. */
	public FileEntry getSource() {
		return source;
	}

	/** Sets the head revision type for this file. */
	public void setHeadType(String type) {
		this.head_type = type;
	}

	/** Returns the head revision type for this file. */
	public String getHeadType() {
		return this.head_type;
	}

	/**
	 * Sets the head date for this file. The expected format for the date is
	 * yyyy/MM/dd. The time will default to 12:00:00 AM.
	 */
	public void setHeadDate(String date) {
		// Format the current time.
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
		// Parse the previous string back into a Date.
		ParsePosition pos = new ParsePosition(0);
		Date hDate = formatter.parse(date, pos);
		this.head_time = hDate.getTime() / 1000;
	}

	/**
	 * Returns a String representation of date for the head revsision of the
	 * file. The format is yyyy/MM/dd.
	 */
	public String getHeadDate() {
		// Format the current time.
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
		return formatter.format(new Date(this.head_time * 1000));
	}

	/** Sets the head revision time for this file. */
	public void setHeadTime(long time) {
		this.head_time = time;
	}

	/** Returns the head revision time for this file. */
	public long getHeadTime() {
		return this.head_time;
	}

	/**
	 * Sets the format used by the getHeadTimeString method. The format of this
	 * string is that of the SimpleDateFormat class.
	 * <p>
	 * An example format would be setTimeFormat("MM/dd HH:mm:ss");
	 * 
	 * @see SimpleDateFormat
	 */
	public void setTimeFormat(String format) {
		if(null == format)
			return;
		fmt = new SimpleDateFormat(format);
	}

	/** Returns the head revision time as a <code>String</code> for this file. */
	public String getHeadTimeString() {
		Date d = new Date(this.head_time * 1000);

		if(null == fmt) {
			fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
			fmt.setTimeZone(TimeZone.getTimeZone("EST"));
		}
		return fmt.format(d);
	}

	/** Sets the head revision action for this file. */
	public void setHeadAction(String action) {
		this.head_action = action;
	}

	/** Returns the head revision action for this file. */
	public String getHeadAction() {
		return this.head_action;
	}

	/** Sets the head revision change number for this file. */
	public void setHeadChange(int change) {
		this.head_change = change;
	}

	/** Returns the head revision change number for this file. */
	public int getHeadChange() {
		return this.head_change;
	}

	/** Sets the head revision number for this file. */
	public void setHeadRev(int rev) {
		this.head_rev = rev;
	}

	/** Returns the head revision number for this file. */
	public int getHeadRev() {
		return this.head_rev;
	}

	/** Sets the revision number the client has for this file. */
	public void setHaveRev(int rev) {
		this.have_rev = rev;
	}

	/** Returns the revision number the client has for this file. */
	public int getHaveRev() {
		return this.have_rev;
	}

	/**
	 * Sets the depot path for this file.
	 * 
	 * @param p
	 *            path for this file in the depot.
	 */
	public void setDepotPath(String p) {
		this.depot_path = p;
	}

	/** Returns the depot path for this file. */
	public String getDepotPath() {
		return depot_path;
	}

	/** Returns the path in local format. Uses the local path delimeter. */
	public static String localizePath(String path) {
		return customizePath(path, '/', File.separatorChar);
	}

	/** Returns the path in depot format. Uses the depot delimeter: '/'. */
	public static String depotizePath(String path) {
		return customizePath(path, File.separatorChar, '/');
	}

	/**
	 * Returns the path after converting characters.
	 * 
	 * @param str
	 *            String to convert.
	 * @param from_char
	 *            Character to be changed from.
	 * @param to_char
	 *            Character to be changed to.
	 */
	public static String customizePath(String str, char from_char, char to_char) {
		StringBuffer strbuf = new StringBuffer();
		int beg = 0, end = 0;
		while(-1 != (end = str.indexOf(from_char, beg))) {
			strbuf.append(str.substring(beg, end));
			strbuf.append(to_char);
			beg = end + 1;
		}
		strbuf.append(str.substring(beg));
		return strbuf.toString();
	}

	/**
	 * Resolves this file. If the force flag is false, and auto-resolve is
	 * attempted (p4 resolve -am). If the force flag is true, an "accept theirs"
	 * resolve is completed (p4 resolve -at).
	 * 
	 * @param force
	 *            Indicates whether the resolve should be forced.
	 */
	public String resolve(boolean force) throws IOException {
		StringBuffer sb = new StringBuffer();
		String l;
		String[] rescmd = { "p4", "resolve", "-am", "fileRev" };
		if(force || -1 != (getHeadType().indexOf("binary")) || -1 != (getHeadType().indexOf("link"))) {
			rescmd[2] = "-at";
		} else {
			rescmd[2] = "-am";
		}
		rescmd[3] = getDepotPath();
		P4Process p = new P4Process(getEnv());
		p.exec(rescmd);
		while(null != (l = p.readLine())) {
			if(null != sb) {
				sb.append(l);
				sb.append('\n');
			}
		}
		p.close();
		return sb.toString();
	}

	/**
	 * Forces a resolve on a set of files. The <code>Enumeration</code>
	 * contains the set of <code>FileEntry</code> objects that need resolved.
	 * 
	 * @param env
	 *            Source control environment to use.
	 * @param en
	 *            <code>Enumeration</code> of <code>FileEntry</code>.
	 */
	public static String resolveAT(Env env, Enumeration en) throws IOException {
		StringBuffer sb = new StringBuffer();
		FileEntry fent;
		String l;
		String[] rescmd = { "p4", "-x", "-", "resolve", "-at" };
		P4Process p = new P4Process(env);
		p.exec(rescmd);
		while(en.hasMoreElements()) {
			fent = (FileEntry) en.nextElement();
			p.println(fent.getDepotPath());
			Debug.notify("resolveAT(): " + fent.getDepotPath());
		}
		p.println("\032\n\032");
		p.flush();
		p.outClose();
		Debug.notify("FileEntry.resolveAT(): Reading more lines.");
		while(null != (l = p.readLine())) {
			if(null != sb) {
				sb.append(l);
				sb.append('\n');
			}
		}
		p.close();
		return sb.toString();
	}

	/**
	 * Resolves all the files in the path. The flags are used by the 'p4
	 * resolve' command to resolve any files in the path. This is just a simple
	 * way to execute the 'p4 resolve' command.
	 * 
	 * @param env
	 *            Source control environment to use.
	 * @param flags
	 *            'p4 resolve' command flags.
	 * @param path
	 *            Path over which to resolve. May include wildcards.
	 */
	public static String resolveAll(Env env, String flags, String path) throws IOException {
		StringBuffer sb = new StringBuffer();
		FileEntry fent;
		String l;
		String[] rescmd = { "p4", "resolve", flags, path };
		P4Process p = new P4Process(env);
		p.exec(rescmd);
		Debug.notify("FileEntry.resolveAll(): Reading more lines.");
		while(null != (l = p.readLine())) {
			if(null != sb) {
				sb.append(l);
				sb.append('\n');
			}
		}
		p.close();
		return sb.toString();
	}

	/**
	 * @deprecated Don't use this anymore.
	 */
	public static String HTMLEncode(String str) {
		if(null == str)
			return null;
		StringBuffer strbuf = new StringBuffer(str.length());
		char tmp;
		for(int i = 0; i < str.length(); i++) {
			tmp = str.charAt(i);
			if('<' == tmp) {
				strbuf.append("&lt;");
			} else if('>' == tmp) {
				strbuf.append("&gt;");
			} else {
				strbuf.append(tmp);
			}
		}
		return strbuf.toString();
	}

	/** Returns the file name. */
	public String getName() {
		int pos;
		String path = getDepotPath();
		if(null == path) {
			path = getClientPath();
		}
		if(null == path) {
			return "";
		}
		if(-1 == (pos = path.lastIndexOf('/'))) {
			return path;
		}
		return path.substring(pos + 1);
	}

	/**
	 * Sets the client path for this file.
	 * 
	 * @param p
	 *            path for this file on the client system.
	 */
	public void setClientPath(String p) {
		this.client_path = p;
	}

	/** Returns the client path for this file. */
	public String getClientPath() {
		return client_path;
	}

	/**
	 * Gets the file information for the specified path.
	 * 
	 * @param p
	 *            Path of the file to gather information about.
	 */
	public static synchronized FileEntry getFile(String p) {
		FileEntry f = new FileEntry(p);
		f.sync();
		return f;
	}

	/**
	 * Returns the list of files for the path. The path may include wildcards.
	 * 
	 * @param env
	 *            Source control environment to use.
	 * @param path
	 *            Path for set of files.
	 */
	public static Vector getFiles(Env env, String path) {
		Vector v = null;
		String[] cmd = { "p4", "fstat", path + "%1" };
		if(null == path)
			return null;

		try {
			P4Process p = new P4Process(env);
			p.exec(cmd);
			v = parseFstat(null, p, true);
			p.close();
		} catch(IOException ex) {
			Debug.out(Debug.ERROR, ex);
		}
		return v;
	}

	/**
	 * Returns a list of <code>FileEntry</code> objects that represent the
	 * history of the specified file.
	 * 
	 * @param env
	 *            Source control environment to use.
	 * @param path
	 *            Path to the file. Must be specific. No wildcards.
	 */
	public static Vector getFileLog(Env env, String path) {
		String[] cmd = { "p4", "filelog", path };
		String l, tmp;
		StringTokenizer st;
		P4Process p;
		FileEntry fent = null, tmpent = null;
		Vector v = new Vector();
		int beg, end;

		if(null == path)
			return v;
		try {
			p = new P4Process(env);
			p.setRawMode(true);
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				l = l.trim();
				if(l.startsWith("info2: ") && null != fent) {
					tmpent = new FileEntry(env);
					beg = 8;
					if(-1 == (end = l.indexOf(' ', beg))) {
						continue;
					}
					tmpent.setHeadAction(l.substring(beg, end));
					beg = end;
					if(-1 == (end = l.indexOf("from "))) {
						tmpent.setDepotPath(path);
					} else {
						beg = end + 5;
						if(-1 == (end = l.indexOf('#', beg))) {
							tmpent.setDepotPath(l.substring(beg));
						} else {
							tmpent.setDepotPath(l.substring(beg, end));
						}
					}
					if(-1 != (end = l.lastIndexOf('#'))) {
						if(-1 != (beg = l.lastIndexOf('#', end - 1))) {
							tmpent.setHaveRev(Integer.parseInt(l.substring(beg + 1, end - 1)));
						}
						tmpent.setHeadRev(Integer.parseInt(l.substring(end + 1)));
					}
					fent.setSource(tmpent);
				} else if(l.startsWith("info1: ")) {
					if(null != fent) {
						v.addElement(fent);
					}
					fent = new FileEntry(env);
					fent.setDepotPath(path);
					st = new StringTokenizer(l.substring(8));
					fent.setHeadRev(Integer.parseInt(st.nextToken()));
					st.nextToken(); // change
					fent.setHeadChange(Integer.parseInt(st.nextToken()));
					fent.setHeadAction(st.nextToken());
					st.nextToken(); // on
					fent.setHeadDate(st.nextToken());
					st.nextToken(); // by
					fent.setOwner(st.nextToken());
					tmp = st.nextToken();
					fent.setHeadType(tmp.substring(1, tmp.length() - 1));
					if(1 < (end = l.lastIndexOf('\''))) {
						if(-1 < (beg = l.lastIndexOf('\'', end - 1))) {
							if(end - beg - 1 > 0) {
								fent.setDescription(l.substring(beg + 1, end - 1));
							}
						}
					}
				}
			}
			p.close();
		} catch(IOException ex) {
			Debug.out(Debug.ERROR, ex);
		}
		if(null != fent && null != fent.getDepotPath()) {
			v.addElement(fent);
		}
		return v;
	}

	/**
	 * Opens the file on the path for edit under the change. If the change is
	 * null, the file is opened under the default changelist.
	 * 
	 * @param env
	 *            P4 Environment
	 * @param path
	 *            Depot or client path to the file being opened for edit.
	 * @param sync
	 *            If true, the file will be sync'd before opened for edit.
	 * @param force
	 *            If true, the file will be opened for edit even if it isn't the
	 *            most recent version.
	 * @param lock
	 *            If true, the file will be locked once opened.
	 * @param chng
	 *            The change that the file will be opened for edit in.
	 */
	public static FileEntry openForEdit(Env env, String path, boolean sync, boolean force, boolean lock, Change chng)
			throws Exception {
		if(sync) {
			FileEntry.syncWorkspace(env, path);
		}
		FileEntry fent = new FileEntry(env, path);
		fent.openForEdit(force, lock, chng);
		return fent;
	}

	/**
	 * Opens this file for edit.
	 * 
	 * @see #openForEdit(Env, String, boolean, boolean, boolean, Change)
	 */
	public void openForEdit() throws Exception {
		openForEdit(true, false, null);
	}

	/**
	 * Opens this file for edit.
	 * 
	 * @see #openForEdit(Env, String, boolean, boolean, boolean, Change)
	 */
	public void openForEdit(boolean force, boolean lock) throws Exception {
		openForEdit(force, lock, null);
	}

	/**
	 * Opens this file for edit.
	 * 
	 * @see #openForEdit(Env, String, boolean, boolean, boolean, Change)
	 */
	public void openForEdit(boolean force, boolean lock, Change chng) throws Exception {
		String[] cmd1;
		String[] cmd2;
		String l;
		P4Process p;
		int i = 0;
		sync();
		if(force) {
			cmd1 = new String[4];
			cmd1[2] = "-f";
		} else {
			cmd1 = new String[3];
		}
		cmd1[0] = "p4";
		cmd1[1] = "sync";
		cmd1[cmd1.length - 1] = getDepotPath();

		cmd2 = new String[(null == chng) ? 3 : 5];
		cmd2[i++] = "p4";
		cmd2[i++] = "edit";
		if(null != chng) {
			cmd2[i++] = "-c";
			cmd2[i++] = String.valueOf(chng.getNumber());
		}
		cmd2[i++] = getClientPath();

		p = new P4Process(getEnv());
		p.exec(cmd1);
		while(null != (l = p.readLine())) {
		}
		p.close();
		p = new P4Process(getEnv());
		p.exec(cmd2);
		while(null != (l = p.readLine())) {
		}
		p.close();
		if(lock)
			obtainLock();
	}

	/**
	 * Obtains the lock for this file. The file must have been opened for edit
	 * prior to this method being called.
	 */
	public void obtainLock() throws Exception {
		String[] cmd = { "p4", "lock", getDepotPath() };
		String l;
		P4Process p;

		p = new P4Process(getEnv());
		p.exec(cmd);
		while(null != (l = p.readLine())) {
		}
		p.close();
	}

	/**
	 * Opens the file on the path for add under the change. If the change is
	 * null, the file is opened under the default changelist.
	 * 
	 * @param env
	 *            P4 Environment
	 * @param path
	 *            Depot or client path to the file being opened for add.
	 * @param chng
	 *            The change that the file will be opened for add in.
	 */
	public static FileEntry openForAdd(Env env, String path, Change chng) throws Exception {
		FileEntry fent = new FileEntry(env, path);
		fent.openForAdd(chng);
		return fent;
	}

	/**
	 * Opens this file for addition.
	 * 
	 * @see #openForAdd(Env, String, Change)
	 */
	public void openForAdd() throws Exception {
		openForAdd(null);
	}

	/**
	 * Opens this file for addition.
	 * 
	 * @see #openForAdd(Env, String, Change)
	 */
	public void openForAdd(Change chng) throws Exception {
		String[] cmd;
		int i = 0;
		cmd = new String[(null == chng) ? 3 : 5];
		cmd[i++] = "p4";
		cmd[i++] = "add";
		if(null != chng) {
			cmd[i++] = "-c";
			cmd[i++] = String.valueOf(chng.getNumber());
		}
		cmd[i++] = getClientPath();
		String l;
		P4Process p;

		if(null == getClientPath()) {
			throw new Exception("No Client Path");
		}

		p = new P4Process(getEnv());
		p.exec(cmd);
		while(null != (l = p.readLine())) {
		}
		p.close();
	}

	/**
	 * Checks in a file that has already been opened on the client using the
	 * description given. A new changelist is created and used for this
	 * submission. The returned <code>FileEntry</code> contains the latest
	 * information for the checked-in file.
	 */
	public static FileEntry checkIn(Env env, String path, String description) throws PerforceException {
		FileEntry fent = new FileEntry(env, path);
		Change chng = new Change(env);
		chng.setDescription(description);
		chng.addFile(fent);
		chng.submit();
		fent.sync();
		return fent;
	}

	/**
	 * Reopens the file with the new type or in the new change list.
	 */
	public void reopen(String type, Change chng) throws PerforceException {
		String[] cmd;
		int i = 0;
		String l;
		P4Process p = null;

		if(null == getClientPath()) {
			try {
				sync();
			} catch(Exception ex) { /* Ignored Exception */
			}
			if(null == getClientPath()) {
				throw new PerforceException("No Client Path");
			}
		}

		if(null == type && null == chng)
			return;
		cmd = new String[(null == type || null == chng) ? 5 : 7];
		cmd[i++] = "p4";
		cmd[i++] = "reopen";
		if(null != type) {
			cmd[i++] = "-t";
			cmd[i++] = type;
		}
		if(null != chng) {
			cmd[i++] = "-c";
			cmd[i++] = String.valueOf(chng.getNumber());
		}
		cmd[i++] = getClientPath();

		try {
			p = new P4Process(getEnv());
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if((-1 != l.indexOf("not opened on this client")) || (-1 != l.indexOf("Invalid file type"))
						|| (-1 != l.indexOf("unknown"))) {
					throw new PerforceException(l);
				}
			}
		} catch(Exception ex) {
			throw new PerforceException(ex.getMessage());
		} finally {
			if(null != p) {
				try {
					p.close();
				} catch(IOException ioex) { /* Ignored Exception */
				}
			}
		}
	}

	/**
	 * Reverts this file.
	 */
	public boolean revert() {
		String[] cmd1 = { "p4", "revert", getDepotPath() };
		String[] cmd2 = { "p4", "sync", getDepotPath() + "#none" };
		String l;
		P4Process p;

		try {
			p = new P4Process(getEnv());
			p.exec(cmd1);
			while(null != (l = p.readLine())) {
			}
			p.close();
			p = new P4Process(getEnv());
			p.exec(cmd2);
			while(null != (l = p.readLine())) {
			}
			p.close();
		} catch(IOException ex) {
			Debug.out(Debug.ERROR, ex);
			return false;
		}
		return true;
	}

	/**
	 * Returns a list of files that are open for edit or add. The list is a
	 * <code>Vectore</code> of <code>FileEntry</code> objects. The only
	 * information that is valid for the object will be the path, until the
	 * {@link #sync() sync} method is called.
	 */
	public static Vector getOpened() {
		return getOpened(null, true, false, -1, null);
	}

	/**
	 * Returns a list of files that are open for edit or add. The list is a
	 * <code>Vectore</code> of <code>FileEntry</code> objects.
	 * <p>
	 * Getting the stats for each <code>FileEntry</code> is a more expensive
	 * operation. By default, this is not done. What this means is that the only
	 * information that is valid for the object will be the path, until the
	 * {@link #sync() sync} method is called.
	 * 
	 * @param env
	 *            Source control environment to use.
	 * @param stat
	 *            Indicates that file statistics should be gathered.
	 */
	public static Vector getOpened(Env env, boolean stat) {
		return getOpened(env, stat, false, -1, null);
	}

	/**
	 * Returns a list of files that are open for edit or add. The list is a
	 * <code>Vector</code> of <code>FileEntry</code> objects.
	 * <p>
	 * Getting the stats for each <code>FileEntry</code> is a more expensive
	 * operation. By default, this is not done. What this means is that the only
	 * information that is valid for the object will be the path, until the
	 * {@link #sync() sync} method is called.
	 * <p>
	 * If changelist is 0, all the changes in the default changelist are
	 * returned. If it is less than 0, all opened files are returned.
	 * 
	 * @param env
	 *            Source control environment to use.
	 * @param stat
	 *            Indicates that file statistics should be gathered.
	 * @param all
	 *            Indicates that all open files should be returned.
	 * @param changelist
	 *            If non-zero, show files open in this changelist.
	 * @param files
	 *            If non-null, show files open in this <code>Vector</code> of
	 *            <code>FileEntry</code> objects.
	 */
	public static Vector getOpened(Env env, boolean stat, boolean all, int changelist, Vector files) {
		Vector v = new Vector();
		String l, str;
		StringTokenizer st;
		int i = 0, cnt = 2;
		String[] cmd;
		FileEntry fent;
		if(all)
			cnt++;
		if(0 <= changelist)
			cnt += 2;
		if(null != files)
			cnt += files.size();
		cmd = new String[cnt];
		cmd[i++] = "p4";
		cmd[i++] = "opened";
		if(all)
			cmd[i++] = "-a";
		if(0 <= changelist) {
			cmd[i++] = "-c";
			cmd[i++] = (0 == changelist) ? "default" : String.valueOf(changelist);
		}
		if(null != files) {
			Enumeration en = files.elements();
			while(en.hasMoreElements()) {
				cmd[i++] = (String) en.nextElement();
			}
		}
		try {
			P4Process p = new P4Process(env);
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if(!l.startsWith("//")) {
					continue;
				}
				st = new StringTokenizer(l, "#");
				if(null == (str = st.nextToken())) {
					continue;
				}
				fent = new FileEntry(env, str);
				if(null == (str = st.nextToken("# \t"))) {
					continue;
				}
				fent.setHeadRev(Integer.valueOf(str).intValue());
				st.nextToken(" \t"); // Should be the dash here.
				if(null == (str = st.nextToken())) {
					continue;
				}
				fent.setHeadAction(str);
				if(null == (str = st.nextToken())) {
					continue;
				}
				if(str.equals("default")) {
					fent.setHeadChange(-1);
					st.nextToken(); // Change here.
				} else if(str.equals("change")) {
					if(null == (str = st.nextToken())) {
						continue;
					} // Change number
					fent.setHeadChange(Integer.valueOf(str).intValue());
				}
				if(null == (str = st.nextToken(" \t()"))) {
					continue;
				}
				fent.setHeadType(str);
				// Insertion sort...slow but effective.
				for(i = 0; i < v.size(); i++) {
					if(((FileEntry) v.elementAt(i)).getHeadChange() > fent.getHeadChange())
						break;
				}
				v.insertElementAt(fent, i);
			}
			p.close();
		} catch(IOException ex) {
			Debug.out(Debug.ERROR, ex);
		}
		if(stat) {
			Enumeration en = v.elements();
			while(en.hasMoreElements()) {
				fent = (FileEntry) en.nextElement();
				fent.setEnv(env);
				fent.sync();
			}
		}
		return v;
	}

	/**
	 * No-op. This makes no sense for a FileEntry.
	 */
	public void commit() {
	}

	/**
	 * @deprecated
	 * @see #syncWorkspace(Env, String)
	 */
	public String syncMySpace(Env env, String path) throws IOException {
		return FileEntry.syncWorkspace(env, path);
	}

	/**
	 * Returns a <code>Vector</code> of <code>FileEntry</code> objects that
	 * reflect what files were changed by the sync process. If path is null, the
	 * entire workspace is synchronized to the head revision. The path may
	 * contain wildcard characters, as with the command line 'p4 sync' command.
	 * 
	 * @param env
	 *            Source control environment.
	 * @param path
	 *            Path to synchronize. May include wildcards.
	 */
	public static Vector synchronizeWorkspace(Env env, String path) throws IOException {
		String[] cmd;
		if(null == path || path.trim().equals("")) {
			cmd = new String[2];
		} else {
			cmd = new String[3];
			cmd[2] = path;
		}
		cmd[0] = "p4";
		cmd[1] = "sync";

		String l;
		int pos1, pos2;
		Vector v = new Vector();
		FileEntry fent = null;
		try {
			P4Process p = new P4Process(env);
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				fent = null;
				if(!l.startsWith("//")) {
					continue;
				}
				pos1 = 0;
				if(-1 == (pos2 = l.indexOf('#')))
					continue;
				fent = new FileEntry(env, l.substring(pos1, pos2));
				pos1 = pos2 + 1;
				if(-1 == (pos2 = l.indexOf(' ', pos1)))
					continue;
				try {
					fent.setHeadRev(Integer.parseInt(l.substring(pos1, pos2)));
				} catch(Exception ex) {
					fent = null;
					continue;
				}
				pos1 = pos2 + 1;
				if(-1 != (pos2 = l.indexOf("updating ")) || -1 != (pos2 = l.indexOf("added as "))) {
					fent.setClientPath(l.substring(pos2 + 9).trim());
				}
				if(null != fent) {
					v.addElement(fent);
					fent = null;
				}
			}
			p.close();
		} catch(IOException ex) {
			Debug.out(Debug.ERROR, ex);
			throw ex;
		}
		return v;
	}

	/**
	 * Synchronizes the workspace.
	 * 
	 * @param env
	 *            Source control environment.
	 * @param path
	 *            Path to synchronize. May include wildcards.
	 */
	public static String syncWorkspace(Env env, String path) throws IOException {
		String[] cmd;
		if(null == path || path.trim().equals("")) {
			cmd = new String[3];
			cmd[2] = path;
		} else {
			cmd = new String[2];
		}
		cmd[0] = "p4";
		cmd[1] = "sync";

		String l, str = "";
		try {
			P4Process p = new P4Process(env);
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				str += l + "\n";
			}
			p.close();
		} catch(IOException ex) {
			Debug.out(Debug.ERROR, ex);
			throw ex;
		}
		return str;
	}

	/**
	 * Returns a <code>String</code> that contains this file's contents. This
	 * only works well for text files.
	 */
	public String getFileContents() {
		return getFileContents(getEnv(), getDepotPath());
	}

	/**
	 * Returns a <code>String</code> that contains this file's contents. This
	 * only works well for text files.
	 * 
	 * @param env
	 *            Source control environment.
	 * @param path
	 *            Path to the file. Must be specific. No wildcards.
	 */
	public String getFileContents(Env env, String path) {
		String l;
		StringBuffer ret = null;
		String[] cmd = { "p4", "print", path };
		try {
			P4Process p = new P4Process(env);
			p.setRawMode(true);
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if(null == ret) {
					ret = new StringBuffer();
				} else if(l.startsWith("text: ")) {
					ret.append(l.substring(6));
					if(!l.endsWith("\n"))
						ret.append('\n');
				}
			}
			if(null == ret) {
				ret = new StringBuffer();
			}

			if(0 != p.close()) {
				throw new IOException("P4 exited with and error:" + p.getExitCode());
			}
		} catch(IOException ex) {
			Debug.out(Debug.ERROR, ex);
		}
		file_content = ret.toString();
		return file_content;
	}

	public void sync() {
		String l;
		String[] cmd = { "p4", "fstat", "path" };
		if(null != depot_path) {
			cmd[2] = depot_path;
		} else if(null != client_path) {
			cmd[2] = client_path;
		} else {
			return;
		}
		if(0 != head_rev) {
			cmd[2] += "#" + head_rev;
		}
		try {
			P4Process p = new P4Process(getEnv());
			p.exec(cmd);
			parseFstat(this, p, false);
			if(0 != p.close()) {
				throw new IOException("P4 exited with an error:" + p.getExitCode());
			}
		} catch(IOException ex) {
			Debug.out(Debug.ERROR, ex);
		}
	}

	/**
	 * Useful method for parsing that lovely fstat format information.
	 */
	private static Vector parseFstat(FileEntry fe, P4Process p, boolean igndel) {
		FileEntry nfe;
		String l;
		Vector v = new Vector();
		String dataname, datavalue;
		boolean multiple = false;

		if(null == p)
			return null;
		if(null == (nfe = fe))
			nfe = new FileEntry(p.getEnv());

		while(null != (l = p.readLine())) {
			StringTokenizer tokes = new StringTokenizer(l, " ");

			dataname = (String) (tokes.hasMoreElements() ? tokes.nextElement() : null);
			datavalue = (String) (tokes.hasMoreElements() ? tokes.nextElement() : null);
			if(dataname.equals("clientFile")) {
				nfe.setClientPath(datavalue);
			} else if(dataname.equals("depotFile")) {
				if(multiple)
					nfe = new FileEntry(p.getEnv());
				nfe.setDepotPath(datavalue);
				v.add(nfe);
				multiple = true;
			} else if(dataname.equals("headAction")) {
				nfe.setHeadAction(datavalue);
			} else if(dataname.equals("headChange")) {
				nfe.setHeadChange(new Integer(datavalue).intValue());
			} else if(dataname.equals("headRev")) {
				nfe.setHeadRev(new Integer(datavalue).intValue());
			} else if(dataname.equals("headType")) {
				nfe.setHeadType(datavalue);
			} else if(dataname.equals("headTime")) {
				nfe.setHeadTime(new Long(datavalue).longValue());
			} else if(dataname.equals("haveRev")) {
				nfe.setHaveRev(new Integer(datavalue).intValue());
			} else if(dataname.equals("action")) {

			} else if(dataname.equals("change")) {

			} else if(dataname.equals("unresolved")) {

			} else if(dataname.equals("otherOpen")) {

			} else if(dataname.equals("otherLock")) {

			} else if(dataname.equals("ourLock")) {

			}
		}
		return v;
	}

	public String toString() {
		return depot_path + "\n" + client_path + "\nothers: " + other_cnt;
	}

	public String toXML() {
		StringBuffer sb = new StringBuffer("<file><have rev=\"");
		sb.append(getHaveRev());
		sb.append("\"/><head rev=\"");
		sb.append(getHeadRev());
		sb.append("\" change=\"");
		sb.append(getHeadChange());
		sb.append("\" type=\"");
		sb.append(getHeadType());
		sb.append("\" action=\"");
		sb.append(getHeadAction());
		sb.append("\" time=\"");
		sb.append(getHeadTimeString());
		sb.append("\"/>");
		sb.append("<path type=\"depot\">");
		;
		sb.append(getDepotPath());
		sb.append("</path>");
		sb.append("<path type=\"client\">");
		;
		sb.append(getClientPath());
		sb.append("</path>");
		if(null != getDescription()) {
			sb.append("<description>");
			;
			sb.append(getDescription());
			sb.append("</description>");
		}
		sb.append("</file>");
		return sb.toString();
	}
}
