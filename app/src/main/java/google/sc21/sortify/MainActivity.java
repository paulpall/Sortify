//region HEADER, CREDITS & VERSION HISTORY
/*
* --------------------------------------------------
* Sortify App, Created 13/03/2021
* Made by Paul & Udit
* for Google's 2021 Solution Challenge
* --------------------------------------------------
* CREDITS:
*
* Android Developers Camera Documentation:
* https://developer.android.com/training/camera/photobasics
* Sample code for Google Cloud Vision:
*  https://github.com/GoogleCloudPlatform/cloud-vision
* --------------------------------------------------
* VERSION HISTORY:
*
* Version 0.7:
*  Implemented a List View for Displaying Results
*  Improved Matching function to Create a List of
*   Only Items that Both the Camera and Database saw
* - - - - - - - - - - - - - - - - - - - - - - - - -
* Version 0.6:
*  Implemented CSV Dataset Handling.
*  Implemented Scanned Label to Data Matching for
*   Providing Recycling Instructions.
* - - - - - - - - - - - - - - - - - - - - - - - - -
* Version 0.5:
*  Cleaned Code + Improved Comments
* - - - - - - - - - - - - - - - - - - - - - - - - -
* Version 0.4:
*  Image Capturing and Labelling Working!
*  Still not exactly sure about the resolution...
*  (Really Messy Code, I Promise I'll Clean This in
*  the Future!!!)
* - - - - - - - - - - - - - - - - - - - - - - - - -
* Version 0.3:
*  Camera & Image Capture Working.
*  Might need to investigate the image resolution?
*  Also GUI Looks a bit wonky, might need to work
*  on that as well if we have time.
* - - - - - - - - - - - - - - - - - - - - - - - - -
* Version 0.2:
*  Prepared GUI for Camera Interface.
*  Tweaked theme to more suite our app idea! ;)
* - - - - - - - - - - - - - - - - - - - - - - - - -
* Version 0.1:
*   First Commit! Empty Android Studio Template.
 */
//endregion



// OUR PACKAGE
package google.sc21.sortify;



//region IMPORT REQUIRED PACKAGES
// New Android X Classes
import androidx.appcompat.app.AppCompatActivity;
// Android Classes
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
// Google Classes
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequest;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
// Java Classes
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
//endregion



// MAIN ACTVITY CLASS
public class MainActivity extends AppCompatActivity {
    //region Declare Class Objects
    //GUI Objects:
    private Button cameraButton;
    private TextView testLabel;
    private ImageView imageBox;
    private static Bitmap junkPhoto;
    private static List<Junk> referenceDataSet;
    //Constants/Parameters:
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int MAX_LABEL_RESULTS = 10;
    private static final int MAX_DIMENSION = 1200;
    private static final String CLOUD_VISION_API_KEY = BuildConfig.API_KEY;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String ANDROID_CERT_HEADER = "X-Android-Cert";
    private static final String ANDROID_PACKAGE_HEADER = "X-Android-Package";
    private static final String DATASET_FILENAME = "test_dataset.csv";
    //endregion


