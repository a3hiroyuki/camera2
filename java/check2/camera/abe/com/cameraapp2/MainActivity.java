package check2.camera.abe.com.cameraapp2;

import java.nio.ByteBuffer;
import java.util.Map;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

public class MainActivity extends Activity implements Camera2StateMachine.Callback{
	private AutoFitTextureView mTextureView;
	private ImageView mImageView;
	private Camera2StateMachine mCamera2;

	public static String HORIZONTAL_FOV = "hhh";
	public static String VERTICAL_FOV = "sss";

	//Zooming
	protected float mFingerSpacing = 0;
	protected float mZoomLevel = 1f;
	protected float maximumZoomLevel;
	protected Rect zoom;

	private TextView mVerFovTxt, mHorFovTxt,
			          mZoomRationTxt, mCropRegionTxt, mBasicInfoTxt;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mHorFovTxt = (TextView) findViewById(R.id.hor_fov);
		mVerFovTxt = (TextView) findViewById(R.id.ver_fov);
		mZoomRationTxt = (TextView)findViewById(R.id.zoom_ration);
		mCropRegionTxt = (TextView)findViewById(R.id.crop_region);
		mBasicInfoTxt = (TextView)findViewById(R.id.basic_info);

		mTextureView = (AutoFitTextureView) findViewById(R.id.TextureView);
		mImageView = (ImageView) findViewById(R.id.ImageView);
		mCamera2 = new Camera2StateMachine(this, mTextureView);

		mBasicInfoTxt.setText(mCamera2.getCameraBasicInfo());

		RadioGroup radioGroup = (RadioGroup) findViewById(R.id.radiogroup);
		radioGroup.check(R.id.radiobutton_green);
		RadioButton radioButton = (RadioButton) findViewById(radioGroup.getCheckedRadioButtonId());
		radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				double aspectRate = -1;
				if(checkedId == R.id.radiobutton_green){
					aspectRate = (double)4/(double)3;
				}else if(checkedId == R.id.radiobutton_blue){
					aspectRate = (double)16/(double)9;
				}
				mCamera2.setAspectRate(aspectRate);
				mCamera2.open(MainActivity.this, mTextureView);
				initZooming();
			}
		});

	}

	@Override
	protected void onResume() {
		super.onResume();
		mCamera2.open(this, mTextureView);
		maximumZoomLevel = mCamera2.getMaxZoom();
	}

	@Override
	protected void onPause() {
		mCamera2.close();
		super.onPause();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && mImageView.getVisibility() == View.VISIBLE) {
			mTextureView.setVisibility(View.VISIBLE);
			mImageView.setVisibility(View.INVISIBLE);
			return false;
		}
		return super.onKeyDown(keyCode, event);
	}

	public void onClickShutter(View view) {
		mCamera2.takePicture(new ImageReader.OnImageAvailableListener() {
			@Override
			public void onImageAvailable(ImageReader reader) {
				// 撮れた画像をImageViewに貼り付けて表示。
				final Image image = reader.acquireLatestImage();
				ByteBuffer buffer = image.getPlanes()[0].getBuffer();
				byte[] bytes = new byte[buffer.remaining()];
				buffer.get(bytes);
				Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
				image.close();

				mImageView.setImageBitmap(bitmap);
				mImageView.setVisibility(View.VISIBLE);
				mTextureView.setVisibility(View.INVISIBLE);
			}
		});
	}



	@Override
	public boolean onTouchEvent(MotionEvent event) {
		try {
			Rect rect = mCamera2.getRect();
			if (rect == null) return false;
			float currentFingerSpacing;
			if (event.getPointerCount() == 2) { //Multi touch.
				currentFingerSpacing = getFingerSpacing(event);
				float delta = 0.05f; //Control this value to control the zooming sensibility
				if (mFingerSpacing != 0) {
					if (currentFingerSpacing > mFingerSpacing) { //Don't over zoom-in
						if ((maximumZoomLevel - mZoomLevel) <= delta) {
							delta = maximumZoomLevel - mZoomLevel;
						}
						mZoomLevel = mZoomLevel + delta;
					} else if (currentFingerSpacing < mFingerSpacing) { //Don't over zoom-out
						if ((mZoomLevel - delta) < 1f) {
							delta = mZoomLevel - 1f;
						}
						mZoomLevel = mZoomLevel - delta;
					}
					float ratio = (float) 1 / mZoomLevel; //This ratio is the ratio of cropped Rect to Camera's original(Maximum) Rect
					//croppedWidth and croppedHeight are the pixels cropped away, not pixels after cropped
					int croppedWidth = rect.width() - Math.round((float) rect.width() * ratio);
					int croppedHeight = rect.height() - Math.round((float) rect.height() * ratio);
					//Finally, zoom represents the zoomed visible area
					zoom = new Rect(croppedWidth / 2, croppedHeight / 2,
							rect.width() - croppedWidth / 2, rect.height() - croppedHeight / 2);
					//previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
					Log.v("abe33", zoom.toString());
					mCamera2.zoom(zoom);
				}
				mFingerSpacing = currentFingerSpacing;
				mZoomRationTxt.setText(String.format("× %.2f", mZoomLevel));
			} else { //Single touch point, needs to return true in order to detect one more touch point
				return true;
			}

			//captureSession.setRepeatingRequest(previewRequestBuilder.build(), captureCallback, null);
			return true;
		} catch (final Exception e) {
			//Error handling up to you
			return true;
		}
	}

	private void initZooming(){
		mFingerSpacing = 0;
		mZoomLevel = 1f;
		mZoomRationTxt.setText("×1");
	}

	private float getFingerSpacing(MotionEvent event) {
		float x = event.getX(0) - event.getX(1);
		float y = event.getY(0) - event.getY(1);
		return (float) Math.sqrt(x * x + y * y);
	}

	@Override
	public void setFov(Map<String, String> fovMap, Rect cropRect) {
		mHorFovTxt.setText(fovMap.get(HORIZONTAL_FOV));
		mVerFovTxt.setText(fovMap.get(VERTICAL_FOV));
		mCropRegionTxt.setText(String.format("crop領域：%d × %d", cropRect.width(), cropRect.height()));
	}
}
