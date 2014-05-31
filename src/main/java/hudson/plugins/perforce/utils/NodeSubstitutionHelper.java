/*
 * The MIT License
 *
 * Copyright 2014 Oleg Nenashev <o.v.nenashev@gmail.com>.
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

package hudson.plugins.perforce.utils;

import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.plugins.perforce.PerforceSCM;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 *
 * @author Oleg Nenashev <o.v.nenashev@gmail.com>
 */
public class NodeSubstitutionHelper {

    private static final Logger LOGGER = Logger.getLogger(PerforceSCM.class.getName());

    /**
     * Gets default variable substitutions for the {@link Node}.
     * The method injects global and node-specific {@link EnvironmentVariablesNodeProperty}
     * instances.
     * @param instance Instance of {@link PerforceSCM}
     * @param node Target node. Can be null
     * @param target Output collection
     */
    /*package*/ static void getDefaultNodeSubstitutions(
            @Nonnull PerforceSCM instance,
            @CheckForNull Node node, 
            @Nonnull Map<String, String> target) {
        // Global node properties
        for (NodeProperty globalNodeProperty: Hudson.getInstance().getGlobalNodeProperties()) {
            if (globalNodeProperty instanceof EnvironmentVariablesNodeProperty) {
                target.putAll(((EnvironmentVariablesNodeProperty)globalNodeProperty).getEnvVars());
            }
        }
        
        // Local node properties
        if (node != null) {
            for (NodeProperty nodeProperty : node.getNodeProperties()) {
                if (nodeProperty instanceof EnvironmentVariablesNodeProperty) {
                    target.putAll(((EnvironmentVariablesNodeProperty) nodeProperty).getEnvVars());
                }
            }
       
            // Push legacy variables
            target.put("nodename", node.getNodeName());
            target.put("hostname", getHostName(node));
            target.put("hash", getNodeHash(node));
        }
    }
    
    /**
     * Get a unique {@link Node} hashcode.
     * Use hashcode of the nodename to get a unique, slave-specific client name
     * @param node Node
     * @return 
     */
    @Nonnull
    private static String getNodeHash(@Nonnull Node node) {
        return String.valueOf(node.getNodeName().hashCode());
    }
    
    @Nonnull
    private static String getHostName(@Nonnull Node node) {
        String host = null;
        try {
            Computer c = node.toComputer();
            if (c != null) {
                host = c.getHostName();
            }
        } catch (Exception ex) {
            // fallback to finally
        } finally {
            if (host == null) {
                LOGGER.log(Level.WARNING, "Could not get hostname for slave " + node.getDisplayName());
                host = "UNKNOWNHOST";
            }
        }
        if (host.contains(".")) {
            host = String.valueOf(host.subSequence(0, host.indexOf('.')));
        }
        return host;
    }
}
