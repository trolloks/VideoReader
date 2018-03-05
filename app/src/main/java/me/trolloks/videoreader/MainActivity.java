package me.trolloks.videoreader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Surface;
import android.widget.ImageView;

import org.tensorflow.demo.Classifier;
import org.tensorflow.demo.TensorFlowObjectDetectionAPIModel;
import org.tensorflow.demo.env.BorderedText;
import org.tensorflow.demo.env.ImageUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    VideoTask videoTask = null;

    protected int previewWidth = 0;
    protected int previewHeight = 0;

    private Bitmap croppedBitmap = null;
    private BorderedText borderedText;

    private Matrix frameToCropTransform;

    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;

    private static final int TF_OD_API_INPUT_SIZE = 640;
    private static final String TF_OD_API_MODEL_FILE =
            "file:///android_asset/ssd_mobilenet_v1_android_export.pb";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/coco_labels_list.txt";

    private Classifier detector;

    float textSizePx = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            detector = TensorFlowObjectDetectionAPIModel.create(
                    getAssets(), TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);

        } catch (Exception e){
            e.printStackTrace();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        playVideo();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopVideo();
    }

    private void playVideo() {
        stopVideo();
        videoTask = new VideoTask();
        videoTask.execute();
    }

    private void stopVideo() {
        if (videoTask != null)
            videoTask.cancel(true);
        videoTask = null;
    }

    private Bitmap downloadVideo(){
        byte[] image = null;
        Bitmap bm = null;
        Bitmap cropCopyBitmap = null;

        // do web call
        try {
            URL url = new URL("http://192.168.1.34:3000/image");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setDoOutput(false);
            BufferedInputStream in = new BufferedInputStream(urlConnection.getInputStream());
            ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();

            int c;
            while ((c = in.read()) != -1) {
                byteArrayOut.write(c);
            }

            image = byteArrayOut.toByteArray();
            in.close();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (Exception e){
            e.printStackTrace();
        }

        if (image != null) {
            bm = BitmapFactory.decodeByteArray(image, 0, image.length);

            if (previewWidth == 0 && previewHeight == 0) {
                previewWidth = bm.getWidth();
                previewHeight = bm.getHeight();

                textSizePx =
                        TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
                borderedText = new BorderedText(textSizePx);
                borderedText.setTypeface(Typeface.MONOSPACE);
            }

            int cropSize = TF_OD_API_INPUT_SIZE;
            croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

            frameToCropTransform =
                    ImageUtils.getTransformationMatrix(
                            previewWidth, previewHeight,
                            cropSize, cropSize,
                            getScreenOrientation(), false);

            frameToCropTransform.invert(new Matrix());

            cropCopyBitmap = processImage(bm);
        }

        return cropCopyBitmap;
    }

    private Bitmap processImage(Bitmap bm) {
        final Paint paint = new Paint();
        Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(bm, frameToCropTransform, null);
        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);
        for (final Classifier.Recognition result : results) {
            final RectF location = result.getLocation();
            if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE_TF_OD_API) {
                canvas.drawRect(location, paint);
                borderedText.drawText(canvas, location.left, location.bottom + textSizePx, result.getTitle());
            }
        }
        return croppedBitmap;
    }

    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }


    private class VideoTask extends AsyncTask<Void, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(Void... voids) {
            return downloadVideo();
        }

        @Override
        protected void onPostExecute(Bitmap bm) {
            ((ImageView) findViewById(R.id.image)).setImageBitmap(bm);
            new VideoTask().execute();
        }
    }
}
