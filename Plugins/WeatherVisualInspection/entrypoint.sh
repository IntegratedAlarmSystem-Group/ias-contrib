#!/bin/sh

# while ! nc -z kafka 9092
# do
#     echo 'Waiting for queue...'
#     sleep 1
# done
ls -la
java -jar "${1}" weather-visual-inspection-plugin.jar "${2}"
