package de.emdete.biikuta;

import android.util.Log;

public class U implements Constants {
	public static void info(String message) {
		if (BuildConfig.DEBUG && message != null)
			Log.i(TAG, message);
	}

	public static void info(String message, Throwable e) {
		if (BuildConfig.DEBUG && message != null)
			Log.i(TAG, message, e);
	}
}
