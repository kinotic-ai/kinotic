

package org.kinotic.rpc.api.crud;

/**
 * Created by Navíd Mitchell 🤪 on 7/30/21.
 */
public enum SearchComparator {
    EQUALS("="),
    NOT("!"),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUALS(">="),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUALS("<="),
    LIKE("~");

    private final String stringValue;

    SearchComparator(String stringValue) {
        this.stringValue = stringValue;
    }

    public String getStringValue() {
        return stringValue;
    }

    public static final SearchComparator fromStringValue(String stringValue){
        switch (stringValue) {
            case "=":
                return EQUALS;
            case "!":
                return NOT;
            case ">":
                return GREATER_THAN;
            case ">=":
                return GREATER_THAN_OR_EQUALS;
            case "<":
                return LESS_THAN;
            case "<=":
                return LESS_THAN_OR_EQUALS;
            case "~":
                return LIKE;
            default:
                throw new IllegalStateException("Unexpected value: " + stringValue);
        }
    }
}
