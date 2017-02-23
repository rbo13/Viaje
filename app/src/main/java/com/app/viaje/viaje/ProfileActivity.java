package com.app.viaje.viaje;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import helpers.CloudinaryClient;
import helpers.CloudinaryConfiguration;
import helpers.ViajeConstants;
import models.Motorist;

public class ProfileActivity extends AppCompatActivity {

    private FirebaseAuth mFirebaseAuth;
    private FirebaseUser mFirebaseUser;
    private DatabaseReference dbRef;

    private DatabaseReference mFirebaseRef;

    //Caching
    private LruCache<String, String> imageCache;

    private Uri imageCaptureUri;
    InputStream inputStream;

    Cloudinary cloudinary;
    Map config = new HashMap();

    private static final int PICK_FROM_CAMERA = 1;
    private static final int PICK_FROM_GALLERY = 2;

    ProgressBar progressBar;

    //Butterknife
    @BindView(R.id.profilePic_id) ImageView profilePic;

    @BindView(R.id.full_name_text_id) TextView full_name_text;
    @BindView(R.id.plate_number_text_id) TextView plate_number_text;
    //@BindView(R.id.email_text_id) TextView email_text;
    @BindView(R.id.contact_number_text_id) TextView contact_number_text;
    @BindView(R.id.address_text_id) TextView address_text;

    @BindView(R.id.update_full_name_edit) EditText fullNameUpdate;
    @BindView(R.id.update_plate_number_edit) EditText plateNumberUpdate;
    //@BindView(R.id.update_email_edit) EditText emailUpdate;
    @BindView(R.id.update_contact_edit) EditText contactNumberUpdate;
    @BindView(R.id.update_address_edit) EditText addressUpdate;

    @BindView(R.id.save_profile_button)
    Button saveProfile;

    @BindView(R.id.update_profile_button)
    Button updateProfile;

    @BindView(R.id.activity_profile)
    RelativeLayout relativeLayout;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        ButterKnife.bind(this);

        progressBar = (ProgressBar)findViewById(R.id.progressBar);

        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseUser = mFirebaseAuth.getCurrentUser();
        dbRef = FirebaseDatabase.getInstance().getReference();

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;

        imageCache = new LruCache<>(cacheSize);

