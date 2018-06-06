#!/bin/sh

# while ! nc -z kafka 9092
# do
#     echo 'Waiting for queue...'
#     sleep 1
# done
java -jar alma-weather-station-plugin.jar "${@}"
