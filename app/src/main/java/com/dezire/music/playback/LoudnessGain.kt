package com.dezire.music.playback

/*
 * Pure DSP math behind the real-time loudness normalizer, kept free of
 * Android/media3 types so it can be unit-tested on the JVM.
 *
 * This is a peak-based automatic gain controller (AGC) with a soft
 * limiter. It does NOT read ReplayGain/EBU-R128 metadata (Media3 1.4 does
 * not surface those tags and MediaMetadataRetriever does not expose them),
 * so it is explicitly documented as dynamic loudness leveling rather than
 * true ReplayGain normalisation.
 *
 * Behaviour:
 *  - Quiet passages are boosted towards [TARGET_PEAK] using a fast attack
 *    and a slow release so the gain does not pump with the rhythm.
 *  - Loud passages are attenuated below unity.
 *  - Gain is clamped to [MIN_GAIN]..[MAX_GAIN] and the output is passed
 *    through a soft limiter so the digital signal never clips hard.
 */
object LoudnessGain {
    /* Target peak level: roughly -3 dBFS headroom. */
    const val TARGET_PEAK = 0.7f

    /* Gain clamp: -6 dB .. +3.5 dB. */
    const val MIN_GAIN = 0.5f
    const val MAX_GAIN = 1.5f

    /* Soft limiter knee/max so bursts cannot hit 1.0 and clip. */
    const val LIMIT_KNEE = 0.96f
    const val LIMIT_MAX = 0.99f

    /* Smoothing constants: attack responds fast, release recovers slowly. */
    const val ATTACK_FACTOR = 0.30f
    const val RELEASE_FACTOR = 0.02f

    fun isUnity(sample: Float): Boolean = sample == 0f

    /*
     * Gain that would bring [peak] up to [TARGET_PEAK], clamped. A peak of
     * TARGET_PEAK maps to unity gain.
     */
    fun gainForPeak(peak: Float): Float {
        val safe = if (peak.isFinite() && peak > 0f) peak else TARGET_PEAK
        return (TARGET_PEAK / safe).coerceIn(MIN_GAIN, MAX_GAIN)
    }

    /*
     * Approaches [targetGain] from the previous gain using the attack
     * factor when needing more gain and the release factor when backing
     * off, so leveling is reactive yet stable.
     */
    fun nextGain(previous: Float, targetGain: Float, attack: Float, release: Float): Float {
        val delta = targetGain - previous
        val factor = if (delta > 0f) attack else release
        return (previous + delta * factor).coerceIn(MIN_GAIN, MAX_GAIN)
    }

    /*
     * Applies the likely static gain, then a soft-knee limiter. Returns a
     * value that stays within +/-LIMIT_MAX and is always finite. Non-finite
     * samples (which should never arrive from a decoder) are replaced with
     * silence instead of being amplified into the audio stream.
     */
    fun scale(sample: Float, gain: Float): Float {
        if (!sample.isFinite() || !gain.isFinite()) return 0f
        return limiter(sample * gain)
    }

    private fun limiter(input: Float): Float {
        val magnitude = kotlin.math.abs(input)
        if (magnitude <= LIMIT_KNEE) return input
        val sign = if (input >= 0f) 1f else -1f
        val over = (magnitude - LIMIT_KNEE) / (1f - LIMIT_KNEE)
        val headroom = (LIMIT_MAX - LIMIT_KNEE)
        val compressed = LIMIT_KNEE + Math.sqrt(over.toDouble()).toFloat() * headroom
        return sign * compressed.coerceAtMost(LIMIT_MAX)
    }

    /*
     * Convenience envelope: gain approaches the target for [peak] using
     * the default attack/release constants.
     */
    fun smoothedGain(previous: Float, peak: Float): Float =
        nextGain(previous, gainForPeak(peak), ATTACK_FACTOR, RELEASE_FACTOR)
}