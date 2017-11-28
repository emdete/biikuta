#include "HX711.h" // https://github.com/bogde/HX711
#include <SoftwareSerial.h> // https://www.arduino.cc/en/Reference/SoftwareSerial

// create a serial port/UART
SoftwareSerial BT(11, 3); // to hc-06; pin receive, transmit
HX711 LoadCell_1;
HX711 LoadCell_2;
static const unsigned long min_wait = 3L;
static const unsigned long frequency = 5L;

void setup() {
	// init serial: ////////////////////////////////////////////////////////////////////////////////
	Serial.begin(38400);
	while (!Serial) {
		delay(min_wait); // wait to settle down
		// wait for serial port to connect. Needed for native USB port only
	}
	Serial.println("init btM:"); /////////////////////////////////////////////////////////////////////
	BT.begin(9600); //
	Serial.println("init temp:"); ///////////////////////////////////////////////////////////////////
	//analogReference(INTERNAL); // set 1.1v reference
	Serial.println("init force 1:"); ///////////////////////////////////////////////////////////////////
	LoadCell_1.begin(9, 10); // dout, pd_sck; HX711 1 (left)
	LoadCell_1.set_scale(2280.f); // this value is obtained by calibrating the scale with known weights; see the README for details
	LoadCell_1.tare(); // set the scale
	//LoadCell_1.power_down(); // put the ADC in sleep mode
	Serial.println("init force 2:"); /////////////////////////////////////////////////////////////////
	LoadCell_2.begin(5, 6); // dout, pd_sck; HX711 2 (right)
	LoadCell_2.set_scale(2280.f); // this value is obtained by calibrating the scale with known weights; see the README for details
	LoadCell_2.tare(); // set the scale
	//LoadCell_2.power_down(); // put the ADC in sleep mode
	Serial.println("init led 13:"); ///////////////////////////////////////////////////////////////////
	//pinMode(13, OUTPUT); // set pin 13 (default installed led) as an output
	Serial.println("init done"); //////////////////////////////////////////////////////////////////////
	BT.println();
	BT.println("tick;tock;temp;left;right");
}

void loop() {
	unsigned long tick = millis();
	//Serial.println("tick");
	delay(1000 / frequency / 2);
	digitalWrite(13, HIGH);
	float current_millis=0, tock=millis(), temperature=0, left_force=0, right_force=0;
	// read all measures:
	current_millis = tick / 1000.0;
	temperature = analogRead(A0) / 9.31; // line where LM35 is connected, converted to celsius
	//LoadCell_1.power_up();
	left_force = LoadCell_1.get_units();
	//LoadCell_1.power_down();
	//LoadCell_2.power_up();
	right_force = LoadCell_2.get_units();
	//LoadCell_2.power_down();
	tock = (millis() - tock) / 1000.0;
	// output all measures
	if (1) {
		Serial.print(current_millis, 3);
		Serial.print(";");
		Serial.print(tock, 3);
		Serial.print(";");
		Serial.print(temperature, 2);
		Serial.print(";");
		Serial.print(left_force, 1);
		Serial.print(";");
		Serial.print(right_force, 1);
		Serial.println();
	}
	if (1) {
		BT.print(current_millis, 3);
		BT.print(";");
		BT.print(tock, 3);
		BT.print(";");
		BT.print(temperature, 2); // line where LM35 is connected, converted to celsius
		BT.print(";");
		BT.print(left_force, 1);
		BT.print(";");
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
	}
	//Serial.println("tock");
}

