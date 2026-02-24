package org.kinotic.core.api.utils;

import java.util.regex.Pattern;

/**
 * Created By Navíd Mitchell 🤪on 2/13/26
 */
public class CoreUtil {

    private static final Pattern ApplicationPattern = Pattern.compile("^[A-Za-z][A-Za-z0-9._-]*$");
    private static final Pattern ProjectIdPattern = Pattern.compile("^[a-z][a-z0-9._-]*$");

    /**
     * Function will validate the structure application name
     *
     * @param applicationId to validate
     * @throws IllegalArgumentException will be thrown if the structure application is invalid
     */
    public static void validateApplicationId(String applicationId) {
        if (applicationId == null) {
            throw new IllegalArgumentException("Application Id must not be null");
        }
        if (!ApplicationPattern.matcher(applicationId).matches()){
            throw new IllegalArgumentException("Kinotic Application Id Invalid, first character must be a " +
                                                       "letter. And contain only letters, numbers, periods, underscores or dashes. Got "+ applicationId);
        }
    }

    public static void validateProjectId(String projectId){
        if(projectId == null){
            throw new IllegalArgumentException("Project Id must not be null");
        }
        if (!ProjectIdPattern.matcher(projectId).matches()){
            throw new IllegalArgumentException("Kinotic Project Id Invalid, first character must be a " +
                                                       "letter. And contain only letters, numbers, periods, underscores or dashes. Got "+ projectId);
        }
    }


}
