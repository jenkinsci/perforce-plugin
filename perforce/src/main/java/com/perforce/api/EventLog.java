package com.perforce.api;

import java.io.*;
import java.util.*;
import java.net.*;
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
 * This class controls an event log. A set number of log entries are kept in
 * memory, but all log entries are sent to a log file.
 * 
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2001/11/05 $ $Revision: #1 $
 */
public class EventLog {
	private static EventLog primary = null;

	private String title = "EventLog";

	private String filename = "log.txt";

	private PrintWriter fout = null;

	private Vector events;

	private int max_events = 100;

	private DateFormat fmt;

	/**
	 * Default, no-argument constructor. This initialized the Vector of events
	 * and the date and time format.
	 * 
	 * @see Vector
	 */
	public EventLog() {
		this(null, null);
	}

	/**
	 * Constructor that accepts the title for this. The title is used when the
	 * webSend() method is invoked.
	 * 
	 * @param title
	 *            The title for this log.
	 */
	public EventLog(String title) {
		this(title, null);
	}

	/**
	 * Constructor that sets the log title and ouput filename.
	 * 
	 * @param title
	 *            The title for this log.
	 * @param filename
	 *            Name for the output file.
	 */
	public EventLog(String title, String filename) {
		super();

		this.title = (null == title) ? "EventLog" : title;
		this.filename = (null == filename) ? "log.txt" : filename;
		events = new Vector();
		fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
		if(null == primary) {
			primary = this;
		}
		try {
			fout = new PrintWriter(new FileOutputStream(filename, true));
		} catch(Exception ex) {
			fout = null;
		}
	}

	/**
	 * Returns the output file name.
	 */
	public String getFileName() {
		return filename;
	}

	/**
	 * Returns the size of the event log memory. This is the maximum number of
	 * lines that will be maintained in memory.
	 */
	public int getSize() {
		return max_events;
	}

	/**
	 * Sets the size of the log. This is the maximum number of lines that the
	 * log will maintain in memory.
	 * 
	 * @param size
	 *            The number of lines to keep in the log.
	 */
	public synchronized void setSize(int size) {
		max_events = size;
	}

	/**
	 * Logs an event to the common log. The event passed in is added to the log.
	 * If the log is beyond its maximum size, then the oldest events are
	 * dropped.
	 * 
	 * @param event
	 *            The event to be logged.
	 * @param level
	 *            Level for the logged event.
	 * @param encode
	 *            Indicates that the output should be XML/HTML encoded.
	 */
	public static void commonLog(String event, String level, boolean encode) {
		if(null != primary) {
			primary.log(event, level, encode);
		} else {
			System.out.println(event);
		}
	}

	/**
	 * Logs an event to the common log. The event passed in is added to the log.
	 * If the log is beyond its maximum size, then the oldest events are
	 * dropped.
	 * 
	 * @param event
	 *            The event to be logged.
	 * @param level
	 *            Level for the logged event.
	 */
	public static void commonLog(String event, String level) {
		if(null != primary) {
			primary.log(event, level);
		} else {
			System.out.println(event);
		}
	}

	/**
	 * Logs an event to the common log. The event passed in is added to the log.
	 * If the log is beyond its maximum size, then the oldest events are
	 * dropped.
	 * 
	 * @param event
	 *            The event to be logged.
	 */
	public static void commonLog(String event) {
		if(null != primary) {
			primary.log(event);
		} else {
			System.out.println(event);
		}
	}

	/**
	 * Logs an event to this log. The event passed in is added to the log. If
	 * the log is beyond its maximum size, then the oldest events are dropped.
	 * 
	 * @param event
	 *            The event to be logged.
	 */
	public synchronized void log(String event) {
		log(event, null, true);
	}

	/**
	 * Logs an event to this log. The event passed in is added to the log. If
	 * the log is beyond its maximum size, then the oldest events are dropped.
	 * 
	 * @param event
	 *            The event to be logged.
	 * @param level
	 *            Level for the logged event.
	 */
	public synchronized void log(String event, String level) {
		log(event, level, true);
	}

	/**
	 * Logs an event to this log. The event passed in is added to the log. If
	 * the log is beyond its maximum size, then the oldest events are dropped.
	 * 
	 * @param event
	 *            The event to be logged.
	 * @param level
	 *            Level for the logged event.
	 * @param encode
	 *            Indicates that the output should be XML/HTML encoded.
	 */
	public synchronized void log(String event, String level, boolean encode) {
		StringBuffer sb = new StringBuffer("<log level=\"");
		if(null == level) {
			if(-1 == event.indexOf("ERROR")) {
				sb.append("NOMINAL");
			} else {
				sb.append("ERROR");
			}
		} else {
			sb.append(level);
		}
		sb.append("\" date=\"");
		sb.append(fmt.format(new Date()));
		sb.append("\">");
		if(encode) {
			sb.append(Utils.HTMLEncode(event));
		} else {
			sb.append(event);
		}
		sb.append("</log>");
		String msg = sb.toString();
		events.insertElementAt(msg, 0);
		if(null != fout) {
			fout.println(msg);
			fout.flush();
		}
		if(max_events < events.size()) {
			events.removeElementAt(max_events);
		}
	}

	/**
	 * Returns an Enumeration that contains all the events in the log.
	 * 
	 * @return List of Strings in the log.
	 */
	public synchronized Enumeration getLog() {
		return events.elements();
	}

	/**
	 * Prints this log, using the specified format. Each line is placed in the
	 * format string where {0} occurs.
	 * 
	 * @param out
	 *            Print output.
	 * @param format
	 *            Format to use.
	 * @see java.text.MessageFormat
	 */
	public static void printLog(PrintWriter out, String format) {
		printLog(primary, out, format);
	}

	/**
	 * Prints a log, using the specified format. Each line is placed in the
	 * format string where {0} occurs.
	 * 
	 * @param elog
	 *            <code>EventLog</code> to print.
	 * @param out
	 *            Print output.
	 * @param format
	 *            Format to use.
	 * @see java.text.MessageFormat
	 */
	public static void printLog(EventLog elog, PrintWriter out, String format) {
		Object[] args = { "foo" };
		synchronized(elog) {
			Enumeration en = elog.events.elements();
			while(en.hasMoreElements()) {
				args[0] = ((String) en.nextElement());
				out.println(MessageFormat.format(format, args));
			}
		}
	}

	/**
	 * Sets the title for this log.
	 * 
	 * @param title
	 *            The title of the log.
	 */
	public synchronized void setTitle(String title) {
		this.title = title;
	}

	/**
	 * Gets the title of the log.
	 */
	public synchronized String getTitle() {
		return title;
	}
}
