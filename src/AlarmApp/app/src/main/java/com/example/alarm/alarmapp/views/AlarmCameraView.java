package com.example.alarm.alarmapp.views;

import android.content.Context;
import android.hardware.Camera;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;

import com.example.alarm.alarmapp.MainActivity;
import com.example.alarm.alarmapp.R;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AlarmCameraView extends JavaCameraView implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final String TAG = AlarmCameraView.class.getName();

    private int mTimeToCalibrate = 10000;
    private double mAlarmThreshhold = 8d;
    private int mProcessFps = 4;
    private double mAverageOver = 100d / mProcessFps;

    private long mCalibratingStartedAt = 0;
    private long mLastProcessFrameTime = 0;

    private boolean mAreCameraParamsSet = false;
    private State mState = State.IDLE;
    private IAlarmCameraListener mAlarmListener = null;
    private Mat mLastMat = null;

    private volatile double mMovingAbsDiffAvg = -1d;
    private volatile double mMovingDiffAvg = -1d;
    private volatile double mMaxDiff = 0;

    public AlarmCameraView(Context context, int cameraId) {
        super(context, cameraId);
        this.setCvCameraViewListener(this);
    }

    public AlarmCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.setCvCameraViewListener(this);
    }

    public interface IAlarmCameraListener {
        void onAlarm();
        void onCalibrating();
        void onRun();
        Handler getHandler();
    }

    private void onAlarmInternal() {
        Log.d(TAG, "onAlarmInternal");
        if (mAlarmListener != null) mAlarmListener.getHandler().post(() -> mAlarmListener.onAlarm());
    }

    private void onCalibratingInternal() {
        Log.d(TAG, "onCalibratingInternal");
        if (mAlarmListener != null) mAlarmListener.getHandler().post(() -> mAlarmListener.onCalibrating());
    }

    private void onRunInternal() {
        Log.d(TAG, "onRunInternal");
        if (mAlarmListener != null) mAlarmListener.getHandler().post(() -> mAlarmListener.onRun());
    }

    public enum State {
        IDLE, CALIBRATING, RUNNING
    }

    public State getCurrState() {
        return mState;
    }

    public void startAlarm() {
        mCalibratingStartedAt = System.currentTimeMillis();
        mState = State.CALIBRATING;
        Log.d(TAG, "State: " + mState);
        onCalibratingInternal();
    }

    public void stopAlarm() {
        mCalibratingStartedAt = 0;
        mLastProcessFrameTime = 0;

        mMovingAbsDiffAvg = -1;
        mMaxDiff = 0;

        mState = State.IDLE;
    }

    public void setAlarmListener(IAlarmCameraListener alarmListener) {
        this.mAlarmListener = alarmListener;
    }

    public void removeAlarmListener() {
        this.mAlarmListener = null;
    }

    private long getProcessTimeoutMs() {
        return 1000 / mProcessFps;
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

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        Mat screenMat = inputFrame.gray();

        if (mState == State.RUNNING || mState == State.CALIBRATING) {
            mLastProcessFrameTime = System.currentTimeMillis();

            if (mState == State.CALIBRATING) {
                if (System.currentTimeMillis() - mCalibratingStartedAt >= mTimeToCalibrate) {
                    mState = State.RUNNING;
                    onRunInternal();
                    Log.d(TAG, "State: " + mState);
                }
            }

            if (mLastMat != null) {
                Mat diff = new Mat(screenMat.size(), screenMat.type());
                Core.absdiff(screenMat, mLastMat, diff);
                final double diffD = Core.mean(diff).val[0];
                if (mMovingAbsDiffAvg == -1d) mMovingAbsDiffAvg = diffD;
                else {
                    mMovingAbsDiffAvg = (mMovingAbsDiffAvg * (mAverageOver - 1) + diffD) / mAverageOver;
                }
                if (mMovingDiffAvg == -1d) mMovingDiffAvg = 0;
                else {
                    mMovingDiffAvg = (mMovingDiffAvg * (mAverageOver - 1) + Math.abs(diffD - mMovingAbsDiffAvg)) / mAverageOver;
                }

                if (mMaxDiff < diffD) {
                    mMaxDiff = diffD;
                }
                double absCurrAlarmThreshold = (mMovingDiffAvg * mAlarmThreshhold) + mMovingAbsDiffAvg;
                boolean alarmTriggered = diffD > absCurrAlarmThreshold;
                if (alarmTriggered) Log.d(TAG, "Alarm Triggered: " + new Date().toGMTString());

                Log.v(TAG,
                        String.format("onProcessedFrame:\t%s\t%s\t%s\t%s\t%s",
                            String.format(getContext().getString(R.string.curr_alarm_threshold_val), absCurrAlarmThreshold),
                            String.format(getContext().getString(R.string.moving_diff_abs_avg_val), mMovingAbsDiffAvg),
                            String.format(getContext().getString(R.string.moving_diff_avg_val), mMovingDiffAvg),
                            String.format(getContext().getString(R.string.max_diff_val), mMaxDiff),
                            String.format(getContext().getString(R.string.AbsDiff_val), diffD)
                        )
                );

                if (alarmTriggered && mState == State.RUNNING) onAlarmInternal();

                //release resources
                diff.release();
                mLastMat.release();
            }
            mLastMat = screenMat;
        }
        return screenMat;
    }
}
