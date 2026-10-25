package com.macl.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Maldives Astronomical Prayer Time Calculation for Male' and Hulhule.
 * Coordinates: Lat 4.19° N, Lon 73.53° E, UTC+5.
 * Standard calculation method matching the Maldives Ministry of Islamic Affairs.
 */
public final class PrayerCalculator {

    public static final ZoneId MALDIVES_ZONE = ZoneId.of("Indian/Maldives");
    private static final double LAT = 4.1918;
    private static final double LNG = 73.5292;
    private static final double TIMEZONE = 5.0;

    public static class PrayerTime {
        public final String name;
        public final LocalTime time;

        public PrayerTime(String name, LocalTime time) {
            this.name = name;
            this.time = time;
        }
    }

    public static class NextPrayer {
        public final String name;
        public final LocalTime time;
        public final long minutesRemaining;

        public NextPrayer(String name, LocalTime time, long minutesRemaining) {
            this.name = name;
            this.time = time;
            this.minutesRemaining = minutesRemaining;
        }
    }

    public static List<PrayerTime> calculate(LocalDate date) {
        int dayOfYear = date.getDayOfYear();
        // Solar declination
        double B = 2.0 * Math.PI * (dayOfYear - 81) / 365.0;
        double declination = Math.toRadians(23.45 * Math.sin(B));
        
        // Equation of time in minutes
        double eot = 9.87 * Math.sin(2 * B) - 7.53 * Math.cos(B) - 1.5 * Math.sin(B);
        
        // Solar noon in hours
        double solarNoon = 12.0 + (TIMEZONE * 15.0 - LNG) / 15.0 - eot / 60.0;

        double latRad = Math.toRadians(LAT);

        // Fajr (18 degrees below horizon)
        double fajrHourAngle = hourAngle(-18.0, latRad, declination);
        double fajr = solarNoon - fajrHourAngle;

        // Sunrise (0.833 degrees below horizon)
        double sunriseHourAngle = hourAngle(-0.833, latRad, declination);
        double sunrise = solarNoon - sunriseHourAngle;

        // Dhuhr
        double dhuhr = solarNoon + 0.05; // 3 minutes past zenith

        // Asr (Shafi'i: shadow length = object + noon shadow)
        double asrAltitude = Math.toDegrees(Math.atan(1.0 / (1.0 + Math.tan(Math.abs(latRad - declination)))));
        double asrHourAngle = hourAngle(asrAltitude, latRad, declination);
        double asr = solarNoon + asrHourAngle;

        // Maghrib (sunset)
        double maghrib = solarNoon + sunriseHourAngle;

        // Isha (18 degrees below horizon)
        double isha = solarNoon + fajrHourAngle;

        List<PrayerTime> list = new ArrayList<>(6);
        list.add(new PrayerTime("Fajr", toLocalTime(fajr)));
        list.add(new PrayerTime("Sunrise", toLocalTime(sunrise)));
        list.add(new PrayerTime("Dhuhr", toLocalTime(dhuhr)));
        list.add(new PrayerTime("Asr", toLocalTime(asr)));
        list.add(new PrayerTime("Maghrib", toLocalTime(maghrib)));
        list.add(new PrayerTime("Isha", toLocalTime(isha)));
        return list;
    }

    private static double hourAngle(double altitudeDeg, double latRad, double declRad) {
        double altRad = Math.toRadians(altitudeDeg);
        double cosHA = (Math.sin(altRad) - Math.sin(latRad) * Math.sin(declRad)) /
                (Math.cos(latRad) * Math.cos(declRad));
        cosHA = Math.max(-1.0, Math.min(1.0, cosHA));
        return Math.toDegrees(Math.acos(cosHA)) / 15.0;
    }

    private static LocalTime toLocalTime(double decimalHours) {
        while (decimalHours < 0) decimalHours += 24.0;
        while (decimalHours >= 24) decimalHours -= 24.0;
        int hours = (int) decimalHours;
        int minutes = (int) Math.round((decimalHours - hours) * 60.0);
        if (minutes >= 60) {
            hours = (hours + 1) % 24;
            minutes = 0;
        }
        return LocalTime.of(hours, minutes);
    }

    public static NextPrayer findNext(LocalDateTime now) {
        List<PrayerTime> today = calculate(now.toLocalDate());
        LocalTime current = now.toLocalTime();

        for (PrayerTime pt : today) {
            if (pt.name.equals("Sunrise")) continue; // Sunrise is not an obligatory prayer
            if (pt.time.isAfter(current)) {
                long diff = java.time.Duration.between(current, pt.time).toMinutes();
                return new NextPrayer(pt.name, pt.time, diff);
            }
        }

        // If after Isha, next is tomorrow's Fajr
        List<PrayerTime> tomorrow = calculate(now.toLocalDate().plusDays(1));
        PrayerTime fajr = tomorrow.get(0);
        long diff = java.time.Duration.between(now, LocalDateTime.of(now.toLocalDate().plusDays(1), fajr.time)).toMinutes();
        return new NextPrayer("Fajr", fajr.time, diff);
    }
}
