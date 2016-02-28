package com.shadowmaps.shadowmapsbox;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.View;

import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.Polygon;
import com.mapbox.mapboxsdk.annotations.PolygonOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.views.MapView;
import com.shadowmaps.listener.ShadowMaps;

import java.util.Arrays;

public class ShadowMapsBoxMap extends AppCompatActivity {

    // ShadowMaps GPS Listener and intent broadcast receiver
    private ShadowMaps shadowMaps;
    private BroadcastReceiver shadowMapsReceiver;

    // Settings
    private String current_mode;
    private SharedPreferences prefs;

    // Master UI Elements
    private FloatingActionButton fab;
    private MapView mapView = null;
    private MarkerOptions shadowMapsMarkerOptions;
    private Marker shadowMapsMarker;
    private Polygon circlePoly;

    private final int MY_PERMISSIONS_REQUEST_FINE_LOCATION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup UI
        setContentView(R.layout.activity_shadow_maps_box_map);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        fab = (FloatingActionButton) findViewById(R.id.fab);

        // Get mode setting from last use (pedestrian/vehicular) and initialize fab icon
        prefs = getSharedPreferences("shadowmaps", MODE_PRIVATE);
        current_mode = prefs.getString("mode", "pedestrian");
        setMode(current_mode);
        // Listen for clicks on the floating button to switch between pedestrian and vehicular
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (current_mode.equals("pedestrian")) {
                    current_mode = setMode("vehicular");
                    shadowMaps.setMotionModel(current_mode);
                } else {
                    current_mode = setMode("pedestrian");
                    shadowMaps.setMotionModel(current_mode);
                }
                Snackbar.make(view, "Now in " + current_mode + " mode.", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        // Set up mapView
        mapView = (MapView) findViewById(R.id.mapboxMapView);
        mapView.setStyleUrl(Style.MAPBOX_STREETS);
        mapView.setLogoVisibility(View.INVISIBLE);
        mapView.setZoomLevel(17);
        mapView.onCreate(savedInstanceState);

        // For Android 5+: Check if we have location permissions, if not request them
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // TODO: Request permission nicely.
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_FINE_LOCATION);
            }
        }

        // If we do have location permission, display location on map and start ShadowMaps
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mapView.setMyLocationEnabled(true);
            // Start ShadowMaps and set motion model.
            if(shadowMaps == null && savedInstanceState == null) {
                Log.v("ShadowMaps", "Starting ShadowMaps");
                startShadowMaps();
            }
        }
    }

    public void startShadowMaps() {
        shadowMaps = new ShadowMaps(this, ShadowMaps.Mode.REALTIME);
        // Pass in "vehicular" or "pedestrian"
        shadowMaps.setMotionModel(current_mode);
        // Register receiver to receive location updates from ShadowMaps
        shadowMapsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Extract lat, lon, radius and show on map
                Log.v("ShadowMaps", "Received Intent from ShadowMaps");
                float lat = intent.getFloatExtra("lat", 0);
                float lon = intent.getFloatExtra("lon", 0);
                float acc = intent.getFloatExtra("radius", 0);
                showCircleAndPin(lat, lon, acc);
            }
        };
    }

    /**
     * Displays a pin with accuracy on a MapBox MapView
     * @param lat
     * @param lon
     * @param acc
     */
    public void showCircleAndPin(float lat, float lon, float acc) {
        if (lat != 0 && lon != 0) {
            if (shadowMapsMarkerOptions == null) {
                shadowMapsMarkerOptions = new MarkerOptions()
                        .position(new LatLng(lat, lon))
                        .title("ShadowMaps Estimate")
                        .snippet(String.format("Accuracy: %.2f", acc));
                shadowMapsMarker = mapView.addMarker(shadowMapsMarkerOptions);
            } else {
                shadowMapsMarkerOptions.position(new LatLng(lat, lon));
                shadowMapsMarkerOptions.snippet(String.format("Accuracy: %.2f meters", acc));
                CameraPosition cam = new CameraPosition.Builder().target(new LatLng(lat, lon)).build();
                mapView.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 500, null);
                PolygonOptions circle = getPolygonCircleForCoordinate(lat, lon, acc);
                boolean showBubble = false;
                if (shadowMapsMarker != null) {
                    showBubble = shadowMapsMarker.isInfoWindowShown();
                    shadowMapsMarker.remove();
                }
                if (circlePoly != null) {
                    circlePoly.remove();
                }
                circlePoly = mapView.addPolygon(circle);
                shadowMapsMarkerOptions.position(new LatLng(lat, lon)).snippet(String.format("Accuracy: %.2f meters", acc));
                shadowMapsMarker = mapView.addMarker(shadowMapsMarkerOptions);
                if (showBubble) {
                    shadowMapsMarker.showInfoWindow(mapView);
                }
            }

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_shadow_maps_box_map, menu);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_FINE_LOCATION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        mapView.setMyLocationEnabled(true);
                        if(shadowMaps == null) {
                            startShadowMaps();
                        }
                    }
                }
                return;
            }
        }
    }

    protected String setMode(String mode) {
        if (mode.equals("pedestrian")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_directions_walk_white_24dp, getTheme()));
            } else {
                fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_directions_walk_white_24dp));
            }
        } else if (mode.equals("vehicular")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_directions_car_white_24dp, getTheme()));
            } else {
                fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_directions_car_white_24dp));
            }
        }
        prefs.edit().putString("mode", mode).apply();
        return mode;
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
        registerReceiver(shadowMapsReceiver,
                new IntentFilter("shadowmaps.location.update"));
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
        if(shadowMapsReceiver != null) {
            unregisterReceiver(shadowMapsReceiver);
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        shadowMaps.stop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    private PolygonOptions getPolygonCircleForCoordinate(float latitude, float longitude, double meterRadius) {
        int degreesBetweenPoints = 8;
        int numberOfPoints = (int) Math.floor(360 / degreesBetweenPoints);
        double distRadians = meterRadius / 6371000.0;
        double centerLatRadians = latitude * Math.PI / 180;
        double centerLonRadians = longitude * Math.PI / 180;

        LatLng[] coordinates = new LatLng[numberOfPoints];

        for (int i = 0; i < numberOfPoints; i++) {
            double degrees = i * degreesBetweenPoints;
            double degreeRadians = degrees * Math.PI / 180;
            double pointLatRadians = Math.asin(Math.sin(centerLatRadians)
                    * Math.cos(distRadians) + Math.cos(centerLatRadians) * Math.sin(distRadians) * Math.cos(degreeRadians));
            double pointLonRadians = centerLonRadians + Math.atan2(Math.sin(degreeRadians) * Math.sin(distRadians)
                    * Math.cos(centerLatRadians), Math.cos(distRadians) - Math.sin(centerLatRadians)
                    * Math.sin(pointLatRadians));
            double pointLat = pointLatRadians * 180 / Math.PI;
            double pointLon = pointLonRadians * 180 / Math.PI;
            LatLng point = new LatLng(pointLat, pointLon);
            coordinates[i] = point;
        }

        PolygonOptions polygonOptions = new PolygonOptions();
        polygonOptions.addAll(Arrays.asList(coordinates));
        polygonOptions.alpha(0.6f);
        polygonOptions.fillColor(Color.RED);

        return polygonOptions;
    }


}
