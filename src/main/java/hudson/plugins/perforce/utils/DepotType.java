/*
 * The MIT License
 *
 * Copyright 2013 Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
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

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Provides databound-transfer of depot type parameters for PerforceSCM.
 * Class is developed in order to resolve <a href="https://issues.jenkins-ci.org/browse/JENKINS-18583">JENKINS-18583 issue</a>
 * @see PerforceSCMceSCM
 * @author Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
 * @since 1.4.0
 */
public class DepotType {
    public static final String USE_P4STREAM_MARKER="stream";
    public static final String USE_CLIENTSPEC_MARKER="file";
    public static final String USE_PROJECTPATH_MARKER="map";
    
    String value;
    String p4Stream;
    String clientSpec;
    String projectPath;
    
    @DataBoundConstructor
    public DepotType(String value, String p4Stream, String clientSpec, String projectPath) {
        this.value = (value != null) ? value : "";
        this.p4Stream = p4Stream;
        this.clientSpec = clientSpec;
        this.projectPath = projectPath;
    }

    public String getProjectPath() {
        return projectPath;
    }
    
    public boolean useProjectPath() {
        return value.equals(USE_PROJECTPATH_MARKER);
    }

    public String getClientSpec() {
        return clientSpec;
    }

    public boolean useClientSpec() {
        return value.equals(USE_CLIENTSPEC_MARKER);
    }
    
    public String getP4Stream() {
        return p4Stream;
    }

    public boolean useP4Stream() {
        return value.equals(USE_P4STREAM_MARKER);
    }
}
