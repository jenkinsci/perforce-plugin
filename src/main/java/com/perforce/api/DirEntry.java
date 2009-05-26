package com.perforce.api;

import java.io.*;
import java.util.*;

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
 * Representation of a source control directory.
 * 
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2001/11/05 $ $Revision: #1 $
 */
public final class DirEntry extends SourceControlObject {
	private String path;

	private DirEntry parent;

	private boolean opened = false;

	private Vector subdirs = null;

	private Vector files = null;

	private static HashDecay dirs = null;

	/** Default, no-argument constructor. */
	public DirEntry() {
		super();
		subdirs = new Vector();
		files = new Vector();
		setCache();
	}

	/**
	 * Constructs a directory entry.
	 * 
	 * @param e
	 *            Source control environment to use.
	 */
	public DirEntry(Env e) {
		this();
		setEnv(e);
	}

	/**
	 * Constructs a directory entry.
	 * 
	 * @param e
	 *            Source control environment to use.
	 * @param path
	 *            The path for this directory.
	 */
	public DirEntry(Env e, String path) {
		this(e);
		setPath(path);
	}

	/**
	 * Constructs a directory entry.
	 * 
	 * @param base
	 *            Another <code>DirEntry</code> used to set the environment.
	 * @param path
	 *            The path for this directory.
	 */
	public DirEntry(DirEntry base, String path) {
		this();
		setEnv(base.getEnv());
		setPath(path);
	}

	/**
	 * Loads the directories and files for this directory.
	 */
	public void sync() {
		try {
			loadDirs(getEnv());
			loadFiles(getEnv(), path);
			inSync();
		} catch(Exception ex) {
		}
	}

	/**
	 * Does nothing. Doesn't do anything here, since directories are not really
	 * stored in perforce.
	 */
	public void commit() {
	}

	private static HashDecay setCache() {
		if(null == dirs) {
			dirs = new HashDecay(120000);
			dirs.start();
		}
		return dirs;
	}

	public HashDecay getCache() {
		return setCache();
	}

	/**
	 * Returns a directory entry for the supplied path.
	 * 
	 * @param env
	 *            Source control environment to use.
	 * @param path
	 *            The path for this directory.
	 * @param sync
	 *            Forces the directory information to be current.
	 */
	public static DirEntry getDirEntry(Env env, String path, boolean sync) {
		DirEntry de;
		if(null == path || path.trim().equals(""))
			return null;
		if(null == (de = (DirEntry) setCache().get(path))) {
			de = new DirEntry(env, path);
		}
		if(null != env)
			de.setEnv(env);
		if(sync)
			de.sync();
		dirs.put(path, de);
		return de;
	}

	/**
	 * Sets the path for this directory.
	 * 
	 * @param path
	 *            New path for this directory.
	 */
	public void setPath(String path) {
		if(null == path)
			return;
		path = path.trim();
		if(path.equals("") || !path.startsWith("//"))
			return;
		// TODO: Add checks for wildcards here!!!
		/*
		 * if (null != alldirs) { synchronized (alldirs) { if (this ==
		 * (DirEntry)(alldirs.get(this.path))) { alldirs.remove(this.path); }
		 * alldirs.put(path, this); } }
		 */
		this.path = path;
	}

	/**
	 * Returns the path for this directory.
	 */
	public String getPath() {
		return this.path;
	}

	/**
	 * Returns the base path for this directory. This includes everything up to
	 * the last path delimeter.
	 */
	public String getBasePath() {
		int pos = path.lastIndexOf('/');
		if(-1 == pos) {
			return "//";
		}
		return path.substring(0, pos + 1);
	}

	/**
	 * Returns the parent director. Constructs a new <code>DirEntry</code>
	 * that represents the parent and returns it.
	 */
	public DirEntry getParent() {
		DirEntry parent = null;
		int pos;

		if(-1 == (pos = path.lastIndexOf('/')))
			return null;
		String parent_path = path.substring(0, pos);
		if(parent_path.equals("/"))
			return null;
		/*
		 * if (null != alldirs) { synchronized (alldirs) { parent =
		 * (DirEntry)alldirs.get(parent_path); } }
		 */
		if(null == parent) {
			parent = new DirEntry(this, parent_path);
		}
		return parent;
	}

