package com.example.alarm.alarmapp.views;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;

import org.opencv.android.JavaCameraView;

/**
 * Copyright (C) 2017  Jannes Peters
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

public class AlarmCameraView extends JavaCameraView {
    private boolean mAreCameraParamsSet = false;

    public AlarmCameraView(Context context, int cameraId) {
        super(context, cameraId);
    }

    public AlarmCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onPreviewFrame(byte[] frame, Camera arg1) {
        if (!mAreCameraParamsSet) {
            mAreCameraParamsSet = true;
            Camera.Parameters params = mCamera.getParameters();
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
            mCamera.setParameters(params);
        }
        super.onPreviewFrame(frame, arg1);

    }
}
