package it.unipd.dei.esp1617.visualize;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Vibrator;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

@SuppressWarnings("deprecation")
public class VisualizeActivity extends Activity {

	static {
		System.loadLibrary("caffe");
		System.loadLibrary("caffe_jni");
	}

	// Request code for camera permissions
	private static final int REQUEST_CAMERA_AND_STORAGE_PERMISSIONS = 1;

	// Request code for the intent redirecting the user to the settings to manually change permissions
	private static final int REQUEST_CAMERA_AND_STORAGE_PERMISSIONS_INTENT = 2;

	// Constants containing the angle that the camera image needs to be rotated clockwise so it shows
	// correctly on the display in each possible device rotation.
	// See https://developer.android.com/reference/android/hardware/Camera.CameraInfo.html#orientation
	// for more info regarding camera orientation.
	private static final int PORTRAIT_CAMERA_ORIENTATION = getCameraOrientation();
	private static final int LANDSCAPE_CAMERA_ORIENTATION = (PORTRAIT_CAMERA_ORIENTATION+270)%360;
	private static final int R_PORTRAIT_CAMERA_ORIENTATION = (PORTRAIT_CAMERA_ORIENTATION+180)%360;
	private static final int R_LANDSCAPE_CAMERA_ORIENTATION = (PORTRAIT_CAMERA_ORIENTATION+90)%360;

	// Permissions required by the app
	private static final String[] REQUIRED_PERMISSIONS = new String[]{
					Manifest.permission.CAMERA,
					Manifest.permission.WRITE_EXTERNAL_STORAGE
	};

	// External storage
	private final File sdcard = Environment.getExternalStorageDirectory();

	// Folder containing the Caffe model of our neural network + various needed files
	private final String modelDir = sdcard.getAbsolutePath() + "/caffe_mobile/bvlc_googlenet";
	private final String modelProto = modelDir + "/deploy.prototxt";
	private final String modelBinary = modelDir + "/bvlc_googlenet.caffemodel";
	private final String meanFile = modelDir + "/imagenet_mean.binaryproto";

	// The current language adopted by the user
	private final Locale currentLanguage = Locale.getDefault();

	// Tag used for debug purposes
	private static final String TAG = "Visualize";

	// Container for the classes of the synsets
	private String[] IMAGENET_CLASSES;

	// Instance of Caffe
	private CaffeMobile caffeMobile;

	// Instance of Text to Speech
	private TextToSpeech t1;

	// Instance of Camera
	private Camera mCamera;

	// Surface on which the camera preview is shown
	private CameraPreview mPreview;

	// File for temporarily storing the picture taken
	private File pictureFile;

	// A listener used to determine when device rotation has occurred and change the camera rotation
	// accordingly, since the app is locked to portrait mode.
	private OrientationEventListener myOrientationEventListener;

	// Current device rotation stored as the rotation needed by the camera to display correctly
	// See the following link for a more detailed explanation on the concept of camera rotation
	// https://developer.android.com/reference/android/hardware/Camera.CameraInfo.html#orientation
	private int deviceRotation;

	// Boolean variable indicating whether the camera is available or not. The camera is unavailable
	// as soon as the picture is taken and returns available once the prediction process is complete
	private boolean cameraAvailable;

	// FrameLayout containing the SurfaceView with the camera preview
	private FrameLayout preview;

	// FrameLayout for the overlay appearing during the image processing containing the progress bar
	private FrameLayout loadingOverlay;

	// Layout containing the four TextViews displaying the result
	private RelativeLayout resultView;

