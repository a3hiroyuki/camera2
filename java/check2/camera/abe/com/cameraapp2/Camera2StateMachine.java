// Copyright 2015 kotemaru.org. (http://www.apache.org/licenses/LICENSE-2.0)
package check2.camera.abe.com.cameraapp2;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

public class Camera2StateMachine {
	private static final String TAG = Camera2StateMachine.class.getSimpleName();
	private CameraManager mCameraManager;

	private CameraDevice mCameraDevice;
	private CameraCaptureSession mCaptureSession;
	private ImageReader mImageReader;
	private CaptureRequest.Builder mPreviewRequestBuilder;

	private AutoFitTextureView mTextureView;
	private Handler mHandler = null; // default current thread.
	private State mState = null;
	private ImageReader.OnImageAvailableListener mTakePictureListener;

	private Context mCon;
	private CameraCharacteristics mCharacteristics;
	private CameraParam mCameraParam;
	private Callback mCallback;

	private Rect mCurrentRect;

	public interface Callback{
		public abstract void setFov(Map<String, String> fovMab);
	}

	public Camera2StateMachine(Activity activity, AutoFitTextureView textureView){
		mTextureView = textureView;
		mCameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
		mCon = activity;
		mCallback = (MainActivity)activity;
		try {
			String cameraId = Camera2Util.getCameraId(mCameraManager, CameraCharacteristics.LENS_FACING_BACK);
			mCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
		mCameraParam = new CameraParam(mCharacteristics);
	}

	public float getMaxZoom(){
		return  mCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
	}

	public Rect getRect(){
		return mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
	}
	public void setAspectRate(double rate){
		mCameraParam.setOutputAspectRate(rate);
		if(mState == mPreviewState){
			try{
				if(rate > 1.5){
//					mTextureView.setPreviewSize(3000, 6000);
//					mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//					SurfaceTexture texture = mTextureView.getSurfaceTexture();
//					texture.setDefaultBufferSize(2500, 4000);
//					Surface surface = new Surface(texture);
//					mPreviewRequestBuilder.removeTarget(surface);
//					mPreviewRequestBuilder.addTarget(surface);
//					mCameraDevice.
					mTextureView.setPreviewSize(720, 1280 );
				}else{
//					mTextureView.setPreviewSize(3000, 4000);
					mTextureView.setPreviewSize(3032, 4032 );
				}
				//mState.aspectChanged();
				nextState(mAbortState);
				//nextState(mOpenCameraState);
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	public void open(Activity activity, AutoFitTextureView textureView) {
		if (mState != null) throw new IllegalStateException("Alrady started state=" + mState);
		nextState(mInitSurfaceState);
	}

	public boolean takePicture(ImageReader.OnImageAvailableListener listener) {
		if (mState != mPreviewState) return false;
		mTakePictureListener = listener;
		nextState(mAutoFocusState);
		return true;
	}

	public void zoom(Rect zoom){
		try{
			mState.zoom(zoom);
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	public void close() {
		nextState(mAbortState);
	}

	// ----------------------------------------------------------------------------------------
	// The following private
	private void shutdown() {
		if (null != mCaptureSession) {
			mCaptureSession.close();
			mCaptureSession = null;
		}
		if (null != mCameraDevice) {
			mCameraDevice.close();
			mCameraDevice = null;
		}
		if (null != mImageReader) {
			mImageReader.close();
			mImageReader = null;
		}
	}

	private void nextState(State nextState) {
		Log.d(TAG, "state: " + mState + "->" + nextState);
		try {
			if (mState != null) mState.finish();
			mState = nextState;
			if (mState != null) mState.enter();
		} catch (CameraAccessException e) {
			Log.e(TAG, "next(" + nextState + ")", e);
			shutdown();
		}
	}

	private abstract class State {
		private String mName;

		public State(String name) {
			mName = name;
		}

		//@formatter:off
		public String toString() {
			return mName;
		}

		public void enter() throws CameraAccessException { }
		public void onSurfaceTextureAvailable(int width, int height) { }
		public void onCameraOpened(CameraDevice cameraDevice) { }
		public void zoom(Rect zoomRect) throws CameraAccessException{ }
		public void aspectChanged() throws CameraAccessException{ }
		public void onSessionConfigured(CameraCaptureSession cameraCaptureSession) { }
		public void onCaptureResult(CaptureResult result, boolean isCompleted) throws CameraAccessException { }
		public void finish() throws CameraAccessException { }
		//@formatter:on
	}

	// ===================================================================================
	// State Definition
	private final State mInitSurfaceState = new State("InitSurface") {
		public void enter() throws CameraAccessException {
			if (mTextureView.isAvailable()) {
				nextState(mOpenCameraState);
			} else {
				mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
			}
		}

		public void onSurfaceTextureAvailable(int width, int height) {
			nextState(mOpenCameraState);
		}

		private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
			@Override
			public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
				if (mState != null) mState.onSurfaceTextureAvailable(width, height);
			}

			@Override
			public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
				// TODO: ratation changed.
				Log.v("abe", "aaaaaaaaaaaa");
			}

			@Override
			public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
				return true;
			}

			@Override
			public void onSurfaceTextureUpdated(SurfaceTexture texture) {
			}
		};
	};
	private final CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
		@Override
		public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
			onCaptureResult(partialResult, false);
		}
		@Override
		public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
			onCaptureResult(result, true);
			Rect r = result.get(CaptureResult.SCALER_CROP_REGION);
			//Log.v("abe1", r.toString());
		}
		private void onCaptureResult(CaptureResult result, boolean isCompleted) {
			try {
				if (mState != null) mState.onCaptureResult(result, isCompleted);
			} catch (CameraAccessException e) {
				Log.e(TAG, "handle():", e);
				Log.v("abe1", "abe1");
				nextState(mAbortState);
			}
		}
	};

	private boolean mIsFirst = true;
	// -----------------------------------------------------------------------------------
	private final State mOpenCameraState = new State("OpenCamera") {
		public void enter() throws CameraAccessException {
			// configureTransform(width, height);
			String cameraId = Camera2Util.getCameraId(mCameraManager, CameraCharacteristics.LENS_FACING_BACK);
			StreamConfigurationMap map = mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

			mImageReader = Camera2Util.getMaxSizeImageReader(map, ImageFormat.JPEG);
//			mImageReader = ImageReader.newInstance(
//					//maxSize.getWidth(), maxSize.getHeight(), // for landscape.
//					1800, 3600, // for portrait.
//					ImageFormat.JPEG, /*maxImages*/1);
			Rect rect = mCameraParam.createOutputRegion(mCameraParam.getSensorActiveRegion());
			Log.v("abe111", rect.toString());
//			mImageReader = ImageReader.newInstance(
//					//maxSize.getWidth(), maxSize.getHeight(), // for landscape.
//					rect.height() * 2 , rect.width() * 2  , // for portrait.
//					ImageFormat.JPEG, /*maxImages*/1);
			//Size previewSize = Camera2Util.getBestPreviewSize(map, mImageReader);
			//mTextureView.setPreviewSize(previewSize.getHeight(), previewSize.getWidth());
			//mTextureView.setPreviewSize(rect.height(), rect.width() );
			if(mCameraParam.getOutputAspectRate() > 1.5){
				mTextureView.setPreviewSize(480, 800 );
			}else{
				mTextureView.setPreviewSize(360, 480 );
			}

			if (ActivityCompat.checkSelfPermission(mCon, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
				// TODO: Consider calling
				//    ActivityCompat#requestPermissions
				// here to request the missing permissions, and then overriding
				//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
				//                                          int[] grantResults)
				// to handle the case where the user grants the permission. See the documentation
				// for ActivityCompat#requestPermissions for more details.
				return;
			}
			mCameraManager.openCamera(cameraId, mStateCallback, mHandler);
			Log.d(TAG, "openCamera:" + cameraId);
		}
		public void onCameraOpened(CameraDevice cameraDevice) {
			mCameraDevice = cameraDevice;
			nextState(mCreateSessionState);
		}

		private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
			@Override
			public void onOpened(CameraDevice cameraDevice) {
				if (mState != null) mState.onCameraOpened(cameraDevice);
			}
			@Override
			public void onDisconnected(CameraDevice cameraDevice) {
				nextState(mAbortState);
			}
			@Override
			public void onError(CameraDevice cameraDevice, int error) {
				Log.e(TAG, "CameraDevice:onError:" + error);
				nextState(mAbortState);
			}
		};
	};
	// -----------------------------------------------------------------------------------
	private final State mCreateSessionState = new State("CreateSession") {
		public void enter() throws CameraAccessException {
			mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			SurfaceTexture texture = mTextureView.getSurfaceTexture();
			texture.setDefaultBufferSize(mTextureView.getPreviewWidth(), mTextureView.getPreviewHeight());
			Surface surface = new Surface(texture);
			mPreviewRequestBuilder.removeTarget(surface);
			mPreviewRequestBuilder.addTarget(surface);
			//List<Surface> outputs = Arrays.asList(surface, mImageReader.getSurface());
			List<Surface> outputs = Arrays.asList(surface);
			mCameraDevice.createCaptureSession(outputs, mSessionCallback, mHandler);
		}
		public void onSessionConfigured(CameraCaptureSession cameraCaptureSession) {
			mCaptureSession = cameraCaptureSession;
			nextState(mPreviewState);
		}

		private final CameraCaptureSession.StateCallback mSessionCallback = new CameraCaptureSession.StateCallback() {
			@Override
			public void onConfigured(CameraCaptureSession cameraCaptureSession) {
				if (mState != null) mState.onSessionConfigured(cameraCaptureSession);
			}
			@Override
			public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
				nextState(mAbortState);
			}
		};
	};
	// -----------------------------------------------------------------------------------
	private final State mPreviewState = new State("Preview") {
		public void enter() throws CameraAccessException {
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
			Rect rect = mCameraParam.createOutputRegion(mCameraParam.getSensorActiveRegion());
			mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, rect);
			mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mHandler);
			//mTextureView.setPreviewSize(rect.height(), rect.width() );
		}
		@Override
		public void zoom(Rect zoomRect) throws CameraAccessException{
			//mCameraParam.setCroppingRegion(zoomRect);
			zoomRect = mCameraParam.createOutputRegion(zoomRect);
			mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
			mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mHandler);
		}
		@Override
		public void aspectChanged() throws CameraAccessException{
			//Rect rect = mCameraParam.createOutputRegion();
			//Log.v("abe123", rect.toString());
			//mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, rect);
			//mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mHandler);
		}
		@Override
		public void onCaptureResult(CaptureResult result, boolean isCompleted) throws CameraAccessException {
			Rect r = result.get(CaptureResult.SCALER_CROP_REGION);
			if(r != null){
				Map map = mCameraParam.calcFov(r);
				mCallback.setFov(map);
			}
		}
	};

	// -----------------------------------------------------------------------------------
	private final State mAutoFocusState = new State("AutoFocus") {
		public void enter() throws CameraAccessException {
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
			mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mHandler);
		}
		public void onCaptureResult(CaptureResult result, boolean isCompleted) throws CameraAccessException {
			Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
			boolean isAfReady = afState == null
					|| afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
					|| afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED;
			if (isAfReady) {
				nextState(mAutoExposureState);
			}
		}
	};
	// -----------------------------------------------------------------------------------
	private final State mAutoExposureState = new State("AutoExposure") {
		public void enter() throws CameraAccessException {
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
					CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
			mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mHandler);
		}
		public void onCaptureResult(CaptureResult result, boolean isCompleted) throws CameraAccessException {
			Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
			boolean isAeReady = aeState == null
					|| aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED
					|| aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED;
			if (isAeReady) {
				nextState(mTakePictureState);
			}
		}
	};
	// -----------------------------------------------------------------------------------
	private final State mTakePictureState = new State("TakePicture") {
		public void enter() throws CameraAccessException {
			final CaptureRequest.Builder captureBuilder =
					mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
			captureBuilder.addTarget(mImageReader.getSurface());
			captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
			captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
			captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90); // portraito
			mImageReader.setOnImageAvailableListener(mTakePictureListener, mHandler);

			mCaptureSession.stopRepeating();
			mCaptureSession.capture(captureBuilder.build(), mCaptureCallback, mHandler);
		}
		public void onCaptureResult(CaptureResult result, boolean isCompleted) throws CameraAccessException {
			if (isCompleted) {
				nextState(mPreviewState);
			}
		}
		public void finish() throws CameraAccessException {
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
			mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mHandler);
			mTakePictureListener = null;
		}
	};
	// -----------------------------------------------------------------------------------
	private final State mAbortState = new State("Abort") {
		public void enter() throws CameraAccessException {
			shutdown();
			nextState(null);
		}
	};
}
