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

import java.util.List;
import java.util.ArrayList;

/**
 * Provide base support for views.
 * 
 * @author Mike Wille
 */
public abstract class AbstractViewsSupport implements java.io.Serializable {
	protected List<String> views;
	protected boolean dirty = false;

	public AbstractViewsSupport() {
		views = new ArrayList<String>();
	}

	/**
	 * Has this object's fields been changed since it was created,
	 * or since the last call to clearDirty()?
	 * 
	 * @return true if dirty
	 */
    public boolean isDirty() {
        return dirty;
    }
    
    public final void markDirty() {
        dirty = true;
    }
    
    public final void clearDirty() {
        dirty = false;
    }
    
	/**
	 * @return the view
	 */
	public List<String> getViews() {
		return views;
	}

	/**
	 * Returns the list of views concatenated together with \n as delimeters.
	 * 
	 * @return
	 */
	public String getViewsAsString() {
		StringBuilder sb = new StringBuilder();
		for(String view : views) {
			sb.append(view + "\n");
		}
		return sb.toString();
	}

	/**
	 * @param view
	 *            the view to set
	 */
	public void addView(String view) {
		views.add(view);
		markDirty();
	}

	/**
	 * Removes all views from this client.
	 */
	public void clearViews() {
		views.clear();
        markDirty();
	}
}