	// TextViews for displaying the result on-screen, one for each screen orientation, plus one that
	// is a reference to the active one.
	private TextView bottomText, leftText, topText, rightText, activeText;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Remove notification bar to go fullscreen
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
						WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_visualize);

		preview = (FrameLayout) findViewById(R.id.camera_preview);
		loadingOverlay = (FrameLayout) findViewById(R.id.loading_overlay);
		resultView = (RelativeLayout) findViewById(R.id.result_text);

		bottomText = (TextView) findViewById(R.id.tv_bottom);
		leftText = (TextView) findViewById(R.id.tv_left);
		topText = (TextView) findViewById(R.id.tv_top);
		rightText = (TextView) findViewById(R.id.tv_right);
		// The active TextView where we display the result of the prediction is by default the one at
		// the bottom of the screen, and it will change according to the orientation of the device.
		activeText = bottomText;

		// Set the correct audio stream
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		// Text-to-speech instance
		t1 = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
			@Override
			public void onInit(int status) {
				if (status != TextToSpeech.ERROR) {
					t1.setLanguage(currentLanguage);
				}
			}
		});

		initActivity();

	}

	@Override
	protected void onResume() {
		super.onResume();

		// If media volume is zero, set it to a reasonable value (i.e. half the maximum volume),
		// otherwise the user won't be able to hear the result through the text-to-speech function
		// (or worse, he might not even realize that the app uses the text-to-speech function).
		// This might not look like a good practice since we are forcing the volume to a certain level
		// regardless of the user will, but given that the target user for this app is supposed to be
		// blind, simply showing a Toast telling him to raise the volume isn't a good idea
		AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		int maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

		if (am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0){
			am.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume / 2, 0);
		}

		// Initialize Camera
		if (mCamera == null && hasPermissions()) {
			initCamera();
		}

		// Enable orientation listener
		if (myOrientationEventListener != null && myOrientationEventListener.canDetectOrientation()) {
			myOrientationEventListener.enable();
		}
	}

	@Override
	protected void onPause() {
		// Disable orientation listener
		if (myOrientationEventListener != null) {
			myOrientationEventListener.disable();
		}
		// Release the camera
		releaseCamera();

		// Reset the active TextView to the default one (i.e. the one at the bottom)
		activeText.setVisibility(View.GONE);
		activeText = bottomText;
		activeText.setVisibility(View.VISIBLE);

		// If the app goes in background before a prediction task is completed, the loading overlay
		// must disappear or it will be still there once the app resumes
		if(loadingOverlay.getVisibility()==View.VISIBLE) loadingOverlay.setVisibility(View.GONE);

		super.onPause();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		t1.shutdown();	// Shut down Text-to-Speech instance
	}

	/**
	 * Initialize the activity.
	 */
	private void initActivity() {
		// Checks if the permissions have been granted, and asks for them if they haven't been granted.
		if (!hasPermissions()) {
			requestPermissions();
			return;
		}

		// Checks whether the required files exist on the external storage, extracting and copying
		// them from the asset files contained in the APK in case they are missing.
		File modelProtoFile = new File(modelProto);
		File modelBinaryFile = new File(modelBinary);
		File meanBinaryFile = new File(meanFile);
		if(!modelBinaryFile.exists() || !modelProtoFile.exists() || !meanBinaryFile.exists()) {
			copyAssets();
		}

		// Orientation listener to manually manage different orientations
		myOrientationEventListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {

			// Executed when the orientation of the device changes. The "orientation" variable is the
			// actual orientation recorded by the device sensor, while the "degrees" variable is assigned
			// to the corresponding value of Camera orientation (see the following link for more info on
			// Camera orientation)
			// https://developer.android.com/reference/android/hardware/Camera.CameraInfo.html#orientation
			@Override
			public void onOrientationChanged(int orientation) {
				int degrees = -1;
				if (orientation >= 315 || orientation < 45) {
					degrees = PORTRAIT_CAMERA_ORIENTATION; // Portrait
				} else if (orientation >= 45 && orientation < 135) {
					degrees = R_LANDSCAPE_CAMERA_ORIENTATION; // Reverse landscape
				} else if (orientation >= 135 && orientation < 225) {
					degrees = R_PORTRAIT_CAMERA_ORIENTATION; // Reverse portrait
				} else if (orientation >= 225 && orientation < 315) {
					degrees = LANDSCAPE_CAMERA_ORIENTATION; // Landscape
				}

				// Check if the orientation actually changed
				if (deviceRotation != degrees && mCamera != null && mCamera.getParameters() != null) {
					// Hides the TextView previously shown since the phone just changed orientation.
					if (activeText != null) activeText.setVisibility(View.GONE);

					// Set the active TextView according to the screen orientation.
					// (Cannot use a switch statement since the rotation variables are not compile-time constant)
					if(degrees == LANDSCAPE_CAMERA_ORIENTATION)
						activeText = leftText;
					else if(degrees == PORTRAIT_CAMERA_ORIENTATION)
						activeText = bottomText;
					else if(degrees == R_LANDSCAPE_CAMERA_ORIENTATION)
						activeText = rightText;
					else if(degrees == R_PORTRAIT_CAMERA_ORIENTATION)
						activeText = topText;

					// Shows the newly activated TextView
					if(activeText != null) activeText.setVisibility(View.VISIBLE);

					// Change the parameters of the camera instance according to the screen orientation.
					Camera.Parameters params = mCamera.getParameters();
					params.setRotation(degrees);
					mCamera.setParameters(params);
					Log.d(TAG, "Orientation changed; previous: " + Integer.toString(deviceRotation) + ", " +
									"new: "+ Integer.toString(degrees));
					deviceRotation = degrees;
				}
			}
		};

		// Set up Caffe
		caffeMobile = new CaffeMobile();
		caffeMobile.loadModel(modelProto, modelBinary);
		caffeMobile.setMean(meanFile);

		// Get the list of classes of objects on which the image recognition model was trained
		String assetFile;
		AssetManager am = this.getAssets();
		assetFile = getString(R.string.synset_path);
		try {
			InputStream is = am.open(assetFile);
			Scanner sc = new Scanner(is);
			List<String> lines = new ArrayList<>();
			while (sc.hasNextLine()) {
				String temp = sc.nextLine();
				lines.add(temp.substring(temp.indexOf(" ") + 1));
			}
			IMAGENET_CLASSES = lines.toArray(new String[0]);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Initialize the Camera instance and its parameters.
	 */
	private void initCamera() {
		// Get an instance of Camera
		mCamera = getCameraInstance();

		// Get Camera parameters
		Camera.Parameters params = mCamera.getParameters();

		// The default rotation is portrait since the app is locked on portrait mode
		deviceRotation = PORTRAIT_CAMERA_ORIENTATION;
		params.setRotation(deviceRotation);

		// Set the maximum available resolution for the capture
		Camera.Size maxSize = params.getSupportedPictureSizes().get(0);
		params.setPictureSize(maxSize.width, maxSize.height);

		// Check if the device supports continuous auto-focus
		List<String> focusModes = params.getSupportedFocusModes();
		if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
			// Auto-focus mode is supported, hence use it
			params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
		}

		// set Camera parameters
		mCamera.setParameters(params);

		// Create our Preview view and set it as the content of our activity.
		mPreview = new CameraPreview(this, mCamera, deviceRotation);

		// Assign the camera preview to the FrameLayout
		preview.addView(mPreview);

		// Add a listener to the FrameLayout to take pictures by simply tapping on the screen
		preview.setOnClickListener(
						new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								if (cameraAvailable) {
									cameraAvailable = false;
									mCamera.takePicture(ShutterCallback, null, mPicture);
									mCamera.startPreview();
								}
							}
						}
		);

		// Camera is now initialized and available for taking pictures
		cameraAvailable = true;

		// Bring to front the layouts, which would otherwise be hidden by the preview surface.
		loadingOverlay.bringToFront();
		resultView.bringToFront();
	}

	/**
	 * Get an instance of Camera.
	 *
	 * @return the Camera instance
	 */
	private static Camera getCameraInstance() {
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
		} catch (Exception e) {
			Log.e(TAG, "Unable to retrieve a Camera instance");
			// Camera is not available (in use or does not exist)
		}
		return c;
	}

	/**
	 * Release the Camera instance as well as the surface for the Camera preview.
	 */
	private void releaseCamera() {
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera.setPreviewCallback(null);
			mPreview.getHolder().removeCallback(mPreview);
			mCamera.release();
			mCamera = null;
			// Destroys the surface displaying the Camera preview
			preview.removeView(mPreview);
			mPreview = null;
		}
	}

	/**
	 * Callback invoked when the user takes a picture. Vibrates as feedback when the picture has been
	 * taken, and displays the overlay with the progress bar.
	 */
	private final Camera.ShutterCallback ShutterCallback = new Camera.ShutterCallback() {
		@Override
		public void onShutter() {
		resultView.setVisibility(View.GONE);

		Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		v.vibrate(50);
		loadingOverlay.setVisibility(View.VISIBLE);
		}
	};

	/**
	 * Callback invoked when the picture data is actually collected and is ready to be saved.
	 * Proceeds to save the picture in a temporary file (returned by the getOutputMediaFile function)
	 * and then starts the prediction task.
	 */
	private final Camera.PictureCallback mPicture = new Camera.PictureCallback() {

		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
		pictureFile = getOutputMediaFile();
		if (pictureFile == null) {
			Log.d(TAG, "Error creating media file, check storage permissions: ");
			return;
		}

		try {
			FileOutputStream fos = new FileOutputStream(pictureFile);
			fos.write(data);
			fos.close();
		} catch (FileNotFoundException e) {
			Log.d(TAG, "File not found: " + e.getMessage());
		} catch (IOException e) {
			Log.d(TAG, "Error accessing file: " + e.getMessage());
		}

		// Starts the image prediction task
		PredictionTask predictionTask = new PredictionTask();
		predictionTask.execute(pictureFile.getPath());
		}
	};

	/**
	 * AsyncTask for image prediction.
	 */
	private class PredictionTask extends AsyncTask<String, Void, Integer> {

		/**
		 * Starts the prediction task in background.
		 *
		 * @param imgPath The path to the image stored in the temporary file.
		 * @return The index of the predicted class.
		 */
		@Override
		protected Integer doInBackground(String... imgPath) {
			return caffeMobile.predictImage(imgPath[0])[0];
		}

		/**
		 * Executed once the prediction task is done. Hides the overlay with the progress bar and
		 * Text-To-Speech the resulting class, as well as showing it in the active TextView.
		 *
		 * @param result the index of the predicted label received from the doInBackground method.
		 */
		@Override
		protected void onPostExecute(Integer result) {
			loadingOverlay.setVisibility(View.GONE);
			t1.speak(IMAGENET_CLASSES[result], TextToSpeech.QUEUE_FLUSH, null, "speak");
			showTextResult(IMAGENET_CLASSES[result]);

			// Delete the picture after the prediction is completed
			// since we don't need it anymore and we don't want to take up storage space
			if(!pictureFile.delete()) Log.d(TAG, "Unable to delete file");

			// Camera is available again to take picture
			cameraAvailable = true;
		}
	}

	// ---------------------------- Helper methods ---------------------------- //

	/**
	 * Set the text of the different TextViews to the resulting class of the image prediction task
	 * and toggle the visibility of the FrameLayout containing the TextViews.
	 *
	 * @param result The resulting object class from the image prediction.
	 */
	private void showTextResult(String result) {
		bottomText.setText(result);
		leftText.setText(result);
		topText.setText(result);
		rightText.setText(result);
		resultView.setVisibility(View.VISIBLE);
	}

	/**
	 * Method called once a photo has been taken. This method takes care of creating a new directory
	 * (if not yet existing) that will contain the temporary photos taken by the user.
	 * The method also creates the File object that will contain the temporary photo, assigning it
	 * a name based on the current timestamp.
	 *
	 * @return The File container created.
	 */
	private static File getOutputMediaFile() {
		File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
						Environment.DIRECTORY_PICTURES), "Visualize");

		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				Log.d(TAG, "failed to create directory");
				return null;
			}
		}

		// Create a media file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
		File mediaFile;
		mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".jpg");

		return mediaFile;
	}

	/**
	 * Returns the angle that the camera image needs to be rotated clockwise so it shows correctly
	 * on the display in its natural orientation. For more info about Camera rotation:
	 * https://developer.android.com/reference/android/hardware/Camera.CameraInfo.html#orientation
	 */
	private static int getCameraOrientation() {
		Camera.CameraInfo info = new Camera.CameraInfo();
		Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info);
		return info.orientation;
	}

	/**
	 * Takes care of copying the external files needed to initialize CaffeMobile for the prediction
	 * task (namely "bvlc_googlenet.caffemodel", "deploy.prototxt" and "imagenet_mean.binaryproto").
	 * These files are copied to the directory "/caffe_mobile/bvlc_googlenet/" located in the root of
	 * the device's external storage.
	 */
	private void copyAssets() {
		AssetManager assetManager = getAssets();
		String[] files = null;
		try {
			files = assetManager.list("caffe_mobile/bvlc_googlenet");
		} catch (IOException e) {
			Log.e(TAG, "Failed to get asset file list.", e);
		}
		if (files != null) {
			for (String filename : files) {
				InputStream in = null;
				OutputStream out = null;
				try {
					in = assetManager.open("caffe_mobile/bvlc_googlenet/"+filename);
					File dir = new File(modelDir);
					if(!dir.exists()) {
						if(!dir.mkdirs()) {
							Log.d(TAG, "Could not create directory");
						}
					}
					File outFile = new File(modelDir, filename);
					out = new FileOutputStream(outFile);
					copyFile(in, out);
				} catch(IOException e) {
					Log.e(TAG, "Failed to copy asset file: " + filename, e);
				}
				finally {
					if (in != null) {
						try {
							in.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					if (out != null) {
						try {
							out.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
	}

	/**
	 * Helper method for copying a file from the input stream to the output stream.
	 *
	 * @param in InputStream containing the bytes of the input file.
	 * @param out OutputStream containing the output file container.
	 * @throws IOException IOException
	 */
	private void copyFile(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[1024];
		int read;
		while((read = in.read(buffer)) != -1){
			out.write(buffer, 0, read);
		}
	}

	// ------------ Methods for handling the permissions request on Android >= 6.0 ------------ //

	/**
	 * Checks whether the app has been granted the required permissions.
	 *
	 * @return  Whether the permissions have been granted.
	 */
	private boolean hasPermissions() {
		return (ActivityCompat.checkSelfPermission(this, REQUIRED_PERMISSIONS[0]) ==
						PackageManager.PERMISSION_GRANTED)
						&& (ActivityCompat.checkSelfPermission(this, REQUIRED_PERMISSIONS[1]) ==
						PackageManager.PERMISSION_GRANTED);
	}

	/**
	 * Checks whether the app should show the rationale explaining the user why the app needs the
	 * permissions after he didn't grant the permissions.
	 *
	 * @return Whether the app should show the rationale.
	 */
	private boolean shouldShowRationale() {
		return ActivityCompat.shouldShowRequestPermissionRationale(VisualizeActivity.this,
						REQUIRED_PERMISSIONS[0])
						|| ActivityCompat.shouldShowRequestPermissionRationale(VisualizeActivity.this,
						REQUIRED_PERMISSIONS[1]);
	}

	/**
	 * Asks the user to grant the permissions needed by the application.
	 */
	private void requestPermissions() {
		if (shouldShowRationale()) {
			// If the user has already started the app in the past and didn't grant the permissions,
			// show a dialog explaining why the permissions are needed
			AlertDialog.Builder builder = new AlertDialog.Builder(VisualizeActivity.this);
			builder.setMessage(R.string.permission);
			builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
					ActivityCompat.requestPermissions(VisualizeActivity.this, REQUIRED_PERMISSIONS,
									REQUEST_CAMERA_AND_STORAGE_PERMISSIONS);
				}
			});
			builder.setCancelable(false);
			builder.show();
		} else    // Otherwise just ask for permissions
			ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS,
							REQUEST_CAMERA_AND_STORAGE_PERMISSIONS);
	}

	/**
	 * Method that handles the user's decision on whether or not to grant the required permissions.
	 */
	@Override
	public void onRequestPermissionsResult(int requestCode,
																				 @NonNull String[] permissions,
																				 @NonNull int[] grantResults) {
		if (requestCode == REQUEST_CAMERA_AND_STORAGE_PERMISSIONS) {
			if (grantResults.length == 2
							&& grantResults[0] == PackageManager.PERMISSION_GRANTED
							&& grantResults[1] == PackageManager.PERMISSION_GRANTED) {
				// Permissions granted, initialize the activity
				initActivity();
			} else {
				// Permissions denied. Alert the user that if he doesn't authorize the permissions he
				// cannot use the app, and give him the possibility to manually change the
				// permissions by redirecting him to the app's settings if he has checked the
				// "Do not ask again" checkbox
				AlertDialog.Builder builder = new AlertDialog.Builder(VisualizeActivity.this);
				builder.setTitle(R.string.perm_denied);
				builder.setMessage(R.string.perm_denied_verbose);
				builder.setPositiveButton(R.string.perm_given, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
						if (shouldShowRationale())
							// Simply ask for permissions again if he didn't check "Do not ask again"
							ActivityCompat.requestPermissions(VisualizeActivity.this,
											REQUIRED_PERMISSIONS, REQUEST_CAMERA_AND_STORAGE_PERMISSIONS);
						else {
							// Otherwise redirect to the settings to manually change permissions
							Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
							Uri uri = Uri.fromParts("package", getPackageName(), null);
							intent.setData(uri);
							startActivityForResult(intent, REQUEST_CAMERA_AND_STORAGE_PERMISSIONS_INTENT);

							Toast.makeText(getBaseContext(), R.string.perm_setting_redirect, Toast.LENGTH_LONG)
											.show();
						}
					}
				});
				builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// Simply close the app if he doesn't grant the permissions
						dialog.cancel();
						finish();
					}
				});
				builder.setCancelable(false);
				builder.show();
			}
		}
	}

	/**
	 * Handles the result of an activity. In this case, it is called only to handle the intent that
	 * redirects the user to the settings of the app in order to allow him to authorize the app with
	 * the required permissions, and it is called upon returning to the app.
	 *
	 * @param requestCode The code of the request.
	 * @param resultCode The code of the request's result.
	 * @param data An Intent possibly containing data.
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_CAMERA_AND_STORAGE_PERMISSIONS_INTENT) {
			// Check if user authorized the app
			if (hasPermissions()) {
				//Got Permissions
				Toast.makeText(getBaseContext(), R.string.perm_given_verbose,Toast.LENGTH_LONG).show();
				initActivity();
			} else if (shouldShowRationale()) {
				// Triggered when a user allows and then denies a permission for which he previously ticked
				// "don't ask again", which invalidates the tick
				requestPermissions();
			}
		}

		super.onActivityResult(requestCode, resultCode, data);
	}
}
