package com.truex.referenceapp.ads;

/**
 * Types of ads supported by the reference app.
 *
 * REGULAR: Traditional non-interactive video ads
 *
 * TRUEX: Interactive choice card ads
 *   - User opts in via choice card
 *   - Completing interaction earns credit to skip entire ad break
 *   - Fires AD_FREE_POD event when credit earned
 *
 * IDVX: Interactive inline ads
 *   - Starts automatically without opt-in
 *   - Plays inline with other ads in the break
 *   - Never earns credit, always continues to next ad
 */
public enum AdType {
    REGULAR,    // Traditional video ads
    TRUEX,      // Interactive choice card with ad credit
    IDVX        // Interactive without credit, plays inline
}
