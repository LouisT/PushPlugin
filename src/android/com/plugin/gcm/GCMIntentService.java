package com.plugin.gcm;


import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.media.AudioManager;

import com.google.android.gcm.GCMBaseIntentService;

@SuppressLint("NewApi")
public class GCMIntentService extends GCMBaseIntentService {

	/* Relevant only if you used theNewMr code for multiple notifications here - https://github.com/thenewmr/PushPlugin/commit/728e975442e45668b7d809aec028396a9b023dfa
	Re-Added this line to set a default NOTIFICATION_ID -- this is used for the dismissal of notifications if the notId isn't set
	Make sure the variable NOTIFICATION_ID is set like this: */
	public static final int NOTIFICATION_ID = 237;
	private static final String TAG = "GCMIntentService";

	public GCMIntentService() {
		super("GCMIntentService");
	}

	@Override
	public void onRegistered(Context context, String regId) {

		Log.v(TAG, "onRegistered: "+ regId);

		JSONObject json;

		try
		{
			json = new JSONObject().put("event", "registered");
			json.put("regid", regId);

			Log.v(TAG, "onRegistered: " + json.toString());

			// Send this JSON data to the JavaScript application above EVENT should be set to the msg type
			// In this case this is the registration ID
			PushPlugin.sendJavascript( json );

		}
		catch( JSONException e)
		{
			// No message to the user is sent, JSON failed
			Log.e(TAG, "onRegistered: JSON exception");
		}
	}

	@Override
	public void onUnregistered(Context context, String regId) {
		Log.d(TAG, "onUnregistered - regId: " + regId);
	}

	@Override
	protected void onMessage(Context context, Intent intent) {
		Log.d(TAG, "onMessage - context: " + context);

		// Extract the payload from the message
		Bundle extras = intent.getExtras();
		if (extras != null)
		{

			//Added as a way of remotely dismissing notifications
			String cancelNotificationString = extras.getString("cancelNotification");

			if (cancelNotificationString != null)
			{
				int CurrentNotificationID=NOTIFICATION_ID;
				String notIdOnMessage = extras.getString("notId");
				if (notIdOnMessage != null)
				{
					CurrentNotificationID = Integer.parseInt(extras.getString("notId"));
				}

				NotificationManager mNotificationManagerCancel = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
				mNotificationManagerCancel.cancel((String)getAppName(context), CurrentNotificationID);
			}

			else
			{
				// if we are in the foreground, just surface the payload, else post it to the statusbar
				if (PushPlugin.isInForeground())
				{
					extras.putBoolean("foreground", true);
					PushPlugin.sendExtras(extras);
				}
				else
				{
					extras.putBoolean("foreground", false);

					// Send a notification if there is a message
					if (extras.getString("message") != null && extras.getString("message").length() != 0)
					{
						createNotification(context, extras);
					}

				}
            		}
        	}
	}

	public void createNotification(Context context, Bundle extras)
	{
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		String appName = getAppName(this);

		Intent notificationIntent = new Intent(this, PushHandlerActivity.class);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		notificationIntent.putExtra("pushBundle", extras);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		//Download custom notification icon with the GCM variable 'icon'
		Bitmap myBitmap = null;
		String customIconUrl= null;
		customIconUrl=extras.getString("icon");

		  try {
		        URL url = new URL(customIconUrl);
		        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		        connection.setDoInput(true);
		        connection.connect();
		        InputStream input = connection.getInputStream();
		        myBitmap = BitmapFactory.decodeStream(input);
		    } catch (IOException e) {
		        e.printStackTrace();
		    }

		// Set the defaults for notifications
                int defaults = Notification.DEFAULT_ALL;
                if (extras.getString("defaults") != null) {
                        try {
                                defaults = Integer.parseInt(extras.getString("defaults"));
                        } catch (NumberFormatException e) {}
                }

		//Added setLargeIcon to downloaded image
		NotificationCompat.Builder mBuilder =
			new NotificationCompat.Builder(context)
 				// Disable sound notification during a call when `silentInCall` is passed, vibrate only.
				.setDefaults((extras.getString("silentInCall") != null && inActiveCall(context)?Notification.DEFAULT_VIBRATE:defaults))
				.setSmallIcon(context.getApplicationInfo().icon)
				.setLargeIcon(myBitmap)
				.setWhen(System.currentTimeMillis())
				.setContentTitle(extras.getString("title"))
				.setContentIntent(contentIntent)
				.setAutoCancel(true);

		String message = extras.getString("message");
		if (message != null) {
			mBuilder.setContentText(message);

			//Added message to ticker when message is set
			mBuilder.setTicker(extras.getString("title") + "\n" + message);

			//BigText support added so users can expand notifications
			mBuilder.setStyle(new NotificationCompat.BigTextStyle()
			 .bigText(message));
		} else {
			mBuilder.setContentText("<missing message content>");
			mBuilder.setTicker(extras.getString("title"));
		}

		String msgcnt = extras.getString("msgcnt");
		if (msgcnt != null) {
			mBuilder.setNumber(Integer.parseInt(msgcnt));
		}

		/* Relevant only if you used theNewMr code for multiple notifications here - https://github.com/thenewmr/PushPlugin/commit/728e975442e45668b7d809aec028396a9b023dfa
		Changed notId default to the value set above for NOTIFICATION_ID -- this is used for the dismissal of notifications if the notId isn't set
		Make sure the variable notId = NOTIFICATION_ID like this: */
		int notId = NOTIFICATION_ID;

		try {
			notId = Integer.parseInt(extras.getString("notId"));
		}
		catch(NumberFormatException e) {
			Log.e(TAG, "Number format exception - Error parsing Notification ID: " + e.getMessage());
		}
		catch(Exception e) {
			Log.e(TAG, "Number format exception - Error parsing Notification ID" + e.getMessage());
		}

		mNotificationManager.notify((String) appName, notId, mBuilder.build());

	}

	public static void closeAllNotifications(Context context)
        {
                NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                mNotificationManager.cancelAll();
        }

	private static String getAppName(Context context)
	{
		CharSequence appName =
				context
					.getPackageManager()
					.getApplicationLabel(context.getApplicationInfo());

		return (String)appName;
	}

	@Override
	public void onError(Context context, String errorId) {
		Log.e(TAG, "onError - errorId: " + errorId);
	}

	// Detect if there is an active call via AudioManager
	public static boolean inActiveCall(Context context) {
   		AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
 		return (manager.getMode() == AudioManager.MODE_IN_CALL);
	}
}
