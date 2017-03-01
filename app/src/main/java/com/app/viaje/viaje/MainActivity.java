package com.app.viaje.viaje;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.LruCache;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

import java.util.Date;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import helpers.ViajeConstants;
import models.Emergency;
import models.Motorist;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener{

    //Checks user in-activity.
    private Timer timer;

    //For snackbar
    private CoordinatorLayout coordinatorLayout;

    private FirebaseAuth mFirebaseAuth;
    private FirebaseUser mFirebaseUser;
    private DatabaseReference dbRef;
    private EditText email;

    //Caching
    private LruCache<String, String> imageCache;

    private LocationManager locationManager;
    private LocationListener locationListener;

    Location mLastLocation;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    String lat, lon;

    //Butterknife
    @BindView(R.id.profilePic) ImageView profilePic;

    //GPSTracker Service
    GPSTracker gps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        coordinatorLayout = (CoordinatorLayout) findViewById(R.id.coordinatorLayout);

        buildGoogleApiClient();

        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseUser = mFirebaseAuth.getCurrentUser();
        dbRef = FirebaseDatabase.getInstance().getReference();

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;

        imageCache = new LruCache<>(cacheSize);

        if(mFirebaseUser == null){
            // Not logged-in.
            loadLoginView();
        }

        ButterKnife.bind(this);
    }


    @Override
    protected void onStart() {
        super.onStart();

        mGoogleApiClient.connect();

        if(isOnline()) {
            displayProfilePic();
        }else {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

            builder.setTitle("Internet Connectivity");
            builder.setMessage("It seems that you are not connected to the internet. \n Check your internet connectivity.");

            String positiveText = "Okay";

            builder.setPositiveButton(positiveText, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {


                }

            });

            AlertDialog dialog = builder.create();

            dialog.show();
        }


    }

    private void displayProfilePic() {
        SharedPreferences sharedPreferences = getSharedPreferences("motoristInfo", Context.MODE_PRIVATE);
        String email_address = sharedPreferences.getString("email", "");
        final String sharedPrefProfilePic = sharedPreferences.getString("profile_pic", "");

        if(imageCache.get(email_address) != null){

            Picasso.with(getApplicationContext()).load(sharedPrefProfilePic).into(profilePic);
        }else {

            final Query queryRef = dbRef.child(ViajeConstants.USERS_KEY)
                    .orderByChild(ViajeConstants.EMAIL_ADDRESS_FIELD)
                    .equalTo(email_address);

            if(mFirebaseUser != null){

                queryRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {

                        for (DataSnapshot userDataSnapshot : dataSnapshot.getChildren()){

                            Motorist motorist = userDataSnapshot.getValue(Motorist.class);

                            String profile_pic = motorist.getProfile_pic();
                            String email = motorist.getEmail_address();

//                            imageCache.put(email, profile_pic);
                            Picasso.with(getApplicationContext()).load(profile_pic).into(profilePic);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e("ERROR:", "onCancelled", databaseError.toException());
                    }
                });

            }

        }
    }

    protected boolean isOnline() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnectedOrConnecting()) ? true : false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mGoogleApiClient.disconnect();
    }

    @Override
    protected void onPause() {
        super.onPause();

        displayProfilePic();

        timer = new Timer();

        Log.i("Main", "Invoking logout timer");
        LogOutTimerTask logoutTimerTask = new LogOutTimerTask();

        timer.schedule(logoutTimerTask, 60000);

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (timer != null) {
            timer.cancel();
            Log.i("Main", "cancel timer");
            timer = null;
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_logout) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void loadLoginView(){
        Intent intent = new Intent(this, LogInActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void sendSafezoneHelp() {

        sendEmergencyHelpToSafezone("Hospital Emergency!", "hospital");

    }

    private void sendEmergencyHelpToSafezone(String emergencyDescription, String safezoneType) {
        SharedPreferences sharedPreferences = getSharedPreferences("motoristInfo", Context.MODE_PRIVATE);
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

        long millis = new Date().getTime();

        String email = sharedPreferences.getString("email", "");

        gps = new GPSTracker(MainActivity.this);

        if(gps.canGetLocation()){

            double latitude = gps.getLatitude();
            double longitude = gps.getLongitude();

            Emergency emergency = new Emergency();

            emergency.setEmail(email);
            emergency.setDescription(emergencyDescription);
            emergency.setStatus("pending");
            emergency.setLatitude(latitude);
            emergency.setLongitude(longitude);
            emergency.setTimestamp(millis);
            emergency.setSafezoneType(safezoneType);

            dbRef.child(ViajeConstants.EMERGENCIES_KEY).push().setValue(emergency);

            Snackbar snackbar = Snackbar.make(coordinatorLayout, "Help is coming..", Snackbar.LENGTH_LONG);
            View sbView = snackbar.getView();
            TextView textView = (TextView) sbView.findViewById(android.support.design.R.id.snackbar_text);
            textView.setTextColor(Color.RED);

            snackbar.show();
            //Toast.makeText(getApplicationContext(), "Your Location is - \nLat: " + latitude + "\nLong: " + longitude, Toast.LENGTH_LONG).show();

        }else{
            gps.showSettingsAlert();
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(100); // Update location every second

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);


        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        if (mLastLocation != null) {
            lat = String.valueOf(mLastLocation.getLatitude());
            lon = String.valueOf(mLastLocation.getLongitude());

            SharedPreferences sharedPreferences = getSharedPreferences("userCoordinates", Context.MODE_PRIVATE);

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("latitude", lat);
            editor.putString("longitude", lon);
            editor.apply();
        }

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        buildGoogleApiClient();
    }

    synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

    }

    @Override
    public void onLocationChanged(Location location) {

//        lat = String.valueOf(location.getLatitude());
//        lon = String.valueOf(location.getLongitude());
//
//        SharedPreferences sharedPreferences = getSharedPreferences("userCoordinates", Context.MODE_PRIVATE);
//
//        SharedPreferences.Editor editor = sharedPreferences.edit();
//        editor.putString("latitude", lat);
//        editor.putString("longitude", lon);
//        editor.apply();
//
//        /**
//         * Update the single login user
//         * at firebase "online_users" record at
//         * a given time of 1 minute.
//         */
//        if(mFirebaseUser != null){
//
//            new android.os.Handler().postDelayed(new Runnable() {
//                @Override
//                public void run() {
//                    updateUserLocationOnLocationChanged(lat, lon);
//                }
//            }, 60000);
//
//        }


    }

    private void updateUserLocationOnLocationChanged(final String string_latitude, final String string_longitude) {

        final double latitude = Double.parseDouble(string_latitude);
        final double longitude = Double.parseDouble(string_longitude);

        SharedPreferences sharedPreferences = getSharedPreferences("motoristInfo", Context.MODE_PRIVATE);
        String email_address = sharedPreferences.getString("email", "");

        final Query queryRef = dbRef.child(ViajeConstants.ONLINE_USERS_KEY)
                .orderByChild(ViajeConstants.MOTORIST_EMAIL_ADDRESS_KEY)
                .equalTo(email_address);

        if(mFirebaseUser != null){

            queryRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {

                    String key = "";

                    for (DataSnapshot nodeDataSnapshot : dataSnapshot.getChildren()){
                        key = nodeDataSnapshot.getKey();
                    }

                    HashMap<String, Object> updated_online_user = new HashMap<>();
                    updated_online_user.put("latitude", latitude);
                    updated_online_user.put("longitude", longitude);

                    queryRef.getRef().child(key).updateChildren(updated_online_user);
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.e("ERROR:", "onCancelled", databaseError.toException());
                }
            });

        }
    }

    private void deleteUserRecordOnFirebase() {

        SharedPreferences sharedPreferences = getSharedPreferences("motoristInfo", Context.MODE_PRIVATE);
        String email_address = sharedPreferences.getString("email", "");

        dbRef = FirebaseDatabase.getInstance().getReference()
                .child(ViajeConstants.ONLINE_USERS_KEY);

        dbRef.orderByChild(ViajeConstants.MOTORIST_EMAIL_ADDRESS_KEY)
                .equalTo(email_address)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {

                        DataSnapshot nodeDataSnapshot = dataSnapshot.getChildren().iterator().next();
                        String key = nodeDataSnapshot.getKey();

                        dbRef.child(key).removeValue(new DatabaseReference.CompletionListener() {
                            @Override
                            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {

                                if(databaseError == null){
                                    mFirebaseAuth.signOut();
                                    loadLoginView();
                                }
                            }
                        });
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e("ERROR:", "onCancelled", databaseError.toException());
                    }
                });

    }

    /**
     * In-line class for logout timer
     */

    private class LogOutTimerTask extends TimerTask{

        @Override
        public void run() {

            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    if(!(MainActivity.this).isFinishing())
                    {
                        /**
                         * Show Help Dialog
                         */
                        showHelpDialog();
                    }
                }
            });

        }

        private void showHelpDialog() {

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

            builder.setTitle(getString(R.string.dialog_title));
            builder.setMessage(getString(R.string.dialog_message));

            String positiveText = getString(R.string.dialog_positive_text);

            builder.setPositiveButton(positiveText, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {

                    Snackbar snackbar = Snackbar.make(coordinatorLayout, "Don't send help..", Snackbar.LENGTH_LONG);
                    View sbView = snackbar.getView();
                    TextView textView = (TextView) sbView.findViewById(android.support.design.R.id.snackbar_text);
                    textView.setTextColor(Color.RED);

                    snackbar.show();
                }

            });

            String negativeText = getString(R.string.dialog_negative_text);
            builder.setNegativeButton(negativeText, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Toast.makeText(MainActivity.this, "Send Help!", Toast.LENGTH_SHORT).show();
                    sendSafezoneHelp();
                }
            });

            AlertDialog dialog = builder.create();

            dialog.show();
        }
    }

    /**
     * Butterknife components.
     */
    @OnClick(R.id.logout_button)
    void onLogout(){
        /**
         * Delete the logged in user
         * to "online_users" record
         * at firebase.
         */
        deleteUserRecordOnFirebase();
        SharedPreferences motoristInfo = getApplicationContext().getSharedPreferences("motoristInfo", MODE_PRIVATE);
        motoristInfo.edit().clear().apply();
    }

    @OnClick(R.id.emergency_button_id)
    void onEmergencyClick(){

        sendSafezoneHelp();
    }

    @OnClick(R.id.to_map_button_id)
    void toMapActivity(){

        Intent intent = new Intent(getApplicationContext(), MapsActivity.class);
        startActivity(intent);
    }

    @OnClick(R.id.to_profile_button_id)
    void toProfileActivity(){

        Intent intent = new Intent(getApplicationContext(), ProfileActivity.class);
        startActivity(intent);
    }

    @OnClick(R.id.to_assistance_button_id)
    void toAssistanceActivity(){

        Intent intent = new Intent(getApplicationContext(), AssistanceActivity.class);
        startActivity(intent);
    }

    @OnClick(R.id.to_announcement)
    void toAnnouncementActivity() {

        Intent intent = new Intent(getApplicationContext(), AnnouncementActivity.class);
        startActivity(intent);
    }

}
