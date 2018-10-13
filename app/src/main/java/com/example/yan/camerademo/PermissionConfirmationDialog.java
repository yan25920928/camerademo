package com.example.yan.camerademo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
/**
 * 相机权限申请弹窗交互，OK/Cancel 点击监听事件
 */
public class PermissionConfirmationDialog extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final Activity activity = getActivity();
        new AlertDialog.Builder(getActivity()).setMessage(R.string.request_permission)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    activity.requestPermissions(new String[]{Manifest.permission.CAMERA},
                                            MainActivity.REQUEST_CAMERA_PERMISSION);
                                }
                            }
                        })
                .setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (activity != null) {
                                    activity.finish();
                                }
                            }
                        })
                .create();
        return super.onCreateDialog(savedInstanceState);
    }
}
