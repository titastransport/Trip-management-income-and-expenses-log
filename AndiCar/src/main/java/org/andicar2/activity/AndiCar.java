/*
 * AndiCar
 *
 *  Copyright (c) 2016 Miklos Keresztes (miklos.keresztes@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.andicar2.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.multidex.MultiDexApplication;
import android.support.v4.content.ContextCompat;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;

import java.util.Calendar;

import andicar.n.utils.ConstantValues;
import io.fabric.sdk.android.Fabric;

public class AndiCar extends MultiDexApplication {
    private static Resources appResources;
    private static SharedPreferences appPreferences;
    @SuppressLint("StaticFieldLeak")
    private static Context mCtx;

    public static Resources getAppResources() {
        return appResources;
    }

    public static SharedPreferences getDefaultSharedPreferences() {
        return appPreferences;
    }

    public static Context getAppContext() {
        return mCtx;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Set up Crashlytics, disabled for debug builds
        Crashlytics crashlyticsKit = new Crashlytics.Builder()
                .core(new CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build()).build();
        Fabric.with(this, crashlyticsKit);

        AndiCar.appResources = getResources();
        AndiCar.appPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        AndiCar.mCtx = getApplicationContext();

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            ConstantValues.BASE_FOLDER = Environment.getExternalStorageDirectory().getAbsolutePath() + "/andicar";
        }
        else {
            ConstantValues.BASE_FOLDER = getApplicationContext().getApplicationInfo().dataDir + "/andicar";
        }

        ConstantValues.REPORT_FOLDER = ConstantValues.BASE_FOLDER + "/" + ConstantValues.REPORT_FOLDER_NAME + "/";
        ConstantValues.BACKUP_FOLDER = ConstantValues.BASE_FOLDER + "/" + ConstantValues.BACKUP_FOLDER_NAME + "/";
        ConstantValues.TRACK_FOLDER = ConstantValues.BASE_FOLDER + "/" + ConstantValues.TRACK_FOLDER_NAME + "/";
        ConstantValues.TEMP_FOLDER = ConstantValues.BASE_FOLDER + "/" + ConstantValues.TEMP_FOLDER_NAME + "/";
        ConstantValues.LOG_FOLDER = ConstantValues.BASE_FOLDER + "/" + ConstantValues.LOG_FOLDER_NAME + "/";

        initPreferences();

    }

    @SuppressLint("WrongConstant")
    private void initPreferences() {
        //set up the backup service
        if (!appPreferences.contains(appResources.getString(R.string.pref_key_backup_service_exec_hour))) {
            boolean isActive = true;

            if (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                isActive = false;
            }
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(946753200778L);

            SharedPreferences.Editor editor = appPreferences.edit();
            editor.putInt(getString(R.string.pref_key_backup_service_exec_hour), cal.get(Calendar.HOUR_OF_DAY));
            editor.putInt(getString(R.string.pref_key_backup_service_exec_minute), cal.get(Calendar.MINUTE));
            editor.putString(getString(R.string.pref_key_backup_service_schedule_type), ConstantValues.BACKUP_SERVICE_DAILY);
            editor.putInt(getString(R.string.pref_key_backup_service_keep_last_backups_no), 3);
            editor.putBoolean(getString(R.string.pref_key_backup_service_enabled), isActive);
            editor.putString(getString(R.string.pref_key_backup_service_backup_days), "1111111");
            editor.putBoolean(getString(R.string.pref_key_backup_service_show_notification), true);
            editor.apply();
        }
    }
}
