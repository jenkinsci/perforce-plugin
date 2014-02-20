/*
 * The MIT License
 *
 * Copyright 2014 Oleg Nenashev, Synopsys Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.plugins.perforce.credentials;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.plugins.perforce.PerforceSCM;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * This {@link ExtensionPoint} allows to specify custom credential sources
 * for {@link PerforceSCM}.
 * Extension point should have {@link DataBoundConstructor} and {@link P4CredentialsProviderDescriptor}.  
 * <p>
 * <b>Configuration files.</b><br/>
 * <i>config.jelly</i> file defines configuration properties inside a job's
 * configuration. This file is mandatory.<br/>
 * <i>global.jelly</i> file allows to setup the global configuration, which
 * will appear inside the {@link PerforceSCM} section.
 * </p>
 * @see P4GlobalPassword
 * @see P4LocalCredentialsProvider
 * @author Oleg Nenashev <nenashev@synopsys.com>
 * @since TODO
 */
public abstract class P4CredentialsProvider implements ExtensionPoint, 
        Describable<P4CredentialsProvider> {
    
    public static final Class<? extends P4CredentialsProvider> DEFAULT = 
            P4LocalCredentialsProvider.class;

    /**
     * Retrieves a user name to be used for authentication in {@link PerforceSCM}.
     * @return User ID
     */
    public abstract String getUser();

    /**
     * Gets a password to be used for authentication in {@link PerforceSCM}.
     * All encryption methods should be implemented inside {@link P4CredentialsProvider}.
     * @return Decrypted password to be used for authentication.
     */
    public abstract String getPassword();
    
    /**
     * Sets a new user.
     * @deprecated This method is designed to retain the backward compatibility for
     * {@link P4LocalPassword}. Other providers may ignore this call.
     * @param user A user name to be set.
     */
    public void setUser(String user) {
        // do nothing by default
    }
   
    /**
     * Sets a new password.
     * @deprecated This method is designed to retain the backward compatibility for
     * {@link P4LocalPassword}. Other providers may ignore this call.
     * @param password A password to be set.
     */
    public void setPassword(String password) {
        // do nothing by default
    }
    
    
    @Override
    public P4CredentialsProviderDescriptor getDescriptor() {
        return (P4CredentialsProviderDescriptor) Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Get list of all registered {@link JobRestriction}s.
     * @return List of {@link UserMacroExtension}s.
     */    
    public static DescriptorExtensionList<P4CredentialsProvider,P4CredentialsProviderDescriptor> all() {
        return Hudson.getInstance().<P4CredentialsProvider,P4CredentialsProviderDescriptor> 
                getDescriptorList(P4CredentialsProvider.class);
    }
    
    /**
     * Returns list of {@link P4CredentialsProviderDescriptor}s.
     * @return List of available descriptors.
     */
    public static List<Descriptor<P4CredentialsProvider>> allDescriptors() {
        return Hudson.getInstance().getDescriptorList(P4CredentialsProvider.class);
    }
    
    
    
}
