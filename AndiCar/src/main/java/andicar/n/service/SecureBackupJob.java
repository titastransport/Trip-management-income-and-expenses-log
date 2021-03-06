package andicar.n.service;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import org.andicar2.activity.AndiCar;
import org.andicar2.activity.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import andicar.n.activity.miscellaneous.BackupListActivity;
import andicar.n.activity.preference.PreferenceActivity;
import andicar.n.interfaces.AndiCarAsyncTaskListener;
import andicar.n.utils.AndiCarCrashReporter;
import andicar.n.utils.ConstantValues;
import andicar.n.utils.FileUtils;
import andicar.n.utils.LogFileWriter;
import andicar.n.utils.Utils;
import andicar.n.utils.notification.AndiCarNotification;

/**
 * Created by Miklos Keresztes on 17.10.2017.
 */

public class SecureBackupJob extends JobService implements AndiCarAsyncTaskListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    public static final String BK_FILE_KEY = "bkFile";
    private static final String LogTag = "AndiCar SecureBackupJob";
    public static final String TAG = "SecureBackupJob";
    private final SharedPreferences mPreferences = AndiCar.getDefaultSharedPreferences();
    private final ArrayList<String> mFileToSend = new ArrayList<>();
    private String zippedBk;
    private LogFileWriter debugLogFileWriter = null;
    private GoogleApiClient mGoogleApiClient;

    @Override
    public boolean onStartJob(JobParameters jobParams) {
        final String bkFileToSend;
        final String bkFileName;

        try {
//            if (FileUtils.isFileSystemAccessGranted(getApplicationContext())) {
                FileUtils.createFolderIfNotExists(getApplicationContext(), ConstantValues.LOG_FOLDER);
                File debugLogFile = new File(ConstantValues.LOG_FOLDER + "SecureBackupJob.log");
                debugLogFileWriter = new LogFileWriter(debugLogFile, false);
                debugLogFileWriter.appendnl("onStartCommand begin");
                debugLogFileWriter.flush();
//            }

            if (jobParams.getExtras() == null) {
                if (debugLogFileWriter != null) {
                    debugLogFileWriter.appendnl("no params. Terminating process.");
                    debugLogFileWriter.flush();
                }
                jobFinished(jobParams, false);
                return false;
            }

            //check if secure backup is enabled
            if (!mPreferences.getBoolean(getString(R.string.pref_key_secure_backup_enabled), false)) {
                if (debugLogFileWriter != null) {
                    debugLogFileWriter.appendnl("SecureBackup not activated. Terminating process.");
                    debugLogFileWriter.flush();
                }
                jobFinished(jobParams, false);
                return false;
            }

            //check if a google account was chosen
            if (mPreferences.getString(getString(R.string.pref_key_google_account), "").length() == 0) {
                AndiCarNotification.showGeneralNotification(this, AndiCarNotification.NOTIFICATION_TYPE_NOT_REPORTABLE_ERROR, (int) System.currentTimeMillis(),
                        getString(R.string.pref_category_secure_backup), getString(R.string.error_107), PreferenceActivity.class, null);
                if (debugLogFileWriter != null) {
                    debugLogFileWriter.appendnl("no Google account chosen. Terminating process.");
                    debugLogFileWriter.flush();
                }
                jobFinished(jobParams, false);
                return false;
            }

            //check if destination email (sendTo) exists
            if (mPreferences.getString(getString(R.string.pref_key_secure_backup_destination), "0").equals("1") && //GMai;
                    mPreferences.getString(getString(R.string.pref_key_secure_backup_emailTo), "").length() == 0) {
                AndiCarNotification.showGeneralNotification(this, AndiCarNotification.NOTIFICATION_TYPE_NOT_REPORTABLE_ERROR, (int) System.currentTimeMillis(),
                        getString(R.string.pref_category_secure_backup), getString(R.string.error_106), PreferenceActivity.class, null);
                if (debugLogFileWriter != null) {
                    debugLogFileWriter.appendnl("No recipient email. Terminating process.");
                    debugLogFileWriter.flush();
                }
                jobFinished(jobParams, false);
                return false;
            }

            if (mPreferences.getString(getString(R.string.pref_key_secure_backup_destination), "0").equals("0") && //GDrive;
                    mPreferences.getString(getString(R.string.pref_key_secure_backup_gdrive_folder_id), "").equals("")) {
                AndiCarNotification.showGeneralNotification(this, AndiCarNotification.NOTIFICATION_TYPE_NOT_REPORTABLE_ERROR, (int) System.currentTimeMillis(),
                        getString(R.string.pref_category_secure_backup), getString(R.string.error_115), PreferenceActivity.class, null);
                if (debugLogFileWriter != null) {
                    debugLogFileWriter.appendnl("No drive folder selected. Terminating process.");
                    debugLogFileWriter.flush();
                }
                jobFinished(jobParams, false);
                return false;
            }

            bkFileToSend = jobParams.getExtras().getString(BK_FILE_KEY);
            if (bkFileToSend != null) {
                File bkFile = new File(bkFileToSend);
                if (bkFile.exists()) {
                    bkFileName = bkFile.getName();
                }
                else {
                    bkFileName = "";
                }
            }
            else {
                bkFileName = "";
            }

            if (bkFileToSend == null || bkFileName.length() == 0) {
                AndiCarNotification.showGeneralNotification(this, AndiCarNotification.NOTIFICATION_TYPE_NOT_REPORTABLE_ERROR, (int) System.currentTimeMillis(),
                        getString(R.string.pref_category_secure_backup), String.format(getString(R.string.error_100), " (" + bkFileToSend + ")"), BackupListActivity.class, null);
                if (debugLogFileWriter != null) {
                    debugLogFileWriter.appendnl("Backup file not found. Terminating process.");
                    debugLogFileWriter.flush();
                }
                Log.e(LogTag, "Backup file not found (" + bkFileToSend + ")");
                jobFinished(jobParams, false);
                return false;
            }

            String errorMessage = FileUtils.createFolderIfNotExists(this, ConstantValues.TEMP_FOLDER);
            if (errorMessage != null) {
                AndiCarNotification.showGeneralNotification(this, AndiCarNotification.NOTIFICATION_TYPE_NOT_REPORTABLE_ERROR, (int) System.currentTimeMillis(),
                        getString(R.string.pref_category_secure_backup), errorMessage, null, null);

                if (debugLogFileWriter != null) {
                    debugLogFileWriter.appendnl("Error in creating temporary folder: ").append(errorMessage);
                    debugLogFileWriter.flush();
                }
                jobFinished(jobParams, false);
                return false;
            }

            new Thread(new Runnable() {
                @Override
                public void run() {
                    Bundle fileBundle = new Bundle();
                    fileBundle.putString(bkFileName, bkFileToSend);

                    try {
                        if (mPreferences.getBoolean(getString(R.string.pref_key_secure_backup_send_tracks), false)) {
                            if (debugLogFileWriter != null) {
                                debugLogFileWriter.appendnl("Send gps track files option is: ON");
                            }
                            ArrayList<String> gpsTrackFiles = FileUtils.getFileNames(getApplicationContext(), ConstantValues.TRACK_FOLDER, null);
                            if (gpsTrackFiles != null && gpsTrackFiles.size() > 0) {
                                if (debugLogFileWriter != null) {
                                    debugLogFileWriter.appendnl("Preparing gps tracks to send.");
                                }
                                String gpsTrackFile;
                                for (String trackFile : gpsTrackFiles) {
                                    gpsTrackFile = trackFile;
                                    fileBundle.putString(ConstantValues.TRACK_FOLDER_NAME + "/" + gpsTrackFile, ConstantValues.TRACK_FOLDER + gpsTrackFile);
                                }
                            }
                            else {
                                if (debugLogFileWriter != null) {
                                    debugLogFileWriter.appendnl("No gps track files found.");
                                }
                            }
                        }
                        else {
                            if (debugLogFileWriter != null) {
                                debugLogFileWriter.appendnl("Send gps track files option is: OFF");
                            }
                        }
                        if (debugLogFileWriter != null) {
                            debugLogFileWriter.flush();
                        }

                        zippedBk = ConstantValues.TEMP_FOLDER + bkFileName.replace(".db", "") + ".zi_";
                        FileUtils.zipFiles(getApplicationContext(), fileBundle, zippedBk);
                        if (FileUtils.mLastException != null) {
                            AndiCarNotification.showGeneralNotification(getApplicationContext(), AndiCarNotification.NOTIFICATION_TYPE_NOT_REPORTABLE_ERROR,
                                    (int) System.currentTimeMillis(), getString(R.string.pref_category_secure_backup), FileUtils.mLastErrorMessage, null, null);
                            return;
                        }

                        mFileToSend.add(zippedBk);

                        if (mPreferences.getString(getString(R.string.pref_key_secure_backup_destination), "0").equals("0")) { //GDrive
                            if (debugLogFileWriter != null) {
                                debugLogFileWriter.appendnl("calling GDriveUploader for file: ").append(zippedBk);
                                debugLogFileWriter.flush();
                            }
                            mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                                    .addApi(Drive.API)
                                    .addScope(Drive.SCOPE_FILE)
                                    .addConnectionCallbacks(SecureBackupJob.this)
                                    .addOnConnectionFailedListener(SecureBackupJob.this)
                                    .setAccountName(mPreferences.getString(getString(R.string.pref_key_google_account), ""))
                                    .build();
                            mGoogleApiClient.connect();
                        } else {
                            if (debugLogFileWriter != null) {
                                debugLogFileWriter.appendnl("calling SendGMailTask for file: ").append(zippedBk);
                                debugLogFileWriter.flush();
                            }

                            new SendGMailTask(getApplicationContext(), mPreferences.getString(getString(R.string.pref_key_google_account), null),
                                    mPreferences.getString(getString(R.string.pref_key_secure_backup_emailTo), null),
                                    getString(R.string.secure_backup_mail_subject), getString(R.string.secure_backup_mail_body), mFileToSend, SecureBackupJob.this).execute();
                        }

                        if (debugLogFileWriter != null) {
                            debugLogFileWriter.appendnl("onStartCommandEnded");
                            debugLogFileWriter.flush();
                        }
                    }
                    catch (Exception e) {
                        try {
                            if (debugLogFileWriter != null) {
                                debugLogFileWriter.appendnl(e.getMessage());
                                debugLogFileWriter.appendnl(Utils.getStackTrace(e));
                                debugLogFileWriter.flush();
                            }
                        }
                        catch (Exception ignored) {
                        }
                    }
                }
            }).start();
        }
        catch (Exception e) {
            try {
                if (debugLogFileWriter != null) {
                    debugLogFileWriter.append("\n").append("====Exception Catches on onStartCommand() method====");
                    debugLogFileWriter.append("\n").append("====Stack Trace====");
                    debugLogFileWriter.append("\n").append(Utils.getStackTrace(e));
                    debugLogFileWriter.append("\n").append("=======End Stack Trace=======");
                    debugLogFileWriter.flush();
                }
                AndiCarNotification.showGeneralNotification(getApplicationContext(), AndiCarNotification.NOTIFICATION_TYPE_REPORTABLE_ERROR,
                        (int) System.currentTimeMillis(), getString(R.string.pref_category_secure_backup), e.getMessage(), null, e);
            }
            catch (IOException ignored) {
            }
        }

        jobFinished(jobParams, false);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters job) {
        return false;
    }

    @Override
    public void onAndiCarTaskCompleted(String successMessage) {
        //remove the postponed backup file if exists
        try {
            if (debugLogFileWriter != null) {
                debugLogFileWriter.appendnl("Success.");
                debugLogFileWriter.flush();
            }

            if (mPreferences.getBoolean(getString(R.string.pref_key_secure_backup_show_notification), true)) {
                AndiCarNotification.showGeneralNotification(this, AndiCarNotification.NOTIFICATION_TYPE_INFO, ConstantValues.NOTIFICATION_SECURE_BK_SUCCEEDED,
                        getString(R.string.pref_category_secure_backup), getString(R.string.secure_backup_success_message), null, null);
            }

            //remove temporary zipped file(s)
            removeTemporaryFiles();
        }
        catch (IOException e) {
            AndiCarCrashReporter.sendCrash(e);
        }
    }

    @Override
    public void onAndiCarTaskCancelled(String errorMsg, Exception e) {
        try {
            if (e != null) {
                if (e instanceof UserRecoverableAuthIOException) {
                    //no Google authorization
                    mPreferences.edit().putBoolean(getString(R.string.pref_key_secure_backup_enabled), false).apply();
                    AndiCarNotification.showGeneralNotification(this, AndiCarNotification.NOTIFICATION_TYPE_WARNING, (int) System.currentTimeMillis(), getString(R.string.pref_category_secure_backup),
                            getString(R.string.error_108), null, null);
                    if (debugLogFileWriter != null) {
                        debugLogFileWriter.appendnl("AndiCar is not authorized.");
                        debugLogFileWriter.append("\n").append("====Exception Stack Trace====");
                        debugLogFileWriter.append("\n").append(Utils.getStackTrace(e));
                        debugLogFileWriter.append("\n").append("=======End Stack Trace=======");
                        debugLogFileWriter.flush();
                    }
                }
                else {
                    if (Utils.getStackTrace(e).contains("OutOfMemoryError")) {
                        AndiCarNotification.showGeneralNotification(getApplicationContext(), AndiCarNotification.NOTIFICATION_TYPE_NOT_REPORTABLE_ERROR,
                                (int) System.currentTimeMillis(), getString(R.string.pref_category_secure_backup), getString(R.string.error_124), null, e);
                    }
                    else if (Utils.getStackTrace(e).contains("connect timed out")) {
                        AndiCarNotification.showGeneralNotification(getApplicationContext(), AndiCarNotification.NOTIFICATION_TYPE_NOT_REPORTABLE_ERROR,
                                (int) System.currentTimeMillis(), getString(R.string.pref_category_secure_backup), e.getMessage(), null, e);
                    }
                    else {
                        AndiCarNotification.showGeneralNotification(getApplicationContext(), AndiCarNotification.NOTIFICATION_TYPE_REPORTABLE_ERROR,
                                (int) System.currentTimeMillis(), getString(R.string.pref_category_secure_backup), e.getMessage(), null, e);
                    }

                    if (debugLogFileWriter != null) {
                        debugLogFileWriter.appendnl("Error: ").append(e.getMessage() != null ? e.getMessage() : "");
                        debugLogFileWriter.append("\n").append("====Exception Stack Trace====");
                        debugLogFileWriter.append("\n").append(Utils.getStackTrace(e));
                        debugLogFileWriter.append("\n").append("=======End Stack Trace=======");
                        debugLogFileWriter.flush();
                    }
                }
            }
            else {
                if (errorMsg != null) {
                    AndiCarNotification.showGeneralNotification(getApplicationContext(), AndiCarNotification.NOTIFICATION_TYPE_NOT_REPORTABLE_ERROR,
                            (int) System.currentTimeMillis(), getString(R.string.pref_category_secure_backup), errorMsg, null, null);
                    if (debugLogFileWriter != null) {
                        debugLogFileWriter.appendnl("Error message: ").append(errorMsg);
                        debugLogFileWriter.flush();
                    }
                }
                else {
                    AndiCarNotification.showGeneralNotification(getApplicationContext(), AndiCarNotification.NOTIFICATION_TYPE_NOT_REPORTABLE_ERROR,
                            (int) System.currentTimeMillis(), getString(R.string.pref_category_secure_backup), getString(R.string.error_sorry), null, null);
                    if (debugLogFileWriter != null) {
                        debugLogFileWriter.appendnl("Unknown error.");
                        debugLogFileWriter.flush();
                    }
                }
            }

            if (debugLogFileWriter != null) {
                debugLogFileWriter.appendnl("Removing temporary files");
                debugLogFileWriter.flush();
            }
            //remove temporary zipped file(s)
            removeTemporaryFiles();

            //stop the service
            if (debugLogFileWriter != null) {
                debugLogFileWriter.appendnl("onCanceled ended");
                debugLogFileWriter.flush();
            }
        }
        catch (Exception e1) {
            try {
                if (debugLogFileWriter != null) {
                    debugLogFileWriter.append("\n").append("====Exception Catches on onAndiCarTaskCancelled() method====");
                    debugLogFileWriter.append("\n").append("====Stack Trace====");
                    debugLogFileWriter.append("\n").append(Utils.getStackTrace(e1));
                    debugLogFileWriter.append("\n").append("=======End Stack Trace=======");
                    debugLogFileWriter.flush();
                }
            }
            catch (IOException e2) {
                e2.printStackTrace();
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void removeTemporaryFiles() {
        File tmpFile;
        if (zippedBk != null) {
            tmpFile = new File(zippedBk);
            if (tmpFile.exists()) {
                tmpFile.delete();
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        try {
            if (debugLogFileWriter != null)
                debugLogFileWriter.appendnl("Connected ...");

            new GDriveUploader(getApplicationContext(), mGoogleApiClient, mPreferences.getString(getString(R.string.pref_key_secure_backup_gdrive_folder_id), ""),
                    mFileToSend.get(0), this).startUpload();
        } catch (Exception e) {
            try {
                if (debugLogFileWriter != null) {
                    debugLogFileWriter.append("\n").append("====Exception Catches on onConnected() method====");
                    debugLogFileWriter.append("\n").append("====Stack Trace====");
                    debugLogFileWriter.append("\n").append(Utils.getStackTrace(e));
                    debugLogFileWriter.append("\n").append("=======End Stack Trace=======");
                    debugLogFileWriter.flush();
                }
            } catch (IOException e2) {
                e2.printStackTrace();
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        try {
            if (debugLogFileWriter != null) {
                debugLogFileWriter.append("\n").append("Connection suspended");
                debugLogFileWriter.flush();
            }
        } catch (IOException e2) {
            e2.printStackTrace();
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        try {
            String message = getString(R.string.error_056);
            if (connectionResult.getErrorMessage() != null && connectionResult.getErrorMessage().length() > 0)
                message = connectionResult.getErrorMessage();
            else if (connectionResult.getErrorCode() == ConnectionResult.SIGN_IN_REQUIRED) {
                message = getString(R.string.error_121);
            }
            else if (connectionResult.getErrorCode() == ConnectionResult.SIGN_IN_FAILED) {
                message = getString(R.string.error_122);
            }

            AndiCarNotification.showGeneralNotification(this, AndiCarNotification.NOTIFICATION_TYPE_NOT_REPORTABLE_ERROR,
                    (int) System.currentTimeMillis(), getString(R.string.pref_category_secure_backup), message, null, null);


            if (debugLogFileWriter != null) {
                debugLogFileWriter.appendnl("Connection failed: ").append(message);
                debugLogFileWriter.flush();
            }
        } catch (IOException e2) {
            e2.printStackTrace();
        }
    }
}
