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
package com.tek42.perforce.parse;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tek42.perforce.PerforceException;

/**
 * Abstract class that parses the stringbuilder into key/value pairs and then sends them to a abstract method
 * responsible for building the object. If you extend this class, you do NOT override build(StringBuilder) but
 * buildForm(Map).
 * <p>
 * Useful for all perforce objects that are editable via forms. i.e., User, Workspace, Jobspec, etc.
 * 
 * @author Mike Wille
 */
public abstract class AbstractFormBuilder<T> implements Builder<T> {
	private final Logger logger = LoggerFactory.getLogger("perforce");

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.tek42.perforce.parse.Builder#build(java.lang.StringBuilder)
	 */
	public T build(StringBuilder sb) throws PerforceException {
		// Allow our regexp to match with only one case and not have to handle the case for the last line
		sb.append("Endp:\n");
		logger.debug("Parsing: \n" + sb);
		Pattern p = Pattern.compile("^(\\w+):(.*?)(?=\\n\\w{4,}?:)", Pattern.DOTALL | Pattern.MULTILINE);
		Matcher m = p.matcher(sb.toString());
		Map<String, String> fields = new HashMap<String, String>();
		logger.debug("Parsing response...");
		while(m.find()) {
			String key = m.group(1);
			String value = m.group(2).trim();
			fields.put(key, value);
			logger.debug("Have key: " + key + " = " + value);
		}
		return buildForm(fields);
	}

	/**
	 * Test for null and returns an empty string if the key is not present. Otherwise, returns the value.
	 * 
	 * @param key
	 * @param fields
	 * @return
	 */
	protected String getField(String key, Map<String, String> fields) {
		String value = fields.get(key);
		if(value == null)
			return "";

		return value;
	}

	/**
	 * Like getField(String, Map) except that it assumes the value of the field is a String containing a
	 * delimited list of values.  It parses the string into a string list.  Assumes new line as separator.
	 * @param key	The name of the field.
	 * @param fields	The map of field/value pairs.
	 * @return	A List of strings.
	 */
	protected List<String> getFieldAsList(String key, Map<String, String> fields) {
		String value = fields.get(key);
		if(value == null || value.equals("") || value.equals("\n"))
			return new ArrayList<String>();

		String values[] = value.split("\\n");

		List<String> list = new ArrayList<String>(values.length);
		list.addAll(Arrays.asList(values));
		return list;
	}

	/**
	 * Default implementation for most Perforce operations is to use stdin.  This will return true.
	 * @return	True always
	 */
	public boolean requiresStandardInput() {
		return true;
	}

	/**
	 * Should return a new object set with the data from fields.
	 * 
	 * @param fields
	 * @return
	 * @throws PerforceException
	 */
	public abstract T buildForm(Map<String, String> fields) throws PerforceException;
}
