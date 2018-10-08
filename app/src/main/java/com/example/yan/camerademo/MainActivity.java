package com.example.yan.camerademo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    static final int REQUEST_IMAGE_CAPTURE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkCameraHardware(this);
    }

    private boolean checkCameraHardware(Context context){
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            Log.d("MainActivity","有摄像头");
            getNumOfCameras();
            getCameraInstance();
            return true;
        }else {
            Log.d("MainActivity","无摄像头");
            return false;
        }
    }

    private int getNumOfCameras(){
        Log.d("MainActivity","摄像头:"+Camera.getNumberOfCameras());
        return Camera.getNumberOfCameras();
    }

    public static Camera getCameraInstance(){
        Camera camera = null;
        try {
            camera = Camera.open(1);
        }catch (Exception e){
            Log.d("MainActivity","EXCEPTION:");
        }
        return camera;
    }

    private boolean safeCameraOpen(int id){
        boolean qOpened = false;

        return qOpened;
    }

    private void releaseCameraAndPreview(){

    }

    private void dispatchTakePictureIntent(){
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager())!=null){
            startActivityForResult(takePictureIntent,REQUEST_IMAGE_CAPTURE);
        }
    }
}
