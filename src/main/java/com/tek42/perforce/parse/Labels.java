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

import java.util.ArrayList;
import java.util.List;

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;
import com.tek42.perforce.model.Label;

/**
 * Base API object for interacting with labels.
 * 
 * @author Mike Wille
 */
public class Labels extends AbstractPerforceTemplate {

	public Labels(Depot depot) {
		super(depot);
	}

	/**
	 * Handles both creating and saving labels.
	 * 
	 * @param label
	 * @return
	 */
	public void saveLabel(Label label) throws PerforceException {
		LabelBuilder builder = new LabelBuilder();
		saveToPerforce(label, builder);
	}

	/**
	 * Returns a list of labels in the system. Optionally, you can specify a path argument to return only labels that
	 * contain the specified path.
	 * 
	 * @param path
	 * @return
	 * @throws PerforceException
	 */
	public List<Label> getLabels(String path) throws PerforceException {
		String cmd[];

		if(path != null && !path.equals(""))
			cmd = new String[] { "p4", "labels", path };
		else
			cmd = new String[] { "p4", "labels" };

		List<Label> labels = new ArrayList<Label>();

		StringBuilder response = getPerforceResponse(cmd);
		List<String> names = parseList(response, 1);

		for(String name : names) {
			labels.add(getLabel(name));
		}

		return labels;
	}

	/**
	 * Returns a label specified by name.
	 * 
	 * @param name
	 * @return
	 * @throws PerforceException
	 */
	public Label getLabel(String name) throws PerforceException {
		LabelBuilder builder = new LabelBuilder();
		Label label = builder.build(getPerforceResponse(builder.getBuildCmd(name)));
		return label;
	}
}
