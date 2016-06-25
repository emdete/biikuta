#include <SoftwareSerial.h>
#include <TimerOne.h>

SoftwareSerial BT(10, 11);
// creates a "virtual" serial port/UART
// connect BT module TX to D10
// connect BT module RX to D11
// connect BT Vcc to 5V, GND to GND

int LM35 = A0; // line where LM35 is connected
float tempC; // temperature in celsius
int reading; // value read from ADC
char a; // stores incoming character from other device

void setup()
{
  // set 1.1v reference
  analogReference(INTERNAL);
  // timer
  Timer1.initialize(100);
  Timer1.attachInterrupt(wakeUp);
  // set digital pin to control as an output
  pinMode(13, OUTPUT);
  // set the data rate for the SoftwareSerial port
  BT.begin(9600);
  // Send test message to other device
  BT.println("Hello from Arduino");
}

void wakeUp() {
  BT.println(tempC);
}

void loop()
{
  if (BT.available()) // if text arrived in from BT serial...
  {
    a=(BT.read());
    if (a=='1')
    {
      digitalWrite(13, HIGH);
      BT.println("LED on");
      BT.println(tempC);
    }
    else if (a=='0')
    {
      digitalWrite(13, LOW);
      BT.println("LED off");
      BT.println(tempC);
    }
    else if (a=='?')
    {
      BT.println("Send '1' to turn LED on");
      BT.println("Send '0' to turn LED on");
      BT.println(tempC);
    }
    else {
      BT.println(tempC);
    }
  }
  else
  {
    delay(100);
    int i;
    reading = analogRead(LM35);
    tempC = reading / 9.31;
  }
}
