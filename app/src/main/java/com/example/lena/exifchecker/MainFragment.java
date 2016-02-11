package com.example.lena.exifchecker;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.IOException;


public class MainFragment extends android.support.v4.app.Fragment {
    private static final int REQUEST_GALLERY = 0;
    private static final int REQUEST_PICK_CONTENT = 1;
    private static final int READ_REQUEST_CODE = 1337;

    private static final String TAG = "MainFragment";

    public MainFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // このフラグメント用のレイアウトをインフレートする
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        Button imageSelectButton = (Button)view.findViewById(R.id.button_image_select);
        Button takePictureButton = (Button)view.findViewById(R.id.button_take_picture);
        imageSelectButton.setOnClickListener(buttonImageSelectListener);
        takePictureButton.setOnClickListener(buttonTakePictureListener);
        return view;
    }

    View.OnClickListener buttonImageSelectListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Toast.makeText(getActivity(), "imageSelectButton", Toast.LENGTH_SHORT).show();
            // ギャラリー呼び出し
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(intent, READ_REQUEST_CODE);
        }
    };

    View.OnClickListener buttonTakePictureListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Toast.makeText(getActivity(), "takePicutureButton", Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                Log.i(TAG, "Uri: " + uri.toString());
                String stringUri;
                showImage(uri);
            }
        }
    }

    /**
     * 画像のURIが与えられ、DialogFragmentを使用して画面上に表示されます。
     *
     * @param uri
     */
    public void showImage(Uri uri) {
        if (uri != null) {
            FragmentManager fm = getActivity().getSupportFragmentManager();
            ImageDialogFragment imageDialog = new ImageDialogFragment();
            Bundle fragmentArguments = new Bundle();
            fragmentArguments.putParcelable("URI", uri);
            imageDialog.setArguments(fragmentArguments);
            imageDialog.show(fm, "image_dialog");
        }
    }

    
    public static class ImageDialogFragment extends DialogFragment {
        private Dialog mDialog;
        private Uri mUri;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mUri = getArguments().getParcelable("URI");
        }

        private Uri getUri() {
            String state = Environment.getExternalStorageState();
            if(!state.equalsIgnoreCase(Environment.MEDIA_MOUNTED))
                return MediaStore.Images.Media.INTERNAL_CONTENT_URI;

            return MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        /** Create a Bitmap from the URI for that image and return it.
         *
         * @param uri the Uri for the image to return.
         */
        private Bitmap getBitmapFromUri(Uri uri) {
            ParcelFileDescriptor parcelFileDescriptor = null;
            try {
                parcelFileDescriptor =
                        getActivity().getContentResolver().openFileDescriptor(uri, "r");
                FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
                parcelFileDescriptor.close();

                return image;
            } catch (Exception e) {
                Log.e(TAG, "Failed to load image.", e);
                Toast.makeText(getActivity(), "Failed to get image", Toast.LENGTH_LONG).show();
                return null;
            } finally {
                try {
                    if (parcelFileDescriptor != null) {
                        parcelFileDescriptor.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Error closing ParcelFile Descriptor");
                }
            }
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            mDialog = super.onCreateDialog(savedInstanceState);
            // To optimize for the "lightbox" style layout.  Since we're not actually displaying a
            // title, remove the bar along the top of the fragment where a dialog title would
            // normally go.
            mDialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            final ImageView imageView = new ImageView(getActivity());
            mDialog.setContentView(imageView);

            // BEGIN_INCLUDE (show_image)
            // Loading the image is going to require some sort of I/O, which must occur off the UI
            // thread.  Changing the ImageView to display the image must occur ON the UI thread.
            // The easiest way to divide up this labor is with an AsyncTask.  The doInBackground
            // method will run in a separate thread, but onPostExecute will run in the main
            // UI thread.
            AsyncTask<Uri, Void, Bitmap> imageLoadAsyncTask = new AsyncTask<Uri, Void, Bitmap>() {
                @Override
                protected Bitmap doInBackground(Uri... uris) {
                    dumpImageMetaData(uris[0]);
                    return getBitmapFromUri(uris[0]);
                }

                @Override
                protected void onPostExecute(Bitmap bitmap) {
                    imageView.setImageBitmap(bitmap);
                }
            };
            imageLoadAsyncTask.execute(mUri);
            // END_INCLUDE (show_image)

            return mDialog;
        }

        @Override
        public void onStop() {
            super.onStop();
            if (getDialog() != null) {
                getDialog().dismiss();
            }
        }

        /**
         * Grabs metadata for a document specified by URI, logs it to the screen.
         *
         * @param uri The uri for the document whose metadata should be printed.
         */
        public void dumpImageMetaData(Uri uri) {
            // BEGIN_INCLUDE (dump_metadata)

            // The query, since it only applies to a single document, will only return one row.
            // no need to filter, sort, or select fields, since we want all fields for one
            // document.
            Cursor cursor = getActivity().getContentResolver()
                    .query(uri, null, null, null, null, null);

            try {
                // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
                // "if there's anything to look at, look at it" conditionals.
                if (cursor != null && cursor.moveToFirst()) {

                    // Note it's called "Display Name".  This is provider-specific, and
                    // might not necessarily be the file name.
                    String displayName = cursor.getString(
                            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    Log.i(TAG, "Display Name: " + displayName);

                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    // If the size is unknown, the value stored is null.  But since an int can't be
                    // null in java, the behavior is implementation-specific, which is just a fancy
                    // term for "unpredictable".  So as a rule, check if it's null before assigning
                    // to an int.  This will happen often:  The storage API allows for remote
                    // files, whose size might not be locally known.
                    String size = null;
                    if (!cursor.isNull(sizeIndex)) {
                        // Technically the column stores an int, but cursor.getString will do the
                        // conversion automatically.
                        size = cursor.getString(sizeIndex);
                    } else {
                        size = "Unknown";
                    }
                    Log.i(TAG, "Size: " + size);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            // END_INCLUDE (dump_metadata)
        }
    }



}
