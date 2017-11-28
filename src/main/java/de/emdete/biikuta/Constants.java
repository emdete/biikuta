package de.emdete.biikuta;

import java.text.SimpleDateFormat;

public interface Constants {
	static final SimpleDateFormat timeformat = new SimpleDateFormat("HH:mm:ss.SSS");
	static final String CRC_BAD = "#FF0000";
	static final String CRC_OK = "#FFFF00";
	static final String DEVICE_NAME = "DEVICE_NAME";
	static final String EXTRA_DEVICE_ADDRESS = "device_address";
	static final String LOG = "LOG";
	static final String SAVED_PENDING_REQUEST_ENABLE_BT = "PENDING_REQUEST_ENABLE_BT";
	static final String TAG = "biikuta";
}
