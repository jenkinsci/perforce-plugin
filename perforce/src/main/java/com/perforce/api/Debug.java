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
 * Utility class used for debugging. The level of debugging determines the
 * amount of debugging information that is generated. The process using this
 * class for debugging should ensure that it
 * {@link #setDebugLevel(int) sets the debugging level} appropriately.
 * 
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2001/11/05 $ $Revision: #1 $
 */
public final class Debug {
	/** No debug messages are displayed. */
	public final static int NONE = 0;

	/** Only error messages are displayed. */
	public final static int ERROR = 1;

	/** Error and warning messages are displayed. */
	public final static int WARNING = 2;

	/** Error, warning, and notice messages are displayed. */
	public final static int NOTICE = 3;

	/** Error, warning, notice, and verbose messages are displayed. */
	public final static int VERBOSE = 99;

	/** Debugging output is discarded entirely. */
	public final static int LOG_NONE = 0;

	/**
	 * Debugging output is sent to standard out and to the
	 * {@link EventLog EventLog}.
	 */
	public final static int LOG_SPLIT = 1;

	/** Debugging output is sent only to the {@link EventLog EventLog}. */
	public final static int LOG_ONLY = 3;

	private static int level = NONE;

	private static EventLog elog = null;

	private static int log_level = LOG_ONLY;

	private static boolean show_thread = false;

	/**
	 * Returns the name associated with the specified level.
	 * 
	 * @see #setDebugLevel(int)
	 */
	public static String getLevelName(int level) {
		if(NONE >= level) {
			return "NONE";
		} else if(ERROR >= level) {
			return "ERROR";
		} else if(WARNING >= level) {
			return "WARNING";
		} else if(NOTICE >= level) {
			return "NOTICE";
		}
		return "VERBOSE";
	}

	/**
	 * Sets the debug level for the application. If this is 0, no debug
	 * information is generated.
	 * 
	 * @see #NONE
	 * @see #ERROR
	 * @see #WARNING
	 * @see #NOTICE
	 * @see #VERBOSE
	 * @param l
	 *            The level of debugging to use.
	 */
	public static void setDebugLevel(int l) {
		level = l;
	}

	/**
	 * Returns the current debug level.
	 * 
	 * @see #setDebugLevel(int)
	 */
	public static int getDebugLevel() {
		return level;
	}

	/**
	 * If set <code>true</code>, the thread number is included in all
	 * debugging output.
	 */
	public static void setShowThread(boolean show) {
		show_thread = show;
	}

	/**
	 * Returns the state of showing threads in degugging output.
	 */
	public static boolean getShowThread() {
		return show_thread;
	}

	/**
	 * Sets the <code>EventLog</code> that debugging output should be sent to.
	 * 
	 * @param elog
	 *            <code>EventLog</code> to use.
	 */
	public static void setEventLog(EventLog elog) {
		Debug.elog = elog;
	}

	/**
	 * Returns the current <code>EventLog</code> in use.
	 */
	public static EventLog getEventLog() {
		return elog;
	}

	/**
	 * Sets the logging level. This determines where the debugging output will
	 * be sent. Valid values are "none", "only", or "split". The default is
	 * "only". The default value will be set, if the <code>String</code> does
	 * not match.
	 */
	public static void setLogLevel(String level) {
		if(level.equalsIgnoreCase("split")) {
			setLogLevel(Debug.LOG_SPLIT);
		} else if(level.equalsIgnoreCase("only")) {
			setLogLevel(Debug.LOG_ONLY);
		} else {
			setLogLevel(Debug.LOG_NONE);
		}
	}

	/**
	 * Sets the logging level from the supplied <code>Properties</code>. This
	 * looks for the "p4.log_level" property with the value of either "none",
	 * "split", or "only".
	 */
	public static void setProperties(Properties props) {
		String log = props.getProperty("p4.log_level", "none");
		if(log.equalsIgnoreCase("split")) {
			Debug.log_level = Debug.LOG_SPLIT;
		} else if(log.equalsIgnoreCase("only")) {
			Debug.log_level = Debug.LOG_ONLY;
		} else {
			Debug.log_level = Debug.LOG_NONE;
		}
	}

	/**
	 * Sets the logging level. This determines where the debugging output will
	 * be sent. Valid values are: {@link #LOG_SPLIT LOG_SPLIT},
	 * {@link #LOG_ONLY LOG_ONLY}, and {@link #LOG_NONE LOG_NONE}. The default
	 * is {@link #LOG_ONLY LOG_ONLY}.
	 * <p>
	 * If the log level is set to {@link #LOG_NONE LOG_NONE}, then the debug
	 * level is automatically set to {@link #NONE NONE}.
	 */
	public static void setLogLevel(int log_level) {
		Debug.log_level = log_level;
		if(Debug.LOG_NONE == Debug.log_level)
			Debug.level = Debug.NONE;
	}

	/**
	 * Returns the current logging level.
	 */
	public static int getLogLevel() {
		return Debug.log_level;
	}

	/**
	 * Sends the message to the <code>EventLog</code>
	 * 
	 * @see EventLog
	 */
	private static void errLog(String msg, String level) {
		if(null == elog)
			return;
		elog.log(getThreadName() + msg, level);
	}

	/**
	 * @return The current Thread Name if show_thread is true
	 */
	private static String getThreadName() {
		return show_thread ? Thread.currentThread().getName() + ": " : "";
	}

	/**
	 * Displays an error message for debugging. If the debugging level is set
	 * below ERROR, then no message is displayed.
	 * 
	 * @param msg
	 *            The debugging error message.
	 */
	public static void error(String msg) {
		if(ERROR > level)
			return;
		System.out.println(getThreadName() + "ERROR: " + msg);
		System.out.flush();
		if(LOG_SPLIT <= log_level) {
			errLog(msg, "ERROR");
		}
	}

	/**
	 * Displays a warning message for debugging. If the debugging level is set
	 * below WARNING, then no message is displayed.
	 * 
	 * @param msg
	 *            The debugging warning message.
	 */
	public static void warn(String msg) {
		if(WARNING > level)
			return;
		if(LOG_SPLIT >= log_level) {
			System.out.println(getThreadName() + "WARNING: " + msg);
			System.out.flush();
		}
		if(LOG_SPLIT <= log_level) {
			errLog(msg, "WARNING");
		}
	}

	/**
	 * Displays a notice message for debugging. If the debugging level is set
	 * below NOTICE, then no message is displayed.
	 * 
	 * @param msg
	 *            The debugging notice message.
	 */
	public static void notify(String msg) {
		if(NOTICE > level)
			return;
		if(LOG_SPLIT >= log_level) {
			System.out.println(getThreadName() + msg);
			System.out.flush();
		}
		if(LOG_SPLIT <= log_level) {
			errLog(msg, "NOTIFY");
		}
	}

	/**
	 * Displays a notice message for debugging. If the debugging level is set
	 * below NOTICE, then no message is displayed.
	 * 
	 * @param msg
	 *            The debugging notice message.
	 * @param arry
	 *            Array containing useful debug information.
	 */
	public static void notify(String msg, String[] arry) {
		if(NOTICE > level)
			return;
		StringBuffer sb = new StringBuffer();
		for(int i = 0; i < arry.length; i++) {
			sb.append(arry[i]);
			sb.append(' ');
		}
		if(LOG_SPLIT >= log_level) {
			System.out.println(getThreadName() + msg + sb);
			System.out.flush();
		}
		if(LOG_SPLIT <= log_level) {
			errLog(msg + sb, "NOTIFY");
		}
	}

	/**
	 * Displays a verbose message for debugging. If the debugging level is set
	 * below VERBOSE, then no message is displayed.
	 * 
	 * @param msg
	 *            The debugging notice message.
	 */
	public static void verbose(String msg) {
		if(VERBOSE > level)
			return;
		if(LOG_SPLIT >= log_level) {
			System.out.println(getThreadName() + msg);
			System.out.flush();
		}
		if(LOG_SPLIT <= log_level) {
			errLog(msg, "VERBOSE");
		}
	}

	/**
	 * Displays a verbose message for debugging. If the debugging level is set
	 * below VERBOSE, then no message is displayed.
	 * 
	 * @param msg
	 *            The debugging notice message.
	 * @param arry
	 *            Array containing useful debug information.
	 */
	public static void verbose(String msg, String[] arry) {
		if(VERBOSE > level)
			return;
		StringBuffer sb = new StringBuffer();
		for(int i = 0; i < arry.length; i++) {
			sb.append(arry[i]);
			sb.append(' ');
		}
		if(LOG_SPLIT >= log_level) {
			System.out.println(getThreadName() + msg + sb);
			System.out.flush();
		}
		if(LOG_SPLIT <= log_level) {
			errLog(msg + sb, "VERBOSE");
		}
	}

	/**
	 * Writes the message associated with the <code>Throwable</code> to the
	 * debugging output.
	 * 
	 * @param level
	 *            Debugging level to associate with the message.
	 * @param t
	 *            Throwable that contains the message.
	 */
	public static void out(int level, Throwable t) {
		out("{0}", level, t);
	}

	/**
	 * Writes the formatted message associated with the <code>Throwable</code>
	 * to the debugging output. The message will be placed in the string
	 * generated wherever the '{0}' attribute is placed.
	 * 
	 * @see java.text.MessageFormat
	 * @param format
	 *            Format to use for the debugging output.
	 * @param level
	 *            Debugging level to associate with the message.
	 * @param t
	 *            Throwable that contains the message.
	 */
	public static void out(String format, int level, Throwable t) {

		if(level > Debug.level)
			return;
		try {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			t.printStackTrace(pw);
			pw.close();
			Object[] args = { sw.toString() };
			String msg = MessageFormat.format(format, args);
			if(LOG_SPLIT >= log_level) {
				System.out.println(getThreadName() + msg);
				System.out.flush();
			}
			if(LOG_SPLIT <= log_level) {
				errLog(msg, Debug.getLevelName(level));
			}
		} catch(Exception ex) {
			System.err.println(t);
			System.err.flush();
		}
	}

	/**
	 * Writes the message to the debugging output.
	 * 
	 * @param level
	 *            Debugging level to associate with the message.
	 * @param msg
	 *            Debugging message.
	 */
	public static void out(int level, String msg) {
		if(level > Debug.level)
			return;
		if(LOG_SPLIT >= log_level) {
			System.out.println(getThreadName() + msg);
			System.out.flush();
		}
		if(LOG_SPLIT <= log_level) {
			errLog(msg, Debug.getLevelName(level));
		}
	}

	/**
	 * Writes the message and associated array of Strings to the debugging
	 * output. The message will be followed by all the elements in the
	 * <code>arry</code>.
	 * 
	 * @param level
	 *            Debugging level to associate with the message.
	 * @param msg
	 *            Debugging message.
	 * @param arry
	 *            Array of strings to be sent to the debugging output.
	 */
	public static void out(int level, String msg, String[] arry) {
		if(level > Debug.level)
			return;
		StringBuffer sb = new StringBuffer();
		for(int i = 0; i < arry.length; i++) {
			sb.append(arry[i]);
			sb.append(' ');
		}
		if(LOG_SPLIT >= log_level) {
			System.out.println(getThreadName() + msg + sb);
			System.out.flush();
		}
		if(LOG_SPLIT <= log_level) {
			errLog(msg + sb, Debug.getLevelName(level));
		}
	}
}
