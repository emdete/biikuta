package de.emdete.biikuta;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.Date;
import java.io.File;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import de.emdete.thinstore.StoreObject;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;

public final class DeviceControlActivity extends Activity implements Constants {
	private BluetoothAdapter btAdapter;
	private boolean pendingRequestEnableBt = false;
	private DeviceConnector connector;
	private BluetoothResponseHandler mHandler;
	Measure left_measure;
	Measure right_measure;
	private String deviceName;
	private SQLiteOpenHelper dbHelper;
	private static class Measurement extends StoreObject {
		double tick;
		double tock;
		double temperature;
		double left_force;
		double right_force;
	}
	double left_force_max = 1;
	double right_force_max = 1;
	private static final char FS = '\t';

	public static String printHex(String hex) {
		StringBuilder sb = new StringBuilder();
		int len = hex.length();
		try {
			for (int i = 0; i < len; i += 2) {
				sb.append("0x").append(hex.substring(i, i + 2)).append(" ");
			}
		}
		catch (NumberFormatException e) {
			U.info("printHex NumberFormatException: " + e.getMessage());
		}
		catch (StringIndexOutOfBoundsException e) {
			U.info("printHex StringIndexOutOfBoundsException: " + e.getMessage());
		}
		return sb.toString();
	}

	public static byte[] toHex(String hex) {
		int len = hex.length();
		byte[] result = new byte[len];
		try {
			int index = 0;
			for (int i = 0; i < len; i += 2) {
				result[index] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
				index++;
			}
		}
		catch (NumberFormatException e) {
			U.info("toHex NumberFormatException: " + e.getMessage());
		}
		catch (StringIndexOutOfBoundsException e) {
			U.info("toHex StringIndexOutOfBoundsException: " + e.getMessage());
		}
		return result;
	}

	public static byte[] concat(byte[] A, byte[] B) {
		byte[] C = new byte[A.length + B.length];
		System.arraycopy(A, 0, C, 0, A.length);
		System.arraycopy(B, 0, C, A.length, B.length);
		return C;
	}

	public static int mod(int x, int y) {
		int result = x % y;
		return result < 0 ? result + y : result;
	}

	public static String calcModulo256(String command) {
		int crc = 0;
		for (int i = 0; i < command.length(); i++) {
			crc += (int) command.charAt(i);
		}
		return Integer.toHexString(mod(crc, 256));
	}

	public static String mark(String text, String color) {
		return "<font color=" + color + ">" + text + "</font>";
	}

	public static String getPrefence(Context context, String item) {
		final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
		return settings.getString(item, Constants.TAG);
	}

