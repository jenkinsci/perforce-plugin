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

import hudson.matrix.Axis;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.Job;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Default substitutions for {@link Job}s and {@link Build}s.
 * @author Oleg Nenashev <o.v.nenashev@gmail.com>
 * @since 1.3.32
 */
public class JobSubstitutionHelper {

    private JobSubstitutionHelper() {
    }

    /**package*/ static void getDefaultSubstitutions(
            @Nonnull AbstractProject project, 
            @Nonnull Map<String, String> subst) {
        
        subst.put("JOB_NAME", JobSubstitutionHelper.getSafeJobName(project));
        String rootUrl = Hudson.getInstance().getRootUrl();
        if (rootUrl != null) {   
            subst.put("JOB_URL", rootUrl + project.getUrl());
        }
        
        for (NodeProperty nodeProperty : Hudson.getInstance().getGlobalNodeProperties()) {
            if (nodeProperty instanceof EnvironmentVariablesNodeProperty) {
                subst.putAll(((EnvironmentVariablesNodeProperty) nodeProperty).getEnvVars());
            }
        }
        ParametersDefinitionProperty pdp = (ParametersDefinitionProperty) project.getProperty(hudson.model.ParametersDefinitionProperty.class);
        if (pdp != null) {
            for (ParameterDefinition pd : pdp.getParameterDefinitions()) {
                try {
                    ParameterValue defaultValue = pd.getDefaultParameterValue();
                    if (defaultValue != null) {
                        String name = defaultValue.getName();
                        String value = defaultValue.createVariableResolver(null).resolve(name);
                        subst.put(name, value);
                    }
                } catch (Exception e) {
                    // Do nothing
                }
            }
        }
        
        // Handle Matrix Axes
        if (project instanceof MatrixConfiguration) {
            MatrixConfiguration matrixConfiguration = (MatrixConfiguration) project;
            subst.putAll(matrixConfiguration.getCombination());
        }
        if (project instanceof MatrixProject) {
            MatrixProject matrixProject = (MatrixProject) project;
            for (Axis axis : matrixProject.getAxes()) {
                subst.put(axis.name, axis.size() >0 ? axis.value(0) : "");
            }
        }
    }
    
    public static String getSafeJobName(@Nonnull AbstractBuild build) {
        return getSafeJobName(build.getProject());
    }

    public static String getSafeJobName(@Nonnull AbstractProject project) {
        return project.getFullName().replace('/', '-').replace('=', '-').replace(',', '-');
    }
}
