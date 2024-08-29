/*
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// Enums used to get the locale-aware String for one of the three relative days
enum class RelativeDay { TODAY, YESTERDAY, TOMORROW }

/**
 * Utility methods to help display dates in a nice, easily readable way.
 */
object DateUtils : android.text.format.DateUtils() {

    @Suppress("unused")
    private val TAG: String = DateUtils::class.java.simpleName
    private val DAY_PRECISION_DATE_FORMAT = SimpleDateFormat("yyyyMMdd")
    private val HOUR_PRECISION_DATE_FORMAT = SimpleDateFormat("yyyyMMddHH")

    // Preferred date and time formats for the user - these are stored in the user's preferences.
    // We set them as invalid on startup (range is 0..8 and 0..2 respectively) so we only have to
    // retrieve the preference once rather than every time we wish to format a date or time.
    // See: TextSecurePreferences.DATE_FORMAT_PREF and TextSecurePreferences for further details.
    private var userPreferredDateFormat: Int = -1
    private var userPreferredTimeFormat: Int = 2//-1


    private fun isWithin(millis: Long, span: Long, unit: TimeUnit): Boolean {
        return System.currentTimeMillis() - millis <= unit.toMillis(span)
    }

    private fun isYesterday(`when`: Long): Boolean {
        return isToday(`when` + TimeUnit.DAYS.toMillis(1))
    }

    private fun convertDelta(millis: Long, to: TimeUnit): Int {
        return to.convert(System.currentTimeMillis() - millis, TimeUnit.MILLISECONDS).toInt()
    }

