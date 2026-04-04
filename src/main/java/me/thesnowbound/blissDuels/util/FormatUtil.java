package me.thesnowbound.blissDuels.util;

import java.time.Duration;
import java.util.Locale;

/**
 * Utility class for formatting numbers and cooldowns
 */
public class FormatUtil {

    /**
     * Formats a number to include decimal point if needed
     */
    public static String formatDouble(double num) {
        if (num == (long) num) {
            return String.format(Locale.US, "%.0f", num);
        } else {
            return String.format(Locale.US, "%.1f", num);
        }
    }

    /**
     * Gets cooldown display in action bar format (e.g., "5m 30s")
     */
    public static String getCooldownDisplay(long remainingMillis) {
        if (remainingMillis <= 0) {
            return "&aReady!";
        }

        Duration duration = Duration.ofMillis(remainingMillis);
        long minutes = duration.toMinutes();
        long seconds = duration.minusMinutes(minutes).getSeconds();

        if (minutes > 0) {
            if (seconds < 1) {
                return "&b" + minutes + "m";
            } else {
                return "&b" + minutes + "m " + seconds + "s";
            }
        } else if (seconds > 0) {
            return "&b" + seconds + "s";
        }

        return "&aReady!";
    }

    /**
     * Converts minutes and seconds to milliseconds
     */
    public static long toMilliseconds(int minutes, int seconds) {
        return (long) minutes * 60000L + (long) seconds * 1000L;
    }

    /**
     * Converts seconds to milliseconds
     */
    public static long toMilliseconds(int seconds) {
        return (long) seconds * 1000L;
    }
}

