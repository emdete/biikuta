SCRIPT=~/bin/md2html.py

all: index.html ic_launcher.svg

run: index.html
	firefox file://`pwd`/$<

dbg: all
	chgrp www-data . *
	rsync --archive --verbose . littlun:/var/www/emdete.de/biikuta/.
	firefox https://emdete.de/biikuta/

index.html: index.md Makefile $(SCRIPT)
	$(SCRIPT) < $< > $@

ic_launcher.svg: ../art/ic_launcher.svg
	cp $< $@