	/**
	 * Returns an array of directory names.
	 */
	public String[] getDirNames() {
		return getDirNames(getEnv());
	}

	/**
	 * Returns an array of directory names.
	 * 
	 * @param env
	 *            Source control environment to use.
	 */
	public String[] getDirNames(Env env) {
		String[] names;
		loadDirs(env);
		synchronized(subdirs) {
			names = v2a(subdirs);
		}
		return names;
	}

	/**
	 * Loads the directories, using the default environment.
	 */
	private void loadDirs() {
		loadDirs(getEnv());
	}

	/**
	 * Loads the directories, using the specified environment.
	 * 
	 * @param env
	 *            Source control environment to use.
	 */
	private void loadDirs(Env env) {
		if(!outOfSync(60000))
			return;
		String[] cmd = { "p4", "dirs", path + "%1" };
		String l, dir;
		int pos;

		synchronized(subdirs) {
			subdirs.removeAllElements();
		}

		try {
			P4Process p = new P4Process(env);
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if((!l.startsWith("//")) || (-1 != l.indexOf(" - "))) {
					continue;
				}
				dir = l.trim();
				if(-1 != (pos = dir.lastIndexOf('/'))) {
					dir = dir.substring(pos + 1).trim();
				}
				synchronized(subdirs) {
					subdirs.addElement(dir);
				}
			}
			p.close();
		} catch(IOException ex) {
			Debug.out(Debug.ERROR, ex);
		}
	}

	/**
	 * Converts a <code>Vector</code> to a <code>String</code>. This shows
	 * how old this code is. <code>Vector</code> didn't always do this for us.
	 */
	private String[] v2a(Vector v) {
		String[] tmp = new String[v.size()];
		for(int i = 0; i < v.size(); i++) {
			tmp[i] = (String) v.elementAt(i);
		}
		return tmp;
	}

	/**
	 * Returns an array of file entries for this directory.
	 */
	public FileEntry[] getFiles() {
		return getFiles(getEnv());
	}

	/**
	 * Returns an array of file entries for this directory.
	 * 
	 * @param env
	 *            Source control environment to use.
	 */
	public FileEntry[] getFiles(Env env) {
		loadFiles(env, path);
		if(null == files)
			return null;
		FileEntry[] tmp;
		synchronized(files) {
			tmp = new FileEntry[files.size()];
			for(int i = 0; i < files.size(); i++) {
				tmp[i] = (FileEntry) files.elementAt(i);
				tmp[i].setEnv(env);
			}
		}
		return tmp;
	}

	/**
	 * Returns an array of file names for this directory.
	 */
	public String[] getFileNames() {
		return getFileNames(getEnv());
	}

	/**
	 * Returns an array of file names for this directory.
	 * 
	 * @param env
	 *            Source control environment to use.
	 */
	public String[] getFileNames(Env env) {
		String[] names;
		loadFiles(env, path);
		if(null == files) {
			names = new String[1];
			names[0] = "";
			return names;
		}
		synchronized(files) {
			names = new String[files.size()];
			for(int i = 0; i < files.size(); i++) {
				names[i] = ((FileEntry) files.elementAt(i)).getName();
			}
		}
		return names;
	}

	/**
	 * Loads the files in this directory.
	 */
	private void loadFiles() {
		loadFiles(getEnv(), path);
	}

	/**
	 * Loads the files in this directory.
	 * 
	 * @param env
	 *            Source control environment to use.
	 * @param path
	 *            Directory path to use, instead of this one.
	 */
	private void loadFiles(Env env, String path) {
		if(!outOfSync(60000))
			return;
		files = FileEntry.getFiles(env, path);
	}

	public String toXML() {
		StringBuffer sb = new StringBuffer("<dir path=\"");
		sb.append(getPath());
		sb.append("\"/>");
		return sb.toString();
	}
}
