package check2.camera.abe.com.cameraapp2;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Size;
import android.util.SizeF;

import java.util.HashMap;
import java.util.Map;

public class CameraParam {

    private float[] mFocusArr;
    private SizeF mSensorSize;
    private Size mSensorPixelSize;
    private Size mActiveSensorSize;
    private Rect mSensorActiveRegion;
    //private Rect mSensorCropRegion;
    private double mSensorAspectRate;
    private Size mSensorOutputSize;
    //private Rect mCurrentCroppingRegion;
    private double mOutputAspectRate = (double)4/(double)3;

    public CameraParam(CameraCharacteristics characteristics){
        CameraCharacteristics.Key<float[]> focusKey = CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS;
        mFocusArr = (float[])characteristics.get(focusKey);
        CameraCharacteristics.Key<SizeF> sizeFKey = CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE;
        mSensorSize = (SizeF)characteristics.get(sizeFKey);
        CameraCharacteristics.Key<Size> compSizeKey = CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE;
        mSensorPixelSize = (Size)characteristics.get(compSizeKey);
        CameraCharacteristics.Key<Rect> activeSizeKey = CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE;
        mSensorActiveRegion = (Rect)characteristics.get(activeSizeKey);
        mSensorAspectRate = (double)mSensorActiveRegion.width() / (double)mSensorActiveRegion.height();
        //mCurrentCroppingRegion = mSensorActiveRegion;
    }

    public void setOutputAspectRate(double rate){
        mOutputAspectRate = rate;
    }

    public double getOutputAspectRate(){
        return mOutputAspectRate;
    }

    public void checkSensorOutputSize(Rect rect){
        double outputSensorWidth = rect.width();
        double outputSensorHeight = rect.height();
        mSensorOutputSize = new Size((int)calcWidthSensorSize(outputSensorWidth), (int)calcHeightSensorSize(outputSensorHeight));
    }

//    public void setCroppingRegion(Rect rect){
//        mCurrentCroppingRegion = rect;
//    }

    public Rect getSensorActiveRegion(){
        return mSensorActiveRegion;
    }

    //設定されたアスペクト比に応じて出力画素を返す
    public Rect createOutputRegion(Rect rect){
        double outputSensorWidth = rect.width();
        double outputSensorHeight = rect.height();
        if(mOutputAspectRate > 0){
            if(mOutputAspectRate > mSensorAspectRate){
                outputSensorHeight = outputSensorHeight * (mSensorAspectRate/mOutputAspectRate);
            }else if(mSensorAspectRate > mOutputAspectRate){
                outputSensorWidth = outputSensorWidth * (mOutputAspectRate/mSensorAspectRate);
            }
        }
        final int centerX = mSensorActiveRegion.width() / 2;
        final int centerY = mSensorActiveRegion.height() / 2;
        //final int cropSize = Math.min(mSensorSize.width(), mSensorSize.height());
        Rect rect2 = new Rect(0,
                0,  (int)outputSensorWidth, (int)outputSensorHeight);
        return rect2;
//        return new Rect(rect.left, rect.top,
//                rect.left + (int)outputSensorWidth, rect.top + (int)outputSensorHeight);
    }

    public Map<String, String> calcFov(Rect rect){
        mSensorOutputSize = new Size((int)calcWidthSensorSize(rect.width()), (int)calcHeightSensorSize(rect.height()));
        Map<String, String> fovMap = new HashMap<String, String>();
        fovMap.put(MainActivity.HORIZONTAL_FOV, String.format("%.2f", calcFov(calcWidthSensorSize(rect.width()))));
        fovMap.put(MainActivity.VERTICAL_FOV, String.format("%.2f" ,calcFov(calcWidthSensorSize(rect.height()))));
        return fovMap;
    }

    public double calcFov(double num){
        double d =  2 * Math.atan(num / (2 * mFocusArr[0]));
        return toDegrees(d);
    }

    @Override
    public String toString() {
        //String z = String.format("センサアスペクト比：%.2f", mSensorAspectRate);
        String a = String.format("焦点距離：%s [mm]", String.valueOf(mFocusArr[0]));
        String b = String.format("センササイズ： %.2f × %.2f [mm2]", mSensorSize.getWidth(), mSensorSize.getHeight());
        // String c = String.format("センサの画素数： %d × %d", mSensorPixelSize.getWidth(), mSensorPixelSize.getHeight());
        String c = String.format("センサ有効画素： %d × %d", mSensorActiveRegion.width(), mSensorActiveRegion.height());
        //String e = String.format("出力アスペクト比指定時センササイズ： %.2f × %.2f", calcSensorWidthOfStream(), calcSensorHeightOfStream());
        //String fov1 = "水平画角：" + String.valueOf(calcFov(mSensorOutputSize.getWidth()));
        //String fov2 = "垂直画角：" + String.valueOf(calcFov(mSensorOutputSize.getHeight()));
        return String.format("%s\n%s\n%s", a, b, c);
    }

    private double calcWidthSensorSize(double size){
        return mSensorSize.getWidth() * (size/(double)mSensorPixelSize.getWidth());
    }

    private double calcHeightSensorSize(double size){
        return mSensorSize.getHeight() * (size/(double)mSensorPixelSize.getHeight());
    }

    private double calcSensorSize(double sensorSize, double size){
        return sensorSize * (size/(double)mSensorPixelSize.getWidth());
    }

    private static double toDegrees(double rad) {
        return rad * 180.0 / Math.PI;
    }
}