    // Method to get the String for a relative day in a locale-aware fashion
    public fun getLocalisedRelativeDayString(relativeDay: RelativeDay): String {

        val now = Calendar.getInstance()

        // To compare a time to 'now' we need to use get a date relative it, so plus or minus a day, or not
        val dayAddition = when (relativeDay) {
            RelativeDay.TOMORROW  -> {  1 }
            RelativeDay.YESTERDAY -> { -1 }
            else -> 0 // Today
        }

        val comparisonTime = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayAddition)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return getRelativeTimeSpanString(comparisonTime.timeInMillis,
            now.timeInMillis,
            DAY_IN_MILLIS,
            FORMAT_SHOW_DATE).toString()
    }

    // THIS IS THE METHOD THAT ACTUALLY GETS USED TO GET THE DATE TIME
    fun getFormattedDateTime(time: Long, template: String, locale: Locale): String {
        val localizedPattern = getLocalizedPattern(template, locale)
        return SimpleDateFormat(localizedPattern, locale).format(Date(time))
    }

    // Method to get the user's preferred time format, whether that's 12-hour or 24-hour
    fun getHourFormat(c: Context): String {
        // If this is the first run..
        if (userPreferredTimeFormat == -1) {
            // ..update our preferred time format (will return -1 if no saved pref).
            userPreferredTimeFormat = TextSecurePreferences.getTimeFormatPref(c)

            // If no saved value was written we'll write 0 for "Follow system setting" - this will only run on first install
            if (userPreferredTimeFormat == -1) {
                userPreferredTimeFormat = 0
                TextSecurePreferences.setTimeFormatPref(c, userPreferredTimeFormat)
            }

            // If the preferred time format is "Follow system setting" then we need to find out what the system setting is!
            if (userPreferredTimeFormat == 0) {
                val is24HourFormat = DateFormat.is24HourFormat(c)

                // Set the time format we'll use to either 24 or 12 hours.
                // IMPORTANT: We don't WRITE this to the pref - we just use it while the app is running!
                // Note: See TextSecurePreferences.TIME_FORMAT_PREF for further details of available formats.
                userPreferredTimeFormat = if (is24HourFormat) 2 else 1
            }
        }

        // At this point userPreferredTimeFormat will ALWAYS be either 1 or 2 - regardless of if the saved
        // pref is 0 to "Follow system settings".
        return if (userPreferredTimeFormat == 1) "hh:mm a" else "HH:mm"
    }

    fun getDisplayFormattedTimeSpanString(c: Context, locale: Locale, timestamp: Long): String {
        // Note: Date-formats are in TR-35 format.
        // See: https://www.unicode.org/reports/tr35/tr35-dates.html#Date_Format_Patterns
        val t = if (isToday(timestamp)) {
            Log.w("ACL", "Within today")
            // If it's within the last 24 hours we just give the time in 24-hour format, such as "13:27" for 1:27pm
            getFormattedDateTime(timestamp, getHourFormat(c), locale)
        } else if (isWithin(timestamp, 6, TimeUnit.DAYS)) {
            Log.w("ACL", "Within week")
            // If it's within the last week we give the day as 3 letters then the time in 24-hour format, such as "Fri 13:27" for Friday 1:27pm
            getFormattedDateTime(timestamp, "EEE " + getHourFormat(c), locale)
        } else if (isWithin(timestamp, 365, TimeUnit.DAYS)) {
            Log.w("ACL", "Within year")
            // If it's within the last year we give the month as 3 letters then the time in 24-hour format, such as "Mar 13:27" for March 1:27pm
            // CAREFUL: "MMM d + getHourFormat(c)" actually turns out to be "8 July, 17:14" etc. - it is DAY-NUMBER and then MONTH (which can go up to 4 chars) - and THEN the time. Wild.
            getFormattedDateTime(timestamp, "MMM d " + getHourFormat(c), locale)
        } else {
            // NOTE: The `userPreferredDateFormat` is ONLY ever used on dates which exceed one year!
            // See the Figma linked in SES-360 for details.


            Log.w("ACL", "More than 1 year")
            // If the date is more than a year ago then we get "19 July 2023, 16:19" type format

            // If this is the first run..
            if (userPreferredDateFormat == -1) {
                // ..update our preferred date format (will return -1 if no saved pref).
                userPreferredDateFormat = TextSecurePreferences.getDateFormatPref(c)

                // If no saved value was written we'll write 0 for "Follow system setting" - this will only run on first install
                if (userPreferredDateFormat == -1) {
                    userPreferredDateFormat = 0
                    //TextSecurePreferences.setDateFormatPref(c, userPreferredDateFormat) ACL PUT THIS BACK!!!!!!!!!!!!!!!!!!!!!
                }

                // If the preferred date format is "Follow system setting" then we need to find out what the system setting is!
                if (userPreferredDateFormat == 0) {
                    val dateFormat = DateFormat.getDateFormat(c)

                    // Check if the DateFormat instance is a SimpleDateFormat
                    if (dateFormat is SimpleDateFormat) {
                        Log.w("ACL", "Date pattern: " + dateFormat.toPattern())

                        when (dateFormat) {
                            "M/d/yy" -> FFS
                        }


                    } else {
                        // If the dateFormat isn't a SimpleDateFormat from which we can extract a pattern then the best
                        // we can do is pick a sensible default like DD/MM/YYYY - which equates to option 3 out of our
                        // available options (see TextSecurePreferences.DATE_FORMAT_PREF for further details).
                        userPreferredDateFormat = 3
                        // IMPORTANT: We don't WRITE this to the pref - we just use it while the app is running!
                    }

                    // Set the time format we'll use to either 24 or 12 hours.
                    // IMPORTANT: We don't WRITE this to the pref - we just use it while the app is running!
                    // Note: See TextSecurePreferences.TIME_FORMAT_PREF for further details of available formats.
                    //userPreferredTimeFormat = if (is24HourFormat) 2 else 1
                }
            }

            getFormattedDateTime(timestamp, "MMM d " + getHourFormat(c) + ", yyyy", locale)
        }

        Log.w("ACL", "t is: $t")
        return t

    }

    fun getDetailedDateFormatter(context: Context?, locale: Locale): SimpleDateFormat {
        val dateFormatPattern = if (DateFormat.is24HourFormat(context)) {
            getLocalizedPattern("MMM d, yyyy HH:mm:ss zzz", locale)
        } else {
            getLocalizedPattern("MMM d, yyyy hh:mm:ss a zzz", locale)
        }

        return SimpleDateFormat(dateFormatPattern, locale)
    }

    // Method to get the String for a relative day in a locale-aware fashion, including using the
    // auto-localised words for "today" and "yesterday" as appropriate.
    fun getRelativeDate(
        context: Context,
        locale: Locale,
        timestamp: Long
    ): String {
        return if (isToday(timestamp)) {
            getLocalisedRelativeDayString(RelativeDay.TODAY)
        } else if (isYesterday(timestamp)) {
            getLocalisedRelativeDayString(RelativeDay.YESTERDAY)
        } else {
            getFormattedDateTime(timestamp, "EEE, MMM d, yyyy", locale)
        }
    }

    fun isSameDay(t1: Long, t2: Long): Boolean {
        return DAY_PRECISION_DATE_FORMAT.format(Date(t1)) == DAY_PRECISION_DATE_FORMAT.format(Date(t2))
    }

    fun isSameHour(t1: Long, t2: Long): Boolean {
        return HOUR_PRECISION_DATE_FORMAT.format(Date(t1)) == HOUR_PRECISION_DATE_FORMAT.format(Date(t2))
    }

    private fun getLocalizedPattern(template: String, locale: Locale): String {
        return DateFormat.getBestDateTimePattern(locale, template)
    }

    /**
     * e.g. 2020-09-04T19:17:51Z
     * https://www.iso.org/iso-8601-date-and-time-format.html
     *
     * @return The timestamp if able to be parsed, otherwise -1.
     */
    @SuppressLint("ObsoleteSdkInt")
    @JvmStatic
    public fun parseIso8601(date: String?): Long {

        if (date.isNullOrEmpty()) { return -1 }

        val format = if (Build.VERSION.SDK_INT >= 24) {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.getDefault())
        } else {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
        }

        try {
            return format.parse(date).time
        } catch (e: ParseException) {
            Log.w(TAG, "Failed to parse date.", e)
            return -1
        }
    }
}