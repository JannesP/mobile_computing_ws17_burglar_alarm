package com.example.alarm.alarmapp;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.example.alarm.alarmapp.views.AlarmCameraView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;

import java.util.Date;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2, Handler.Callback, View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "MainActivity";

    private static final int TIMEOUT_START = 10000;
    private static final int TIMEOUT_CALIBRATE = 10000;
    private static final double ALARM_THRESHHOLD_FACTOR = 1.3;
    private static final int FRAME_DIVIDER = 8;
    private static final double AVERAGE_OVER = 100d / FRAME_DIVIDER;

    private AlarmCameraView mCameraView;
    private Button mBtnClear;
    private ToggleButton mTbtnStartStop;
    private Switch mSwSound;
    private TextView mTvAbsDiff, mTvMovingDiffAvg, mTvMaxDiff, mTvAlarmTriggered, mTvState;

    private MediaPlayer mAlarmPlayer;
    private Handler mUiHandler;

    private State mState = State.IDLE;
    private Mat mLastMat = null;
    private volatile double mMovingDiffAvg = -1d;
    private volatile double mMaxDiff = 0;
    private volatile int mFrameNumber = 0;

    private enum State {
        IDLE, WAITING_TO_START, CALIBRATING, RUNNING
    }

    private enum Command {
        BUTTON_START_STOP, START_TIMER, ALARM, CALIBRATE_TIMER
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mUiHandler = new Handler(this);
        mAlarmPlayer = MediaPlayer.create(this, R.raw.sound_alarm);

        mCameraView = findViewById(R.id.cameraView);
        mTvAbsDiff = findViewById(R.id.tvAbsDiff);
        mTvMovingDiffAvg = findViewById(R.id.tvMovingDiffAvg);
        mTvMaxDiff = findViewById(R.id.tvMaxDiff);
        mTvAlarmTriggered = findViewById(R.id.tvAlarmTriggered);
        mTvState = findViewById(R.id.tvState);
        mBtnClear = findViewById(R.id.btnClear);
        mSwSound = findViewById(R.id.swAlarmSound);
        mTbtnStartStop = findViewById(R.id.tbtnStartStop);

        mTbtnStartStop.setOnCheckedChangeListener(this);
        mBtnClear.setOnClickListener(this);

        mCameraView.setVisibility(SurfaceView.VISIBLE);
        mCameraView.setCvCameraViewListener(this);
    }

    private void onCommand(Command cmd) {
        switch (mState) {
            case IDLE:
                switch (cmd) {
                    case BUTTON_START_STOP:
                        mTbtnStartStop.setEnabled(false);
                        mState = State.WAITING_TO_START;
                        mUiHandler.postDelayed(() -> onCommand(Command.START_TIMER), TIMEOUT_START);
                        break;
                    default:
                        Log.e(TAG, "invalid command in " + mState + ": " + cmd);
                }
                break;
            case WAITING_TO_START:
                switch (cmd) {
                    case START_TIMER:
                        mState = State.CALIBRATING;
                        mUiHandler.postDelayed(() -> onCommand(Command.CALIBRATE_TIMER), TIMEOUT_CALIBRATE);
                        mTbtnStartStop.setEnabled(true);
                        break;
                    default:
                    Log.e(TAG, "invalid command in " + mState + ": " + cmd);
                }
                break;
            case CALIBRATING:
                switch (cmd) {
                    case CALIBRATE_TIMER:
                        mState = State.RUNNING;
                        break;
                    case BUTTON_START_STOP:
                        mState = State.IDLE;
                        break;
                    default:
                        Log.e(TAG, "invalid command in " + mState + ": " + cmd);
                }
                break;
            case RUNNING:
                switch (cmd) {
                    case BUTTON_START_STOP:
                        mState = State.IDLE;
                        break;
                    case ALARM:
                        if(mSwSound.isChecked()) playAlarmSound();
                        break;
                    default:
                        Log.e(TAG, "invalid command in " + mState + ": " + cmd);
                }
                break;
            default:
                Log.e(TAG, "State " + mState + " not implemented.");
        }
        mTvState.setText(String.format(getString(R.string.state_val), mState.toString()));
    }

    //region lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_2, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCameraView != null) mCameraView.disableView();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    //endregion

    private void playAlarmSound() {
        if(!mAlarmPlayer.isPlaying()){
            mAlarmPlayer.seekTo(0);
            mAlarmPlayer.start();
        }
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mCameraView.enableView();

                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.i(TAG, "onCameraViewStarted()");

    }

    @Override
    public void onCameraViewStopped() {
        Log.i(TAG, "onCameraViewStopped()");
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat currMat = inputFrame.gray();

        mFrameNumber = (mFrameNumber++) % FRAME_DIVIDER;
        if (mFrameNumber == 0 && (mState == State.RUNNING || mState == State.CALIBRATING)){
            if (mLastMat != null) {
                Mat diff = new Mat(currMat.size(), currMat.type());
                Core.absdiff(currMat, mLastMat, diff);
                final double diffD = Core.mean(diff).val[0];
                if (mMovingDiffAvg == -1d) mMovingDiffAvg = diffD;
                else {
                    mMovingDiffAvg = (mMovingDiffAvg * (AVERAGE_OVER - 1) + diffD) / AVERAGE_OVER;
                }
                if (mMaxDiff < diffD) {
                    mMaxDiff = diffD;
                }
                final boolean alarmTriggered = diffD > (mMovingDiffAvg * ALARM_THRESHHOLD_FACTOR);
                if (alarmTriggered) Log.d(TAG, "Alarm Triggered: " + new Date().toGMTString());
                mUiHandler.post(() -> {
                    mTvAbsDiff.setText(String.format(getString(R.string.AbsDiff_val), diffD));
                    mTvMovingDiffAvg.setText(String.format(getString(R.string.moving_diff_avg_val), mMovingDiffAvg));
                    mTvMaxDiff.setText(String.format(getString(R.string.max_diff_val), mMaxDiff));
                    if (alarmTriggered && mState == State.RUNNING) onCommand(Command.ALARM);
                    mTvAlarmTriggered.setText(String.format(getString(R.string.alarm_triggered_val), alarmTriggered));
                });
                //release resources
                mLastMat.release();
            }


            mLastMat = currMat;
        }
        return currMat;
    }

    @Override
    public boolean handleMessage(Message msg) {
        return false;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnClear:
                mMovingDiffAvg = 0;
                mMaxDiff = 0;
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.tbtnStartStop:
                onCommand(Command.BUTTON_START_STOP);
                break;
        }
    }
}
