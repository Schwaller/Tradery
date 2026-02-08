package com.tradery.license;

/**
 * CLI tool for generating license keys.
 * Usage: java LicenseKeyGenerator [daysValid]
 *
 * Run via Gradle: ./gradlew generateLicenseKey --args="365"
 */
public class LicenseKeyGenerator {

    public static void main(String[] args) {
        int daysValid = 365; // default: 1 year

        if (args.length > 0) {
            try {
                daysValid = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Usage: LicenseKeyGenerator [daysValid]");
                System.err.println("  daysValid: number of days until expiry (default: 365)");
                System.exit(1);
            }
        }

        if (daysValid <= 0) {
            System.err.println("Error: daysValid must be positive");
            System.exit(1);
        }

        String key = LicenseKeyCodec.generate(daysValid);
        LicenseKeyCodec.LicenseResult validation = LicenseKeyCodec.validate(key);

        System.out.println(key);
        System.err.println("Expires: " + validation.expiryDate() + " (" + daysValid + " days)");
    }
}
