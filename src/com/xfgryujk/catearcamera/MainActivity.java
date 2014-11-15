package com.xfgryujk.catearcamera;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;

public class MainActivity extends Activity {
	public CameraPreview mCameraPreview;
	public ImageView mResultPreview;
	public ImageView mFocusResult;
	public Button mButtonShutter;
	public Button mButtonSetting;
	
	protected OrientationEventListener mOrientationListener;
	
	/** Used to calculate cat ear rectangle */
	public float mXScale, mYScale, mWScale, mHScale;
	public static final float[] mDefaultXScale = {0.1280f, 0.1614f, 0.5080f, 0.2560f, 0.0560f, 0.3240f, 0.0040f, -0.024f};
	public static final float[] mDefaultYScale = {0.3320f, 0.1470f, 0.4440f, 0.4360f, 0.5520f, 0.1600f, 0.4160f, 0.1800f};
	public static final float[] mDefaultWScale = {1.2700f, 1.2437f, 1.7938f, 1.6394f, 1.3577f, 1.4115f, 1.1436f, 1.1064f};
	public static final float[] mDefaultHScale = {0.6695f, 0.2585f, 3.0025f, 1.6509f, 1.7650f, 1.3177f, 1.5444f, 1.2566f};

	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// No title
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		// Full screen, keep screen on
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
				, WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		// Initialize UI
		setContentView(R.layout.main_activity);
		mCameraPreview = (CameraPreview)findViewById(R.id.camera_preview);
		mResultPreview = (ImageView)findViewById(R.id.result_preview);
		mFocusResult   = (ImageView)findViewById(R.id.focus_result);
		mButtonShutter = (Button)findViewById(R.id.shutter_button);
		mButtonSetting = (Button)findViewById(R.id.setting_button);
		
		mButtonShutter.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				mCameraPreview.takePicture();
			}
		});
		
		SettingsManager.initialize(this);
		mButtonSetting.setOnClickListener(SettingsManager.mOnButtonSettingClick);
		
		// Orientation
		mOrientationListener = new OrientationEventListener(this) {
			@Override
			public void onOrientationChanged(int orientation) {
				if (orientation != ORIENTATION_UNKNOWN)
				{
					if ((orientation >= 0 && orientation < 45) || (orientation >= 315 && orientation < 360))
						mCameraPreview.mRotation = 270;
					else if (orientation >= 45 && orientation < 135)
						mCameraPreview.mRotation = 180;
					else if (orientation >= 135 && orientation < 225)
						mCameraPreview.mRotation = 90;
					else// if (orientation >= 225 && orientation < 315)
						mCameraPreview.mRotation = 0;
				}
			}
		};
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		SettingsManager.onActivityResult(requestCode, resultCode, data);
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		mOrientationListener.disable();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		mOrientationListener.enable();
	}
	
	@Override
	protected void onDestroy() {
		// Stop threads
		mCameraPreview.mCanThreadsRun = false;
		synchronized (mCameraPreview) {
			mCameraPreview.notifyAll();
		}
		
		super.onDestroy();
		System.exit(0);
	}

	// Motion
	private long mDownTime;
	/** true : drag, false : zoom */
	private boolean mIsDragMode = true;
	private float mStartX, mStartY;
	private float mStartXScale, mStartYScale, mStartWScale, mStartHScale;
	private float mStartDX, mStartDY, mStartDist;
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		switch(event.getAction() & MotionEvent.ACTION_MASK)
		{
		case MotionEvent.ACTION_DOWN:
			mDownTime = event.getEventTime();
			mStartX = event.getX();
			mStartY = event.getY();
			mStartXScale = mXScale;
			mStartYScale = mYScale;
			mStartWScale = mWScale;
			mStartHScale = mHScale;
			return true;
		
		case MotionEvent.ACTION_UP:
			if (event.getEventTime() - mDownTime <= 200)
				mCameraPreview.onClick();
			return true;
		
		case MotionEvent.ACTION_MOVE:
			if (mIsDragMode) // Dragging
			{
				switch(mCameraPreview.mRotation)
				{
				case 0:
					mXScale = mStartXScale - (event.getX() - mStartX) / 250f;
					mYScale = mStartYScale - (event.getY() - mStartY) / 250f;
					break;
				case 90:
					mXScale = mStartXScale - (event.getY() - mStartY) / 250f;
					mYScale = mStartYScale + (event.getX() - mStartX) / 250f;
					break;
				case 180:
					mXScale = mStartXScale + (event.getX() - mStartX) / 250f;
					mYScale = mStartYScale + (event.getY() - mStartY) / 250f;
					break;
				case 270:
					mXScale = mStartXScale + (event.getY() - mStartY) / 250f;
					mYScale = mStartYScale - (event.getX() - mStartX) / 250f;
					break;
				}
			}
			else // Zooming
			{
				if(SettingsManager.mKeepScale)
				{
					float dx = event.getX(1) - event.getX(0), dy = event.getY(1) - event.getY(0);
					float dist = (float)Math.sqrt(dx * dx + dy * dy);
					mWScale = mStartWScale * dist / mStartDist;
					mHScale = mStartHScale * dist / mStartDist;
				}
				else
				{
					if(mCameraPreview.mRotation == 0 || mCameraPreview.mRotation == 180)
					{
						mWScale = mStartWScale * (event.getX(1) - event.getX(0)) / mStartDX;
						mHScale = mStartHScale * (event.getY(1) - event.getY(0)) / mStartDY;
					}
					else
					{
						mWScale = mStartWScale * (event.getY(1) - event.getY(0)) / mStartDY;
						mHScale = mStartHScale * (event.getX(1) - event.getX(0)) / mStartDX;
					}
				}
			}
			return true;
		
		case MotionEvent.ACTION_POINTER_UP:
			mIsDragMode = true;
			return true;
		
		case MotionEvent.ACTION_POINTER_DOWN:
			mStartDX =  event.getX(1) - event.getX(0);
			mStartDY =  event.getY(1) - event.getY(0);
			mStartDist = (float)Math.sqrt(mStartDX * mStartDX + mStartDY * mStartDY);
			if(mStartDist > 10f)
				mIsDragMode = false;
			return true;
		}
		return false;
	}
}
