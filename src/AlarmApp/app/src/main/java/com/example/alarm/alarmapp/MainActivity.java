package com.example.alarm.alarmapp;

import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.TextView;

import com.example.alarm.alarmapp.views.AlarmCameraView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener;
import org.opencv.android.CameraGLSurfaceView;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.engine.OpenCVEngineInterface;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2, Handler.Callback {
    private static final String TAG = "MainActivity";

    private static final int FRAME_DIVIDER = 4;
    private static final double AVERAGE_OVER = 100d / FRAME_DIVIDER;

    private AlarmCameraView mCameraView;
    private TextView mTvAbsDiff, mTvMovingDiffAvg;
    private Handler mUiHandler;


    private Mat mLastMat = null;
    private double mMovingDiffAvg = -1d;
    private double mMaxDiff = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mUiHandler = new Handler(this);

        mCameraView = findViewById(R.id.cameraView);
        mTvAbsDiff = findViewById(R.id.tvAbsDiff);
        mTvMovingDiffAvg = findViewById(R.id.tvMovingDiffAvg);

        mCameraView.setVisibility(SurfaceView.VISIBLE);
        mCameraView.setCvCameraViewListener(this);
    }

    //region lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_13, this, mLoaderCallback);
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

        if (mLastMat != null) {
            Mat diff = new Mat(currMat.size(), currMat.type());
            Core.absdiff(currMat, mLastMat, diff);
            double diffF = Core.mean(diff).val[0];
            if (mMovingDiffAvg == -1d) mMovingDiffAvg = diffF;
            else {
                mMovingDiffAvg = (mMovingDiffAvg * (AVERAGE_OVER - 1) + diffF) / AVERAGE_OVER;
            }
            mUiHandler.post(() -> {
                mTvAbsDiff.setText(String.format(getString(R.string.AbsDiff_val), diffF));
                mTvMovingDiffAvg.setText(String.format(getString(R.string.moving_diff_avg_val), mMovingDiffAvg));
            });
            //release resources
            mLastMat.release();
        }


        mLastMat = currMat;
        return currMat;
    }

    @Override
    public boolean handleMessage(Message msg) {
        return false;
    }
}
