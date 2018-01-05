package com.example.alarm.alarmapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.example.alarm.alarmapp.views.AlarmCameraView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

/**
 * This is the MainActivity. It handles all the ui related stuff like buttons and controls the AlarmCameraView.
 */
public class MainActivity extends AppCompatActivity implements Handler.Callback, View.OnClickListener, AlarmCameraView.IAlarmCameraListener {
    private static final String TAG = "MainActivity";

    private static final int TIMEOUT_START = 10000;

    private AlarmCameraView mCameraView;
    private ToggleButton mTbtnStartStop;
    private Switch mSwSound;
    private TextView mTvAlarmTriggered, mTvState;
    private boolean mHasPermission = false;

    private Runnable mRunnableStartTimer = () -> onCommand(Command.START_TIMER);

    private MediaPlayer mAlarmPlayer;
    private Handler mUiHandler;

    private State mState = State.IDLE;

    @Override
    public void onAlarm() {
        Log.d(TAG, "onAlarm()");
        onCommand(Command.ALARM);
    }

    @Override
    public void onCalibrating() {
        Log.d(TAG, "onCalibrating()");
        onCommand(Command.CALIBRATING);
    }

    @Override
    public void onRun() {
        Log.d(TAG, "onRun()");
        onCommand(Command.RUNNING);
    }

    @Override
    public Handler getHandler() {
        return mUiHandler;
    }

    private enum State {
        IDLE, WAITING_TO_START, CALIBRATING, RUNNING
    }

    private enum Command {
        BUTTON_START_STOP, START_TIMER, ALARM, CALIBRATING, RUNNING, ON_PAUSE, RESET_ALARM_TEXT
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //check for camera permission, if it is not granted invoke a layout that displays an error
        mHasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        if (!mHasPermission) {
            setContentView(R.layout.activity_main_permission_missing);
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, 0);
            return; //cancel remaining init. To start the real app we need permission from the start.
        }

        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mUiHandler = new Handler(this);
        mAlarmPlayer = MediaPlayer.create(this, R.raw.sound_alarm);

        mCameraView = findViewById(R.id.cameraView);
        mTvAlarmTriggered = findViewById(R.id.tvAlarmTriggered);
        mTvState = findViewById(R.id.tvState);
        mSwSound = findViewById(R.id.swAlarmSound);
        mTbtnStartStop = findViewById(R.id.tbtnStartStop);

        mTbtnStartStop.setOnClickListener(this);

        mCameraView.setVisibility(SurfaceView.VISIBLE);
        mCameraView.setAlarmListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 0:
                //restart app if we got the permission
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Intent newActivity = new Intent(this, MainActivity.class);
                    startActivity(newActivity);
                    finish();
                }
                break;
        }
    }

    private void onCommand(Command cmd) {
        Log.d(TAG, "Command: " + cmd.toString() + " received in state: " + mState.toString());
        if (cmd == Command.RESET_ALARM_TEXT) mTvAlarmTriggered.setText("false");
        switch (mState) {
            case IDLE:
                switch (cmd) {
                    case BUTTON_START_STOP:
                        mState = State.WAITING_TO_START;
                        mUiHandler.postDelayed(mRunnableStartTimer, TIMEOUT_START);
                        break;
                    default:
                        Log.e(TAG, "invalid command in " + mState + ": " + cmd);
                }
                break;
            case WAITING_TO_START:
                switch (cmd) {
                    case START_TIMER:
                        mCameraView.startAlarm();
                        break;
                    case CALIBRATING:
                        mState = State.CALIBRATING;
                        break;
                    case BUTTON_START_STOP:
                        mState = State.IDLE;
                        mUiHandler.removeCallbacks(mRunnableStartTimer);
                        break;
                    case ON_PAUSE:
                        mState = State.IDLE;
                        mUiHandler.removeCallbacks(mRunnableStartTimer);
                        break;
                    default:
                    Log.e(TAG, "invalid command in " + mState + ": " + cmd);
                }
                break;
            case CALIBRATING:
                switch (cmd) {
                    case BUTTON_START_STOP:
                    case ON_PAUSE:
                        mState = State.IDLE;
                        mCameraView.stopAlarm();
                        break;
                    case RUNNING:
                        mState = State.RUNNING;
                        break;
                    default:
                        Log.e(TAG, "invalid command in " + mState + ": " + cmd);
                }
                break;
            case RUNNING:
                switch (cmd) {
                    case BUTTON_START_STOP:
                    case ON_PAUSE:
                        mState = State.IDLE;
                        mCameraView.stopAlarm();
                        break;
                    case ALARM:
                        if(mSwSound.isChecked()) playAlarmSound();
                        mTvAlarmTriggered.setText("ALAAAAAARM!");
                        mUiHandler.postDelayed(() -> onCommand(Command.RESET_ALARM_TEXT), 2000);
                        break;
                    default:
                        Log.e(TAG, "invalid command in " + mState + ": " + cmd);
                }
                break;
            default:
                Log.e(TAG, "State " + mState + " not implemented.");
        }
        Log.d(TAG, "State after command execution: " + mState.toString());
        mTvState.setText(String.format(getString(R.string.state_val), mState.toString()));
        //set start stop button to correct rendering for the current state
        mTbtnStartStop.setChecked(mState == State.RUNNING || mState == State.CALIBRATING || mState == State.WAITING_TO_START);
    }

    //region lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        if (mHasPermission) {
            if (!OpenCVLoader.initDebug()) {
                Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
                OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_2, this, mLoaderCallback);
            } else {
                Log.d(TAG, "OpenCV library found inside package. Using it!");
                mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mHasPermission) {
            if (mCameraView != null) {
                mCameraView.disableView();
                onCommand(Command.ON_PAUSE);
            }
        }
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
    public boolean handleMessage(Message msg) {
        return false;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.tbtnStartStop:
                onCommand(Command.BUTTON_START_STOP);
                break;
        }
    }
}
