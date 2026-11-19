
package com.example.abdullah_ahmed_s2530964;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private Button incidentsButton;
    private Button roadworksButton;
    private TextView recordCount;
    private ListView trafficList;
    private EditText searchRoad;
    private Spinner distanceSpinner;

    // Glasgow city centre
    private static final double GLASGOW_LATITUDE = 55.8642;
    private static final double GLASGOW_LONGITUDE = -4.2518;

    private double selectedDistanceKm = 0;

    // All downloaded records
    private final ArrayList<TrafficEvent> allTrafficEvents =
            new ArrayList<>();

    // Records displayed after filtering
    private final ArrayList<TrafficEvent> trafficEvents =
            new ArrayList<>();

    private ArrayAdapter<TrafficEvent> adapter;

    // Traffic Scotland API credentials
    //Usning buildConfig to retrieve user ID,key instead of providing the original client ID and key
    private final String clientId =
            BuildConfig.TRAFFIC_SCOTLAND_CLIENT_ID;

    private final String clientKey =
            BuildConfig.TRAFFIC_SCOTLAND_CLIENT_KEY;

    private final String unplannedEventsURL =
            "https://datex2.trafficscotland.org/rest/2.3/publications/UnplannedEvents/Content.xml";

    private final String roadworksURL =
            "https://datex2.trafficscotland.org/rest/2.3/publications/CurrentRoadworks/Content.xml";

    // Prevent older downloads from replacing newer data
    private int requestNumber = 0;

    // Automatic refresh every 5 minutes
    private static final long REFRESH_INTERVAL =
            5 * 60 * 1000L;

    private final Handler refreshHandler =
            new Handler(Looper.getMainLooper());

    private String selectedFeedURL = "";
    private String selectedFeedCategory = "";

    private boolean appVisible = false;

    //Created this method to run data refreshing every 5 min
    private final Runnable refreshRunnable = new Runnable() {

        @Override
        public void run() {

            if (!appVisible || selectedFeedURL.isEmpty()) {
                return;
            }

            Log.d(
                    "TRAFFIC_REFRESH",
                    "Automatic refresh: " + selectedFeedCategory
            );

            loadTraffic(selectedFeedURL,selectedFeedCategory);

            refreshHandler.postDelayed(this, REFRESH_INTERVAL);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        // Set up the interface in the method instead of writing them inside onCrate
        setupLayout();
    }

    // Set up portrait or landscape interface
    private void setupLayout() {

        setContentView(R.layout.activity_main);

        // Apply system bar padding
        final int padding = (int) (8 * getResources().getDisplayMetrics().density);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main),
                (v, insets) -> {

                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    v.setPadding(
                            bars.left + padding,
                            bars.top + padding,
                            bars.right + padding,
                            bars.bottom + padding
                    );

                    return insets;
                }
        );

        // Connect Java variables to XML views
        incidentsButton = findViewById(R.id.incidentsButton);

        roadworksButton = findViewById(R.id.roadworksButton);

        recordCount = findViewById(R.id.recordCount);

        trafficList = findViewById(R.id.trafficList);

        searchRoad = findViewById(R.id.searchRoad);

        distanceSpinner = findViewById(R.id.distanceSpinner);

        setupDistanceSpinner();
        setupTrafficList();
        setupSearch();
        setupButtons();

        // Display the records already held in memory
        filterTraffic(searchRoad.getText().toString());
    }

    // Set up the distance filter
    private void setupDistanceSpinner() {

        String[] distanceOptions = {
                "All distances",
                "Within 10 km of Glasgow",
                "Within 25 km of Glasgow",
                "Within 50 km of Glasgow"
        };

        ArrayAdapter<String> distanceAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, distanceOptions);

        distanceAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        distanceSpinner.setAdapter(distanceAdapter);

        // Restore the selected distance
        int selectedPosition = 0;

        if (selectedDistanceKm == 10) {
            selectedPosition = 1;
        } else if (selectedDistanceKm == 25) {
            selectedPosition = 2;
        } else if (selectedDistanceKm == 50) {
            selectedPosition = 3;
        }

        distanceSpinner.setSelection(selectedPosition);

        distanceSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {

                        switch (position) {

                            case 1:
                                selectedDistanceKm = 10;
                                break;

                            case 2:
                                selectedDistanceKm = 25;
                                break;

                            case 3:
                                selectedDistanceKm = 50;
                                break;

                            default:
                                selectedDistanceKm = 0;
                                break;
                        }

                        if (adapter != null) {
                            filterTraffic(
                                    searchRoad.getText().toString()
                            );
                        }
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent) {
                    }
                }
        );
    }

    // Set up the traffic records list
    private void setupTrafficList() {

        adapter = new ArrayAdapter<TrafficEvent>(
                this,
                android.R.layout.simple_list_item_1,
                trafficEvents) {

            @Override
            public View getView(
                    int position,
                    View convertView,
                    ViewGroup parent) {

                View row = super.getView(
                        position,
                        convertView,
                        parent
                );

                TrafficEvent event = getItem(position);

                TextView rowText =
                        row.findViewById(android.R.id.text1);

                if (event != null) {

                    int colour = getRoadworksColour(event);

                    if (colour == Color.TRANSPARENT) {

                        row.setBackgroundColor(
                                Color.TRANSPARENT
                        );

                        android.content.res.TypedArray colours =
                                getContext().obtainStyledAttributes(
                                        new int[]{
                                                android.R.attr.textColorPrimary
                                        }
                                );

                        try {
                            rowText.setTextColor(
                                    colours.getColor(
                                            0,
                                            Color.WHITE
                                    )
                            );
                        } finally {
                            colours.recycle();
                        }

                    } else {

                        row.setBackgroundColor(colour);

                        rowText.setTextColor(
                                Color.rgb(30, 30, 30)
                        );
                    }
                }

                return row;
            }
        };

        trafficList.setAdapter(adapter);

        trafficList.setOnItemClickListener(
                (parent, view, position, id) -> {

                    TrafficEvent event =
                            trafficEvents.get(position);

                    showEventDetails(event);
                }
        );
    }

    // Search traffic records
    private void setupSearch() {

        searchRoad.addTextChangedListener(
                new TextWatcher() {

                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count) {

                        filterTraffic(s.toString());
                    }

                    @Override
                    public void afterTextChanged(
                            Editable s) {
                    }
                }
        );
    }

    // Category buttons
    private void setupButtons() {

        incidentsButton.setOnClickListener(v -> {

            selectedFeedURL = unplannedEventsURL;
            selectedFeedCategory = "Unplanned Events";

            loadTraffic(
                    selectedFeedURL,
                    selectedFeedCategory
            );

            scheduleAutomaticRefresh();
        });

        roadworksButton.setOnClickListener(v -> {

            selectedFeedURL = roadworksURL;
            selectedFeedCategory = "Current Roadworks";

            loadTraffic(
                    selectedFeedURL,
                    selectedFeedCategory
            );

            scheduleAutomaticRefresh();
        });
    }

    // Handle rotation without losing downloaded records
    @Override
    public void onConfigurationChanged(Configuration newConfig) {

        // Remember current search and distance selection
        String savedSearch =
                searchRoad.getText().toString();

        double savedDistance =
                selectedDistanceKm;

        // Hide keyboard before changing layout
        InputMethodManager keyboard =
                (InputMethodManager)
                        getSystemService(
                                Context.INPUT_METHOD_SERVICE
                        );

        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(
                    searchRoad.getWindowToken(),
                    0
            );
        }

        super.onConfigurationChanged(newConfig);

        // Keep current filter values
        selectedDistanceKm = savedDistance;

        // Load the appropriate XML layout again
        setupLayout();

        // Restore the search field
        searchRoad.setText(savedSearch);

        // Reapply filters to existing records
        filterTraffic(savedSearch);

        Log.d(
                "TRAFFIC_ROTATION",
                "Layout changed. Records preserved: "
                        + allTrafficEvents.size()
        );
    }

    // Schedule automatic refresh
    private void scheduleAutomaticRefresh() {

        refreshHandler.removeCallbacks(refreshRunnable);

        if (appVisible && !selectedFeedURL.isEmpty()) {

            refreshHandler.postDelayed(
                    refreshRunnable,
                    REFRESH_INTERVAL
            );
        }
    }

    // Download and display the selected traffic feed
    private void loadTraffic(String url, String category) {

        int currentRequest = ++requestNumber;

        // Keep existing records while downloading
        if (!allTrafficEvents.isEmpty()) {

            recordCount.setText(
                    "Refreshing " + category + "..."
            );

        } else {

            recordCount.setText(
                    "Loading " + category + "..."
            );
        }

        new Thread(() -> {

            try {

                String xml = downloadXML(url);

                ArrayList<TrafficEvent> loadedEvents =
                        parseTrafficXML(xml, category);

                runOnUiThread(() -> {

                    if (currentRequest != requestNumber
                            || isFinishing()
                            || isDestroyed()) {
                        return;
                    }

                    // Replace records only after success
                    allTrafficEvents.clear();

                    allTrafficEvents.addAll(
                            loadedEvents
                    );

                    // Restore existing filters
                    filterTraffic(
                            searchRoad.getText().toString()
                    );

                    Log.d(
                            "TRAFFIC_REFRESH",
                            "Successfully loaded "
                                    + loadedEvents.size()
                                    + " records"
                    );
                });

            } catch (Exception e) {

                Log.e(
                        "TRAFFIC_ERROR",
                        "Unable to download " + category,
                        e
                );

                runOnUiThread(() -> {

                    if (currentRequest != requestNumber
                            || isFinishing()
                            || isDestroyed()) {
                        return;
                    }

                    // Keep previous records after failure
                    if (!allTrafficEvents.isEmpty()) {

                        filterTraffic(
                                searchRoad.getText().toString()
                        );

                        recordCount.setText(
                                "Connection failed. Showing "
                                        + "previously downloaded records ("
                                        + trafficEvents.size()
                                        + " displayed)."
                        );

                    } else {

                        recordCount.setText(
                                "Unable to load "
                                        + category
                                        + ". Check your internet connection."
                        );
                    }
                });
            }

        }).start();
    }

    // Download XML using Traffic Scotland authentication
    private String downloadXML(String address) throws Exception {

        HttpURLConnection connection = null;

        try {

            URL url = new URL(address);

            connection =
                    (HttpURLConnection)
                            url.openConnection();

            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);

            String credentials =
                    clientId + ":" + clientKey;

            String encoded =
                    Base64.getEncoder().encodeToString(
                            credentials.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            connection.setRequestProperty(
                    "Authorization",
                    "Basic " + encoded
            );

            int responseCode = connection.getResponseCode();

            if (responseCode != HttpURLConnection.HTTP_OK) {

                throw new Exception(
                        "HTTP error: " + responseCode
                );
            }

            StringBuilder builder =
                    new StringBuilder();

            try (BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(
                                         connection.getInputStream(),
                                         StandardCharsets.UTF_8
                                 )
                         )) {

                String line;

                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
            }

            String xml = builder.toString();

            int start = xml.indexOf("<?xml");

            if (start > 0) {
                xml = xml.substring(start);
            }

            return xml;

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // Parse Traffic Scotland XML using XmlPullParser
    private ArrayList<TrafficEvent> parseTrafficXML(String xml, String category) throws Exception {

        ArrayList<TrafficEvent> events =
                new ArrayList<>();

        XmlPullParserFactory factory =
                XmlPullParserFactory.newInstance();

        factory.setNamespaceAware(true);

        XmlPullParser xpp =
                factory.newPullParser();

        xpp.setInput(new StringReader(xml));

        String incidentId = "";
        String incidentType = "";
        String description = "";
        String startTime = "";
        String endTime = "";
        String location = "";
        String pendingLocation = "";

        double latitude = Double.NaN;
        double longitude = Double.NaN;

        boolean insidePublicComment = false;
        boolean insideLocationName = false;
        boolean insideRecord = false;
        boolean insidePointCoordinates = false;

        int eventType = xpp.getEventType();

        while (eventType != XmlPullParser.END_DOCUMENT) {

            if (eventType == XmlPullParser.START_TAG) {

                String tag = xpp.getName();

                if (tag.equals("situationRecord")) {

                    insideRecord = true;

                    incidentId =
                            xpp.getAttributeValue(
                                    null,
                                    "id"
                            );

                    incidentType =
                            xpp.getAttributeValue(
                                    "http://www.w3.org/2001/XMLSchema-instance",
                                    "type"
                            );

                    description = "";
                    startTime = "";
                    endTime = "";
                    location = "";
                    pendingLocation = "";

                    latitude = Double.NaN;
                    longitude = Double.NaN;

                    insidePublicComment = false;
                    insideLocationName = false;
                    insidePointCoordinates = false;

                } else if (insideRecord) {

                    if (tag.equals("pointCoordinates")) {
                        insidePointCoordinates = true;
                    }

                    if (insidePointCoordinates
                            && tag.equals("latitude")) {

                        String value = xpp.nextText();

                        try {

                            if (Double.isNaN(latitude)) {
                                latitude =
                                        Double.parseDouble(
                                                value.trim()
                                        );
                            }

                        } catch (NumberFormatException e) {

                            Log.w(
                                    "TRAFFIC_COORD",
                                    "Invalid latitude: " + value
                            );
                        }
                    }

                    if (insidePointCoordinates
                            && tag.equals("longitude")) {

                        String value = xpp.nextText();

                        try {

                            if (Double.isNaN(longitude)) {
                                longitude =
                                        Double.parseDouble(
                                                value.trim()
                                        );
                            }

                        } catch (NumberFormatException e) {

                            Log.w(
                                    "TRAFFIC_COORD",
                                    "Invalid longitude: " + value
                            );
                        }
                    }

                    if (tag.equals("generalPublicComment")) {
                        insidePublicComment = true;
                    }

                    if (tag.equals("name")
                            || tag.equals("otherName")) {

                        insideLocationName = true;
                        pendingLocation = "";
                    }

                    if (tag.equals("overallStartTime")) {
                        startTime = xpp.nextText();
                    }

                    if (tag.equals("overallEndTime")) {
                        endTime = xpp.nextText();
                    }

                    if (tag.equals("value")) {

                        if (insidePublicComment) {

                            String value = xpp.nextText();

                            if (!value.isEmpty()) {

                                if (!description.isEmpty()) {
                                    description += "\n";
                                }

                                description += value;
                            }

                        } else if (insideLocationName) {

                            pendingLocation = xpp.nextText();
                        }
                    }

                    if (tag.equals(
                            "tpegOtherPointDescriptorType")) {

                        String descriptorType =
                                xpp.nextText();

                        if (descriptorType.equals(
                                "intersectionName")
                                || descriptorType.equals(
                                "nonLinkedPointName")) {

                            if (!pendingLocation.isEmpty()) {

                                if (!location.isEmpty()
                                        && !location.equals(
                                        pendingLocation)) {

                                    location += " → ";
                                }

                                if (!location.equals(
                                        pendingLocation)
                                        && !location.endsWith(
                                        "→ " + pendingLocation)) {

                                    location += pendingLocation;
                                }
                            }
                        }

                        pendingLocation = "";
                    }
                }
            }

            if (eventType == XmlPullParser.END_TAG) {

                String tag = xpp.getName();

                if (tag.equals("pointCoordinates")) {
                    insidePointCoordinates = false;
                }

                if (tag.equals("generalPublicComment")) {
                    insidePublicComment = false;
                }

                if (tag.equals("name")
                        || tag.equals("otherName")) {

                    insideLocationName = false;
                    pendingLocation = "";
                }

                if (tag.equals("situationRecord")
                        && insideRecord) {

                    TrafficEvent event =
                            new TrafficEvent(
                                    incidentId == null
                                            ? "" : incidentId,

                                    incidentType == null
                                            ? "Traffic Event"
                                            : incidentType,

                                    description,
                                    startTime,
                                    endTime,

                                    location.isEmpty()
                                            ? "Location unavailable"
                                            : location,

                                    category,
                                    latitude,
                                    longitude
                            );

                    events.add(event);

                    if (event.hasCoordinates()) {

                        Log.d(
                                "TRAFFIC_COORD",
                                "Valid coordinates: "
                                        + event.getLatitude()
                                        + ", "
                                        + event.getLongitude()
                        );
                    }

                    insideRecord = false;
                }
            }

            eventType = xpp.next();
        }

        return events;
    }

    // Calculate distance from Glasgow
    private double calculateDistanceKm(TrafficEvent event) {

        if (!event.hasCoordinates()) {
            return Double.NaN;
        }

        float[] results = new float[1];

        Location.distanceBetween(
                GLASGOW_LATITUDE,
                GLASGOW_LONGITUDE,
                event.getLatitude(),
                event.getLongitude(),
                results
        );

        return results[0] / 1000.0;
    }

    // Filter by road name and distance
    private void filterTraffic(String searchText) {

        trafficEvents.clear();

        String query =
                searchText.trim()
                        .toLowerCase(Locale.ROOT);

        int missingCoordinates = 0;

        for (TrafficEvent event : allTrafficEvents) {

            String location =
                    event.getLocation()
                            .toLowerCase(Locale.ROOT);

            String description =
                    event.getDescription()
                            .toLowerCase(Locale.ROOT);

            boolean matchesRoad =
                    query.isEmpty()
                            || location.contains(query)
                            || description.contains(query);

            if (!matchesRoad) {
                continue;
            }

            boolean matchesDistance = true;

            if (selectedDistanceKm > 0) {

                if (!event.hasCoordinates()) {

                    missingCoordinates++;
                    matchesDistance = false;

                } else {

                    double distanceKm =
                            calculateDistanceKm(event);

                    matchesDistance =
                            !Double.isNaN(distanceKm)
                                    && distanceKm <= selectedDistanceKm;
                }
            }

            if (matchesDistance) {
                trafficEvents.add(event);
            }
        }

        adapter.notifyDataSetChanged();

        String message =
                "Showing " + trafficEvents.size()
                        + " of " + allTrafficEvents.size()
                        + " records";

        if (selectedDistanceKm > 0) {

            message += " within "
                    + (int) selectedDistanceKm
                    + " km of Glasgow";

            if (missingCoordinates > 0) {

                message += "\n"
                        + missingCoordinates
                        + " matching records have no coordinates";
            }
        }

        recordCount.setText(message);
    }

    // Roadworks duration colours
    private int getRoadworksColour(TrafficEvent event) {

        if (!event.getCategory().equals(
                "Current Roadworks")) {

            return Color.TRANSPARENT;
        }

        try {

            OffsetDateTime start =
                    OffsetDateTime.parse(
                            event.getStartTime()
                    );

            OffsetDateTime end =
                    OffsetDateTime.parse(
                            event.getEndTime()
                    );

            long hours =
                    Duration.between(
                            start,
                            end
                    ).toHours();

            if (hours < 0) {
                return Color.TRANSPARENT;
            }

            if (hours < 24) {

                return Color.rgb(165, 225, 175);

            } else if (hours <= 168) {

                return Color.rgb(255, 213, 120);

            } else {

                return Color.rgb(245, 155, 155);
            }

        } catch (DateTimeParseException e) {

            return Color.TRANSPARENT;
        }
    }

    // Display selected event details
    private void showEventDetails(TrafficEvent event) {

        String details =
                "Category: " + event.getCategory() + "\n\n"
                        + "Type: " + event.getType() + "\n\n"
                        + "Description:\n"
                        + event.getDescription() + "\n\n"
                        + "Location:\n"
                        + event.getLocation() + "\n\n"
                        + "Start time: "
                        + event.getStartTime() + "\n\n"
                        + "End time: "
                        + (event.getEndTime().isEmpty()
                        ? "Not specified"
                        : event.getEndTime());

        if (event.hasCoordinates()) {

            details += "\n\nCoordinates: "
                    + event.getLatitude()
                    + ", "
                    + event.getLongitude();
        }

        new AlertDialog.Builder(this)
                .setTitle("Traffic Event Details")
                .setMessage(details)
                .setPositiveButton("Close", null)
                .show();
    }

    @Override
    protected void onStart() {

        super.onStart();

        appVisible = true;

        scheduleAutomaticRefresh();
    }

    @Override
    protected void onStop() {

        appVisible = false;

        refreshHandler.removeCallbacks(
                refreshRunnable
        );

        super.onStop();
    }

    @Override
    protected void onDestroy() {

        refreshHandler.removeCallbacks(
                refreshRunnable
        );

        super.onDestroy();
    }
}
