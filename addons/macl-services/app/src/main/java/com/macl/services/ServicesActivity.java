package com.macl.services;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ServicesActivity extends Activity {

    private View tabFis, tabFisWeb, tabBus, tabPrayer;
    private View contentFis, contentFisWeb, contentBus, contentPrayer;
    private TextView btnDepartures, btnArrivals;
    private TextView btnFilterAll, btnFilterIntl, btnFilterDom, btnRefreshFis;
    private TextView colDestOrigin, colGateBelt, tvFisLastUpdated;
    private LinearLayout flightListContainer, busListContainer, prayerCardsRow;
    private TextView prayerNextName, prayerCountdown;
    private WebView webViewFis;

    private boolean isDepartures = true;
    private String currentCategory = "all"; // "all", "international", "domestic"
    private boolean webViewLoaded = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshData();
            FisData.fetchLiveFlights(() -> {
                populateFlights();
                updateLastUpdatedBadge();
            });
            handler.postDelayed(this, 30000); // 30s auto-refresh
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_services);

        tabFis = findViewById(R.id.tabFis);
        tabFisWeb = findViewById(R.id.tabFisWeb);
        tabBus = findViewById(R.id.tabBus);
        tabPrayer = findViewById(R.id.tabPrayer);

        contentFis = findViewById(R.id.contentFis);
        contentFisWeb = findViewById(R.id.contentFisWeb);
        contentBus = findViewById(R.id.contentBus);
        contentPrayer = findViewById(R.id.contentPrayer);

        btnDepartures = findViewById(R.id.btnDepartures);
        btnArrivals = findViewById(R.id.btnArrivals);
        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterIntl = findViewById(R.id.btnFilterIntl);
        btnFilterDom = findViewById(R.id.btnFilterDom);
        btnRefreshFis = findViewById(R.id.btnRefreshFis);

        colDestOrigin = findViewById(R.id.colDestOrigin);
        colGateBelt = findViewById(R.id.colGateBelt);
        tvFisLastUpdated = findViewById(R.id.tvFisLastUpdated);

        flightListContainer = findViewById(R.id.flightListContainer);
        busListContainer = findViewById(R.id.busListContainer);
        prayerCardsRow = findViewById(R.id.prayerCardsRow);

        prayerNextName = findViewById(R.id.prayerNextName);
        prayerCountdown = findViewById(R.id.prayerCountdown);
        webViewFis = findViewById(R.id.webViewFis);

        setupTabs();
        setupFisFilters();
        setupWebView();
        int initialTab = getIntent().getIntExtra("tab", 0);
        showTab(initialTab);

        // Fetch live flight information from fis.com.mv
        FisData.fetchLiveFlights(() -> {
            populateFlights();
            updateLastUpdatedBadge();
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int tab = intent.getIntExtra("tab", -1);
        if (tab >= 0) {
            showTab(tab);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshData();
        handler.postDelayed(ticker, 30000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    private void setupTabs() {
        tabFis.setOnClickListener(v -> showTab(0));
        tabFisWeb.setOnClickListener(v -> showTab(1));
        tabBus.setOnClickListener(v -> showTab(2));
        tabPrayer.setOnClickListener(v -> showTab(3));

        tabFis.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) showTab(0); });
        tabFisWeb.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) showTab(1); });
        tabBus.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) showTab(2); });
        tabPrayer.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) showTab(3); });
    }

    private void showTab(int index) {
        tabFis.setSelected(index == 0);
        tabFisWeb.setSelected(index == 1);
        tabBus.setSelected(index == 2);
        tabPrayer.setSelected(index == 3);

        contentFis.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        contentFisWeb.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        contentBus.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        contentPrayer.setVisibility(index == 3 ? View.VISIBLE : View.GONE);

        View tabView = null;
        if (index == 0) tabView = tabFis;
        else if (index == 1) tabView = tabFisWeb;
        else if (index == 2) tabView = tabBus;
        else if (index == 3) tabView = tabPrayer;
        if (tabView != null) tabView.requestFocus();

        if (index == 1 && !webViewLoaded && webViewFis != null) {
            webViewLoaded = true;
            webViewFis.loadUrl("https://fis.com.mv");
        }
        refreshData();
    }

    private void setupWebView() {
        if (webViewFis == null) return;
        WebSettings settings = webViewFis.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        webViewFis.setBackgroundColor(Color.parseColor("#0C184A"));
        webViewFis.setWebViewClient(new WebViewClient());
        webViewFis.setWebChromeClient(new WebChromeClient());
    }

    private void setupFisFilters() {
        btnDepartures.setSelected(true);
        btnArrivals.setSelected(false);
        btnFilterAll.setSelected(true);
        btnFilterIntl.setSelected(false);
        btnFilterDom.setSelected(false);

        btnDepartures.setOnClickListener(v -> {
            isDepartures = true;
            btnDepartures.setSelected(true);
            btnArrivals.setSelected(false);
            colDestOrigin.setText("ROUTING (DEST)");
            colGateBelt.setText("TERMINAL & GATE");
            populateFlights();
        });

        btnArrivals.setOnClickListener(v -> {
            isDepartures = false;
            btnDepartures.setSelected(false);
            btnArrivals.setSelected(true);
            colDestOrigin.setText("ROUTING (ORIGIN)");
            colGateBelt.setText("TERMINAL & BELT");
            populateFlights();
        });

        btnFilterAll.setOnClickListener(v -> {
            currentCategory = "all";
            btnFilterAll.setSelected(true);
            btnFilterIntl.setSelected(false);
            btnFilterDom.setSelected(false);
            populateFlights();
        });

        btnFilterIntl.setOnClickListener(v -> {
            currentCategory = "international";
            btnFilterAll.setSelected(false);
            btnFilterIntl.setSelected(true);
            btnFilterDom.setSelected(false);
            populateFlights();
        });

        btnFilterDom.setOnClickListener(v -> {
            currentCategory = "domestic";
            btnFilterAll.setSelected(false);
            btnFilterIntl.setSelected(false);
            btnFilterDom.setSelected(true);
            populateFlights();
        });

        btnRefreshFis.setOnClickListener(v -> {
            btnRefreshFis.setText("Refreshing...");
            FisData.fetchLiveFlights(() -> {
                btnRefreshFis.setText("↻ Refresh");
                populateFlights();
                updateLastUpdatedBadge();
            });
        });
    }

    private void updateLastUpdatedBadge() {
        if (tvFisLastUpdated != null) {
            tvFisLastUpdated.setText("Live fis.com.mv • " + FisData.getLastUpdated());
        }
    }

    private void refreshData() {
        populateFlights();
        populateBusSchedule();
        populatePrayerTimes();
    }

    private void populateFlights() {
        flightListContainer.removeAllViews();
        List<FisData.Flight> flights = FisData.getFlights(isDepartures, currentCategory);

        for (int i = 0; i < flights.size(); i++) {
            FisData.Flight f = flights.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(16), dp(10), dp(16), dp(10));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setBackgroundResource(R.drawable.card_bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            row.setLayoutParams(lp);

            // 1. Scheduled / Estimated (Column matching fis.com.mv)
            LinearLayout timeCol = new LinearLayout(this);
            timeCol.setOrientation(LinearLayout.VERTICAL);
            timeCol.setLayoutParams(new LinearLayout.LayoutParams(dp(140), LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView tvSched = new TextView(this);
            tvSched.setText(f.scheduledTime);
            tvSched.setTextColor(Color.WHITE);
            tvSched.setTextSize(15);
            tvSched.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            timeCol.addView(tvSched);

            if (f.estimatedTime != null && !f.estimatedTime.isEmpty() && !f.estimatedTime.equals(f.scheduledTime)) {
                TextView tvEst = new TextView(this);
                tvEst.setText("est " + f.estimatedTime);
                tvEst.setTextColor(Color.parseColor("#FFC629"));
                tvEst.setTextSize(11);
                timeCol.addView(tvEst);
            }
            row.addView(timeCol);

            // 2. Flight & Airline
            LinearLayout flightCol = new LinearLayout(this);
            flightCol.setOrientation(LinearLayout.VERTICAL);
            flightCol.setLayoutParams(new LinearLayout.LayoutParams(dp(200), LinearLayout.LayoutParams.WRAP_CONTENT));

            LinearLayout numRow = new LinearLayout(this);
            numRow.setOrientation(LinearLayout.HORIZONTAL);
            numRow.setGravity(Gravity.CENTER_VERTICAL);

            if (f.airlineCode != null && !f.airlineCode.isEmpty()) {
                TextView codeBadge = new TextView(this);
                codeBadge.setText(f.airlineCode);
                codeBadge.setTextSize(10);
                codeBadge.setTextColor(Color.WHITE);
                codeBadge.setBackgroundColor(Color.parseColor("#1E293B"));
                codeBadge.setPadding(dp(5), dp(2), dp(5), dp(2));
                LinearLayout.LayoutParams codeLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                codeLp.rightMargin = dp(6);
                codeBadge.setLayoutParams(codeLp);
                numRow.addView(codeBadge);
            }

            TextView tvFlight = new TextView(this);
            tvFlight.setText(f.flightNo);
            tvFlight.setTextColor(Color.parseColor("#FFC629"));
            tvFlight.setTextSize(15);
            tvFlight.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
            numRow.addView(tvFlight);
            flightCol.addView(numRow);

            TextView tvAirline = new TextView(this);
            tvAirline.setText(f.airline);
            tvAirline.setTextColor(Color.parseColor("#94A3B8"));
            tvAirline.setTextSize(12);
            flightCol.addView(tvAirline);
            row.addView(flightCol);

            // 3. Routing (Destination / Origin)
            LinearLayout routeCol = new LinearLayout(this);
            routeCol.setOrientation(LinearLayout.HORIZONTAL);
            routeCol.setGravity(Gravity.CENTER_VERTICAL);
            routeCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

            String code = isDepartures ? f.destinationCode : f.originCode;
            String name = isDepartures ? f.destination : f.origin;

            if (code != null && !code.isEmpty()) {
                TextView tvCode = new TextView(this);
                tvCode.setText(code);
                tvCode.setTextColor(Color.parseColor("#00B2E3"));
                tvCode.setTextSize(14);
                tvCode.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
                tvCode.setPadding(0, 0, dp(8), 0);
                routeCol.addView(tvCode);
            }

            TextView tvDest = new TextView(this);
            tvDest.setText(name);
            tvDest.setTextColor(Color.WHITE);
            tvDest.setTextSize(14);
            routeCol.addView(tvDest);
            row.addView(routeCol);

            // 4. Terminal & Gate/Belt
            TextView tvGate = new TextView(this);
            tvGate.setLayoutParams(new LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT));
            String termText = (f.terminal != null && !f.terminal.isEmpty() ? f.terminal + " " : "")
                    + (f.category != null && f.category.equalsIgnoreCase("domestic") ? "DOM" : "INTL")
                    + (f.gate != null && !f.gate.isEmpty() ? " • " + f.gate : "");
            tvGate.setText(termText);
            tvGate.setTextColor(Color.parseColor("#E2E8F0"));
            tvGate.setTextSize(13);
            row.addView(tvGate);

            // 5. Status Badge
            TextView tvStatus = new TextView(this);
            tvStatus.setLayoutParams(new LinearLayout.LayoutParams(dp(130), LinearLayout.LayoutParams.WRAP_CONTENT));
            tvStatus.setText(f.getDisplayStatus());
            tvStatus.setGravity(Gravity.END);
            tvStatus.setTextSize(13);
            tvStatus.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tvStatus.setTextColor(f.getStatusColor());
            row.addView(tvStatus);

            flightListContainer.addView(row);
        }
    }

    private void populateBusSchedule() {
        busListContainer.removeAllViews();
        List<BusScheduleData.BusRoute> routes = BusScheduleData.getRoutes();
        LocalTime now = LocalTime.now();

        for (BusScheduleData.BusRoute r : routes) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.card_bg);
            card.setPadding(dp(20), dp(16), dp(20), dp(16));
            card.setFocusable(true);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(12);
            card.setLayoutParams(lp);

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            TextView code = new TextView(this);
            code.setText(r.routeName);
            code.setTextColor(Color.WHITE);
            code.setTextSize(14);
            code.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
            header.addView(code);

            View space = new View(this);
            header.addView(space, new LinearLayout.LayoutParams(0, 1, 1.0f));

            TextView next = new TextView(this);
            next.setText("Next: in " + r.getNextDepartureMinutes(now) + " mins");
            next.setTextColor(Color.parseColor("#00B2E3"));
            next.setTextSize(14);
            header.addView(next);

            card.addView(header);

            TextView dest = new TextView(this);
            dest.setText(r.destination + " • " + r.stops);
            dest.setTextColor(Color.parseColor("#8CFFFFFF"));
            dest.setTextSize(13);
            dest.setPadding(0, dp(6), 0, 0);
            card.addView(dest);

            busListContainer.addView(card);
        }
    }

    private void populatePrayerTimes() {
        LocalDateTime now = LocalDateTime.now(PrayerCalculator.MALDIVES_ZONE);
        List<PrayerCalculator.PrayerTime> prayers = PrayerCalculator.calculate(now.toLocalDate());
        PrayerCalculator.NextPrayer next = PrayerCalculator.findNext(now);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("hh:mm a");
        if (prayerNextName != null) prayerNextName.setText(next.name);
        if (prayerCountdown != null) prayerCountdown.setText("in " + next.minutesRemaining + " mins (" + next.time.format(fmt) + ")");

        prayerCardsRow.removeAllViews();
        for (PrayerCalculator.PrayerTime pt : prayers) {
            addPrayerCard(pt.name, pt.time, pt.name.equalsIgnoreCase(next.name));
        }
    }

    private void addPrayerCard(String name, LocalTime time, boolean isNext) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setPadding(dp(12), dp(20), dp(12), dp(20));
        card.setFocusable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        lp.rightMargin = dp(10);
        card.setLayoutParams(lp);

        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextColor(isNext ? Color.parseColor("#FFC629") : Color.WHITE);
        tvName.setTextSize(15);
        tvName.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(tvName);

        TextView tvTime = new TextView(this);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("hh:mm a");
        tvTime.setText(time.format(fmt));
        tvTime.setTextColor(isNext ? Color.parseColor("#00B2E3") : Color.parseColor("#B3FFFFFF"));
        tvTime.setTextSize(15);
        tvTime.setPadding(0, dp(8), 0, 0);
        card.addView(tvTime);

        if (isNext) {
            TextView badge = new TextView(this);
            badge.setText("UPCOMING");
            badge.setTextSize(10);
            badge.setTextColor(Color.parseColor("#FFC629"));
            badge.setPadding(0, dp(6), 0, 0);
            card.addView(badge);
        }

        prayerCardsRow.addView(card);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
