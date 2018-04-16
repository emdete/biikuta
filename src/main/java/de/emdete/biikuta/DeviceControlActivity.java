package de.emdete.biikuta;

import android.os.Vibrator;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import de.emdete.thinstore.StoreObject;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.Set;

public final class DeviceControlActivity extends Activity implements Constants {
	private BluetoothAdapter btAdapter = null;
	private BluetoothResponseHandler mHandler = null;
	private DeviceConnector connector = null;
	private Measure left_measure = null;
	private Measure right_measure = null;
	private Menu menu = null;
	private SQLiteOpenHelper dbHelper = null;
	private String deviceName = null;
	private boolean pendingRequestEnableBt = false;
	private double left_force_max = 3000;
	private double right_force_max = 3000;
	private Vibrator vibrator = null;
	private ToneGenerator toneGenerator = null;
	private long lastBeep = -1l;
	private int beepPoints = 0;

	public static class Measurement extends StoreObject {
		long timestamp;
		double tick;
		double tock;
		double temperature;
		double left_force;
		double right_force;

		public Measurement() throws Exception {
		}

		public Measurement(String message) throws Exception {
			String[] values = message.trim().split("\t");
			if (values.length < 5) {
				throw new Exception("Measurement: less than 5 elements length=" + values.length);
			}
			this.timestamp = System.currentTimeMillis();
			this.tick = Double.parseDouble(values[0]);
			this.tock = Double.parseDouble(values[1]);
			this.temperature = Double.parseDouble(values[2]);
			this.left_force = Double.parseDouble(values[3]);
			this.right_force = Double.parseDouble(values[4]);
			//U.info("Measurement: " + this);
			if (this.left_force < 0) {
				this.left_force = -this.left_force;
			}
			if (this.right_force < 0) {
				this.right_force = -this.right_force;
			}
		}

		public String toString() {
			return "Measurement"
				+ ", timestamp=" + FULL_ISO_DATE_FORMAT.format(new Date(this.timestamp))
				+ ", tick=" + this.tick
				+ ", tock=" + this.tock
				+ ", temperature=" + this.temperature
				+ ", left_force=" + this.left_force
				+ ", right_force=" + this.right_force
				;
		}

