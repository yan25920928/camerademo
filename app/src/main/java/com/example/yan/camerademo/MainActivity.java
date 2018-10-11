package com.example.yan.camerademo;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Size;
import android.view.Surface;

import android.view.TextureView;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;


public class MainActivity extends AppCompatActivity {

    private Handler mHandler = new Handler();
    private TextureView mTextureView;
    private String mCameraId;
    private CameraDevice mCameraDevice;
    private Size mPreviewSize;
    private ImageReader mImageReader;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mPreviewSerssion;
    private CaptureRequest mCaptureRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initListener();
    }

    private void initView() {
        mTextureView = findViewById(R.id.textureView);
    }

    private void initListener() {
        /**
         * 设置TextureView的回调监听
         */
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                setupCamera();
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    /**
     * 设置默认启用后置摄像头，并且设置图像尺寸获取摄像头的ID
     * 通过CameraManager，遍历所有摄像头
     * 根据摄像头特征，默认启用后置
     */
    private void setupCamera() {
        //获取CameraManager
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            //遍历所有摄像头
            for (String id : cameraManager.getCameraIdList()) {
                //获取相机特征
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                //LENS_FACING_FRONT——前置，LENS_FACING_BACK——后置，LENS_FACING_EXTERNAL——外置
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT)
                    //非后置时，直接跳出循环
                    continue;
                //获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                // 设置预览静态图片，使用最大尺寸
                mPreviewSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new Comparator<Size>() {
                            @Override
                            public int compare(Size o1, Size o2) {
                                return Long.signum(o1.getWidth() * o1.getHeight() - o2.getWidth() * o2.getHeight());
                            }
                        });
                mCameraId = id;
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 打开相机
     */
    private void openCamera() {
        //获取CameraManager
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        //检查权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            return;
        }

        //  打开相机
        //  第一个参数指示打开哪个摄像头
        //  第二个参数stateCallback为相机的状态回调接口
        //  第三个参数用来确定Callback在哪个线程执行，为null的话就在当前线程执行
        try {
            cameraManager.openCamera(mCameraId, stateCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 启用我们设备的回调开始预览
     */
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            //初始化CameraDevice
            mCameraDevice = camera;
            //开启预览
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {

        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {

        }
    };

    /**
     * 开启预览界面:设置Surface把它与CaptureRequestBuilder对象关联，再设置会话开始捕获画面
     */
    private void startPreview() {
        SurfaceTexture mSurfaceTexture = mTextureView.getSurfaceTexture();
        //设置缓冲区大小
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        //获取surface显示预览数据
        Surface mSurface = new Surface(mSurfaceTexture);
        //读取图片
        setupImageReader();
        //获取ImageReader的surface
        Surface imageReaderSurface = mImageReader.getSurface();
        try {
            //创建CaptureRequest.Builder对象，TEMPLATE_PREVIEW表示预览请求
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            //设置surface作为预览数据的显示界面
            mPreviewBuilder.addTarget(mSurface);
            //创建相机捕获会话，
            // 第一个参数是捕获数据的输出Surface列表，
            // 第二个参数是CameraCaptureSession的状态回调接口，当它创建好后会回调onConfigured方法，
            // 第三个参数用来确定Callback在哪个线程执行，为null的话就在当前线程执行
            mCameraDevice.createCaptureSession(Arrays.asList(mSurface, imageReaderSurface), mSessionStateCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 读取图片
     */
    private void setupImageReader() {
        //指定尺寸宽，高 和 图片格式，以及最多获取图像流的帧数
        mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                ImageFormat.JPEG, 2);

        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                mHandler.post(new ImageSaver(reader.acquireNextImage(), getApplication()));
            }
        }, mHandler);
    }

    /**
     * CameraCaptureSession状态接口回调
     */
    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            //创建捕获请求
            mCaptureRequest = mPreviewBuilder.build();
            mPreviewSerssion = session;
            //设置反复捕获数据的请求，这样预览界面就会一直有数据传送显示
            try {
                mPreviewSerssion.setRepeatingRequest(mCaptureRequest, mSessionCaptureCallback, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

        }
    };

    /**
     * CameraCaptureSession.CaptureCallback 设置预览完成的逻辑处理
     */
    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            //重启预览
            restartPreview();
        }
    };

    /**
     * 重启预览
     */
    private void restartPreview() {
        try {
            //执行setRepeatingRequest方法就行了，注意mCaptureRequest是之前开启预览设置的请求
            mPreviewSerssion.setRepeatingRequest(mCaptureRequest, null, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}
