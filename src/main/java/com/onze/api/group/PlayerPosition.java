package com.onze.api.group;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PlayerPosition {
    GOALKEEPER,
    DEFENDER,
    RIGHT_DEFENDER,
    LEFT_DEFENDER,
    CENTER_DEFENDER,
    RIGHT_BACK,
    LEFT_BACK,
    DEFENSIVE_MIDFIELDER,
    MIDFIELDER,
    RIGHT_MIDFIELDER,
    LEFT_MIDFIELDER,
    CENTRAL_MIDFIELDER,
    PLAYMAKER,
    ATTACKER,
    RIGHT_WINGER,
    LEFT_WINGER,
    CENTER_FORWARD;

    @JsonCreator
    public static PlayerPosition fromApiValue(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "WINGER", "STRIKER" -> ATTACKER;
            default -> valueOf(value);
        };
    }

    public static boolean canPlayPosition(
            PlayerPosition profilePosition,
            PlayerPosition targetPosition) {
        if (profilePosition == null || targetPosition == null) {
            return false;
        }
        if (profilePosition == targetPosition) {
            return true;
        }
        return switch (profilePosition) {
            case DEFENDER -> switch (targetPosition) {
                case RIGHT_DEFENDER,
                        LEFT_DEFENDER,
                        CENTER_DEFENDER,
                        RIGHT_BACK,
                        LEFT_BACK -> true;
                default -> false;
            };
            case MIDFIELDER -> switch (targetPosition) {
                case DEFENSIVE_MIDFIELDER,
                        RIGHT_MIDFIELDER,
                        LEFT_MIDFIELDER,
                        CENTRAL_MIDFIELDER,
                        PLAYMAKER -> true;
                default -> false;
            };
            case ATTACKER -> switch (targetPosition) {
                case RIGHT_WINGER, LEFT_WINGER, CENTER_FORWARD -> true;
                default -> false;
            };
            default -> false;
        };
    }
}
