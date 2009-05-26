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
 * Data class for p4 jobspec field information. This class can be used to
 * contain field information for a particular field within the jobspec.
 * <p>
 * TBD: This class is not really used anywhere else. It is intended to be used
 * for more interaction with the jobs interface.
 * 
 * @author <a href="mailto:david@markley.cc">David Markley</a>
 * @version $Date: 2002/08/05 $ $Revision: #2 $
 */
public final class JobField {
	private int code = 0;

	private String name = "";

	private int data_type = 0;

	private int len = 0;

	private int field_type = 0;

	private Vector values;

	private String preset;

	private static Hashtable fields;

	private JobField[] fieldarray;

	private final static int BASECODE = 101;

	private final static int MAXCODE = 199;

	/** Data type is a single word (any value) */
	public final static int WORD = 1;

	/** Data type is a date/time field */
	public final static int DATE = 2;

	/** Data type is one of a set of words */
	public final static int SELECT = 3;

	/** Data type is a one-liner */
	public final static int LINE = 4;

	/** Data type is a block of text */
	public final static int TEXT = 5;

	/** Field type has no default, not required to be present */
	public final static int OPTIONAL = 6;

	/** Field type has default provided, still not required */
	public final static int DEFAULT = 7;

	/** Field type has default provided, value must be present */
	public final static int REQUIRED = 8;

	/** Field type has set once to the default and never changed */
	public final static int ONCE = 9;

	/** Field type has always reset to the default upon saving */
	public final static int ALWAYS = 10;

	public JobField() {
		super();
		if(null == fields) {
			fields = new Hashtable();
		}
		if(null == fieldarray) {
			fieldarray = new JobField[MAXCODE - BASECODE + 1];
		}
		values = new Vector();
	}

	public JobField(int code, String name, int dtype, int len, int ftype) {
		this();
		setDataType(dtype);
		setLength(len);
		setFieldType(ftype);
		setName(name);
		setCode(code);
	}

	public JobField(int code, String name, String dtype, int len, String ftype) {
		this();
		setDataType(dtype);
		setLength(len);
		setFieldType(ftype);
		setName(name);
		setCode(code);
	}

	private static JobField parseField(String def) {
		StringTokenizer st = new StringTokenizer(def);
		int code, len;
		String name, dtype, ftype;
		JobField jf;

		try {
			st.nextToken(); /* Skip 'info:' */
			code = Integer.valueOf(st.nextToken()).intValue();
			name = st.nextToken();
			dtype = st.nextToken();
			len = Integer.valueOf(st.nextToken()).intValue();
			ftype = st.nextToken();
			jf = new JobField(code, name, dtype, len, ftype);
		} catch(Exception ex) {
			ex.printStackTrace(System.out);
			return null;
		}
		return jf;
	}

	public static void loadFields(Env env, boolean redo) {
		if(!redo && null != fields) {
			return;
		}
		fields = new Hashtable();
		String cmd[] = { "p4", "jobspec", "-o" };
		String l;
		JobField jf = null;
		try {
			P4Process p = new P4Process(env);
			p.setRawMode(true);
			p.exec(cmd);
			while(null != (l = p.readLine())) {
				if(l.startsWith("#"))
					continue;
				if(l.startsWith("info: Fields:")) {
					while(null != (l = p.readLine()) && l.startsWith("info: \t")) {
						jf = JobField.parseField(l);
					}
				}
				if(l.startsWith("Preset:")) {
				}
				if(l.startsWith("Values:")) {
				}
			}
			p.close();
		} catch(IOException ex) {
		}
	}

	public static JobField getField(String name) {
		if(null == fields) {
			fields = new Hashtable();
		}
		return (JobField) fields.get(name);
	}

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		if(name.trim().equals(""))
			return;
		if(!this.name.trim().equals("")) {
			fields.remove(this.name);
		}
		this.name = name;
		fields.put(this.name, this);
	}

	public int getDataType() {
		return data_type;
	}

	public void setDataType(int dtype) {
		this.data_type = dtype;
	}

	public void setDataType(String dtype) {
		if(dtype.equals("word")) {
			this.data_type = JobField.WORD;
		} else if(dtype.equals("date")) {
			this.data_type = JobField.DATE;
		} else if(dtype.equals("select")) {
			this.data_type = JobField.SELECT;
		} else if(dtype.equals("line")) {
			this.data_type = JobField.LINE;
		} else {
			this.data_type = JobField.TEXT;
		}
	}

	public int getLength() {
		return len;
	}

	public void setLength(int len) {
		this.len = len;
	}

	public int getFieldType() {
		return field_type;
	}

	public void setFieldType(int ftype) {
		this.field_type = ftype;
	}

	public void setFieldType(String ftype) {
		if(ftype.equals("optional")) {
			this.field_type = JobField.OPTIONAL;
		} else if(ftype.equals("always")) {
			this.field_type = JobField.ALWAYS;
		} else if(ftype.equals("required")) {
			this.field_type = JobField.REQUIRED;
		} else if(ftype.equals("once")) {
			this.field_type = JobField.ONCE;
		} else {
			this.field_type = JobField.DEFAULT;
		}
	}

	public void setPreset(String value) {
		this.preset = value;
	}

	public String getPreset() {
		return this.preset;
	}

	public void addValue(String value) {
		values.addElement(value);
	}

	public Enumeration getValues() {
		return values.elements();
	}

	public String toXML() {
		StringBuffer sb = new StringBuffer("<field>");
		sb.append("</field>");
		return sb.toString();
	}

	/**
	 * Used for testing.
	 * 
	 * @deprecated Actually in use, but this keeps it out of the docs.
	 */
	public static void main(String[] args) {
		String propfile = "/etc/p4.conf";
		Env environ = null;
		Debug.setDebugLevel(Debug.VERBOSE);
		if(0 < args.length)
			propfile = args[0];
		try {
			environ = new Env(propfile);
		} catch(PerforceException ex) {
			System.out.println("Could not load properties from " + propfile + ": " + ex);
			System.exit(-1);
		}
		System.out.println(environ);
		loadFields(environ, true);
		Enumeration en = fields.keys();
		JobField jf;
		while(en.hasMoreElements()) {
			jf = (JobField) fields.get(en.nextElement());
			System.out.println(jf.getName() + " [" + jf.getCode() + "]: " + jf.getDataType());
		}
		Utils.cleanUp();
	}
}
