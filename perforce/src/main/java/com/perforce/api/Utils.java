package com.perforce.api;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;

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
 * Class that contains static utility methods.
 * 
 * @see HashDecay
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2002/08/12 $ $Revision: #6 $
 */
public final class Utils {

	/**
	 * Initializes the package, in order to avoid some arbitrary JVM problems
	 * that have been encountered. This is a hack and should not have to be done
	 * if certain overbearing empires starting with the letter M built a fully
	 * compliant JVM.
	 */
	public static void initPackage() {
		(new Branch()).getCache();
		(new Change()).getCache();
		(new Client()).getCache();
		(new DirEntry()).getCache();
		(new FileEntry()).getCache();
		(new Job()).getCache();
		(new Label()).getCache();
		(new User()).getCache();
		Properties props = System.getProperties();
	}

	/**
	 * Check to see if the current Java Virtual Machine is made by Microsoft
	 * 
	 * @return boolean true if the java.vendor property comtains the word
	 *         'Microsoft'
	 */
	public static boolean isMSJVM() {
		return (System.getProperty("java.vendor").indexOf("Microsoft") != -1);
	}

	/**
	 * Check to see if the current operating system is a Windows OS
	 * 
	 * @return boolean true if the os.name property comtains the word 'Windows'
	 */
	public static boolean isWindows() {
		return (System.getProperty("os.name").indexOf("Windows") != -1);
	}

	/**
	 * Returns true if the <code>path</code> matches the <code>wildpath</code>.
	 * Only perforce wildcards are considered in the <code>wildpath</code>.
	 */
	public static boolean wildPathMatch(String wildpath, String path) {
		// System.out.println("Matching: "+wildpath+" to "+path);
		wildpath = wildpath.trim();
		path = path.trim();
		boolean match = true;
		boolean in_dots = false;
		int i, j, plen = path.length(), wplen = wildpath.length();
		char wc, pc;
		for(j = 0, i = 0; i < wplen && j < plen; i++) {
			if('%' == (wc = wildpath.charAt(i))) {
				wc = wildpath.charAt(++i);
				if('0' > wc || '9' < wc) {
					match = false;
					break;
				}
				while('/' != path.charAt(j) && j < plen) {
					j++;
				}
				continue;
			} else if('*' == wc) {
				while('/' != path.charAt(j) && j < plen) {
					j++;
				}
				continue;
			}
			if('.' == wc && wildpath.regionMatches(i, "...", 0, 3)) {
				i += 2;
				in_dots = true;
				continue;
			}
			if(path.charAt(j++) != wc) {
				if(!in_dots) {
					match = false;
					break;
				} else {
					i--;
				}
			} else if(in_dots) {
				String wpath2 = wildpath.substring(i);
				String path2 = path.substring(j - 1);
				if(wildPathMatch(wpath2, path2)) {
					return true;
				} else {
					i--;
				}
			}
		}
		if(j < plen)
			return in_dots;
		if(i < wplen)
			return false;
		return match;
	}

