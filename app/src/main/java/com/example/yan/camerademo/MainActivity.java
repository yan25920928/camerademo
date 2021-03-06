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
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;

import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;


public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    private HandlerThread mThreadHandler;
    private Handler mHandler = new Handler();

    private TextureView mTextureView;
    private Button mBtnShoot;
    private Button mBtnSwitch;
    private ImageView mImageOverview;
    private String mCameraId;
    private CameraDevice mCameraDevice;
    private Size mPreviewSize;
    private ImageReader mImageReader;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mPreviewSession;
    private CaptureRequest mCaptureRequest;

    public static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";
    //方向取向
    private static final SparseIntArray ORIENTATION = new SparseIntArray();

    static {
        ORIENTATION.append(Surface.ROTATION_0, 90);
        ORIENTATION.append(Surface.ROTATION_90, 0);
        ORIENTATION.append(Surface.ROTATION_180, 270);
        ORIENTATION.append(Surface.ROTATION_270, 180);
    }

    /**
     * 预览界面数据传送监听回调
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera(CameraCharacteristics.LENS_FACING_BACK);
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };
    private CameraCharacteristics cameraCharacteristics;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestCameraPermission();
        initView();
        initData();
        initListener();
    }

    /**
     * 处理相机权限动态申请及结果回调
     */
    private void requestCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                new PermissionConfirmationDialog().show(getSupportFragmentManager(), FRAGMENT_DIALOG);
            } else {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            }
        }
    }

    /**
     * 权限申请处理结果回调
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getResources().getString(R.string.request_permission)).
                        show(getSupportFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * 初始化View控件
     */
    private void initView() {
        mTextureView = findViewById(R.id.textureView);
        mBtnShoot = findViewById(R.id.btn_shoot);
        mBtnSwitch = findViewById(R.id.btn_switch);
        mImageOverview = findViewById(R.id.img_overview);
    }

    /**
     * 初始化数据
     */
    private void initData() {
        mThreadHandler = new HandlerThread("CAMERA2");
        mThreadHandler.start();
        mHandler = new Handler(mThreadHandler.getLooper());
    }

    @Override
    protected void onResume() {
        super.onResume();
        //是否可渲染
        if (mTextureView.isAvailable()) {
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    private void initListener() {

        //  拍摄按钮点击事件响应
        mBtnShoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    //获取屏幕方向
                    int rotation = getWindowManager().getDefaultDisplay().getRotation();
                    //设置CaptureRequest输出到mImageReader
                    //CaptureRequest添加imageReaderSurface，不加的话就会导致ImageReader的onImageAvailable()方法不会回调
                    mPreviewBuilder.addTarget(mImageReader.getSurface());
                    //设置拍照方向
                    mPreviewBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATION.get(rotation));
                    //聚焦
                    mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    //停止预览
                    mPreviewSession.stopRepeating();

                    //开始拍照，然后回调上面的接口重启预览
                    //因为mPreviewBuilder设置ImageReader作为target，所以会自动回调ImageReader的onImageAvailable()方法保存图片
                    mPreviewSession.capture(mPreviewBuilder.build(), mSessionCaptureCallback, null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        //  镜头切换按钮点击事件,获取当前使用的镜头
        //  点击切换关闭之前已经打开的镜头和预览,启用指定的镜头
        mBtnSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 当前镜头ID值
                int facingId = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);

                //关闭相机
                mCameraDevice.close();

                if (facingId == CameraCharacteristics.LENS_FACING_BACK)
                    setupCamera(CameraCharacteristics.LENS_FACING_FRONT);
                else
                    setupCamera(CameraCharacteristics.LENS_FACING_BACK);

                //打开相机
                openCamera();
            }
        });
    }

    /**
     * 设置默认启用后置摄像头，并且设置图像尺寸获取摄像头的ID
     * 通过CameraManager，遍历所有摄像头
     * @param orientation 指定使用哪个摄像头
     */
    private void setupCamera(int orientation) {
        //获取CameraManager
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            //遍历所有摄像头
            for (String id : cameraManager.getCameraIdList()) {
                //获取相机特征
                cameraCharacteristics = cameraManager.getCameraCharacteristics(id);
                //LENS_FACING_FRONT——前置，LENS_FACING_BACK——后置，LENS_FACING_EXTERNAL——外置
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) != orientation)
                    //非指定对应镜头时，直接跳出循环
                    continue;
                //获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
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
            cameraManager.openCamera(mCameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 关闭相机，释放资源
     */
    private void closeCamera() {

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
        //获取预览界面
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
            mCameraDevice.createCaptureSession(Arrays.asList(mSurface, imageReaderSurface), mSessionStateCallback, null);
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
            mPreviewSession = session;
            //设置反复捕获数据的请求，这样预览界面就会一直有数据传送显示
            try {
                mPreviewSession.setRepeatingRequest(mCaptureRequest, mSessionCaptureCallback, mHandler);
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
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
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
            mPreviewSession.setRepeatingRequest(mCaptureRequest, null, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}
