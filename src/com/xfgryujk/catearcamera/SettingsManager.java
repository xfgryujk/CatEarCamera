package com.xfgryujk.catearcamera;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsManager {
	protected static MainActivity mMainActivity;
	
	public static View mDialogView = null;
	public static ListView mListView = null;
	public static SimpleAdapter mAdapter = null;
	public static Dialog mDialog = null;

	public static int mCameraID;
	public static int mEV;
	public static String mWhiteBalance;
	public static int mPreviewResolution;
	public static int mResolution;
	public static String mISO;
	public static String mPath;
	
	/** Default image index or -1 : custom image */
	public static int mImageType;
	public static final int[] mDefaultImage = {R.drawable.catear1, R.drawable.catear2, R.drawable.twintail, 
		R.drawable.kana, R.drawable.kim, R.drawable.rage_comic, R.drawable.yao, R.drawable.doge};
	public static String mImagePath;
	public static boolean mPreview;
	public static boolean mKeepScale;
	public static int mMinFaceScale;
	public static float mScaleFactor;
	public static boolean mDebugMode;
	
	
	/** Load settings */
	public static void initialize(MainActivity mainActivity) {
		mMainActivity = mainActivity;
		
		// Load settings
		Resources res = mMainActivity.getResources();
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mMainActivity);
		mCameraID       = pref.getInt(res.getString(R.string.pref_camera_id), 0);
		mEV             = pref.getInt(res.getString(R.string.pref_EV) + mCameraID, 0);
		mWhiteBalance   = pref.getString(res.getString(R.string.pref_white_balance) + mCameraID, "auto");
		mPreviewResolution = pref.getInt(res.getString(R.string.pref_preview_resolution) + mCameraID, 0);
		mResolution     = pref.getInt(res.getString(R.string.pref_resolution) + mCameraID, 0);
		mISO            = pref.getString(res.getString(R.string.pref_ISO) + mCameraID, "auto");
		mPath           = pref.getString(res.getString(R.string.pref_path), Environment.getExternalStorageDirectory().getPath() + "/CatEarCamera");
		
		mImageType      = pref.getInt(res.getString(R.string.pref_image_type), 0);
		mImagePath      = pref.getString(res.getString(R.string.pref_image_path), "");
		mMainActivity.mCameraPreview.loadImage();
		mPreview        = pref.getBoolean(res.getString(R.string.pref_preview), true);
		if(!mPreview)
			mMainActivity.mResultPreview.setVisibility(View.INVISIBLE);
		mKeepScale      = pref.getBoolean(res.getString(R.string.pref_keep_scale), true);
		mMinFaceScale   = pref.getInt(res.getString(R.string.pref_min_face_scale), 10);
		mScaleFactor    = pref.getFloat(res.getString(R.string.pref_scale_factor), 1.5f);
		mDebugMode      = pref.getBoolean(res.getString(R.string.pref_debug_mode), false);
	}

	@SuppressLint("InflateParams")
	protected static void initializeDialog() {
		mDialogView = LayoutInflater.from(mMainActivity).inflate(R.layout.settings_dialog, null);

		// Set list
		mListView = (ListView)mDialogView.findViewById(R.id.settings_list);
		mListView.setOnItemClickListener(mOnDialogItemClick);
		
		// Set items
		List<HashMap<String, String>> data = new ArrayList<HashMap<String, String>>();
		HashMap<String, String> item;
		Resources res = mMainActivity.getResources();
		
		item = new HashMap<String, String>();
		item.put("name", res.getString(R.string.camera_id));
		item.put("value",Integer.toString(mCameraID));
		data.add(item);
		
		item = new HashMap<String, String>();
		item.put("name", res.getString(R.string.EV));
		item.put("value", Integer.toString(mEV));
		data.add(item);
		
		item = new HashMap<String, String>();
		item.put("name", res.getString(R.string.white_balance));
		item.put("value", mWhiteBalance);
		data.add(item);
		
		item = new HashMap<String, String>();
		item.put("name", res.getString(R.string.preview_resolution));
		Camera camera = mMainActivity.mCameraPreview.getCamera();
		Parameters params = camera.getParameters();
		List<Size> sizes = params.getSupportedPreviewSizes();
		item.put("value", sizes.get(mPreviewResolution).width + " * " + sizes.get(mPreviewResolution).height);
		data.add(item);
		
		item = new HashMap<String, String>();
		item.put("name", res.getString(R.string.resolution));
		sizes = params.getSupportedPictureSizes();
		item.put("value", sizes.get(mResolution).width + " * " + sizes.get(mResolution).height);
		data.add(item);
		
		item = new HashMap<String, String>();
		item.put("name", res.getString(R.string.ISO));
		item.put("value", mISO);
		data.add(item);
		
		item = new HashMap<String, String>();
		item.put("name", res.getString(R.string.path));
		item.put("value", mPath);
		data.add(item);
		
		item = new HashMap<String, String>();
		item.put("name", res.getString(R.string.image_type));
		item.put("value", res.getStringArray(R.array.image_type_string)[mImageType + 1]);
		data.add(item);
		
		item = new HashMap<String, String>();
		item.put("name", res.getString(R.string.image_path));
		item.put("value", mImagePath);
		data.add(item);
		
		item = new HashMap<String, String>();
		item.put("name", res.getString(R.string.preview));
		item.put("value", res.getString(mPreview ? R.string.yes : R.string.no));
		data.add(item);
		
		item = new HashMap<String, String>();
		item.put("name", res.getString(R.string.keep_scale));
		item.put("value", res.getString(mKeepScale ? R.string.yes : R.string.no));
		data.add(item);
		
		item = new HashMap<String, String>();
		item.put("name", res.getString(R.string.min_face_scale));
		item.put("value", Integer.toString(mMinFaceScale) + "%");
		data.add(item);
		
		item = new HashMap<String, String>();
		item.put("name", res.getString(R.string.scale_factor));
		item.put("value", Float.toString(mScaleFactor));
		data.add(item);
		
		item = new HashMap<String, String>();
		item.put("name", res.getString(R.string.debug_mode));
		item.put("value", res.getString(mDebugMode ? R.string.yes : R.string.no));
		data.add(item);
		
		mAdapter = new SimpleAdapter(mMainActivity, 
				data, R.layout.settings_list, 
				new String[] {"name", "value"}, 
				new int[] {R.id.setting_name, R.id.setting_value}
		);
		mListView.setAdapter(mAdapter);
		
		// Set dialog
		mDialog = new Dialog(mMainActivity, R.style.settingsDialog);
		mDialog.setContentView(mDialogView);
		mDialog.setCanceledOnTouchOutside(true);
	}

	/** Show settings dialog */
	public static final OnClickListener mOnButtonSettingClick = new OnClickListener() { 
		public void onClick(View buttonView) {
			if(mDialog == null)
				initializeDialog();
			Window dialogWindow = mDialog.getWindow();
			DisplayMetrics dm = new DisplayMetrics();
			mMainActivity.getWindowManager().getDefaultDisplay().getMetrics(dm);
			LayoutParams lp = dialogWindow.getAttributes();
			lp.gravity = Gravity.LEFT | Gravity.TOP;
			lp.x       = (int)(20.0f * dm.density);
			lp.y       = (int)(13.3f * dm.density);
			lp.height  = Math.min(dm.heightPixels - (int)(26.6f * dm.density), (int)((mListView.getCount() * 36 + 3.5) * dm.density));
			mDialog.show();
			dialogWindow.setAttributes(lp);
		}
	};
	
	protected static OnItemClickListener mOnDialogItemClick = new OnItemClickListener() {
		@SuppressLint("NewApi")
		@SuppressWarnings("unchecked")
		public void onItemClick(final AdapterView<?> adapterView, final View view, final int position, long id) {
			final Resources res = mMainActivity.getResources();
			final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mMainActivity);
			final Camera camera = mMainActivity.mCameraPreview.getCamera();
			final Parameters params = camera.getParameters();
			final AlertDialog.Builder builder = new AlertDialog.Builder(mMainActivity);
			
			switch(position)
			{
			case 0: // Camera ID
				if(VERSION.SDK_INT >= 9)
				{
					int cameraID = mCameraID + 1;
					if(cameraID >= Camera.getNumberOfCameras())
						cameraID = 0;
					if(mCameraID != cameraID)
					{
						mCameraID = cameraID;
						pref.edit().putInt(res.getString(R.string.pref_camera_id), mCameraID).commit();
						// Load camera settings
						mEV           = pref.getInt(res.getString(R.string.pref_EV) + mCameraID, 0);
						mWhiteBalance = pref.getString(res.getString(R.string.pref_white_balance) + mCameraID, "auto");
						mResolution   = pref.getInt(res.getString(R.string.pref_resolution) + mCameraID, 0);
						mISO          = pref.getString(res.getString(R.string.pref_ISO) + mCameraID, "auto");
						mMainActivity.mCameraPreview.resetCamera();
						// Update values
						((HashMap<String, String>)adapterView.getItemAtPosition(position)).put("value", Integer.toString(mCameraID));
						((HashMap<String, String>)adapterView.getItemAtPosition(1)).put("value", Integer.toString(mEV));
						((HashMap<String, String>)adapterView.getItemAtPosition(2)).put("value", mWhiteBalance);
						List<Size> sizes2 = mMainActivity.mCameraPreview.getCamera().getParameters().getSupportedPreviewSizes();
						((HashMap<String, String>)adapterView.getItemAtPosition(3)).put("value", 
								sizes2.get(mPreviewResolution).width + " * " + sizes2.get(mPreviewResolution).height);
						sizes2 = mMainActivity.mCameraPreview.getCamera().getParameters().getSupportedPictureSizes();
						((HashMap<String, String>)adapterView.getItemAtPosition(4)).put("value", 
								sizes2.get(mResolution).width + " * " + sizes2.get(mResolution).height);
						((HashMap<String, String>)adapterView.getItemAtPosition(5)).put("value", mISO);
						mAdapter.notifyDataSetChanged();
					}
				}
				break;
				
			case 1: // EV
				showSeekBarDialog(res.getString(R.string.EV), params.getMinExposureCompensation(), 
						params.getMaxExposureCompensation(), mEV, new OnSeekBarDialogPositiveButton() {
					public void onClick(int progress) {
						mEV = progress;
						pref.edit().putInt(res.getString(R.string.pref_EV) + mCameraID, mEV).commit();
						((HashMap<String, String>)adapterView.getItemAtPosition(position)).put("value", Integer.toString(mEV));
						mAdapter.notifyDataSetChanged();
						params.setExposureCompensation(mEV);
						camera.setParameters(params);
					}
				});
				break;
				
			case 2: // White balance
				final List<String> whiteBalances = params.getSupportedWhiteBalance();
				builder.setTitle(res.getString(R.string.white_balance))
				.setItems(whiteBalances.toArray(new String[whiteBalances.size()]), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						mWhiteBalance = whiteBalances.get(which);
						pref.edit().putString(res.getString(R.string.pref_white_balance) + mCameraID, mWhiteBalance).commit();
						((HashMap<String, String>)adapterView.getItemAtPosition(position)).put("value", whiteBalances.get(which));
						mAdapter.notifyDataSetChanged();
						params.setWhiteBalance(mWhiteBalance);
						camera.setParameters(params);
					}
				})
				.create()
				.show();
				break;
				
			case 3: // Preview resolution
				final List<Size> sizes2 = params.getSupportedPreviewSizes();
				final String[] sizesString2 = new String[sizes2.size()];
				for(int i = 0; i < sizes2.size(); i++)
					sizesString2[i] = sizes2.get(i).width + " * " + sizes2.get(i).height;
				builder.setTitle(res.getString(R.string.preview_resolution))
				.setItems(sizesString2, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if(which != mPreviewResolution)
						{
							mPreviewResolution = which;
							pref.edit().putInt(res.getString(R.string.pref_preview_resolution) + mCameraID, mPreviewResolution).commit();
							((HashMap<String, String>)adapterView.getItemAtPosition(position)).put("value", sizesString2[which]);
							mAdapter.notifyDataSetChanged();
							mMainActivity.mCameraPreview.resetCamera();
						}
					}
				})
				.create()
				.show();
				break;
				
			case 4: // Resolution
				final List<Size> sizes = params.getSupportedPictureSizes();
				final String[] sizesString = new String[sizes.size()];
				for(int i = 0; i < sizes.size(); i++)
					sizesString[i] = sizes.get(i).width + " * " + sizes.get(i).height;
				builder.setTitle(res.getString(R.string.resolution))
				.setItems(sizesString, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if(which != mResolution)
						{
							mResolution = which;
							pref.edit().putInt(res.getString(R.string.pref_resolution) + mCameraID, mResolution).commit();
							((HashMap<String, String>)adapterView.getItemAtPosition(position)).put("value", sizesString[which]);
							mAdapter.notifyDataSetChanged();
							mMainActivity.mCameraPreview.resetCamera();
						}
					}
				})
				.create()
				.show();
				break;
				
			case 5: // ISO ***** Not every device support!
				String flat = params.flatten();
				//Log.i("ISO test", flat);
				String values_keyword = null;
				if(flat.contains("iso-values")) // Most used keywords
					values_keyword = "iso-values";
				else if(flat.contains("iso-mode-values")) // Google galaxy nexus keywords
					values_keyword = "iso-mode-values";
				else if(flat.contains("iso-speed-values")) // Micromax a101 keywords
					values_keyword = "iso-speed-values";
				else if(flat.contains("nv-picture-iso-values")) // LG dual p990 keywords
					values_keyword = "nv-picture-iso-values";
				// Add other eventual keywords here...
				String iso = null;
				if(values_keyword != null)
				{
					// Flatten contains the ISO key!!
					iso = flat.substring(flat.indexOf(values_keyword));
					iso = iso.substring(iso.indexOf("=") + 1);
					if(iso.contains(";"))
						iso = iso.substring(0, iso.indexOf(";"));
					
					//for(String str : isoValues)
					//	Log.i("ISO test", str);
				}
				//else // ISO not supported in a known way
				//	Log.i("ISO test", "ISO is not supported");

				final String[] defaultISOs = {"auto", "50", "100", "200", "400", "800", "1600", "3200"};
				final String[] isoValues = values_keyword != null ? iso.split(",") : defaultISOs;
				
				builder.setTitle(res.getString(R.string.ISO))
				.setItems(isoValues, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						params.set("iso", isoValues[which]);
						params.set("iso-speed", isoValues[which]);
						params.set("nv-picture-iso", isoValues[which]);
						try {
							camera.setParameters(params); // Will throw if this ISO is not supported
							mISO = isoValues[which];
							pref.edit().putString(res.getString(R.string.pref_ISO) + mCameraID, mISO).commit();
							((HashMap<String, String>)adapterView.getItemAtPosition(position)).put("value", mISO);
							mAdapter.notifyDataSetChanged();
						} catch(Exception e) {
							e.printStackTrace();
							Toast.makeText(mMainActivity, mMainActivity.getResources().getString(R.string.this_ISO_is_not_supported), 
									Toast.LENGTH_SHORT).show();
						}
					}
				})
				.create()
				.show();
				break;
				
			case 6: // Path
				final EditText edit = new EditText(mMainActivity);
				edit.setText(mPath);
				edit.setSingleLine();
				builder.setTitle(res.getString(R.string.path))
				.setView(edit)
				.setPositiveButton(res.getString(R.string.ok), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						mPath = edit.getText().toString();
						pref.edit().putString(res.getString(R.string.pref_path), mPath).commit();
						((HashMap<String, String>)adapterView.getItemAtPosition(position)).put("value", mPath);
						mAdapter.notifyDataSetChanged();
					}
				})
				.setNegativeButton(res.getString(R.string.cancel), null)
				.show();
				break;
				
			case 7: // Image type
				final String[] imageTypes = res.getStringArray(R.array.image_type_string);
				builder.setTitle(res.getString(R.string.image_type))
				.setItems(imageTypes, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, final int which) {
						if(mImageType == which - 1)
							return;
						mImageType = which - 1;
						mMainActivity.mCameraPreview.loadImage();
						pref.edit().putInt(res.getString(R.string.pref_image_type), mImageType).commit();
						((HashMap<String, String>)adapterView.getItemAtPosition(position)).put("value", imageTypes[mImageType + 1]);
						mAdapter.notifyDataSetChanged();
					}
				})
				.create()
				.show();
				break;
				
			case 8: // Image path
				mMainActivity.startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), 0);
				break;
				
			case 9: // Preview
				mPreview = !mPreview;
				pref.edit().putBoolean(res.getString(R.string.pref_preview), mPreview).commit();
				((HashMap<String, String>)adapterView.getItemAtPosition(position)).put("value", res.getString(mPreview ? R.string.yes : R.string.no));
				mAdapter.notifyDataSetChanged();
				mMainActivity.mResultPreview.setVisibility(mPreview ? View.VISIBLE : View.INVISIBLE);
				break;
				
			case 10: // Keep scale
				mKeepScale = !mKeepScale;
				pref.edit().putBoolean(res.getString(R.string.pref_keep_scale), mKeepScale).commit();
				((HashMap<String, String>)adapterView.getItemAtPosition(position)).put("value", res.getString(mKeepScale ? R.string.yes : R.string.no));
				mAdapter.notifyDataSetChanged();
				break;
				
			case 11: // Min face size
				showSeekBarDialog(res.getString(R.string.min_face_scale), 5, 
						90, mMinFaceScale, new OnSeekBarDialogPositiveButton() {
					public void onClick(int progress) {
						mMinFaceScale = progress;
						pref.edit().putInt(res.getString(R.string.pref_min_face_scale), mMinFaceScale).commit();
						((HashMap<String, String>)adapterView.getItemAtPosition(position)).put("value", Integer.toString(mMinFaceScale));
						mAdapter.notifyDataSetChanged();
					}
				});
				break;
				
			case 12: // Scale factor
				final EditText edit2 = new EditText(mMainActivity);
				edit2.setText(Float.toString(mScaleFactor));
				edit2.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
				builder.setTitle(res.getString(R.string.scale_factor))
				.setView(edit2)
				.setPositiveButton(res.getString(R.string.ok), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Float scaleFactor;
						try {
							scaleFactor = Float.parseFloat(edit2.getText().toString());
						} catch (NumberFormatException e) {
							return;
						}
						if(scaleFactor < 1.1)
							return;
						mScaleFactor = scaleFactor;
						pref.edit().putFloat(res.getString(R.string.pref_scale_factor), mScaleFactor).commit();
						((HashMap<String, String>)adapterView.getItemAtPosition(position)).put("value", Float.toString(mScaleFactor));
						mAdapter.notifyDataSetChanged();
					}
				})
				.setNegativeButton(res.getString(R.string.cancel), null)
				.show();
				break;
				
			case 13: // Debug mode
				mDebugMode = !mDebugMode;
				pref.edit().putBoolean(res.getString(R.string.pref_debug_mode), mDebugMode).commit();
				((HashMap<String, String>)adapterView.getItemAtPosition(position)).put("value", res.getString(mDebugMode ? R.string.yes : R.string.no));
				mAdapter.notifyDataSetChanged();
				break;
			}
		}
	};
	
	/** Receive image path */
	@SuppressWarnings("unchecked")
	public static void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 0 && resultCode == Activity.RESULT_OK)
		{
			String[] proj = {MediaStore.Images.Media.DATA};
			Uri uri = data.getData();
			@SuppressWarnings("deprecation")
			Cursor cursor = mMainActivity.managedQuery(uri, proj, null, null, null);
			if(cursor == null) // Chosen by explorer
				mImagePath = uri.getPath();
			else // Chosen by gallery
			{
				int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
				cursor.moveToFirst();
				mImagePath = cursor.getString(columnIndex);
			}
			
			PreferenceManager.getDefaultSharedPreferences(mMainActivity).edit()
				.putString(mMainActivity.getResources().getString(R.string.pref_image_path), mImagePath).commit();
			((HashMap<String, String>)mAdapter.getItem(8)).put("value", mImagePath);
			mAdapter.notifyDataSetChanged();
			
			if(mImageType == -1) // Is custom image
				mMainActivity.mCameraPreview.loadImage();
		}
	}
	
	protected interface OnSeekBarDialogPositiveButton {
		public void onClick(int progress);
	}
	@SuppressLint("InflateParams")
	protected static void showSeekBarDialog(String title, final int min, int max, int initial, 
			final OnSeekBarDialogPositiveButton onPositiveButton) {
		Resources res = mMainActivity.getResources();
		View view = LayoutInflater.from(mMainActivity).inflate(R.layout.seekbar_dialog, null);
		
		// Set TextView and SeekBar
		final TextView progressText = (TextView)view.findViewById(R.id.progress_text);
		progressText.setText(Integer.toString(initial));
		
		final SeekBar seekBar = (SeekBar)view.findViewById(R.id.seekBar);
		seekBar.setMax(max - min);
		seekBar.setProgress(initial - min);
		seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			public void onProgressChanged(SeekBar thiz, int progress, boolean fromUser) {
				progressText.setText(Integer.toString(progress + min));
			}
			public void onStartTrackingTouch(SeekBar thiz) {}
			public void onStopTrackingTouch(SeekBar thiz) {}
		});
		
		// Set dialog
		new AlertDialog.Builder(mMainActivity)
		.setTitle(title)
		.setView(view)
		.setPositiveButton(res.getString(R.string.ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				onPositiveButton.onClick(seekBar.getProgress() + min);
			}
		})
		.setNegativeButton(res.getString(R.string.cancel), null)
		.show();
	}
}
