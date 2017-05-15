package sysu.mobile.limk.library;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.LocationManager;

import sysu.mobile.limk.library.jama.Matrix;

public class SensorUtils {
    private static SensorUtils mInstance;

    public static synchronized SensorUtils getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new SensorUtils(context);
        } else {
            mInstance.mContext = context;
        }
        return mInstance;
    }

    private Context mContext;
    private SensorManager mSensorManager;
    private Sensor mGyroSensor;
    private Sensor mGravSensor;

    private static final float NS2S = 1.0f / 1000000000.0f;
    private static final float EPSILON = 0.000001f;

    private Matrix mCurRotationMatrix;
    private Matrix mCurGravityMatrix;
    private double[] initHorizon = new double[3];
    private double[] drift;

    private final double[] deltaRotationVector = new double[4];
    private double timestamp = 0;
    private boolean isReady;

    private Sensor mOrientationSensor;
    private LocationManager mLocationManager;
    private String mLocationProvider;
    private float mTargetDirection;

    private SensorEventListener mGyroEventListener = new SensorEventListener() {

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (timestamp != 0) {
                final double dT = (event.timestamp - timestamp) * NS2S;

                float axisX = event.values[0] - (float) drift[0];
                float axisY = event.values[1] - (float) drift[1];
                float axisZ = event.values[2] - (float) drift[2];

                double omegaMagnitude = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);

                if (omegaMagnitude > EPSILON) {
                    axisX /= omegaMagnitude;
                    axisY /= omegaMagnitude;
                    axisZ /= omegaMagnitude;
                }

                double thetaOverTwo = omegaMagnitude * dT / 2.0f;
                double sinThetaOverTwo = Math.sin(thetaOverTwo);
                double cosThetaOverTwo = Math.cos(thetaOverTwo);
                deltaRotationVector[0] = sinThetaOverTwo * axisX;
                deltaRotationVector[1] = sinThetaOverTwo * axisY;
                deltaRotationVector[2] = sinThetaOverTwo * axisZ;
                deltaRotationVector[3] = cosThetaOverTwo;
            }
            timestamp = event.timestamp;
            float[] deltaRotationMatrix = new float[9];
            float[] deltaVector = new float[4];
            for (int i = 0; i < deltaRotationVector.length; i++) {
                deltaVector[i] = (float) deltaRotationVector[i];
            }
            SensorManager.getRotationMatrixFromVector(deltaRotationMatrix,
                    deltaVector);
            Matrix rotationMatrix = new Matrix(deltaRotationMatrix, 3);
            mCurRotationMatrix = rotationMatrix.times(mCurRotationMatrix);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private SensorEventListener mGravEventListener = new SensorEventListener() {

        @Override
        public void onSensorChanged(SensorEvent event) {
            mCurGravityMatrix = new Matrix(event.values, 3);
            if (!isReady) {
                reset();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private SensorUtils(Context context) {
        mContext = context;
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        //mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mGravSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        drift = new double[3];

        mOrientationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
    }

    public void registerSensor() {
        mSensorManager.registerListener(mGyroEventListener, mGyroSensor,
                SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(mGravEventListener, mGravSensor,
                SensorManager.SENSOR_DELAY_FASTEST);

        drift[0] = PreferenceUtils.getDoubleValue(mContext, "x_drift");
        drift[1] = PreferenceUtils.getDoubleValue(mContext, "y_drift");
        drift[2] = PreferenceUtils.getDoubleValue(mContext, "z_drift");

        //reset();
        mSensorManager.registerListener(mOrientationSensorEventListener, mOrientationSensor,
                SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void unregisterSensor() {
        mSensorManager.unregisterListener(mGyroEventListener);
        mSensorManager.unregisterListener(mGravEventListener);

        mSensorManager.unregisterListener(mOrientationSensorEventListener);
    }

    public void reset() {
        isReady = false;
        mCurRotationMatrix = Matrix.identity(3, 3);

        double[] init = new double[]{1, 0, 0};
        if (mCurGravityMatrix != null) {
            initHorizon = projectToHorizon(init,
                    mCurGravityMatrix.getRowPackedCopy());
            isReady = true;
        }
    }

    public double getAngle() {

        if (!isReady) {
            return Double.MAX_VALUE;
        }

        Matrix inverse = mCurRotationMatrix.inverse();
        Matrix initMatrix = new Matrix(initHorizon, 3);
        Matrix oMatrix = inverse.times(initMatrix);

        double[] xg_1 = oMatrix.getRowPackedCopy();
        double[] xg_2 = projectToHorizon(new double[]{1, 0, 0},
                mCurGravityMatrix.getRowPackedCopy());

        double cosDelta = (xg_1[0] * xg_2[0] + xg_1[1] * xg_2[1] + xg_1[2]
                * xg_2[2])
                / (norm(xg_1) * norm(xg_2));
        if (cosDelta > 1 || cosDelta < -1) {
            cosDelta = 1;
        }
        double delta = Math.acos(cosDelta);
        double angle = Math.toDegrees(delta);

        double[] cross = crossProduct(xg_1, xg_2);
        double[] gravity = mCurGravityMatrix.getRowPackedCopy();
        double[] projectcross = projectToHorizon(cross, gravity);
        for (int i = 0; i < 3; i++) {
            projectcross[i] = cross[i] - projectcross[i];
        }
        double cosCross = (projectcross[0] * gravity[0] + projectcross[1]
                * gravity[1] + projectcross[2] * gravity[2])
                / norm(projectcross) / norm(gravity);
        if (cosCross > 1) {
            cosCross = 1;
        } else if (cosCross < -1) {
            cosCross = -1;
        }

        double orientation = Math.toDegrees(Math.acos(cosCross));
        if (orientation < 90) {
            angle = -angle;
        }
        return angle;
    }

    private double norm(double[] vec) {
        double result = 0;
        for (double v : vec) {
            result += v * v;
        }
        result = Math.sqrt(result);

        return result;
    }

    private double[] projectToHorizon(double[] vec, double[] gravity) {
        double[] xg = new double[3];
        double factor = (gravity[0] * vec[0]
                + gravity[1] * vec[1]
                + gravity[2] * vec[2]) / (norm(gravity));
        double normG = norm(gravity);
        xg[0] = factor * gravity[0] / normG;
        xg[1] = factor * gravity[1] / normG;
        xg[2] = factor * gravity[2] / normG;

        double[] result = new double[3];
        result[0] = vec[0] - xg[0];
        result[1] = vec[1] - xg[1];
        result[2] = vec[2] - xg[2];
        return result;
    }

    private double[] crossProduct(double[] vec_1, double[] vec_2) {
        double[] result = new double[3];

        result[0] = vec_1[1] * vec_2[2] - vec_1[2] * vec_2[1];
        result[1] = vec_1[2] * vec_2[0] - vec_1[0] * vec_2[2];
        result[2] = vec_1[0] * vec_2[1] - vec_1[1] * vec_2[0];

        return result;
    }

    private SensorEventListener mOrientationSensorEventListener = new SensorEventListener() {

        @Override
        public void onSensorChanged(SensorEvent event) {
            float direction = event.values[0] * -1.0f;
            mTargetDirection = normalizeDegree(direction);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private float normalizeDegree(float degree) {
        return (degree + 720) % 360;
    }

    public float getCompassDirection() {
        return mTargetDirection;
    }

}
