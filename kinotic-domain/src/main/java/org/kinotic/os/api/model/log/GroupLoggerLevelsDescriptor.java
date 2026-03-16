package org.kinotic.os.api.model.log;

import java.util.List;

/**
 * Created by Navíd Mitchell 🤪 on 4/5/23.
 */
public
class GroupLoggerLevelsDescriptor extends LoggerLevelsDescriptor {

    private final List<String> members;

    public GroupLoggerLevelsDescriptor(LogLevel configuredLevel, List<String> members) {
        super(configuredLevel);
        this.members = members;
    }

    public List<String> getMembers() {
        return this.members;
    }

}