		static void exportAndClear(final SQLiteDatabase db, final PrintWriter out) throws Exception {
			long start = System.currentTimeMillis();
			long count = 0;
			out.println(
				"timestamp" + FS +
				"tick" + FS +
				"tock" + FS +
				"temperature" + FS +
				"left_force" + FS +
				"right_force");
			for (StoreObject item: StoreObject.select(db, Measurement.class)) {
				final Measurement val = (Measurement)item;
				out.println(
					FULL_ISO_DATE_FORMAT.format(new Date(val.timestamp)) + FS +
					val.tick + FS +
					val.tock + FS +
					val.temperature + FS +
					val.left_force + FS +
					val.right_force);
				// exportAndClear count=744, millis=8992
				//val.delete(db);
				count++;
			}
			// exportAndClear count=560, millis=358
			new Measurement().truncate(db);
			U.info("exportAndClear count=" + count + ", millis=" + (System.currentTimeMillis() - start));
		}
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
			String no_bluetooth = getString(R.string.no_bt_support);
			showAlertDialog(no_bluetooth);
			U.info(no_bluetooth);
		}
		PreferenceManager.setDefaultValues(this, R.xml.settings_activity, false);
		if (mHandler == null)
			mHandler = new BluetoothResponseHandler(this);
		else
			mHandler.setTarget(this);
		setContentView(R.layout.main);
		left_measure = (Measure)findViewById(R.id.left_measure);
		right_measure = (Measure)findViewById(R.id.right_measure);
		if (isConnected() && savedInstanceState != null) {
			setDeviceName(savedInstanceState.getString(DEVICE_NAME));
		}
		else {
			getActionBar().setSubtitle(getString(R.string.msg_not_connected));
		}
		vibrator = (Vibrator)getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
		toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME);
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
		left_measure.setPercentage(0);
		right_measure.setPercentage(0);
		String address = null;
		int count = 0;
		BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
		Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
		U.info("search for an HC-06");
		if (pairedDevices != null && !pairedDevices.isEmpty()) {
			for (BluetoothDevice device : pairedDevices) {
				if ("HC-06".equals(device.getName())) {
					address = device.getAddress();
					count++;
				}
			}
		}
		if (address != null && count == 1) {
			U.info("found an HC-06 at address=" + address);
			connectToDevice(address);
		}
		else {
			startActivityForResult(new Intent(this, DeviceListActivity.class), R.id.REQUEST_CONNECT_DEVICE);
		}
	}

	@Override public boolean onSearchRequested() {
		if (isAdapterReady())
			startConnection();
		return false;
	}

	@Override public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.device_control_activity, menu);
		final MenuItem bluetooth = menu.findItem(R.id.menu_start);
		this.menu = menu;
		if (bluetooth != null) {
			if (isConnected()) {
				bluetooth.setIcon(R.drawable.ic_action_device_bluetooth_connected);
				menu.findItem(R.id.menu_start).setTitle(R.string.action_bluetooth_off);
			}
			else {
				bluetooth.setIcon(R.drawable.ic_action_device_bluetooth);
				menu.findItem(R.id.menu_start).setTitle(R.string.action_bluetooth);
			}
		}
		return true;
	}

	@Override public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_start: {
				if (isAdapterReady()) {
					if (isConnected()) {
						stopConnection();
					}
					else {
						startConnection();
					}
				}
				else {
					startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), R.id.REQUEST_ENABLE_BT);
				}
				return true;
			}
			case R.id.menu_export: {
				try {
					File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
					path.mkdirs();
					final File file = new File(path, FULL_ISO_DATE_FORMAT.format(new java.util.Date(System.currentTimeMillis())) + ".tsv");
					U.info("onOptionsItemSelected.menu_export: file=" + file);
					new Thread() {
						public void run() {
							try {
								PrintWriter out = new PrintWriter(new BufferedOutputStream(new FileOutputStream(file)));
								try {
									Measurement.exportAndClear(dbHelper.getWritableDatabase(), out);
								}
								finally {
									out.close();
								}
								DeviceControlActivity.this.runOnUiThread(new Runnable() {
									public void run() {
										showAlertDialog("Exported to " + file);
									}
								});
							}
							catch (Exception e) {
								U.info("exception: e=" + e, e);
							}
						}
					}.start();
				}
				catch (Exception e) {
					U.info("exception: e=" + e, e);
				}
				return true;
			}
			case R.id.menu_send: {
				final Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, "current max: "
					+ "left_force_max=" + left_force_max/1000 + "kg, "
					+ "right_force_max=" + right_force_max/1000 + "kg"
					);
				startActivity(Intent.createChooser(intent, getString(R.string.menu_send)));
				return true;
			}
			case R.id.menu_settings: {
				final Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				return true;
			}
			default: {
				return super.onOptionsItemSelected(item);
			}
		}
	}

	@Override public void onStart() {
		super.onStart();
		if (btAdapter != null && !btAdapter.isEnabled() && !pendingRequestEnableBt) {
			pendingRequestEnableBt = true;
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, R.id.REQUEST_ENABLE_BT);
		}
	}

	private void connectToDevice(String address) {
		BluetoothDevice device = btAdapter.getRemoteDevice(address);
		if (isAdapterReady() && connector == null) {
			setupConnector(device);
		}
	}

	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
			case R.id.REQUEST_CONNECT_DEVICE:
				if (resultCode == Activity.RESULT_OK) {
					String address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
					connectToDevice(address);
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
		//U.info("receivedMessage: " + message);
		try {
			Measurement val = new Measurement(message);
			if (left_force_max < val.left_force)
				left_force_max = val.left_force;
			if (right_force_max < val.right_force)
				right_force_max = val.right_force;
			left_measure.setPercentage(val.left_force / left_force_max * 100);
			right_measure.setPercentage(val.right_force / right_force_max * 100);
			playSound(Math.max(val.left_force, val.right_force));
			val.insert(dbHelper.getWritableDatabase());
		}
		catch (Exception e) {
			left_measure.setFault();
			right_measure.setFault();
			U.info("receivedMessage: e=" + e, e);
		}
	}

	void setDeviceName(String deviceName) {
		this.deviceName = deviceName;
		//getActionBar().setSubtitle( deviceName);
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
						final ActionBar bar = activity.getActionBar();
						switch (msg.arg1) {
							case R.id.STATE_CONNECTED:
								U.info("MESSAGE_STATE_CHANGE: STATE_CONNECTED");
								bar.setSubtitle(getString(R.string.msg_connected));
								break;
							case R.id.STATE_CONNECTING:
								U.info("MESSAGE_STATE_CHANGE: STATE_CONNECTING");
								bar.setSubtitle(getString(R.string.msg_connecting));
								break;
							case R.id.STATE_NONE:
								U.info("MESSAGE_STATE_CHANGE: STATE_NONE");
								bar.setSubtitle(getString(R.string.msg_not_connected));
								left_measure.setFault();
								right_measure.setFault();
								break;
							default:
								U.info("unknown MESSAGE_STATE_CHANGE: " + msg.arg1);
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
						U.info("MESSAGE_WRITE: message=" + msg);
						break;
					case R.id.MESSAGE_TOAST:
						U.info("MESSAGE_TOAST: message=" + msg);
						break;
					default:
						U.info("unknown message=" + msg.what);
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

	private boolean enable = true;
	private double minimum =1200.;
	private double divisor = 800.;
	private int calmdown = 4;
	private int alarm = 8;
	private int alert = 10;
	private long silence = 800;
	private void playSound(double max) {
		try {
			// beep point calc
			if (max > minimum) {
				beepPoints += (int)((max - minimum) / divisor);
			}
			else {
				beepPoints -= calmdown;
			}
			if (beepPoints < 0) {
				beepPoints = 0;
			}
			if (beepPoints >= alarm) {
				long lastBeep = System.currentTimeMillis();
				if (lastBeep - this.lastBeep > silence) { // dont beep too often
					vibrator.vibrate(400);
					toneGenerator.startTone(
						beepPoints <= alert?
						ToneGenerator.TONE_PROP_ACK:
						ToneGenerator.TONE_SUP_ERROR
						, 400
						//no:
						//ToneGenerator.TONE_PROP_PROMPT
						//ToneGenerator.TONE_PROP_NACK
						//ToneGenerator.TONE_PROP_BEEP
						//ToneGenerator.TONE_PROP_BEEP2
						//ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE
						);
					U.info("sound played");
					this.lastBeep = lastBeep;
				}
				beepPoints = 0;
			}
		}
		catch (Exception e) {
			U.info("exception: e=" + e, e);
		}
	}
}
