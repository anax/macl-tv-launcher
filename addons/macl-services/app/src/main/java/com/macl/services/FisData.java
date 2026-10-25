package com.macl.services;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FisData {

    public static class Flight {
        public final String id;
        public final String flightNo;
        public final String airline;
        public final String airlineCode;
        public final String origin;
        public final String originCode;
        public final String destination;
        public final String destinationCode;
        public final String scheduledTime;
        public final String estimatedTime;
        public final String terminal;
        public final String gate;
        public final String status;
        public final String type;       // "departure" or "arrival"
        public final String category;   // "international" or "domestic"
        public final int delayMinutes;

        public Flight(String id, String flightNo, String airline, String airlineCode,
                      String origin, String originCode, String destination, String destinationCode,
                      String scheduledTime, String estimatedTime, String terminal, String gate,
                      String status, String type, String category, int delayMinutes) {
            this.id = id;
            this.flightNo = flightNo;
            this.airline = airline;
            this.airlineCode = airlineCode;
            this.origin = origin;
            this.originCode = originCode;
            this.destination = destination;
            this.destinationCode = destinationCode;
            this.scheduledTime = scheduledTime;
            this.estimatedTime = estimatedTime;
            this.terminal = terminal;
            this.gate = gate;
            this.status = status;
            this.type = type;
            this.category = category;
            this.delayMinutes = delayMinutes;
        }

        public int getStatusColor() {
            String s = status.toLowerCase();
            if (s.contains("landed") || s.contains("departed")) {
                return 0xFF3B82F6; // Blue
            } else if (s.contains("on-time") || s.contains("on time")) {
                return 0xFF10B981; // Emerald Green
            } else if (s.contains("boarding") || s.contains("final-call") || s.contains("final call") || s.contains("closing")) {
                return 0xFFFFC629; // MACL Gold
            } else if (s.contains("delayed") || s.contains("cancelled")) {
                return 0xFFEF4444; // Red
            } else {
                return 0xFF00B2E3; // Turquoise / Scheduled
            }
        }

        public String getDisplayStatus() {
            if ("on-time".equalsIgnoreCase(status)) return "On Time";
            if ("gate-closed".equalsIgnoreCase(status)) return "Gate Closed";
            if ("final-call".equalsIgnoreCase(status)) return "Final Call";
            if (status == null || status.isEmpty()) return "Scheduled";
            return Character.toUpperCase(status.charAt(0)) + status.substring(1);
        }
    }

    private static final List<Flight> liveFlights = new ArrayList<>();
    private static String lastUpdated = "Live";
    private static boolean isFetching = false;

    public static synchronized String getLastUpdated() {
        return lastUpdated;
    }

    public static synchronized List<Flight> getFlights(boolean departure, String categoryFilter) {
        List<Flight> source = liveFlights.isEmpty() ? getFallbackFlights() : liveFlights;
        List<Flight> result = new ArrayList<>();
        String targetType = departure ? "departure" : "arrival";

        for (Flight f : source) {
            if (!targetType.equalsIgnoreCase(f.type)) continue;
            if (categoryFilter != null && !categoryFilter.equalsIgnoreCase("all")) {
                if (!categoryFilter.equalsIgnoreCase(f.category)) continue;
            }
            result.add(f);
        }
        return result;
    }

    public static void fetchLiveFlights(Runnable onComplete) {
        if (isFetching) return;
        isFetching = true;

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://fis.com.mv/api/flights");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "CleanYourTV-MACL/1.0");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    String updated = json.optString("lastUpdated", "Live");
                    JSONArray arr = json.optJSONArray("flights");

                    if (arr != null && arr.length() > 0) {
                        List<Flight> parsed = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            String id = obj.optString("id", "");
                            String flightNo = obj.optString("flightNumber", "");
                            String airline = obj.optString("airline", "");
                            String airlineCode = obj.optString("airlineCode", "");
                            String origin = obj.optString("origin", "");
                            String originCode = obj.optString("originCode", "");
                            String destination = obj.optString("destination", "");
                            String destinationCode = obj.optString("destinationCode", "");
                            String scheduledTime = obj.optString("scheduledTime", "");
                            String estimatedTime = obj.optString("estimatedTime", "");
                            String terminal = obj.optString("terminal", "");
                            String gate = obj.optString("gate", "");
                            String status = obj.optString("status", "");
                            String type = obj.optString("type", "departure");
                            String category = obj.optString("category", "international");
                            int delay = obj.optInt("delayMinutes", 0);

                            parsed.add(new Flight(id, flightNo, airline, airlineCode, origin, originCode,
                                    destination, destinationCode, scheduledTime, estimatedTime, terminal, gate,
                                    status, type, category, delay));
                        }
                        synchronized (FisData.class) {
                            liveFlights.clear();
                            liveFlights.addAll(parsed);
                            lastUpdated = updated;
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
                isFetching = false;
                if (onComplete != null) {
                    new Handler(Looper.getMainLooper()).post(onComplete);
                }
            }
        }).start();
    }

    private static List<Flight> getFallbackFlights() {
        List<Flight> list = new ArrayList<>();
        list.add(new Flight("dep-1", "SQ 437", "Singapore Airlines", "SQ", "Male", "MLE", "Singapore", "SIN", "23:30", "23:30", "T1", "Gate 4", "final-call", "departure", "international", 0));
        list.add(new Flight("dep-2", "QR 675", "Qatar Airways", "QR", "Male", "MLE", "Doha", "DOH", "21:05", "21:05", "T1", "Gate 1", "on-time", "departure", "international", 0));
        list.add(new Flight("dep-3", "EK 653", "Emirates", "EK", "Male", "MLE", "Dubai", "DXB", "23:00", "23:00", "T1", "Gate 2", "gate-closed", "departure", "international", 0));
        list.add(new Flight("dep-4", "UL 104", "Srilankan Airlines", "UL", "Male", "MLE", "Colombo", "CMB", "20:55", "20:55", "T1", "Gate 5", "on-time", "departure", "international", 0));
        list.add(new Flight("dep-5", "EY 377", "Etihad Airways", "EY", "Male", "MLE", "Abu Dhabi", "AUH", "21:45", "21:45", "T1", "Gate 3", "on-time", "departure", "international", 0));
        list.add(new Flight("dep-6", "TK 735", "Turkish Airlines", "TK", "Male", "MLE", "Istanbul", "IST", "22:35", "22:35", "T1", "Gate 2", "on-time", "departure", "international", 0));
        list.add(new Flight("dep-7", "Q2 118", "Maldivian", "Q2", "Male", "MLE", "Kooddoo", "GKK", "23:30", "23:30", "T2", "Gate D1", "on-time", "departure", "domestic", 0));
        list.add(new Flight("dep-8", "Q2 250", "Maldivian", "Q2", "Male", "MLE", "Hanimaadhoo", "HAQ", "23:00", "23:00", "T2", "Gate D2", "on-time", "departure", "domestic", 0));
        list.add(new Flight("dep-9", "MH 486", "Malaysia Airlines", "MH", "Male", "MLE", "Kuala Lumpur", "KUL", "23:40", "23:40", "T1", "Gate 4", "on-time", "departure", "international", 0));
        list.add(new Flight("dep-10", "FZ 1570", "Flydubai", "FZ", "Male", "MLE", "Dubai", "DXB", "23:30", "23:30", "T1", "Gate 1", "gate-closed", "departure", "international", 0));

        list.add(new Flight("arr-1", "EK 658", "Emirates", "EK", "Dubai", "DXB", "Male", "MLE", "20:30", "20:30", "T1", "Belt 1", "landed", "arrival", "international", 0));
        list.add(new Flight("arr-2", "SQ 436", "Singapore Airlines", "SQ", "Singapore", "SIN", "Male", "MLE", "21:50", "21:50", "T1", "Belt 3", "landed", "arrival", "international", 0));
        list.add(new Flight("arr-3", "QR 674", "Qatar Airways", "QR", "Doha", "DOH", "Male", "MLE", "22:45", "22:45", "T1", "Belt 2", "landed", "arrival", "international", 0));
        list.add(new Flight("arr-4", "AI 267", "Air India", "AI", "Bengaluru", "BLR", "Male", "MLE", "23:40", "23:40", "T1", "Belt 4", "on-time", "arrival", "international", 0));
        list.add(new Flight("arr-5", "Q2 119", "Maldivian", "Q2", "Dharavandhoo", "DRV", "Male", "MLE", "22:15", "22:15", "T2", "Belt D", "landed", "arrival", "domestic", 0));
        return list;
    }
}
