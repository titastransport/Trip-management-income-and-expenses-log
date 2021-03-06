/*
 * AndiCar
 *
 *  Copyright (c) 2017 Miklos Keresztes (miklos.keresztes@gmail.com)
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

package andicar.n.utils.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import org.andicar2.activity.R;

import andicar.n.activity.dialogs.GeneralNotificationDialogActivity;
import andicar.n.utils.Utils;

/**
 * Created by Miklos Keresztes on 9/6/16.
 * <p>
 * Helper class for AndiCarNotification.Builder
 */
class GeneralNotificationBuilder extends Notification.Builder {

    private static final String NOTIFICATION_CHANEL_GENERAL_ID = "generalNotifications";
    private static final CharSequence NOTIFICATION_CHANEL_GENERAL_NAME = "AndiCar general notifications";
    /**
     * @param context           context
     * @param notificationType  : see AndiCarNotification class constants
     *                          ERROR (NOTIFICATION_TYPE_NOT_REPORTABLE_ERROR or NOTIFICATION_TYPE_REPORTABLE_ERROR)
     *                          WARNING (NOTIFICATION_TYPE_WARNING)
     *                          INFO (NOTIFICATION_TYPE_INFO)
     * @param requestCode       request code for PendingIntent.getActivity
     * @param contentTitle      notification title
     * @param contentText       notification text
     * @param resultIntentClass result intent for this setContentIntent
     */
    GeneralNotificationBuilder(Context context, NotificationManager notificationManager, int notificationType, int requestCode, String contentTitle, String contentText,
                               Class resultIntentClass, Exception exception) {
        super(context);

        Intent resultIntent;
        if (resultIntentClass != null) {
            resultIntent = new Intent(context, resultIntentClass);
        }
        else {
            resultIntent = new Intent(context, GeneralNotificationDialogActivity.class);
            resultIntent.putExtra(GeneralNotificationDialogActivity.NOTIFICATION_MESSAGE_KEY, contentTitle);
            resultIntent.putExtra(GeneralNotificationDialogActivity.NOTIFICATION_DETAIL_KEY, contentText);
            resultIntent.putExtra(GeneralNotificationDialogActivity.NOTIFICATION_DATETIME, System.currentTimeMillis());
            if (exception != null) {
                resultIntent.putExtra(GeneralNotificationDialogActivity.NOTIFICATION_EXCEPTION_STRING_KEY, Utils.getStackTrace(exception));
            }
            resultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        switch (notificationType) {
            case AndiCarNotification.NOTIFICATION_TYPE_NOT_REPORTABLE_ERROR:
                this.setSmallIcon(R.drawable.ic_notif_andicar_error);
                resultIntent.putExtra(GeneralNotificationDialogActivity.DIALOG_TYPE_KEY, GeneralNotificationDialogActivity.DIALOG_TYPE_NOT_REPORTABLE_ERROR);
                break;
            case AndiCarNotification.NOTIFICATION_TYPE_REPORTABLE_ERROR:
                this.setSmallIcon(R.drawable.ic_notif_andicar_error);
                resultIntent.putExtra(GeneralNotificationDialogActivity.DIALOG_TYPE_KEY, GeneralNotificationDialogActivity.DIALOG_TYPE_REPORTABLE_ERROR);
                break;
            case AndiCarNotification.NOTIFICATION_TYPE_WARNING:
                this.setSmallIcon(R.drawable.ic_notif_andicar_error);
                resultIntent.putExtra(GeneralNotificationDialogActivity.DIALOG_TYPE_KEY, GeneralNotificationDialogActivity.DIALOG_TYPE_WARNING);
                break;
            case AndiCarNotification.NOTIFICATION_TYPE_INFO:
                this.setSmallIcon(R.drawable.ic_notif_andicar_info);
                resultIntent.putExtra(GeneralNotificationDialogActivity.DIALOG_TYPE_KEY, GeneralNotificationDialogActivity.DIALOG_TYPE_INFO);
                break;
            default:
                throw new UnsupportedOperationException("Unknown notification type");
        }

        this.setContentTitle(contentTitle);
        this.setContentText(contentText);
        this.setAutoCancel(true);
        //noinspection deprecation
        this.setDefaults(Notification.DEFAULT_ALL);
        this.setStyle(new Notification.BigTextStyle().bigText(contentText));
        this.setSubText(Utils.getFormattedDateTime(System.currentTimeMillis(), false));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANEL_GENERAL_ID, NOTIFICATION_CHANEL_GENERAL_NAME, NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.setDescription(NOTIFICATION_CHANEL_GENERAL_NAME.toString());
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.enableVibration(true);
            notificationManager.createNotificationChannel(notificationChannel);
            this.setChannelId(notificationChannel.getId());
        }

        PendingIntent resultPendingIntent = PendingIntent.getActivity(context, requestCode, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        this.setContentIntent(resultPendingIntent);
    }
}
