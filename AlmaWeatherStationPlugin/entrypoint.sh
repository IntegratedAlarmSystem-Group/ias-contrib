#!/bin/bash

while ! nc -z kafka 9092
do
    echo 'Waiting for queue...'
    sleep 1
done
java -jar AlmaWeatherStationPlugin.jar "${@}"
