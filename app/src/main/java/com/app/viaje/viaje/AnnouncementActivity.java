package com.app.viaje.viaje;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.text.DateFormat;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import butterknife.BindView;
import butterknife.ButterKnife;
import helpers.ViajeConstants;
import models.Announcement;
import models.Post;
import models.Safezone;

public class AnnouncementActivity extends AppCompatActivity {

    @BindView(R.id.timestamp)
    TextView timestamp;

    @BindView(R.id.message)
    TextView message;

    ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_announcement);

        progressBar = (ProgressBar)findViewById(R.id.progressBar);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(550, 100);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setLayoutParams(params);


        ButterKnife.bind(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        progressBar.setVisibility(View.VISIBLE);

        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();

        Query announcementQuery = dbRef.child(ViajeConstants.ANNOUNCEMENTS_KEY);

        announcementQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                if(dataSnapshot != null) {

                    Map<String, Announcement> td = new HashMap<String, Announcement>();

                    for(DataSnapshot announcementSnapshot : dataSnapshot.getChildren()){

                        Announcement announcement = announcementSnapshot.getValue(Announcement.class);
                        td.put(announcementSnapshot.getKey(), announcement);
                    }

                    ArrayList<Announcement> values = new ArrayList<>(td.values());
                    List<String> keys = new ArrayList<String>(td.keySet());

                    StringBuffer sb = new StringBuffer("");

                    for (Announcement announcement : values) {

                        String dateTime = convertTime(announcement.getTimestamp());

                        sb.append(dateTime+"\n"+announcement.getMessage());
                        sb.append("\n \n");
                        message.setText(sb.toString());
                    }

                    progressBar.setVisibility(View.INVISIBLE);

                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private String getDate(String time) {
        long dv = Long.valueOf(time) * 1000;// its need to be in milisecond
        Date df = new java.util.Date(dv);
        String stringDate = new SimpleDateFormat("hh:mm a").format(df);

        return stringDate;

    }

    private String convertTime(long time) {
        Date date = new Date(time);
        Format format = new SimpleDateFormat("MMMM dd, yyyy hh:mm a");
        return format.format(date);
    }
}
