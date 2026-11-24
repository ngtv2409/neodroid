package com.ngtv.neodroid.sshUtils

/**
 * Quotes value as a single POSIX shell word.
 *
 * SSH exec requests run the command through the remote user's shell, so any value
 * interpolated into an exec command must be quoted to prevent it from being parsed as
 * shell syntax. Wrapping the value in single quotes protects all characters except `'`,
 * which is escaped as `'\''` (close quote, escaped quote, reopen quote).
 */
internal fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