    //region Capture Photo Functions
    // Calls Camera Activity:
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        } catch (ActivityNotFoundException e) {
            Log.e(String.valueOf(e), "Camera Intent not Found: ");
            testLabel.setText("Houston, we have a problem!?");
        }
    }
    // Returns Image from Camera Activity:
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            junkPhoto = (Bitmap) extras.get("data");
            uploadImage(junkPhoto);
        }
    }
    //endregion


    //region Process Photo Functions
    // Sets the Image and Calls Cloud:
    public void uploadImage(Bitmap bitmapImage) {
        if (bitmapImage != null) {
            Bitmap bitmap = scaleBitmapDown(bitmapImage, MAX_DIMENSION);
            callCloudVision(bitmap);
        } else {
            Log.d(TAG, "Image picker gave us a null image.");
            Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
        }
    }
    // Scales the Image to Save on Bandwidth:
    private Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {
        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        int resizedWidth = maxDimension;
        int resizedHeight = maxDimension;

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension;
            resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = maxDimension;
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
    }
    //endregion


    //region Threading Function
    private void callCloudVision(final Bitmap bitmap) {
        //Switch Text to Loading
        testLabel.setText(R.string.loading_message);

        //Do the real work in an async task, because we need to use the network anyway
        try {
            AsyncTask<Object, Void, List<EntityAnnotation>> labelDetectionTask = new LabelDetectionTask(this, prepareAnnotationRequest(bitmap));
            labelDetectionTask.execute();
        } catch (IOException e) {
            Log.d(TAG, "failed to make API request because of other IOException " + e.getMessage());
        }
        Intent discoverActivity = new Intent(this, ExploreActivity.class);
        startActivity(discoverActivity);
    }
    //endregion


    //region Thread Tasks Class
    private static class LabelDetectionTask extends AsyncTask<Object, Void, List<EntityAnnotation>> {
        private final WeakReference<MainActivity> mActivityWeakReference;
        private Vision.Images.Annotate mRequest;

        LabelDetectionTask(MainActivity activity, Vision.Images.Annotate annotate) {
            mActivityWeakReference = new WeakReference<>(activity);
            mRequest = annotate;
        }

        @Override
        protected List<EntityAnnotation> doInBackground(Object... params) {
            try {
                Log.d(TAG, "created Cloud Vision request object, sending request");
                BatchAnnotateImagesResponse response = mRequest.execute();
                List<EntityAnnotation> labels = response.getResponses().get(0).getLabelAnnotations();
                return labels;
            } catch (GoogleJsonResponseException e) {
                Log.d(TAG, "failed to make API request because " + e.getContent());
            } catch (IOException e) {
                Log.d(TAG, "failed to make API request because of other IOException " + e.getMessage());
            }
            return null;
            //return "Cloud Vision API request failed. Check logs for details.";
        }

        protected void onPostExecute(List<EntityAnnotation> result) {
            MainActivity activity = mActivityWeakReference.get();
            if (activity != null && !activity.isFinishing()) {
                TextView imageDetail = activity.findViewById(R.id.helloLabel);
                imageDetail.setText(analyseResponse(result));

                //ExploreActivity.loadData(referenceDataSet);
                ExploreActivity.loadData(matchData(result));
                ExploreActivity.setImage(junkPhoto);

            }
        }
    }
    //endregion


    //region Google Cloud Communication Function
    private Vision.Images.Annotate prepareAnnotationRequest(Bitmap bitmap) throws IOException {
        HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        VisionRequestInitializer requestInitializer =
                new VisionRequestInitializer(CLOUD_VISION_API_KEY) {
                    /**
                     * We override this so we can inject important identifying fields into the HTTP
                     * headers. This enables use of a restricted cloud platform API key.
                     */
                    @Override
                    protected void initializeVisionRequest(VisionRequest<?> visionRequest)
                            throws IOException {
                        super.initializeVisionRequest(visionRequest);

                        String packageName = getPackageName();
                        visionRequest.getRequestHeaders().set(ANDROID_PACKAGE_HEADER, packageName);

                        String sig = PackageManagerUtils.getSignature(getPackageManager(), packageName);

                        visionRequest.getRequestHeaders().set(ANDROID_CERT_HEADER, sig);
                    }
                };

        Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
        builder.setVisionRequestInitializer(requestInitializer);

        Vision vision = builder.build();

        BatchAnnotateImagesRequest batchAnnotateImagesRequest =
                new BatchAnnotateImagesRequest();
        batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>() {{
            AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

            // Add the image
            Image base64EncodedImage = new Image();
            // Convert the bitmap to a JPEG
            // Just in case it's a format that Android understands but Cloud Vision
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
            byte[] imageBytes = byteArrayOutputStream.toByteArray();

            // Base64 encode the JPEG
            base64EncodedImage.encodeContent(imageBytes);
            annotateImageRequest.setImage(base64EncodedImage);

            // add the features we want
            annotateImageRequest.setFeatures(new ArrayList<Feature>() {{
                Feature labelDetection = new Feature();
                labelDetection.setType("LABEL_DETECTION");
                labelDetection.setMaxResults(MAX_LABEL_RESULTS);
                add(labelDetection);
            }});

            // Add the list of one thing to the request
            add(annotateImageRequest);
        }});

        Vision.Images.Annotate annotateRequest =
                vision.images().annotate(batchAnnotateImagesRequest);
        // Due to a bug: requests to Vision API containing large images fail when GZipped.
        annotateRequest.setDisableGZipContent(true);
        Log.d(TAG, "created Cloud Vision request object, sending request");

        return annotateRequest;
    }
    //endregion


    //region Response Analysing Function
    private static String analyseResponse(List<EntityAnnotation> labels) {
        StringBuilder message = new StringBuilder();

        if (labels != null) {
            for (EntityAnnotation label : labels) {
                message.append(String.format(Locale.US, "%.3f: %s", label.getScore(), label.getDescription()));
                boolean matchFound = false;
                int item = 0;
                while (!matchFound && item < referenceDataSet.size()) {
                    if (referenceDataSet.get(item).matches(label.getDescription())) {
                        message.append(", Bin: " + referenceDataSet.get(item).returnInfo());
                        matchFound = true;
                    }
                    item++;
                }
                message.append("\n");
            }
        } else {
            message.append("nothing");
        }
        return message.toString();
    }
    //endregion


    //region Relevant Info Combining Function
    private static List<Junk> matchData(List<EntityAnnotation> labels) {
        List<Junk> matchedData = new ArrayList<>();

        if (labels != null) {
            for (EntityAnnotation label : labels) {
                boolean matchFound = false;
                int item = 0;
                while (!matchFound && item < referenceDataSet.size()) {
                    if (referenceDataSet.get(item).matches(label.getDescription())) {
                        if (!matchedData.contains(referenceDataSet.get(item))) {
                            matchedData.add(referenceDataSet.get(item));
                            matchFound = true;
                        }
                    }
                    item++;
                }
            }
        }
        return matchedData;
    }
    //endregion


    //region CSV File Import Function
    private List<Junk> importDataset() {
        List<Junk> referenceData = new ArrayList<>();

        try (InputStreamReader is = new InputStreamReader(getAssets().open(DATASET_FILENAME))){
            BufferedReader reader = new BufferedReader(is);
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                try (Scanner column = new Scanner(line)) {
                    column.useDelimiter(",");
                    referenceData.add(Junk.newItemFromCSV(column));
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "File Not Found: " + e);
        }
        return referenceData;
    }
    //endregion



    //region MAIN FUNCTION
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Connect GUI with Code
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        cameraButton = findViewById(R.id.cameraButton);
        testLabel = findViewById(R.id.helloLabel);
        imageBox = findViewById(R.id.photoView);

        // Import Data-Set
        referenceDataSet = importDataset();

        // Camera Button Pressed
        cameraButton.setOnClickListener(v -> {
            testLabel.setText("Open Camera");
            dispatchTakePictureIntent();

        });
    }
    //endregion
}