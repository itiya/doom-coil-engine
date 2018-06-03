#!/bin/sh
cd $(dirname ${BASH_SOURCE:-$0}) # move script dir
cd ../
sbt assembly
cd deploy
sftp -b sftp.bat conoha
ssh conoha mv doom-coil-engine-assembly-0.1.0-SNAPSHOT.jar btcbot
ssh conoha cd btcbot \&\& "nohup java -jar doom-coil-engine-assembly-0.1.0-SNAPSHOT.jar > nohup.out 2>&1 < /dev/null &"