        config.put("cloud_name", ViajeConstants.CLOUD_NAME);
        config.put("api_key", ViajeConstants.API_KEY);
        config.put("api_secret", ViajeConstants.API_SECRET);
        cloudinary = new Cloudinary(config);
    }

    @Override
    protected void onStart() {
        super.onStart();

        displayUserInformation();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);


        if(resultCode != RESULT_OK) return;

        Bitmap bitmap = null;
        String path = "";

        if(requestCode == PICK_FROM_GALLERY) {
            imageCaptureUri = data.getData();
            path = getRealPathFromUri(imageCaptureUri);

            if(path == null) path = imageCaptureUri.getPath();

            if(path != null) {
                bitmap = BitmapFactory.decodeFile(path);

                try {
                    inputStream = new FileInputStream(path);
                    updateProfilePictureFieldAtFirebase(inputStream);

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

            }

        }else {
            path = imageCaptureUri.getPath();
            bitmap = BitmapFactory.decodeFile(path);

            try{
                inputStream = new FileInputStream(path);
                updateProfilePictureFieldAtFirebase(inputStream);

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        //profilePic.setImageBitmap(bitmap);

    }

    public String getRealPathFromUri(Uri contentUri) {
        String[] proj = {MediaStore.Images.Media.DATA};

        @SuppressWarnings("deprecation")
        Cursor cursor = managedQuery(contentUri, proj, null, null, null);

        if(cursor == null) return null;
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();

        return cursor.getString(column_index);
    }

    private void displayUserInformation(){

        /**
         * Hide the EditText onStart.
         */
        fullNameUpdate.setVisibility(View.GONE);
        plateNumberUpdate.setVisibility(View.GONE);
        //emailUpdate.setVisibility(View.GONE);
        contactNumberUpdate.setVisibility(View.GONE);
        addressUpdate.setVisibility(View.GONE);

        SharedPreferences sharedPreferences = getSharedPreferences("motoristInfo", Context.MODE_PRIVATE);


        String email_address = sharedPreferences.getString("email", "");
        String full_name = sharedPreferences.getString("full_name", "");
        String plate_number = sharedPreferences.getString("plate_number", "");
        String contact_number = sharedPreferences.getString("contact_number", "");
        String profile_pic = sharedPreferences.getString("profile_pic", "");
        String address = sharedPreferences.getString("address", "");

        if(imageCache.get(email_address) != null) {

            Picasso.with(getApplicationContext()).load(profile_pic).into(profilePic);
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

                            String imageUrl = motorist.getProfile_pic();
                            String email = motorist.getEmail_address();
                            Log.d("IMAGE_URL: ", imageUrl);

                            //Display to TextView.
                            full_name_text.setText(motorist.getFull_name());
                            plate_number_text.setText(motorist.getVehicle_information_plate_number());
                            //email_text.setText(motorist.getEmail_address());
                            contact_number_text.setText(motorist.getContact_number());
                            address_text.setText(motorist.getAddress());

                            imageCache.put(email, imageUrl);

                            Picasso.with(getApplicationContext()).load(imageUrl).into(profilePic);
                            progressBar.setVisibility(View.INVISIBLE);
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


    private void updateProfilePictureFieldAtFirebase(final InputStream is) {

        Runnable runnable = new Runnable() {
            @Override
            public void run() {

                try{
                    /**
                     * Cloudinary function to get the
                     * newly uploaded image to cloudinary.
                     */
                    Map uploadResult = cloudinary.uploader().upload(is, ObjectUtils.emptyMap());
                    final String url = (String) uploadResult.get("url");
                    Log.d("IMAGE_URL", url);

                    /**
                     * Get Shared Preferences and update the
                     * 'profile_pic' at Firebase.
                     */
                    SharedPreferences sharedPreferences = getSharedPreferences("motoristInfo", Context.MODE_PRIVATE);
                    String email_address = sharedPreferences.getString("email", "");

                    imageCache.put(email_address, url);

                    final Query queryRef = dbRef.child(ViajeConstants.USERS_KEY)
                            .orderByChild(ViajeConstants.EMAIL_ADDRESS_FIELD)
                            .equalTo(email_address);

                    if(mFirebaseUser != null){

                        queryRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {


                                String key = "";

                                for (DataSnapshot nodeDataSnapshot : dataSnapshot.getChildren()){
                                    key = nodeDataSnapshot.getKey();
                                }

                                HashMap<String, Object> updated_profile_pic = new HashMap<>();
                                updated_profile_pic.put("profile_pic", url);
                                queryRef.getRef().child(key).updateChildren(updated_profile_pic);

                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {
                                Log.e("ERROR:", "onCancelled", databaseError.toException());
                            }
                        });

                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        new Thread(runnable).start();

    }

    private void selectImageFromGalleryOrCapturePhoto() {

        final String[] items = new String[] { "From Camera", "From Gallery" };
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.select_dialog_item, items);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Image");
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                if(which == 0) {
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    File file = new File(Environment.getExternalStorageDirectory(), "tmp_avatar" + String.valueOf(System.currentTimeMillis())+".jpg");
                    imageCaptureUri = Uri.fromFile(file);

                    try{
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageCaptureUri);
                        intent.putExtra("return data", true);
                        startActivityForResult(intent, PICK_FROM_CAMERA);
                    }catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    dialog.cancel();
                } else {
                    Intent intent = new Intent();
                    intent.setType("image/*");
                    intent.setAction(Intent.ACTION_GET_CONTENT);
                    startActivityForResult(Intent.createChooser(intent, "Complete Action Using"), PICK_FROM_GALLERY);
                }
            }
        });

        final AlertDialog dialog = builder.create();
        dialog.show();
    }


    private void editProfile() {

        final SharedPreferences sharedPreferences = getSharedPreferences("motoristInfo", Context.MODE_PRIVATE);
        String email_address = sharedPreferences.getString("email", "");

        final Query queryRef = dbRef.child(ViajeConstants.USERS_KEY)
                .orderByChild(ViajeConstants.EMAIL_ADDRESS_FIELD)
                .equalTo(email_address);

        if(mFirebaseUser != null){

            queryRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {

                    String key = "";

                    for (DataSnapshot nodeDataSnapshot : dataSnapshot.getChildren()){
                        key = nodeDataSnapshot.getKey();
                    }

                    HashMap<String, Object> update_profile = new HashMap<>();
                    update_profile.put("full_name", fullNameUpdate.getText().toString().trim());
                    update_profile.put("vehicle_information_plate_number", plateNumberUpdate.getText().toString().trim());
                    //update_profile.put("email_address", emailUpdate.getText().toString().trim());
                    update_profile.put("contact_number", contactNumberUpdate.getText().toString().trim());
                    update_profile.put("address", addressUpdate.getText().toString().trim());
                    queryRef.getRef().child(key).updateChildren(update_profile);

                    Snackbar snackbar = Snackbar.make(relativeLayout, "Profile Successfully Updated..", Snackbar.LENGTH_LONG);
                    View sbView = snackbar.getView();
                    TextView textView = (TextView) sbView.findViewById(android.support.design.R.id.snackbar_text);
                    textView.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.app_color));
                    snackbar.show();
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.e("ERROR:", "onCancelled", databaseError.toException());
                }
            });

        }
    }


    private void loadLoginView(){
        Intent intent = new Intent(this, LogInActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    //Butterknife components
    @OnClick(R.id.logout_button)
    void onLogout(){
        deleteUserRecordOnFirebase();
    }

    @OnClick(R.id.update_profile_button)
    void onUpdateProfile() {

        saveProfile.setVisibility(View.VISIBLE);
        fullNameUpdate.setVisibility(View.VISIBLE);
        plateNumberUpdate.setVisibility(View.VISIBLE);
        //emailUpdate.setVisibility(View.VISIBLE);
        contactNumberUpdate.setVisibility(View.VISIBLE);
        addressUpdate.setVisibility(View.VISIBLE);

        updateProfile.setVisibility(View.INVISIBLE);
        full_name_text.setVisibility(View.INVISIBLE);
        plate_number_text.setVisibility(View.INVISIBLE);
        //email_text.setVisibility(View.INVISIBLE);
        contact_number_text.setVisibility(View.INVISIBLE);
        address_text.setVisibility(View.INVISIBLE);

        SharedPreferences sharedPreferences = getSharedPreferences("motoristInfo", Context.MODE_PRIVATE);

        String email_address = sharedPreferences.getString("email", "");
        String full_name = sharedPreferences.getString("full_name", "");
        String plate_number = sharedPreferences.getString("plate_number", "");
        String contact_number = sharedPreferences.getString("contact_number", "");
        String profile_pic = sharedPreferences.getString("profile_pic", "");
        String address = sharedPreferences.getString("address", "");

        //Display to TextView.
        fullNameUpdate.setText(full_name);
        plateNumberUpdate.setText(plate_number);
        //emailUpdate.setText(email_address);
        contactNumberUpdate.setText(contact_number);
        addressUpdate.setText(address);

    }

    @OnClick(R.id.save_profile_button)
    void onSaveProfile() {
        SharedPreferences sharedPreferences = getSharedPreferences("motoristInfo", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
       //editor.putString("email", emailUpdate.getText().toString());
        editor.putString("plate_number", plateNumberUpdate.getText().toString());
        editor.putString("full_name", fullNameUpdate.getText().toString());
        editor.putString("contact_number", contactNumberUpdate.getText().toString());
        editor.putString("address", addressUpdate.getText().toString());
        editor.apply();

        saveProfile.setVisibility(View.INVISIBLE);
        fullNameUpdate.setVisibility(View.INVISIBLE);
        plateNumberUpdate.setVisibility(View.INVISIBLE);
        //emailUpdate.setVisibility(View.INVISIBLE);
        contactNumberUpdate.setVisibility(View.INVISIBLE);
        addressUpdate.setVisibility(View.INVISIBLE);

        updateProfile.setVisibility(View.VISIBLE);
        full_name_text.setVisibility(View.VISIBLE);
        plate_number_text.setVisibility(View.VISIBLE);
        //email_text.setVisibility(View.VISIBLE);
        contact_number_text.setVisibility(View.VISIBLE);
        address_text.setVisibility(View.VISIBLE);

        //Display to TextView.
        full_name_text.setText(fullNameUpdate.getText().toString());
        plate_number_text.setText(plateNumberUpdate.getText().toString());
        //email_text.setText(emailUpdate.getText().toString());
        contact_number_text.setText(contactNumberUpdate.getText().toString());
        address_text.setText(addressUpdate.getText().toString());

        editProfile();

    }

    @OnClick(R.id.profilePic_id)
    void onUpdateProfilePic() {
        selectImageFromGalleryOrCapturePhoto();
    }


}