	public static boolean getBooleanPrefence(Context context, String tag) {
		final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
		return settings.getBoolean(tag, true);
	}

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			@Override public void uncaughtException(Thread thread, Throwable e) {
				U.info("error e=" + e, e);
				finish();
			}
		});
		dbHelper = new SQLiteOpenHelper(getBaseContext(), "biikuta", null, 1) {
			@Override public void onCreate(SQLiteDatabase db) {
				U.info("create=" + StoreObject.create(db, Measurement.class));
			}
			@Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			}
		};
		getActionBar().setHomeButtonEnabled(false);
		if (savedInstanceState != null) {
			pendingRequestEnableBt = savedInstanceState.getBoolean(SAVED_PENDING_REQUEST_ENABLE_BT);
		}
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		if (btAdapter == null) {
			final String no_bluetooth = getString(R.string.no_bt_support);
			showAlertDialog(no_bluetooth);
			U.info(no_bluetooth);
		}
		PreferenceManager.setDefaultValues(this, R.xml.settings_activity, false);
		if (mHandler == null)
			mHandler = new BluetoothResponseHandler(this);
		else
			mHandler.setTarget(this);
		setContentView(R.layout.activity_terminal);
		left_measure = (Measure)findViewById(R.id.left_measure);
		right_measure = (Measure)findViewById(R.id.right_measure);
		if (isConnected() && (savedInstanceState != null)) {
			setDeviceName(savedInstanceState.getString(DEVICE_NAME));
		}
		else
			getActionBar().setSubtitle(getString(R.string.msg_not_connected));
	}

	@Override protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean(SAVED_PENDING_REQUEST_ENABLE_BT, pendingRequestEnableBt);
		outState.putString(DEVICE_NAME, deviceName);
	}

	private boolean isConnected() {
		return connector != null && connector.getState() == R.id.STATE_CONNECTED;
	}

	private void stopConnection() {
		if (connector != null) {
			connector.stop();
			connector = null;
			setDeviceName("");
		}
	}

	private void startConnection() {
		stopConnection();
		startActivityForResult(new Intent(this, DeviceListActivity.class), R.id.REQUEST_CONNECT_DEVICE);
	}

	@Override public boolean onSearchRequested() {
		if (isAdapterReady())
			startConnection();
		return false;
	}

	@Override public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.device_control_activity, menu);
		final MenuItem bluetooth = menu.findItem(R.id.menu_search);
		if (bluetooth != null) {
			bluetooth.setIcon(this.isConnected() ?
				R.drawable.ic_action_device_bluetooth_connected :
				R.drawable.ic_action_device_bluetooth);
		}
		return true;
	}

	@Override public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_search:
				if (isAdapterReady()) {
					if (isConnected())
						stopConnection();
					else
						startConnection();
				}
				else {
					startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), R.id.REQUEST_ENABLE_BT);
				}
				return true;
			case R.id.menu_clear:
				exportAndClear();
				return true;
			case R.id.menu_send: {
				final Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, ""); // TODO put in params
				startActivity(Intent.createChooser(intent, getString(R.string.menu_send)));
				return true;
				}
			case R.id.menu_settings: {
				final Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				return true;
				}
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override public void onStart() {
		super.onStart();
		if (btAdapter == null)
			return;
		if (!btAdapter.isEnabled() && !pendingRequestEnableBt) {
			pendingRequestEnableBt = true;
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, R.id.REQUEST_ENABLE_BT);
		}
		final String mode = getPrefence(this, getString(R.string.pref_commands_mode));
	}

	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
			case R.id.REQUEST_CONNECT_DEVICE:
				if (resultCode == Activity.RESULT_OK) {
					String address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
					BluetoothDevice device = btAdapter.getRemoteDevice(address);
					if (isAdapterReady() && (connector == null))
						setupConnector(device);
				}
				break;
			case R.id.REQUEST_ENABLE_BT:
				pendingRequestEnableBt = false;
				if (resultCode != Activity.RESULT_OK) {
					U.info("BT not enabled");
				}
				break;
		}
	}

	private void setupConnector(BluetoothDevice connectedDevice) {
		stopConnection();
		try {
			String emptyName = getString(R.string.empty_device_name);
			DeviceData data = new DeviceData(connectedDevice, emptyName);
			connector = new DeviceConnector(data, mHandler);
			connector.connect();
		}
		catch (IllegalArgumentException e) {
			U.info("setupConnector failed: " + e.getMessage());
		}
	}

	public void sendMessage(byte[] command) {
		if (isConnected()) {
			connector.write(command);
		}
	}

	void receivedMessage(String message) {
		U.info("receivedMessage: " + message);
		String[] values = message.trim().split("\t");
		if (values.length >= 5) {
			Measurement val = new Measurement();
			try {
				val.tick = Double.parseDouble(values[0]);
				val.tock = Double.parseDouble(values[1]);
				val.temperature = Double.parseDouble(values[2]);
				val.left_force = Double.parseDouble(values[3]);
				val.right_force = Double.parseDouble(values[4]);
				U.info("receivedMessage: "
					+ ", tick=" + val.tick
					+ ", tock=" + val.tock
					+ ", temperature=" + val.temperature
					+ ", left_force=" + val.left_force
					+ ", right_force=" + val.right_force
					);
				if (val.left_force < 0)
					val.left_force = -val.left_force;
				if (val.right_force < 0)
					val.right_force = -val.right_force;
				if (left_force_max < val.left_force)
					left_force_max = val.left_force;
				if (right_force_max < val.right_force)
					right_force_max = val.right_force;
				left_measure.setPercentage(val.left_force / left_force_max * 100);
				right_measure.setPercentage(val.right_force / right_force_max * 100);
			}
			catch (NumberFormatException e) {
				left_measure.setFault();
				right_measure.setFault();
				U.info("receivedMessage: e=" + e, e);
				val = null;
			}
			if (val != null) {
				try {
					SQLiteDatabase db = dbHelper.getWritableDatabase();
					val.insert(db);
				}
				catch (Exception e) {
					U.info("receivedMessage: on insert() e=" + e, e);
				}
			}
		}
		else {
			U.info("receivedMessage: less than 5 elements length=" + values.length);
		}
	}

	void setDeviceName(String deviceName) {
		this.deviceName = deviceName;
		getActionBar().setSubtitle(deviceName);
	}

	private class BluetoothResponseHandler extends Handler {
		private WeakReference<DeviceControlActivity> mActivity;

		public BluetoothResponseHandler(DeviceControlActivity activity) {
			mActivity = new WeakReference<DeviceControlActivity>(activity);
		}

		public void setTarget(DeviceControlActivity target) {
			mActivity.clear();
			mActivity = new WeakReference<DeviceControlActivity>(target);
		}

		@Override public void handleMessage(Message msg) {
			DeviceControlActivity activity = mActivity.get();
			if (activity != null) {
				switch (msg.what) {
					case R.id.MESSAGE_STATE_CHANGE:
						U.info("MESSAGE_STATE_CHANGE: " + msg.arg1);
						final ActionBar bar = activity.getActionBar();
						switch (msg.arg1) {
							case R.id.STATE_CONNECTED:
								bar.setSubtitle(getString(R.string.msg_connected));
								break;
							case R.id.STATE_CONNECTING:
								bar.setSubtitle(getString(R.string.msg_connecting));
								break;
							case R.id.STATE_NONE:
								bar.setSubtitle(getString(R.string.msg_not_connected));
								break;
						}
						activity.invalidateOptionsMenu();
						break;
					case R.id.MESSAGE_READ:
						final String readMessage = (String) msg.obj;
						if (readMessage != null) {
							activity.receivedMessage(readMessage);
						}
						break;
					case R.id.MESSAGE_DEVICE_NAME:
						activity.setDeviceName((String) msg.obj);
						break;
					case R.id.MESSAGE_WRITE:
						break;
					case R.id.MESSAGE_TOAST:
						break;
				}
			}
		}
	}

	@Override public synchronized void onResume() {
		super.onResume();
	}

	@Override public synchronized void onPause() {
		super.onPause();
	}

	boolean isAdapterReady() {
		return btAdapter != null && btAdapter.isEnabled();
	}

	void showAlertDialog(String message) {
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
		alertDialogBuilder.setTitle(getString(R.string.app_name));
		alertDialogBuilder.setMessage(message);
		AlertDialog alertDialog = alertDialogBuilder.create();
		alertDialog.show();
	}

	void exportAndClear() {
		try {
			SQLiteDatabase db = dbHelper.getWritableDatabase();
			PrintWriter out = new PrintWriter(new BufferedOutputStream(getApplicationContext().openFileOutput("measures.tsv",
				Context.MODE_WORLD_READABLE|Context.MODE_WORLD_WRITEABLE|Context.MODE_APPEND)));
			out.print("tick");
			out.print(FS);
			out.print("tock");
			out.print(FS);
			out.print("temperature");
			out.print(FS);
			out.print("left_force");
			out.print(FS);
			out.print("right_force");
			out.println();
			for (StoreObject item: StoreObject.select(db, Measurement.class)) {
				Measurement val = (Measurement)item;
				out.print(val.tick);
				out.print(FS);
				out.print(val.tock);
				out.print(FS);
				out.print(val.temperature);
				out.print(FS);
				out.print(val.left_force);
				out.print(FS);
				out.print(val.right_force);
				out.println();
				val.delete(db);
			}
			out.println();
		}
		catch (Exception e) {
			U.info("exception: e=" + e, e);
		}
	}
}