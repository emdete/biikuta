# biikuta

## Zügelkräfte beim Reiten

Der Mensch nutzt seine Hände für fast alle Tätigkeiten. Diese Fähigkeit ist ihm
bei Reiten eher hinderlich. Die Kommunikation mit dem Pferd beim Reiten findet
über viele verschiedene Mittel statt, am wenigsten mit den Händen und über die
Zügel.

Um die Überbewertung der Zügelhilfen klar zu machen, wollte ich immer ein
Messgerät, dass während des Reitens diese Kräfte darstellen kann. Sensoren am
Zügel messen die Kräfte und übertragen die Werte per Funk auf ein Gerät zur
Darstellung. Heute sind die Komponenten für ein solches System günstig zu
bekommen.

- Ein [Arduino](https://www.arduino.cc/) bietet den zentralen Computer am
  Pferd, der die verschiedenen Componenten steuert,
- Kraftsensoren mit einer [Wheatstone
  bridge](https://en.wikipedia.org/wiki/Wheatstone_bridge) nehmen die Kräfte
  auf,
- Ein [Bluetooth Adapter](documentation/file1512931637830.jpg) sendet die Werte
  kontinuierlich zum darstellenden Rechner,
- Der Darstellende Rechner ist ein Smartphone mit einem Programm, dass die
  Kräfte in Balkendiagrammen darstellt.

## Echtzeit Zügelkraftmessung

![Fertiges Modell](documentation/IMG_20180213_164304.jpg)

Alle Details der Umsetzung sind [hier](documentation/) nachzulesen.

## Kontakt

Wer Interesse hat, kann direkt mir mir [Kontakt](mailto:biikuta@emdete.de) aufnehmen.