	/**
	 * Returns the string encoded for HTML use.
	 * <p> > becomes &gt; and < becomes &lt;
	 */
	public static String HTMLEncode(String str) {
		if(null == str)
			return "null";
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

	/**
	 * Returns common prefix for a Vector of strings. This is very useful for
	 * determining a commong prefix for a set of paths.
	 */
	public static String commonPrefix(Vector v) {
		return commonPrefix(v.elements());
	}

	/**
	 * Returns common prefix for an Enumeration of strings.
	 */
	public static String commonPrefix(Enumeration en) {
		if(null == en || !en.hasMoreElements())
			return "";
		String common = (String) en.nextElement();
		String str = null;
		int i, len;
		char[] ar1, ar2;

		ar1 = common.toCharArray();
		while(en.hasMoreElements()) {
			str = (String) en.nextElement();
			ar2 = str.toCharArray();
			if(str.startsWith(common))
				continue;
			len = common.length();
			if(len > str.length())
				len = str.length();
			for(i = 0; i < len; i++) {
				if(ar1[i] != ar2[i])
					break;
			}
			if(0 == i)
				return "";
			common = common.substring(0, i);
			ar1 = common.toCharArray();
		}
		if(-1 != (i = common.indexOf('#')))
			common = common.substring(0, i);
		if(-1 != (i = common.indexOf('@')))
			common = common.substring(0, i);
		return common;
	}

	/**
	 * Returns the change number portion of a depot path, if there is a valid
	 * one found. Otherwise, it returns -1.
	 */
	public final static int getChangeFromPath(String path) {
		int i = path.indexOf('@');
		if(0 > i)
			return -1;
		try {
			return Integer.valueOf(path.substring(i + 1)).intValue();
		} catch(NumberFormatException ex) {
			return -1;
		}
	}

	/**
	 * Cleans up after the package has been used. This stops any running threads
	 * and releases any objects for garbage collection.
	 */
	public static void cleanUp() {
		HashDecay.stopAll();
		System.gc();
	}

	/**
	 * Breaks up a depot path and formats each level. Each format string takes
	 * two arguments. The first is set to the full path to a particular element.
	 * The second is set to the short name for the element.
	 * <p>
	 * This is extremely useful for setting up links from each component of a
	 * path.
	 * 
	 * @param path
	 *            The path to be formatted.
	 * @param pathfmt
	 *            The format to be used for path elements.
	 * @param filefmt
	 *            The format to be used for the file element.
	 * @param revfmt
	 *            The format to be used for the rev component.
	 * @param urlencode
	 *            Determines if paths are encoded.
	 * @see URLEncoder
	 */
	public static StringBuffer formatDepotPath(String path, String pathfmt, String filefmt, String revfmt,
			boolean urlencode) throws PerforceException {
		StringBuffer sb = new StringBuffer("//");
		Object[] args = { "path", "part" };
		int p1 = 1, p2 = 0;

		if(null == path || (!path.startsWith("//"))) {
			throw new PerforceException(path + " is not a depot path.");
		}

		// Don't bother parsing anything if all the formats are null.
		if(null == pathfmt && null == filefmt && null == revfmt) {
			return new StringBuffer(path);
		}

		if(null == pathfmt) {
			p1 = path.lastIndexOf("/");
			sb.append(path.substring(2, p1 + 1));
		} else {
			while(-1 != (p2 = path.indexOf("/", p1 + 1))) {
				args[0] = path.substring(0, p2);
				if(urlencode)
					args[0] = URLEncoder.encode((String) args[0]);
				args[1] = path.substring(p1 + 1, p2);
				sb.append(MessageFormat.format(pathfmt, args));
				sb.append('/');
				p1 = p2;
			}
		}

		String rev = null;
		if(-1 == (p2 = path.indexOf("#", p1 + 1))) {
			p2 = path.length();
		} else {
			rev = path.substring(p2 + 1);
		}
		args[0] = path.substring(0, p2);
		if(urlencode)
			args[0] = URLEncoder.encode((String) args[0]);
		String fname = path.substring(p1 + 1, p2);
		args[1] = fname;
		if(null == filefmt) {
			sb.append(args[1]);
		} else {
			sb.append(MessageFormat.format(filefmt, args));
		}

		if(null != rev) {
			sb.append('#');
			args[0] = path;
			if(urlencode)
				args[0] = URLEncoder.encode((String) args[0]);
			args[1] = rev;
			if(null == revfmt) {
				sb.append(args[1]);
			} else {
				sb.append(MessageFormat.format(revfmt, args));
			}
		}
		return sb;
	}

	public static Enumeration getEnumeration(Iterator i) {
		Vector v = new Vector();
		while(i.hasNext())
			v.addElement(i.next());
		return v.elements();
	}

	/**
	 * @deprecated Useful for testing, but should not be documented.
	 */
	public static void main(String[] argv) {
		Vector v = new Vector(argv.length);
		for(int i = 0; i < argv.length; i++) {
			v.addElement(argv[i]);
			System.out.println(argv[i] + ": " + getChangeFromPath(argv[i]));
		}
		System.out.println("Common: " + commonPrefix(v));
	}
}
