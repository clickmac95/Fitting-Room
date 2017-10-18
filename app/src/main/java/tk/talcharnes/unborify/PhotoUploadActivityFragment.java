package tk.talcharnes.unborify;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import id.zelory.compressor.Compressor;
import pl.aprilapps.easyphotopicker.DefaultCallback;
import pl.aprilapps.easyphotopicker.EasyImage;
import tk.talcharnes.unborify.Utilities.Analytics;
import tk.talcharnes.unborify.Utilities.FirebaseConstants;
import tk.talcharnes.unborify.Utilities.Utils;


/**
 * A placeholder fragment containing a simple view.
 */
public class PhotoUploadActivityFragment extends Fragment {

    private static final String LOG_TAG = PhotoUploadActivityFragment.class.getSimpleName();
    private static final int PERMISSIONS_REQUEST = 123;

    private ImageView userImageToUploadView;
    private Button submitButton;
    private EditText photo_description_edit_text;
    private String photoDescription, user;
    private ProgressBar progressBar;
    private InterstitialAd mInterstitialAd;
    private byte[] bytes;

    public PhotoUploadActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        user = FirebaseConstants.getUser().getUid();

        View rootView = inflater.inflate(R.layout.fragment_photo_upload, container, false);
        photo_description_edit_text = (EditText) rootView.findViewById(R.id.photo_description_edit_text);
        photo_description_edit_text.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_DONE || keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(photo_description_edit_text.getWindowToken(), 0);
                }
                return true;
            }
        });

        progressBar = (ProgressBar) rootView.findViewById(R.id.progressBar);

        AdView mAdView = (AdView) rootView.findViewById(R.id.photo_upload_adview);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

        mInterstitialAd = new InterstitialAd(getContext());
        mInterstitialAd.setAdUnitId("ca-app-pub-3940256099942544/1033173712");
        mInterstitialAd.loadAd(new AdRequest.Builder().build());

        EasyImage.configuration(getActivity())
                .setImagesFolderName("FittingRoom_Data")
                .saveInAppExternalFilesDir();

        userImageToUploadView = (ImageView) rootView.findViewById(R.id.uploadedPhoto);

        userImageToUploadView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                askForPermission();
            }
        });

        submitButton = (Button) rootView.findViewById(R.id.submitButton);
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean editTextNotNull = checkEditTextNotNull();
                if (bytes != null && editTextNotNull) {
                    uploadImage(bytes);
                }
            }
        });

        return rootView;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if(requestCode == PERMISSIONS_REQUEST && grantResults.length > 0) {
            boolean allPermissionsGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult == PackageManager.PERMISSION_DENIED) {
                    allPermissionsGranted = false;
                }
            }
            if(allPermissionsGranted) {
                EasyImage.openChooserWithGallery(PhotoUploadActivityFragment.this, "", 0);
            } else {
                showGrantPermissionDialog();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            EasyImage.handleActivityResult(requestCode, resultCode, data, getActivity(),
                    new DefaultCallback() {
                        @Override
                        public void onImagePickerError(Exception e, EasyImage.ImageSource source, int type) {
                            Log.d(LOG_TAG, "Failed to pick a image, error: " + e.toString());
                        }

                        @Override
                        public void onImagePicked(File imageFile, EasyImage.ImageSource source, int type) {
                            Log.d(LOG_TAG, "Successfully picked an image, source: " + source.name());
                            try {
                                File compressedFile = new Compressor(getActivity()).compressToFile(imageFile);

                                int size = (int) compressedFile.length();
                                bytes = new byte[size];
                                BufferedInputStream buf = new BufferedInputStream(new FileInputStream(compressedFile));
                                int i = buf.read(bytes, 0, bytes.length);
                                buf.close();
                                Glide.with(getActivity()).load(bytes).asBitmap().into(userImageToUploadView);

                            } catch (IOException e) {
                                Log.d(LOG_TAG, "Failed to compress image, error: " + e.toString());
                            }
                        }

                        @Override
                        public void onCanceled(EasyImage.ImageSource source, int type) {
                            //Cancel handling, you might wanna remove taken photo if it was canceled
                            Log.d(LOG_TAG, "Canceling taken photo");
                            if (source == EasyImage.ImageSource.CAMERA) {
                                File photoFile = EasyImage.lastlyTakenButCanceledPhoto(getActivity());
                                if (photoFile != null) {
                                    Boolean isDeleted = photoFile.delete();
                                }
                            }
                        }
                    });
        }
    }

    private void uploadImage(byte[] data) {
        submitButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        photo_description_edit_text.setVisibility(View.GONE);
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType("image/webp")
                .build();

        final String photoName = System.currentTimeMillis() + "_byUser_" + user;
        StorageReference imageRef = FirebaseConstants.getStorRef().child("images/" + photoName + ".webp");
        UploadTask uploadTask = imageRef.putBytes(data, metadata);

        final Resources res = getResources();

        uploadTask.addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                double progress = 100.0 * (taskSnapshot.getBytesTransferred() /
                        taskSnapshot.getTotalByteCount());
                progressBar.setProgress((int) progress);
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                // taskSnapshot.getMetadata() contains file metadata such as size,
                // content-type, and download URL.
                Log.d(LOG_TAG, "Image is successfully uploaded: " + taskSnapshot.getMetadata());

                Uri downloadUrl = taskSnapshot.getDownloadUrl();

                Analytics.registerUpload(getActivity(), user);

                Photo photo = new Photo(user, photoDescription, FirebaseConstants.CATEGORY_FASHION,
                        0, 0, 0, 0, photoName + ".webp");

                FirebaseConstants.getRef().child(FirebaseConstants.PHOTOS).child(photoName).setValue(photo);

                Utils.photosUploadedCounter++;
                if (Utils.photosUploadedCounter % 2 == 0) {
                    if (mInterstitialAd.isLoaded()) {
                        mInterstitialAd.show();
                    } else {
                        Log.d("TAG", "The interstitial wasn't loaded yet.");
                    }
                } else {
                    if (getContext() != null) {
                        //NavUtils.navigateUpFromSameTask(getActivity());
                    }
                }

                setupAd();

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                showUploadDialog(res.getString(R.string.failure_title),
                        res.getString(R.string.failure_message) + " " +
                                res.getString(R.string.dialog_main_message));
            }
        }).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                showUploadDialog(res.getString(R.string.success_title),
                        res.getString(R.string.success_message) + " " +
                                res.getString(R.string.dialog_main_message));
            }
        });
    }

    private void showUploadDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.return_home, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        //getActivity().finish();
                        NavUtils.navigateUpFromSameTask(getActivity());
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        submitButton.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.GONE);
                        photo_description_edit_text.setVisibility(View.VISIBLE);
                        photo_description_edit_text.setText("");
                        userImageToUploadView.setImageDrawable(ContextCompat.getDrawable(getActivity(),
                                R.drawable.ic_add_a_photo_black_48dp));
                        bytes = null;
                        dialog.dismiss();
                    }
                });
        builder.show();
    }

    private void showGrantPermissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Stop!")
                .setMessage("You must grant all the permissions request otherwise you can't use this feature.")
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                });
        builder.show();
    }

    private void setupAd() {
        mInterstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                // Code to be executed when an ad finishes loading.
                Log.i("Ads", "onAdLoaded");
            }

            @Override
            public void onAdFailedToLoad(int errorCode) {
                // Code to be executed when an ad request fails.
                Log.i("Ads", "onAdFailedToLoad");
                if (getContext() != null) {
                   // NavUtils.navigateUpFromSameTask(getActivity());
                }
            }

            @Override
            public void onAdOpened() {
                // Code to be executed when the ad is displayed.
                Log.i("Ads", "onAdOpened");
            }

            @Override
            public void onAdLeftApplication() {
                // Code to be executed when the user has left the app.
                Log.i("Ads", "onAdLeftApplication");
            }

            @Override
            public void onAdClosed() {
                // Code to be executed when when the interstitial ad is closed.
                Log.i("Ads", "onAdClosed");
                if (getContext() != null) {
                   // NavUtils.navigateUpFromSameTask(getActivity());
                }
            }
        });
    }

    private void askForPermission() {

        ArrayList<String> permissionsList = new ArrayList<String>(Arrays
                .asList(android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        android.Manifest.permission.CAMERA));

        for(int i = permissionsList.size()-1; i > -1; i--) {
            if(ActivityCompat.checkSelfPermission(getActivity(), permissionsList.get(i)) ==
                    PackageManager.PERMISSION_GRANTED) {
                permissionsList.remove(i);
            }
        }

        if(permissionsList.size() > 0) {
            ActivityCompat.requestPermissions(getActivity(),
                        permissionsList.toArray(new String[0]), PERMISSIONS_REQUEST);
        } else {
            EasyImage.openChooserWithGallery(PhotoUploadActivityFragment.this, "", 0);
        }

    }

    private boolean checkEditTextNotNull() {
        photoDescription = photo_description_edit_text.getText().toString();

        int photoDescriptionLength = photoDescription.length();

        if(photoDescriptionLength == 0) {
            photo_description_edit_text.setError(getString(R.string.occasion_cannot_be_empty_string));
        } else if(photoDescriptionLength > 40) {
            int tooLongByThisManyCharacters = photoDescriptionLength - 40;
            photo_description_edit_text.setError("Please remove " + tooLongByThisManyCharacters +
                    " characters");
        } else {
            return true;
        }
        return false;
    }

}
