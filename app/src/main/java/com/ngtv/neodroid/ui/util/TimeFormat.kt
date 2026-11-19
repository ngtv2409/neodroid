package com.ngtv.neodroid.ui.util

import android.text.format.DateUtils

fun relativeTime(timestamp: Long?, now: Long = System.currentTimeMillis()): String {
    if (timestamp == null) return "Not connected yet"

    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}
