/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package hudson.plugins.perforce;

import hudson.Extension;
import hudson.Util;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author rpetti
 */
public class PerforceUserProperty extends UserProperty {

    private String perforceId;
    private String perforceEmail;

    public PerforceUserProperty(){
        super();
    }

    @DataBoundConstructor
    public PerforceUserProperty(String perforceId){
        super();
        this.perforceId = Util.fixEmptyAndTrim(perforceId);
    }

    public String getPerforceId() {
        return perforceId;
    }

    public void setPerforceId(String perforceId) {
        this.perforceId = perforceId;
    }

    @Extension
    public static class DescriptorImpl extends UserPropertyDescriptor{

        @Override
        public UserProperty newInstance(User user) {
            return new PerforceUserProperty();
        }

        @Override
        public String getDisplayName() {
            return "Perforce";
        }
        
    }

    public String getPerforceEmail() {
        return perforceEmail;
    }

    public void setPerforceEmail(String perforceEmail) {
        this.perforceEmail = perforceEmail;
    }

}
