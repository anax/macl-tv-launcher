package com.macl.services;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public final class BusScheduleData {

    public static class BusRoute {
        public final String routeName;
        public final String destination;
        public final String stops;
        public final int intervalMinutes;
        public final String operatingHours;
        public final String fare;

        public BusRoute(String routeName, String destination, String stops, int intervalMinutes, String operatingHours, String fare) {
            this.routeName = routeName;
            this.destination = destination;
            this.stops = stops;
            this.intervalMinutes = intervalMinutes;
            this.operatingHours = operatingHours;
            this.fare = fare;
        }

        public int getNextDepartureMinutes(LocalTime now) {
            int currentMinute = now.getMinute();
            int mod = currentMinute % intervalMinutes;
            int next = intervalMinutes - mod;
            return next == 0 ? intervalMinutes : next;
        }
    }

    public static List<BusRoute> getRoutes() {
        List<BusRoute> list = new ArrayList<>();
        list.add(new BusRoute(
                "RTL Route R1 (Bridge Express)",
                "Male' City Terminal",
                "Airport Terminal \u2192 Sinamal\u00e9 Bridge \u2192 Artificial Beach \u2192 Carnival / Westpark",
                15,
                "24 Hours / Daily",
                "MVR 10 (RTL Card / Mastercard / Cash)"
        ));
        list.add(new BusRoute(
                "RTL Route R2 (Hulhumal\u00e9 Phase 1)",
                "Hulhumal\u00e9 Central Park",
                "Airport Terminal \u2192 Airport Link Road \u2192 Ferry Terminal \u2192 Central Park \u2192 Tree Top Hospital",
                15,
                "24 Hours / Daily",
                "MVR 10 (RTL Card / Mastercard / Cash)"
        ));
        list.add(new BusRoute(
                "RTL Route R3 (Hulhumal\u00e9 Phase 2)",
                "Hiyaa Flats / Phase 2 Marina",
                "Airport Terminal \u2192 Channel Bridge \u2192 Hiyaa Towers 1-16 \u2192 Urban Isle",
                20,
                "24 Hours / Daily",
                "MVR 15 (RTL Card / Mastercard / Cash)"
        ));
        list.add(new BusRoute(
                "MACL Airport Airside & Seaplane Shuttle",
                "TMA Seaplane Terminal / Water Aerodrome",
                "Terminal 1 (International) \u2192 Domestic Terminal \u2192 Cargo Complex \u2192 Seaplane Terminal",
                10,
                "05:30 \u2014 18:30 Daily",
                "Free Airport Transfer"
        ));
        return list;
    }
}
