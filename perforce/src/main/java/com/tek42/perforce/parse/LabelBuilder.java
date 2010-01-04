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

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import com.tek42.perforce.PerforceException;
import com.tek42.perforce.model.Label;

/**
 * Responsible for building and saving labels.
 * 
 * @author Mike Wille
 */
public class LabelBuilder extends AbstractFormBuilder<Label> {

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.tek42.perforce.parse.AbstractFormBuilder#buildForm(java.util.Map)
	 */
	@Override
	public Label buildForm(Map<String, String> fields) throws PerforceException {
		Label label = new Label();
		label.setName(fields.get("Label"));
		label.setAccess(getField("Access", fields));
		label.setUpdate(getField("Update", fields));
		label.setDescription(getField("Description", fields));
		label.setOptions(getField("Options", fields));
		label.setOwner(getField("Owner", fields));
		label.setRevision(getField("Revision", fields));
		String views = getField("View", fields);
		for(String view : views.split("\n")) {
			label.addView(view);
		}
		return label;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.tek42.perforce.parse.Builder#getBuildCmd(java.lang.String)
	 */
	public String[] getBuildCmd(String id) {
		return new String[] { "p4", "label", "-o", id };
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.tek42.perforce.parse.Builder#getSaveCmd()
	 */
	public String[] getSaveCmd(Label obj) {
		return new String[] { "p4", "-s", "label", "-i" };
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.tek42.perforce.parse.Builder#save(java.lang.Object, java.io.Writer)
	 */
	public void save(Label label, Writer writer) throws PerforceException {
		try {
			writer.write("Label: " + label.getName() + "\n");
			writer.write("Owner: " + label.getOwner() + "\n");
			writer.write("Description:\n\t" + label.getDescription() + "\n");
			writer.write("Revision: " + label.getRevision() + "\n");
			writer.write("Options: " + label.getOptions() + "\n");
			writer.write("View:\n");
			for(String view : label.getViews()) {
				writer.write("\t" + view + "\n");
			}
			writer.write("\n");
		} catch(IOException e) {
			throw new PerforceException("Failed to save label", e);
		}
	}

}
