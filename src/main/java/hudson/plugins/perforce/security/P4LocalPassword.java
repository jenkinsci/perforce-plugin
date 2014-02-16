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

package hudson.plugins.perforce.security;

import hudson.Extension;
import hudson.plugins.perforce.Messages;
import hudson.plugins.perforce.PerforcePasswordEncryptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author nenashev
 */
public class P4LocalPassword extends P4CredentialsProvider {

    public final String user;
    public final String password;

    @DataBoundConstructor
    public P4LocalPassword(String user, String password) {
        this.user = user;
        
        // Encrypt password if required
        PerforcePasswordEncryptor encryptor = new PerforcePasswordEncryptor();
        if (encryptor.appearsToBeAnEncryptedPassword(password))
            this.password = password;
        else
            this.password = encryptor.encryptString(password);
    }
    
    @Override
    public String getUser() {
        return user;
    }
    
    @Override
    public String getPassword() {
        return password;
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
