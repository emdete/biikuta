#include <SoftwareSerial.h> // https://www.arduino.cc/en/Reference/SoftwareSerial
#include <TimerOne.h> // 

SoftwareSerial BT(10, 11);
// creates a "virtual" serial port/UART
// connect BT module TX to D10
// connect BT module RX to D11
// connect BT Vcc to 5V, GND to GND

int LM35 = A0; // line where LM35 is connected
float tempC; // temperature in celsius
int reading; // value read from ADC
char a; // stores incoming character from other device
int on;

void setup()
{
  on = 1;
  // set 1.1v reference
  analogReference(INTERNAL);
  // timer
  Timer1.initialize(2000000);
  Timer1.attachInterrupt(wakeUp);
  // set digital pin to control as an output
  pinMode(13, OUTPUT);
  // set the data rate for the SoftwareSerial port
  BT.begin(9600);
  // Send test message to other device
  BT.println("bt temp measure");
}

void wakeUp() {
  BT.print("temp=");
  BT.print(tempC, 5);
  BT.print("°C");
  BT.println();
  if (on) {
      digitalWrite(13, HIGH);
  }
  else {
      digitalWrite(13, LOW);
  }
  on = !on;
}

void loop()
{
  if (BT.available()) // if text arrived in from BT serial...
  {
    a=(BT.read());
    if (a=='1')
    {
      on = 1;
      BT.println("LED on");
    }
    else if (a=='0')
    {
      on = 0;
      BT.println("LED off");
    }
    else if (a=='?')
    {
      BT.println("Send '1' to turn LED on");
      BT.println("Send '0' to turn LED on");
    }
    else {
      wakeUp();
    }
  }
  else
  {
    delay(100);
    reading = analogRead(LM35);
    tempC = reading / 9.31;
  }
}

