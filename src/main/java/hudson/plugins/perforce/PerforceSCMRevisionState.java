/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package hudson.plugins.perforce;

import hudson.scm.SCMRevisionState;

/**
 *
 * @author rpetti
 */
public class PerforceSCMRevisionState extends SCMRevisionState {

    private final int revision;

    public PerforceSCMRevisionState(int revision){
        this.revision = revision;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof PerforceSCMRevisionState){
            PerforceSCMRevisionState comp = (PerforceSCMRevisionState) obj;
            return comp.revision == this.revision;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + this.revision;
        return hash;
    }

    public int getRevision() {
        return revision;
    }

}
