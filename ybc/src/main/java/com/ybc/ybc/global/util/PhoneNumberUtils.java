package com.ybc.ybc.global.util;

public final class PhoneNumberUtils {
    private PhoneNumberUtils() {
    }

    public static String normalize(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        return phoneNumber.replaceAll("[^0-9]", "");
    }

    public static String format(String phoneNumber) {
        String normalizedPhoneNumber = normalize(phoneNumber);
        if (normalizedPhoneNumber == null || normalizedPhoneNumber.isBlank()) {
            return normalizedPhoneNumber;
        }

        if (normalizedPhoneNumber.startsWith("02")) {
            if (normalizedPhoneNumber.length() == 9) {
                return normalizedPhoneNumber.substring(0, 2) + "-"
                        + normalizedPhoneNumber.substring(2, 5) + "-"
                        + normalizedPhoneNumber.substring(5);
            }
            if (normalizedPhoneNumber.length() == 10) {
                return normalizedPhoneNumber.substring(0, 2) + "-"
                        + normalizedPhoneNumber.substring(2, 6) + "-"
                        + normalizedPhoneNumber.substring(6);
            }
        }

        if (normalizedPhoneNumber.length() == 10) {
            return normalizedPhoneNumber.substring(0, 3) + "-"
                    + normalizedPhoneNumber.substring(3, 6) + "-"
                    + normalizedPhoneNumber.substring(6);
        }

        if (normalizedPhoneNumber.length() == 11) {
            return normalizedPhoneNumber.substring(0, 3) + "-"
                    + normalizedPhoneNumber.substring(3, 7) + "-"
                    + normalizedPhoneNumber.substring(7);
        }

        return normalizedPhoneNumber;
    }
}
