package com.xfgryujk.catearcamera;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.Toast;

class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
	private static final String TAG = "CameraPreview";
	
	protected MainActivity mActivity;
	protected SurfaceHolder mHolder;
	protected Camera mCamera = null;
	protected Camera.Size mPreviewSize = null;
	protected int mPictureWidth, mPictureHeight;
	/** Used to resize the picture to improve performance */
	protected float mPreviewScale, mPictureScale;
	
	public volatile int mRotation = 0;

	protected byte[] mPreviewData;
	protected Bitmap mPreviewBitmap;
	public volatile boolean mCanThreadsRun = true;
	protected PreviewThread[] mPreviewThreads;
	public static Bitmap mResultBitmap;
	
	protected Bitmap mCatEar;
	protected CascadeClassifier mCascade;
	

	public CameraPreview(Context context) {
		super(context);
		initialize(context);
	}

	public CameraPreview(Context context, AttributeSet attrs) {
		super(context, attrs);
		initialize(context);
	}
	
	/** Start preview thread, load classifier */
	protected void initialize(Context context)
	{
		mActivity = (MainActivity)context;
		mHolder   = getHolder();
		mHolder.addCallback(this);
		
		// Start preview thread
		int nProcessors = Runtime.getRuntime().availableProcessors();
		Log.i(TAG, nProcessors + " threads");
		mPreviewThreads = new PreviewThread[nProcessors];
		for(int i = 0; i < nProcessors; i++)
		{
			mPreviewThreads[i] = new PreviewThread();
			new Thread(mPreviewThreads[i]).start();
		}
		
		// Load classifier
		try {
			InputStream is      = context.getResources().openRawResource(R.raw.haarcascade_frontalface_alt);
			File cascadeDir     = context.getDir("cascade", Context.MODE_PRIVATE);
			File cascadeFile    = new File(cascadeDir, "haarcascade_frontalface_alt.xml");
			FileOutputStream os = new FileOutputStream(cascadeFile);

			byte[] buffer = new byte[4096];
			int bytesRead;
			while((bytesRead = is.read(buffer)) != -1)
				os.write(buffer, 0, bytesRead);
			is.close();
			os.close();

			mCascade = new CascadeClassifier(cascadeFile.getAbsolutePath());
			if(mCascade.empty())
				mCascade = null;

			cascadeFile.delete();
			cascadeDir.delete();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if(mCascade == null)
		{
			new AlertDialog.Builder(mActivity)
			.setTitle(getResources().getString(R.string.error))
			.setMessage(getResources().getString(R.string.failed_to_load_classifier))
			.setPositiveButton(getResources().getString(R.string.ok),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						mActivity.finish();
					}
				}
			)
			.show();
		}
	}

	/** Load the image to be drawn */
	public void loadImage() {
		if(SettingsManager.mImageType != -1) // Default image
		{
			mCatEar = BitmapFactory.decodeResource(getResources(), SettingsManager.mDefaultImage[SettingsManager.mImageType]);
			MainActivity.mXScale = MainActivity.mDefaultXScale[SettingsManager.mImageType];
			MainActivity.mYScale = MainActivity.mDefaultYScale[SettingsManager.mImageType];
			MainActivity.mWScale = MainActivity.mDefaultWScale[SettingsManager.mImageType];
			MainActivity.mHScale = MainActivity.mDefaultHScale[SettingsManager.mImageType];
		}
		else
		{
			mCatEar = BitmapFactory.decodeFile(SettingsManager.mImagePath);
			if(mCatEar == null)
			{
				Toast.makeText(mActivity, getResources().getString(R.string.failed_to_load_image), Toast.LENGTH_SHORT).show();
				PreferenceManager.getDefaultSharedPreferences(mActivity).edit()
					.putInt(getResources().getString(R.string.pref_image_type), 0).commit();
				SettingsManager.mImageType = 0;
				loadImage();
				return;
			}
			else
			{
				MainActivity.mXScale = 0.0f;
				MainActivity.mYScale = 0.0f;
				MainActivity.mWScale = 1.0f;
				MainActivity.mHScale = (float)mCatEar.getHeight() / (float)mCatEar.getWidth();
			}
		}
	}
	
	public Camera getCamera() {
		return mCamera;
	}
	
	public void surfaceChanged(SurfaceHolder _holder, int format, int width, int height) {
		Log.i(TAG, "surfaceChanged()");
	}
	
	/** Reset camera */
	public void surfaceCreated(SurfaceHolder holder) {
		Log.i(TAG, "surfaceCreated()");
		if(mCascade != null)
			resetCamera();
	}
	
	/** Release camera */
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.i(TAG, "surfaceDestroyed()");
		if(mCascade != null)
			releaseCamera();
	}
	
	/** Open camera, set parameters, initialize Mats */
	public void resetCamera() {
		releaseCamera();
		
		// Open camera
		try {
			mCamera = Camera.open();
		} catch (Exception e) {
			e.printStackTrace();
			mCamera = null;
		}
		if(mCamera == null)
		{
			new AlertDialog.Builder(mActivity)
			.setTitle(getResources().getString(R.string.error))
			.setMessage(getResources().getString(R.string.failed_to_open_camera))
			.setPositiveButton(getResources().getString(R.string.ok),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						mActivity.finish();
					}
				}
			)
			.show();
			return;
		}
		
		// Set parameters
		Camera.Parameters params = mCamera.getParameters();
		DisplayMetrics dm = new DisplayMetrics();
		mActivity.getWindowManager().getDefaultDisplay().getMetrics(dm);
		
		params.setExposureCompensation(SettingsManager.mEV);
		params.setWhiteBalance(SettingsManager.mWhiteBalance);
		
		List<Camera.Size> sizes;
		if(mPreviewSize == null)
		{
			// Largest preview size
			sizes = params.getSupportedPreviewSizes();
			int maxPixels = 0;
			for (Camera.Size size : sizes)
			{
				int pixels = size.width * size.height;
				if (maxPixels < pixels)
				{
					maxPixels    = pixels;
					mPreviewSize = size;
				}
			}
			mPreviewScale = Math.min((float)dm.heightPixels / (float)mPreviewSize.height, (float)dm.widthPixels / (float)mPreviewSize.width);
		}
		params.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
				
		sizes = params.getSupportedPictureSizes();
		mPictureWidth  = sizes.get(SettingsManager.mResolution).width;
		mPictureHeight = sizes.get(SettingsManager.mResolution).height;
		mPictureScale  = Math.min((float)dm.heightPixels / (float)mPictureHeight, (float)dm.widthPixels / (float)mPictureWidth);
		params.setPictureSize(mPictureWidth, mPictureHeight);
		
		params.set("iso", SettingsManager.mISO);
		params.set("iso-speed", SettingsManager.mISO);
		params.set("nv-picture-iso", SettingsManager.mISO);
		
		Log.i(TAG, "EV " + SettingsManager.mEV);
		Log.i(TAG, "white balance " + SettingsManager.mWhiteBalance);
		Log.i(TAG, "picture size " + mPictureWidth + " * " + mPictureHeight);
		Log.i(TAG, "ISO " + SettingsManager.mISO);

		mCamera.setParameters(params);
		
		// Set preview display
		try {
			mCamera.setPreviewDisplay(mHolder);
		} catch(IOException e) {
			e.printStackTrace();
		}
		
		// Set preview callback
		mCamera.setPreviewCallback(new PreviewCallback() {
			public void onPreviewFrame(byte[] data, Camera camera) {
				if(SettingsManager.mPreview)
				{
					synchronized (CameraPreview.this) {
						mPreviewData = data;
						CameraPreview.this.notify();
					}
				}
			}
		});
		
		// Initialize Mats and mPreviewBitmap
		for(PreviewThread t : mPreviewThreads)
			t.initializeMats();
		mPreviewBitmap = Bitmap.createBitmap((int)(mPreviewSize.width * mPreviewScale), (int)(mPreviewSize.height * mPreviewScale), Bitmap.Config.ARGB_8888);
		mActivity.mResultPreview.setImageBitmap(mPreviewBitmap);
		
		// Resize
		FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)getLayoutParams();
		lp.width      = (int)(mPreviewSize.width * mPreviewScale);
		lp.height     = (int)(mPreviewSize.height * mPreviewScale);
		lp.leftMargin = (dm.widthPixels - lp.width) / 2;
		lp.topMargin  = (dm.heightPixels - lp.height) / 2;
		setLayoutParams(lp);
		mActivity.mResultPreview.setLayoutParams(lp);
		
		// Start preview
		mCamera.startPreview();
	}
	
	/** Release camera and Mats and mPreviewBitmap */
	public void releaseCamera() {
		// Release camera
		if(mCamera != null)
		{
			mCamera.stopPreview();
			mCamera.setPreviewCallback(null);
			mCamera.release();
			mCamera = null;
			mHandler.removeMessages(MSG_HIDE_FOCUS_RESULT);
			mActivity.mFocusResult.setVisibility(INVISIBLE);
			mActivity.mFocusResult.setImageBitmap(null);
		}
		
		// Release Mats and mPreviewBitmap
		for(PreviewThread t : mPreviewThreads)
			t.releaseMats();
		if(mPreviewBitmap != null)
		{
			mActivity.mResultPreview.setImageBitmap(null);
			mPreviewBitmap.recycle();
			mPreviewBitmap = null;
		}
	}
	
	/** Auto focus */
	public void onClick() {
		mHandler.removeMessages(MSG_HIDE_FOCUS_RESULT);
		mActivity.mFocusResult.setVisibility(INVISIBLE);
		mCamera.autoFocus(new AutoFocusCallback() {
			public void onAutoFocus(boolean successful, Camera camera) {
				mActivity.mFocusResult.setImageResource(successful ? R.drawable.focus_succeeded : R.drawable.focus_failed);
				mActivity.mFocusResult.setVisibility(VISIBLE);
				mHandler.sendEmptyMessageDelayed(MSG_HIDE_FOCUS_RESULT, 2000);
			}
		});
	}
	
	protected class PreviewThread implements Runnable {
		/** Used to detect faces */
		protected Mat mYuv, mRgba, mGray;
		
		public synchronized void initializeMats() {
			mYuv  = new Mat(mPreviewSize.height + mPreviewSize.height / 2, mPreviewSize.width, CvType.CV_8UC1);
			mRgba = new Mat();
			mGray = new Mat();
		}
		
		public synchronized void releaseMats() {
			if (mYuv != null)
			{
				mYuv.release();
				mYuv  = null;
			}
			if (mRgba != null)
			{
				mRgba.release();
				mRgba = null;
			}
			if (mGray != null)
			{
				mGray.release();
				mGray = null;
			}
		}
		
		public void run() {
			byte[] previewData = null;
			
			while(mCanThreadsRun)
			{
				//Log.i(TAG, "Getting preview data");
				synchronized (CameraPreview.this) {
					try {
						CameraPreview.this.wait();
						if(!mCanThreadsRun)
							break;
						previewData = mPreviewData;
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				
				int rotation = mRotation;
				List<Rect> faces;
				synchronized (this) {
					//Log.i(TAG, "Detecting faces");
					faces = detectFaces(previewData, rotation, mYuv, mRgba, mGray);
				}
				
				synchronized (mPreviewBitmap) {
					//Log.i(TAG, "Drawing cat ear");
					mPreviewBitmap.eraseColor(Color.TRANSPARENT);
					drawCatEar(mPreviewBitmap, rotation, faces);
					
					if(!mHandler.hasMessages(MSG_UPDATE_PREVIEW))
						mHandler.sendEmptyMessage(MSG_UPDATE_PREVIEW);
				}
			}
		}
	}

	protected static final int MSG_HIDE_FOCUS_RESULT = 1;
	protected static final int MSG_UPDATE_PREVIEW = 2;
	@SuppressLint("HandlerLeak")
	protected Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch(msg.what)
			{
			case MSG_HIDE_FOCUS_RESULT:
				mActivity.mFocusResult.setVisibility(INVISIBLE);
				mActivity.mFocusResult.setImageBitmap(null);
				break;
				
			case MSG_UPDATE_PREVIEW:
				if(mPreviewBitmap != null)
				{
					synchronized (mPreviewBitmap) {
						mActivity.mResultPreview.invalidate();
					}
				}
				break;
			}
		}
	};
	
	public void takePicture() {
		// Take picture
		mCamera.takePicture(null, null, new PictureCallback() {
			@SuppressLint("SimpleDateFormat")
			public void onPictureTaken(byte[] data, Camera camera) {
				// Deal the picture
				mResultBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
				Mat rgba = Utils.bitmapToMat(mResultBitmap);
				mResultBitmap.recycle();
				List<Rect> faces = detectFaces(rgba, mRotation); // Will rotate rgba
				mResultBitmap = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888);
				Utils.matToBitmap(rgba, mResultBitmap); // Make mResultBitmap mutable
				drawCatEar(mResultBitmap, 0, faces);
				
				// Preview
		        mActivity.startActivity(new Intent(mActivity, PreviewActivity.class));

				camera.startPreview();
			}
		});
	}
	
	protected List<Rect> detectFaces(byte[] YUVdata, int rotation, Mat yuv, Mat rgba, Mat gray) {
		// Convert to Mat
		yuv.put(0, 0, YUVdata);
		Imgproc.cvtColor(yuv, rgba, Imgproc.COLOR_YUV420sp2RGB, 4);
		Imgproc.resize(rgba, rgba, new Size(mPreviewSize.width * mPreviewScale, mPreviewSize.height * mPreviewScale));
		rotateMat(rgba, rotation);
		Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY, 4);
		return doDetectFaces(true, gray);
	}
	
	protected List<Rect> detectFaces(Mat rgba, int rotation) {
		rotateMat(rgba, rotation);
		Mat gray = new Mat();
		Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY, 4);
		// Improve performance
		Imgproc.resize(gray, gray, new Size(gray.cols() * mPictureScale, gray.rows() * mPictureScale));
		return doDetectFaces(false, gray);
	}
	
	protected List<Rect> doDetectFaces(boolean preview, Mat gray) {
		Imgproc.equalizeHist(gray, gray);

		List<Rect> faces = new LinkedList<Rect>();
		synchronized (mCascade) {
			// Detect faces
			mCascade.detectMultiScale(gray, faces, SettingsManager.mScaleFactor, 2, 2 // objdetect.CV_HAAR_SCALE_IMAGE
				, new Size(SettingsManager.mMinFaceSize, SettingsManager.mMinFaceSize));
		}
		
		// Remove repeated faces
		for(Rect r1 : faces)
			for(Rect r2 : faces)
				if(r1 != r2 && r1.x >= r2.x && r1.y >= r2.y && r1.width <= r2.width && r1.height <= r2.height)
					faces.remove(r2);
		
		// Restore to the picture before resize()
		if(!preview)
			for(Rect r : faces)
			{
				r.x      /= mPictureScale;
				r.y      /= mPictureScale;
				r.width  /= mPictureScale;
				r.height /= mPictureScale;
			}
		
		return faces;
	}
	
	protected static void rotateMat(Mat mat, int degree) {
		switch(degree)
		{
		case 90:
			Core.transpose(mat, mat);
			Core.flip(mat, mat, 0);
			break;
		case 180:
			Core.flip(mat, mat, -1);
			break;
		case 270:
			Core.transpose(mat, mat);
			Core.flip(mat, mat, 1);
			break;
		}
	}
	
	/** Call detectFaces before drawCatEar ! */
	protected void drawCatEar(Bitmap bmp, int rotation, List<Rect> faces) {
		Canvas canvas = new Canvas();
		canvas.setBitmap(bmp);
		
		// Rotate
		canvas.translate(bmp.getWidth() / 2, bmp.getHeight() / 2);
		canvas.rotate(rotation);
		if(rotation == 0 || rotation == 180)
			canvas.translate(-bmp.getWidth() / 2, -bmp.getHeight() / 2);
		else
			canvas.translate(-bmp.getHeight() / 2, -bmp.getWidth() / 2);
		
		Paint rectPaint = null;
		if(SettingsManager.mDebugMode)
		{
			rectPaint = new Paint();
			rectPaint.setColor(Color.GREEN);
			rectPaint.setStyle(Paint.Style.STROKE);
			rectPaint.setStrokeWidth(2);
		}
		
		for(Rect r : faces)
		{
			// Draw rectangles of faces
			if(SettingsManager.mDebugMode)
				canvas.drawRect(r.x, r.y, r.x + r.width, r.y + r.height, rectPaint);
			
			// Draw cat ear
			canvas.drawBitmap(mCatEar, null
				, new RectF(r.x - r.width * MainActivity.mXScale
				, r.y - r.height * MainActivity.mYScale
				, r.x - r.width * MainActivity.mXScale + r.width * MainActivity.mWScale
				, r.y - r.height * MainActivity.mYScale + r.height * MainActivity.mHScale)
				, null);
		}
		if(SettingsManager.mDebugMode)
			drawDebugString(canvas, faces);
	}

	protected long mLastFPSTime = System.currentTimeMillis();
	private int mFrameCount = 0;
	private String mFPSString = "";
	/** Output FPS, scales, rectangles, etc. */
	@SuppressLint("DefaultLocale")
	protected void drawDebugString(Canvas canvas, List<Rect> faces) {
		mFrameCount++;
		long time = System.currentTimeMillis();
		if(time - mLastFPSTime >= 1000)
		{
			mFPSString   = String.format("FPS:%.2f", (float)mFrameCount * 1000.0f / (float)(time - mLastFPSTime));
			mLastFPSTime = time;
			mFrameCount  = 0;
		}
		String s = String.format("%s\n%d*%d %d*%d\n%.4f %.4f %.4f %.4f\n"
			, mFPSString
			, mPreviewSize.width, mPreviewSize.height, mPictureWidth, mPictureHeight
			, MainActivity.mXScale, MainActivity.mYScale, MainActivity.mWScale, MainActivity.mHScale);
		
		for(Rect r : faces)
			s += String.format("(%d,%d) %d*%d (%d,%d) %d*%d\n"
				, r.x, r.y, r.width, r.height
				, Math.round(r.x - r.width * MainActivity.mXScale), Math.round(r.y - r.height * MainActivity.mYScale)
				, (int)(r.width * MainActivity.mWScale), (int)(r.height * MainActivity.mHScale));
		
		TextPaint textPaint = new TextPaint();
		textPaint.setColor(Color.GREEN);
		textPaint.setTextSize(20);
		StaticLayout layout = new StaticLayout(s, textPaint, 400, Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true);
		layout.draw(canvas);
	}
}