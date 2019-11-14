package com.demo.ntc;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.appliedrec.rxverid.RxVerID;
import com.appliedrec.verid.core.AuthenticationSessionSettings;
import com.appliedrec.verid.core.Bearing;
import com.appliedrec.verid.core.FaceDetectionRecognitionFactory;
import com.appliedrec.verid.core.FaceDetectionRecognitionSettings;
import com.appliedrec.verid.core.IRecognizable;
import com.appliedrec.verid.ui.VerIDSessionIntent;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

import android.widget.TextView;
import android.widget.Toast;

public class LiveDetectActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_AUTHENTICATION = 0;
    private RxVerID rxVerID;
    private ImageView imageView;
    private Button button;
    private static final String USER_ID = "luis";

    private ArrayList<Disposable> disposables = new ArrayList<>();
    private Uri imageUri;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_detect);

        imageUri = Uri.fromFile(new File(getIntent().getStringExtra("IdScanner.IMAGE_URI")));

        imageView = findViewById(R.id.imageView);
        TextView uriImage = findViewById(R.id.uriImage);
        button = findViewById(R.id.button);
        button.setEnabled(false);

        File f = new File(getIntent().getStringExtra("IdScanner.IMAGE_URI"));
        if(!f.exists()){
            uriImage.setText("Image not exist");
        }else {
            uriImage.setText(imageUri.toString());
        }

        FaceDetectionRecognitionSettings settings = new FaceDetectionRecognitionSettings(null);
        // Enable RenderScript
        settings.setEnableRenderScript(true);
        // Create face detection and recognition factory instance
        FaceDetectionRecognitionFactory faceDetectionRecognitionFactory = new FaceDetectionRecognitionFactory(this, null, settings);
        // Build an instance of RxVerID
        rxVerID = new RxVerID.Builder(this)
                .setFaceDetectionFactory(faceDetectionRecognitionFactory)
                .setFaceRecognitionFactory(faceDetectionRecognitionFactory)
                .build();

        Disposable disposable = rxVerID.detectRecognizableFacesInImage(imageUri, 1)
                .firstOrError()
                .flatMapCompletable(face -> rxVerID.assignFaceToUser(face, USER_ID))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> displayImage(),
                        onError
                );
        disposables.add(disposable);
    }

    private Consumer<? super Throwable> onError = error -> {
        new AlertDialog.Builder(LiveDetectActivity.this)
                .setTitle("Error")
                .setMessage(error.getLocalizedMessage())
                .setPositiveButton("OK", null)
                .create()
                .show();
    };

    private void displayImage(){
        imageView.setVisibility(View.VISIBLE);
        RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(getResources(),
                getIntent().getStringExtra("IdScanner.IMAGE_URI"));
        float density = getResources().getDisplayMetrics().density;
        drawable.setCornerRadius(density * 20f);
        imageView.setImageDrawable(drawable);
        button.setEnabled(true);
        button.setOnClickListener(view -> authenticate());
    }

    private void authenticate(){
        Disposable disposable = rxVerID.getVerID()
                .flatMap(verID -> rxVerID.getUsers()
                .filter(user_id -> user_id.equals(USER_ID))
                .firstOrError()
                .map(user_id -> verID))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        verID -> {
                            AuthenticationSessionSettings settings = new AuthenticationSessionSettings(USER_ID);
                            Intent intent = new VerIDSessionIntent<>(LiveDetectActivity.this, verID, settings);
                            startActivityForResult(intent, REQUEST_CODE_AUTHENTICATION);
                        },
                        onError
                );
        disposables.add(disposable);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_CODE_AUTHENTICATION && resultCode == RESULT_OK && data != null){
            float density = getResources().getDisplayMetrics().density;
            int dialogImageHeight = (int)(density * 150);
            Disposable disposable = rxVerID.getSessionResultFromIntent(data) // Get the session result from the received intent
                    .flatMapObservable(result -> rxVerID.getFacesAndImageUrisFromSessionResult(result, Bearing.STRAIGHT)) // Get the faces that are looking straight at the camera and their accompanying images
                    .firstOrError() // If none are found throw an error
                    .flatMap(detectedFace -> rxVerID.cropImageToFace(detectedFace.getImageUri(), detectedFace.getFace())) // Crop the image to the bounds of the detected face
                    .map(bitmap -> {
                        if (bitmap.getHeight() > dialogImageHeight) {
                            float scale = (float)dialogImageHeight / (float)bitmap.getHeight();
                            return Bitmap.createScaledBitmap(bitmap, (int)((float)bitmap.getWidth()*scale), dialogImageHeight, true); // Make the face image smaller
                        } else {
                            return bitmap;
                        }
                    })
                    .subscribeOn(Schedulers.io()) // Subscribe on a background thread
                    .observeOn(AndroidSchedulers.mainThread()) // Observe on the main thread
                    .subscribe(
                            bitmap -> {
                                ImageView imageView = new ImageView(LiveDetectActivity.this);
                                imageView.setImageBitmap(bitmap);
                                new AlertDialog.Builder(LiveDetectActivity.this)
                                        .setTitle("Authentication succeeded")
                                        .setView(imageView)
                                        .setPositiveButton("OK", null)
                                        .create()
                                        .show();
                            },
                            onError // Show error on failure
                    );
            disposables.add(disposable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Iterator<Disposable> disposableIterator = disposables.iterator();
        while (disposableIterator.hasNext()) {
            Disposable disposable = disposableIterator.next();
            if (!disposable.isDisposed()) {
                disposable.dispose();
            }
            disposableIterator.remove();
        }
    }

}
