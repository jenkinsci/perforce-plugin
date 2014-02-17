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

import hudson.Extension;
import hudson.plugins.perforce.Messages;
import hudson.plugins.perforce.PerforcePasswordEncryptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Allows to define credentials inside a job's configuration.
 * This method is a replacement for the legacy approach.
 * @author Oleg Nenashev <nenashev@synopsys.com>
 * @since TODO
 */
public class P4LocalPassword extends P4CredentialsProvider {

    public String user;
    public String encryptedPassword;

    @DataBoundConstructor
    public P4LocalPassword(String user, String encryptedPassword) {
        this.user = user;
        this.encryptedPassword = PerforcePasswordEncryptor.encryptString2(encryptedPassword);
    }
     
    @Override
    public String getUser() {
        return user;
    }

    @Override
    public void setUser(String user) {
        this.user = user;
    }
    
    public String getEncryptedPassword() {
        return encryptedPassword;
    }
    
    @Override
    public String getPassword() {
        return PerforcePasswordEncryptor.decryptString2(encryptedPassword);
    }

    @Override
    public void setPassword(String password) {
        encryptedPassword = PerforcePasswordEncryptor.encryptString2(password);
    }
    
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    
    public static class DescriptorImpl extends P4CredentialsProviderDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.Security_P4LocalPassword_displayName();
        }       
    }
}
