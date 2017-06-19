package it.unipd.dei.esp1617.visualize;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;


@SuppressWarnings("deprecation")
/*
  Class for the Camera preview. The class implements SurfaceHolder.Callback in order to capture
  the callback events for creating and destroying the view, which are needed for assigning the
  camera preview input.
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
	private static final String TAG = "Visualize";

	private final SurfaceHolder mHolder;	// Give access & control over the SurfaceView's underlying surface
	private final Camera mCamera;					// Camera instance
	private final int mDeviceRotation;		// Current device rotation

	public CameraPreview(Context context, Camera camera, int deviceRotation) {
		super(context);
		mCamera = camera;
		mDeviceRotation = deviceRotation;

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		mHolder = getHolder();
		mHolder.addCallback(this);
	}

	public void surfaceCreated(SurfaceHolder holder) {
		// The Surface has been created, now tell the camera where to draw the preview.
		try {
			mCamera.setPreviewDisplay(holder);
			// change PREVIEW orientation (set to landscape by default) according to the device rotation
			mCamera.setDisplayOrientation(mDeviceRotation);
			mCamera.startPreview();
		} catch (IOException e) {
			Log.d(TAG, "Error setting camera preview: " + e.getMessage());
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		// Empty since we take care of releasing the Camera preview in the main activity
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		// Takes care of changes occurring to the preview.

		if (mHolder.getSurface() == null) {
			// preview surface does not exist
			return;
		}
		// stop preview before making changes
		try {
			mCamera.stopPreview();
		} catch (Exception e) {
			// ignore: tried to stop a non-existent preview
		}
		// set preview size and make any resize, rotate or reformatting changes here
		mCamera.setDisplayOrientation(mDeviceRotation);

		// start preview with new settings
		try {
			mCamera.setPreviewDisplay(mHolder);
			mCamera.startPreview();
		} catch (Exception e) {
			Log.d(TAG, "Error starting camera preview: " + e.getMessage());
		}
	}
}
