#include "HX711.h" // https://github.com/bogde/HX711
#include <SoftwareSerial.h> // https://www.arduino.cc/en/Reference/SoftwareSerial

// create a serial port/UART
SoftwareSerial BT(11, 3); // to hc-06; pin receive, transmit
HX711 left_load_cell;
HX711 right_load_cell;
static const unsigned long min_wait = 3L;
static const unsigned long frequency = 5L; // every 200ms
static float scale = -34.f; // roughly grams
static const char separator = '\t';
static unsigned long tick = 0;

float calibrate_scale(const char* name, HX711 cell) {
	int tries = 0;
	do {
		cell.set_scale(1.f);
		scale = cell.get_units(10) / 1500.f;
		Serial.print("calibrate ");
		Serial.print(name);
		Serial.print(" scale=");
		Serial.println(scale);
	} while (++tries < 1);
	cell.set_scale(scale);
	return scale;
}

float calibrate_tare(const char* name, HX711 cell) {
	float offset = 0;
	cell.set_offset(offset);
	cell.tare();
	Serial.print("tare ");
	Serial.print(name);
	Serial.print(" offset=");
	offset = cell.get_offset();
	Serial.println(offset);
	return offset;
}

void setup() {
	// init serial: ////////////////////////////////////////////////////////////////////////////////
	Serial.begin(38400);
	while (!Serial) {
		delay(min_wait); // wait to settle down
		// wait for serial port to connect. Needed for native USB port only
		yield();
	}
	Serial.println("init BT:"); /////////////////////////////////////////////////////////////////////
	BT.begin(9600); //
	Serial.println("init temp:"); ///////////////////////////////////////////////////////////////////
	//analogReference(INTERNAL); // set 1.1v reference
	Serial.println("init force 1:"); ///////////////////////////////////////////////////////////////////
	left_load_cell.begin(9, 10); // dout, pd_sck; HX711 1 (left)
	left_load_cell.set_scale(scale); // this value is obtained by calibrating the scale with known weights; see the README for details
	left_load_cell.tare(); // set the scale
	//left_load_cell.power_down(); // put the ADC in sleep mode
	Serial.println("init force 2:"); /////////////////////////////////////////////////////////////////
	right_load_cell.begin(5, 6); // dout, pd_sck; HX711 2 (right)
	right_load_cell.set_scale(scale); // this value is obtained by calibrating the scale with known weights; see the README for details
	right_load_cell.tare(); // set the scale
	//right_load_cell.power_down(); // put the ADC in sleep mode
	Serial.println("init led 13:"); ///////////////////////////////////////////////////////////////////
	//pinMode(13, OUTPUT); // set pin 13 (default installed led) as an output
	Serial.println("init done"); //////////////////////////////////////////////////////////////////////
	BT.println();
	BT.println("tick;tock;temp;left;right");
	tick = millis();
}

void loop() {
	//float t1 = calibrate_tare("left", left_load_cell);
	//float t2 = calibrate_tare("right", right_load_cell);
	//float s1 = calibrate_scale("left", left_load_cell);
	//float s2 = calibrate_scale("right", right_load_cell);
	// //
	//Serial.println("tick");
	delay(1000 / frequency / 2);
	digitalWrite(13, HIGH);
	float current_millis=0, tock=millis(), temperature=0, left_force=0, right_force=0;
	// read all measures:
	current_millis = tick / 1000.0;
	temperature = analogRead(A0) / 9.31; // line where LM35 is connected, converted to celsius
	//left_load_cell.power_up();
	left_force = left_load_cell.get_units();
	//left_load_cell.power_down();
	//right_load_cell.power_up();
	right_force = right_load_cell.get_units();
	//right_load_cell.power_down();
	tock = (millis() - tock) / 1000.0;
	// output all measures
	if (1) {
		Serial.print(current_millis, 3);
		Serial.print(separator);
		Serial.print(tock, 3);
		Serial.print(separator);
		Serial.print(temperature, 2);
		Serial.print(separator);
		Serial.print(left_force, 1);
		Serial.print(separator);
		Serial.print(right_force, 1);
    Serial.print(separator);
    if (left_force != .0)
      Serial.print(right_force/left_force, 5);
		Serial.println();
	}
	if (1) {
		BT.print(current_millis, 3);
		BT.print(separator);
		BT.print(tock, 3);
		BT.print(separator);
		BT.print(temperature, 2); // line where LM35 is connected, converted to celsius
		BT.print(separator);
		BT.print(left_force, 1);
		BT.print(separator);
		BT.print(right_force, 1);
		BT.println();
		if (BT.available()) { // if text arrived in from BT serial...
			switch (BT.read()) {
				case '?':
					Serial.println("no function yet");
					BT.println("no function yet");
				break;
				default:
					Serial.println("error, use '?' for help");
					BT.println("error, use '?' for help");
				break;
			}
		}
	}
	digitalWrite(13, LOW);
	tick += 1000L / frequency - min_wait;
	while (millis() < tick) {
		delay(min_wait);
		yield();
	}
	//Serial.println("tock");
}

