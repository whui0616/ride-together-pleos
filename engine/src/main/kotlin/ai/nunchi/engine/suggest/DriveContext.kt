package ai.nunchi.engine.suggest

import ai.nunchi.engine.Gear

data class DriveContext(
    val gear: Gear = Gear.DRIVE,
    val msSinceHardBrake: Long = Long.MAX_VALUE,
    val turnSignalOn: Boolean = false,
    val brakePedalPercent: Int = 0,
    val speedDeltaKmh10s: Double = 0.0,
    val msSinceLastSpeech: Long = Long.MAX_VALUE,
    val repliesSpokenThisTrip: Int = 0,
    val storiesSpokenThisTrip: Int = 0,
    val safetyAlertActive: Boolean = false,
    val metersToNextTbt: Double = Double.MAX_VALUE,
    val hourOfDay: Int = 12,
    val drivingMinutes: Int = 0,
    val batterySoc: Int = 100,
    val wiperOn: Boolean = false,
    val nowMs: Long = 0L,
)
