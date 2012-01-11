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

import com.tek42.perforce.Depot;
import com.tek42.perforce.PerforceException;
import com.tek42.perforce.model.Workspace;
import java.util.ArrayList;

/**
 * Base API object for interacting with workspaces.
 * 
 * @author Mike Wille
 */
public class Workspaces extends AbstractPerforceTemplate {
	public Workspaces(Depot depot) {
		super(depot);
	}

	/**
	 * Returns a workspace specified by name. Stream name is required if a stream workspace is created, 
	 * can be empty otherwise.
	 * 
	 * @param ws_name Workspace name
	 * @param stream_name Stream name
	 * @return
	 * @throws PerforceException
	 */
	public Workspace getWorkspace(String ws_name, String stream_name) throws PerforceException {
		WorkspaceBuilder builder = new WorkspaceBuilder();
		Workspace workspace;
		if (stream_name != null && !stream_name.equals("")) {
		    workspace = builder.build(getPerforceResponse(builder.getBuildCmd(getP4Exe(), ws_name, stream_name)));
		}
		else {
		    workspace = builder.build(getPerforceResponse(builder.getBuildCmd(getP4Exe(), ws_name)));
		}
		if(workspace == null)
			throw new PerforceException("Failed to retrieve workspace: " + ws_name);

		return workspace;
	}

	/**
	 * Saves changes to an existing workspace, or creates a new one.
	 * 
	 * @param workspace
	 * @throws PerforceException
	 */
	public void saveWorkspace(Workspace workspace) throws PerforceException {
		WorkspaceBuilder builder = new WorkspaceBuilder();
		saveToPerforce(workspace, builder);
	}

	/**
	 * Synchronizes to the latest change for the specified path.  Convenience function
	 * for {@see syncTo(String, boolean)}
	 * 
	 * @param path
	 * @return
	 * @throws PerforceException
	 */
	public StringBuilder syncToHead(String path) throws PerforceException {
		return syncToHead(path, false);
	}

	/**
	 * Synchronizes to the latest change for the specified path. Allows a force sync to be performed by passing true to
	 * forceSync parameter.
	 * 
	 * @param path
	 *            The depot path to sync to
	 * @param forceSync
	 *            True to force sync and overwrite local files
	 * @return StringBuilder containing output of p4 response.
	 * @throws PerforceException
	 */
	public StringBuilder syncToHead(String path, boolean forceSync) throws PerforceException {
		if(!path.endsWith("#head")) {
			path += "#head";
		}
		return syncTo(path, forceSync, false);
	}
	
	/**
	 * Provides method to sync to a depot path and allows for any revision, changelist, label, etc.
	 * to be appended to the path.
	 * <p>
	 * A force sync can be specified by passing true to forceSync.
	 * 
	 * @param path
	 * 				The depot path to sync to.  Perforce suffix for [revRange] is allowed.
	 * @param forceSync
	 * 				Should we force a sync to grab all files regardless of version on disk?
	 * @return
	 * 			A StringBuilder that contains the output of the p4 execution.
	 * @throws PerforceException
	 */
	public StringBuilder syncTo(String path, boolean forceSync, boolean populateOnly) throws PerforceException {
                //Error handling and output filtering
                final StringBuilder errors = new StringBuilder();
                ResponseFilter filter = new ResponseFilter(){
                    private int count=0;
                    @Override
                    public boolean accept(String line) {
                        count++;
                        if(line.contains("Request too large")){
                            return true;
                        }
                        //detect errors during syncing
                        //ignore lines containing "files(s) up-to-date", because
                        //perforce classifies that as an 'error' for some strange reason
                        if(line.startsWith("error:") && !line.contains("file(s) up-to-date.")){
                            errors.append(line);
                            errors.append("\n");
                        }
                        //return at most 50 lines. Throw away the rest so we don't run out of memory
                        if(count<50){
                            return true;
                        }
                        return false;
                    }
                };
                //remove all quotes from the path, because perforce doesn't like extra ones very much.
                path = path.replaceAll("\"", "");
                ArrayList<String> cmdLineList = new ArrayList<String>();
                cmdLineList.add(getP4Exe());
                cmdLineList.add("-s");
                cmdLineList.add("sync");
                if(forceSync)
                    cmdLineList.add("-f");
                if(populateOnly)
                    cmdLineList.add("-p");
                cmdLineList.add(path);
                String[] cmdLine = cmdLineList.toArray(new String[cmdLineList.size()]);
		
                StringBuilder response = getPerforceResponse(cmdLine, filter);
                if(hitMax(response)){
                    throw new PerforceException("Hit perforce server limit while " + (forceSync?"force ":"") + "syncing: \n" + response);
                }
                if(errors.length()>0){
                    throw new PerforceException("Errors encountered while " + (forceSync?"force ":"") + "syncing: " + errors.toString());
                }
                return response;
	}

        public StringBuilder flushTo(String path) throws PerforceException {
            StringBuilder response = getPerforceResponse(new String[] { getP4Exe(), "sync", "-k", path });
            if(hitMax(response)){
                throw new PerforceException("Hit perforce server limit while flushing client: " + response);
            }
            return response;
        }

	/**
     * Test whether there are any changes pending for the current client (P4CLIENT env var).
     * 
     * @return
     *          A StringBuilder that contains the output of the p4 execution.
     * @throws PerforceException
     */
    public StringBuilder syncDryRun() throws PerforceException {
        StringBuilder result = getPerforceResponse(new String[] { getP4Exe(), "sync", "-n" });
        return result;
    }

}
