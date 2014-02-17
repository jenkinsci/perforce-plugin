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

import hudson.model.Descriptor;
import java.util.ArrayList;
import java.util.List;

/**
 * A descriptor for {@link P4CredentialsProvider} extension.
 * @author Oleg Nenashev <nenashev@synopsys.com>
 * @since TODO
 */
public abstract class P4CredentialsProviderDescriptor extends Descriptor<P4CredentialsProvider> {
    
    private static final String P4CREDENTIALS_GLOBAL_CONFIG = "global.jelly";
    
    protected P4CredentialsProviderDescriptor(Class<? extends P4CredentialsProvider> clazz) {
        super(clazz); 
    }
    
    protected P4CredentialsProviderDescriptor() {}
    
    public P4CredentialsProviderDescriptor getDefaultCredentialsProvider() {
        return (P4CredentialsProviderDescriptor)P4CredentialsProvider
                .all().find(P4CredentialsProvider.DEFAULT);
    } 
    
    /**
     * Gets the global credentials configuration page.
     * @return A page name or null if not present
     */
    public String getGlobalCredentialsConfigPage() {
        String page = getViewPage(clazz, P4CREDENTIALS_GLOBAL_CONFIG);
        if (page != null && !page.equals(P4CREDENTIALS_GLOBAL_CONFIG)) {
            return page;
        }
        return null;
    }
    
    /**
     * Gets all descriptors, which define the global credentials page.
     * @return List of descriptors having {@link #P4CREDENTIALS_GLOBAL_CONFIG}. 
     */
    public static List<P4CredentialsProviderDescriptor> getDescriptorsForGlobaConfigPage() {
        List<P4CredentialsProviderDescriptor> all = P4CredentialsProvider.all();
        List<P4CredentialsProviderDescriptor> r = new ArrayList<P4CredentialsProviderDescriptor>(all.size());
        for (Descriptor<P4CredentialsProvider> d: all) {
            if (
                    d instanceof P4CredentialsProviderDescriptor
                    && ((P4CredentialsProviderDescriptor)d).getGlobalCredentialsConfigPage() != null
            ) {
                r.add((P4CredentialsProviderDescriptor)d);
            }
        }
        return r;
    }
}
