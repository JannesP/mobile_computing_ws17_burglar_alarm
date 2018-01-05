package com.example.alarm.alarmapp.views;

import android.content.Context;
import android.hardware.Camera;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;

import com.example.alarm.alarmapp.R;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Date;

/**
 * A camera view that has a movement detection.
 */
public class AlarmCameraView extends JavaCameraView implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final String TAG = AlarmCameraView.class.getName();

    private int mTimeToCalibrate = 10000;
    private double mAlarmThreshold = 6d;
    private int mProcessFps = 4;
    private double mAverageOver = 100d / mProcessFps;

    private long mCalibratingStartedAt = 0;

    private boolean mAreCameraParamsSet = false;
    private State mState = State.IDLE;
    private IAlarmCameraListener mAlarmListener = null;
    private Mat mLastMat = null;
    private Mat mEmptyGrayMap = null;

    private double mMovingAbsDiffAvg = -1d;
    private double mMovingDiffAvg = -1d;
    private double mMaxDiff = 0;

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
        mEmptyGrayMap = new Mat(height, width, CvType.CV_8UC1);
    }

    @Override
    public void onCameraViewStopped() {
        if (mEmptyGrayMap != null) mEmptyGrayMap.release();
        mEmptyGrayMap = null;
        if (mLastMat != null) mLastMat.release();
        mLastMat = null;
    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        Mat screenMat = inputFrame.rgba();

        if (mState == State.RUNNING || mState == State.CALIBRATING) {
            if (mState == State.CALIBRATING) {

                if (System.currentTimeMillis() - mCalibratingStartedAt >= mTimeToCalibrate) {
                    mState = State.RUNNING;
                    onRunInternal();
                    Log.d(TAG, "State: " + mState);
                }
            }

            if (mLastMat != null) {
                //calculate trigger values
                Mat grayMat = inputFrame.gray();
                Mat diff = new Mat(grayMat.size(), grayMat.type());
                Core.absdiff(grayMat, mLastMat, diff);
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
                double absCurrAlarmThreshold = (mMovingDiffAvg * mAlarmThreshold) + mMovingAbsDiffAvg;
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

                //draw changes on screenMat
                ArrayList<Mat> mergeMats = new ArrayList<>(3);
                mergeMats.add(diff);
                mergeMats.add(mEmptyGrayMap);
                mergeMats.add(mEmptyGrayMap);

                //create buffer for red diff (rgb mat)
                Mat matRedDiff = new Mat(diff.size(), screenMat.type());
                //merge two empty single channel mats and the diff mat as different channels into rgb mat
                Core.merge(mergeMats, matRedDiff);
                //create buffer for gray image in rgb mat
                Mat grayRgbMat = new Mat(screenMat.size(), screenMat.type());
                //convert gray single channel mat to rgb mat
                Imgproc.cvtColor(grayMat, grayRgbMat, Imgproc.COLOR_GRAY2RGB);
                //add the gray and red overlay together
                Core.add(grayRgbMat, matRedDiff, screenMat);

                //release resources
                grayRgbMat.release();
                grayMat.release();
                matRedDiff.release();
                diff.release();
                mLastMat.release();
            }
            mLastMat = inputFrame.gray();
        }
        return screenMat;
    }
}
