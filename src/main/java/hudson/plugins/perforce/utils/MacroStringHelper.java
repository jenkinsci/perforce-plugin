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
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.plugins.perforce.PerforceSCM;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * The class aggregates all variables substitution methods within the plugin.
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
     * Substitute parameters and validate contents of the resulting string.
     * This is a generic method, which invokes routines for {@link AbstractBuild} if it is not null.
     * Otherwise, the default handlers for {@link AbstractProject} and {@link Node} will be used.
     * @param string Input string to be substituted
     * @param instance Instance of {@link PerforceSCM}
     * @param build A build to be substituted
     * @param project A project
     * @param node A node to be substituted
     * @param env Additional environment variables.
     * @return Substituted string. May be null if the input string is null
     * @throws ParameterSubstitutionException Format error (unresolved variable, etc.)
     */
    public static String substituteParameters(
            @CheckForNull String string,
            @Nonnull PerforceSCM instance,        
            @CheckForNull AbstractBuild build,
            @CheckForNull AbstractProject project,
            @CheckForNull Node node,
            @CheckForNull Map<String, String> env)
            throws ParameterSubstitutionException, InterruptedException {
        
        return build != null
                ? substituteParameters(string, instance, build, env)
                : substituteParameters(string, instance, project, node, env);
    }
    
    /**
     * Substitute parameters and validate contents of the resulting string
     * @param string Input string to be substituted
     * @param instance Instance of {@link PerforceSCM}
     * @param project A project
     * @param node A node to be substituted
     * @param env Additional environment variables.
     * @return Substituted string. May be null if the input string is null
     * @throws ParameterSubstitutionException Format error (unresolved variable, etc.)
     */
    public static String substituteParameters(
            @CheckForNull String string,
            @Nonnull PerforceSCM instance,
            @CheckForNull AbstractProject project,
            @CheckForNull Node node,
            @CheckForNull Map<String, String> env)
            throws ParameterSubstitutionException, InterruptedException {
        if (string == null) return null;
        String result = substituteParametersNoCheck(string, instance, project, node, env);
        checkString(result);
        return result;
    }
    
    /**
     * Substitute parameters and validate contents of the resulting string
     * @param string Input string
     * @param subst Variables Map
     * @return Substituted string. May be null if the input string is null
     * @throws ParameterSubstitutionException Format error (unresolved variable, etc.)
     */
    public static String substituteParameters(
            @CheckForNull String string,
            @Nonnull Map<String, String> subst)
            throws ParameterSubstitutionException {
        if (string == null) return null;
        String result = substituteParametersNoCheck(string, subst);
        checkString(result);
        return result;
    }
    
    /**
     * Substitute parameters and validate contents of the resulting string
     * @param string Input string to be substituted
     * @param instance Instance of {@link PerforceSCM}
     * @param build A build to be substituted
     * @param env Additional environment variables.
     * @return Substituted string. May be null if the input string is null
     * @throws ParameterSubstitutionException Format error (unresolved variable, etc.)
     */
    public static String substituteParameters(
            @CheckForNull String string,
            @Nonnull PerforceSCM instance,
            @Nonnull AbstractBuild build,
            @CheckForNull Map<String, String> env)
            throws ParameterSubstitutionException, InterruptedException {
        if (string == null) return null;
        String result = substituteParametersNoCheck(string, instance, build, env);
        checkString(result);
        return result;
    }
    
    /**
     * Checks string from unsubstituted variable references.
     * @param string Input string (should be substituted before call).
     *      Null string will be interpreted as OK
     * @throws ParameterSubstitutionException Substitution error
     */
    public static void checkString(@CheckForNull String string) throws ParameterSubstitutionException {
        
        // Conditional fail on substitution error
        if (containsMacro(string)) {
            throw new ParameterSubstitutionException(string, "Found unresolved macro at '" + string + "'");
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
     */
    private static String substituteParametersNoCheck(
            @CheckForNull String string, 
            @Nonnull Map<String, String> subst) {
        if (string == null) {
            return null;
        }
        String newString = string;
        for (Map.Entry<String, String> entry : subst.entrySet()) {
            final @CheckForNull String key = entry.getKey();
            final @CheckForNull String value = entry.getValue(); 
            if (key == null || value == null) {
                continue;
            }
            newString = newString.replace("${" + key + "}", value);
        }
        return newString;
    }
    
    /**
     * Check if the input string contains macro variables.
     * @param str String to be checked
     * @return true if the string 
     */
    public static boolean containsMacro(@CheckForNull String str) {
        return str != null && str.contains("${");
    }
  
    /**
     * Check if the input string contains the specified variable reference.
     * @param str String to be checked
     * @param variableName Variable name
     * @return true if the string contains the specified variable
     */
    public static boolean containsVariable(@CheckForNull String str, @Nonnull String variableName) {
        return str != null && str.contains("${" + variableName + "}");
    }
    
     /**
     * Substitute parameters and validate contents of the resulting string.
     * This is a keystone method of {@link MacroStringHelper}. 
     * @param inputString Input string to be substituted
     * @param project A project
     * @param instance Instance of {@link PerforceSCM}
     * @param node A node to be substituted
     * @param env Additional environment variables.
    *  @return Substituted string is not null and contains macro definition.
     */        
    private static String substituteParametersNoCheck (
            @Nonnull String inputString,
            @Nonnull PerforceSCM instance,
            @CheckForNull AbstractProject project,
            @CheckForNull Node node,
            @CheckForNull Map<String, String> env) throws InterruptedException {
        
        if (!containsMacro(inputString)) { // do nothing for the missing macro
            return inputString;
        }
        String outputString = inputString;
        
        // Substitute additional environment vars if possible
        if (env != null && !env.isEmpty()) {
            outputString = substituteParametersNoCheck(outputString, env);
            if (!containsMacro(outputString)) { //exit if no macros left
                return outputString;
            }
        }
                
        // Prepare the substitution container and substitute vars
        Map<String, String> substitutions = new HashMap<String, String>();
        getDefaultCoreSubstitutions(substitutions);
        NodeSubstitutionHelper.getDefaultNodeSubstitutions(instance, node, substitutions);
        if (project != null) { 
            JobSubstitutionHelper.getDefaultSubstitutions(project, substitutions);
        }
        getDefaultSubstitutions(instance, substitutions);
        outputString = substituteParametersNoCheck(outputString, substitutions);    
        
        return outputString;
    }
    
    /**
     * Substitute parameters and validate contents of the resulting string.
     * Environment variables have the highest priority.
     * @param inputString Input string
     * @param instance Instance of {@link PerforceSCM}
     * @param build Related build
     * @param env Additional environment variables
     * @return Substituted string
     */        
    private static String substituteParametersNoCheck(
            @Nonnull String inputString,
            @Nonnull PerforceSCM instance,
            @Nonnull AbstractBuild build, 
            @CheckForNull Map<String, String> env) throws InterruptedException {
        
        if (!containsMacro(inputString)) {
            return inputString;
        }
        String result = inputString;
        
        // Substitute variables with custom values (escaping, etc)
        final Map<String, String> customVars = new TreeMap<String, String>();
        customVars.put("JOB_NAME", JobSubstitutionHelper.getSafeJobName(build));
        result = MacroStringHelper.substituteParametersNoCheck(result, customVars);
        
        // Try to build the full environment. Nested calls count is handled in PerforceSCM::buildEnvVars() 
        Map<String, String> environmentVarsFromExtensions = new TreeMap<String, String>();
        try {
            EnvVars vars = build.getEnvironment(TaskListener.NULL);
            environmentVarsFromExtensions.putAll(vars);
        } catch (IOException ex) {
            Logger.getLogger(PerforceSCM.class.getName()).log(Level.SEVERE, null, ex);
        }
        result = MacroStringHelper.substituteParametersNoCheck(result, environmentVarsFromExtensions);
              
        // Intermediate check to avoid wasted efforts
        if (!containsMacro(result)) {
            return result;
        }
        
        // Substitute static variables
        result = substituteParametersNoCheck(result, instance, build.getProject(), build.getBuiltOn(), env);
              
        // Substitute default build variables
        Map<String, String> substitutions = new HashMap<String, String>();
        getDefaultBuildSubstitutions(build, substitutions);    
        result = MacroStringHelper.substituteParametersNoCheck(result, substitutions);
        result = MacroStringHelper.substituteParametersNoCheck(result, build.getBuildVariables());
              
        return result;
    }
    
    /**
     * Gets variables of {@link Hudson} instance.
     */
    private static void getDefaultCoreSubstitutions(@Nonnull Map<String, String> env) {
        String rootUrl = Hudson.getInstance().getRootUrl();
        if (rootUrl != null) {
            env.put("JENKINS_URL", rootUrl);
            env.put("HUDSON_URL", rootUrl); // Legacy compatibility
        }
        env.put("JENKINS_HOME", Hudson.getInstance().getRootDir().getPath());
        env.put("HUDSON_HOME", Hudson.getInstance().getRootDir().getPath());   // legacy compatibility
    }
    
    /**
     * Substitutes {@link PerforceSCM}-specific variables.
     * In order to retain the backward compatibility, the input subst map's
     * should contain {@link AbstractProject} variables from 
     * {@link #getDefaultSubstitutions(hudson.model.AbstractProject, java.util.Map)}.
     * @param instance {@link PerforceSCM} instance
     * @param subst Input substitutions. 
     */
    private static void getDefaultSubstitutions(
            @Nonnull PerforceSCM instance, 
            @Nonnull Map<String, String> subst) {
        subst.put("P4USER", MacroStringHelper.substituteParametersNoCheck(instance.getEffectiveP4User(), subst));
    }
    
    private static void getDefaultBuildSubstitutions(
            @Nonnull AbstractBuild build, 
            @Nonnull Map<String, String> subst) {
        String hudsonName = Hudson.getInstance().getDisplayName().toLowerCase();
        subst.put("BUILD_TAG", hudsonName + "-" + build.getProject().getName() + "-" + String.valueOf(build.getNumber()));
        subst.put("BUILD_ID", build.getId());
        subst.put("BUILD_NUMBER", String.valueOf(build.getNumber()));
        String rootUrl = Hudson.getInstance().getRootUrl();
        if (rootUrl != null) {   
            subst.put("BUILD_URL", rootUrl + build.getUrl());
        }
    }
}