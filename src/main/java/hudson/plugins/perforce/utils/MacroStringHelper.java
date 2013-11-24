/*
 * The MIT License
 *
 * Copyright 2013 Mike Wille, Brian Westrich, Victor Szoltysek,
 *                Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
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

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.plugins.perforce.PerforceSCM;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides validation of macro strings after parameter substitution.
 * @author Mike Wille
 * @author Brian Westrich
 * @author Victor Szoltysek
 * @author Rob Petti
 * @author Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
 * @since 1.3.25
 */
public class MacroStringHelper {
    public static final Level SUBSTITUTION_ERROR_LEVEL = Level.WARNING;
    /**
     * Substitute parameters and validate contents of the resulting string
     * @param string Input string
     * @param subst Variables Map
     * @return Substituted string
     * @throws ParameterSubstitutionException Format error (unresolved variable, etc.)
     */
    public static String substituteParameters(String string, Map<String, String> subst) 
            throws ParameterSubstitutionException
    {
        String result = substituteParametersNoCheck(string, subst);
        checkString(result);
        return result;
    }
    
    /**
     * Substitute parameters and validate contents of the resulting string
     * @param string Input string
     * @param build Related build
     * @param env Additional variables to be substituted. Used as a workaround for build environment
     * @return Substituted string
     * @throws ParameterSubstitutionException Format error (unresolved variable, etc.)
     */
    public static String substituteParameters(String string, AbstractBuild build, Map<String, String> env) 
            throws ParameterSubstitutionException
    {
        String result = substituteParametersNoCheck(string, build, env);
        checkString(result);
        return result;
    }
    
    /**
     * Checks string from unsubstituted variable references.
     * @param string Input string (should be substituted before call)
     * @throws ParameterSubstitutionException Substitution error
     */
    public static void checkString(String string) throws ParameterSubstitutionException
    {
        if (string == null) {
            return;
        }
        
        // Conditional fail on substitution error
        if ( true && string.matches(".*\\$\\{.*\\}.*")) {
            throw new ParameterSubstitutionException(string, "Found unresolved macro at '"+string+"'");
        }
        
        //TODO: manage validation by global params?
        //TODO: Check single brackets
        //TODO: Add checks for '$' without brackets 
    }

    /**
     * Substitute parameters and validate contents of the resulting string
     * @param string Input string
     * @param subst Variables Map
     * @return Substituted string
     * @deprecated Use checked methods instead
     */
    public static String substituteParametersNoCheck(String string, Map<String, String> subst) {
        if (string == null) {
            return null;
        }
        String newString = string;
        for (Map.Entry<String, String> entry : subst.entrySet()) {
            newString = newString.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return newString;
    }
    
    public static boolean containsMacro(String str) {
        return str != null && str.contains("${");
    }

    /**
     * Substitute parameters and validate contents of the resulting string
     * @param string Input string
     * @param build Related build
     * @return Substituted string
     * @deprecated Use checked methods instead
     */        
    public static String substituteParametersNoCheck(String inputString, AbstractBuild build, Map<String, String> env) {
        if (!containsMacro(inputString)) {
            return inputString;
        }
        
        // Substitute environment if possible
        String string = inputString;
        if (env != null && !env.isEmpty()) {
            string = substituteParametersNoCheck(string, env);
            
            //exit if no macros left
            if (!containsMacro(inputString)) {
                return string;
            }
        }
        
        // Try to substitute via node and global environment
        for (NodeProperty nodeProperty : Hudson.getInstance().getGlobalNodeProperties()) {
            if (nodeProperty instanceof EnvironmentVariablesNodeProperty) {
                string = ((EnvironmentVariablesNodeProperty) nodeProperty).getEnvVars().expand(string);
            }
        }
        for (NodeProperty nodeProperty : build.getBuiltOn().getNodeProperties()) {
            if (nodeProperty instanceof EnvironmentVariablesNodeProperty) {
                string = ((EnvironmentVariablesNodeProperty) nodeProperty).getEnvVars().expand(string);
            }
        }
        if (!containsMacro(string)) {
            return string;
        }
        
        // The last attempts: Try to build the full environment
        Map<String, String> subst = new TreeMap<String, String>();
        boolean useEnvironment = true;
        for (StackTraceElement ste : (new Throwable()).getStackTrace()) {
            if (ste.getMethodName().equals("buildEnvVars") && ste.getClassName().equals(PerforceSCM.class.getName())) {
                useEnvironment = false;
            }
        }
        if (useEnvironment) {
            try {
                EnvVars vars = build.getEnvironment(TaskListener.NULL);
                subst.putAll(vars);
            } catch (IOException ex) {
                Logger.getLogger(PerforceSCM.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InterruptedException ex) {
                Logger.getLogger(PerforceSCM.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if (!containsMacro(string)) {
            return string;
        }
       
        //TODO: remove? 
        subst.put("JOB_NAME", getSafeJobName(build));
        String hudsonName = Hudson.getInstance().getDisplayName().toLowerCase();
        subst.put("BUILD_TAG", hudsonName + "-" + build.getProject().getName() + "-" + String.valueOf(build.getNumber()));
        subst.put("BUILD_ID", build.getId());
        subst.put("BUILD_NUMBER", String.valueOf(build.getNumber()));
        
        String result = MacroStringHelper.substituteParametersNoCheck(string, subst);
        result = MacroStringHelper.substituteParametersNoCheck(result, build.getBuildVariables());
        return result;
    }
    
    public static String getSafeJobName(AbstractBuild build) {
        return getSafeJobName(build.getProject());
    }

    public static String getSafeJobName(AbstractProject project) {
        return project.getFullName().replace('/', '-').replace('=', '-').replace(',', '-');
    }
}
