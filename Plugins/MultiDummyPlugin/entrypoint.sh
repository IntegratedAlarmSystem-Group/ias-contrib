#!/bin/sh

# while ! nc -z kafka 9092
# do
#     echo 'Waiting for queue...'
#     sleep 1
# done
ls -la
ls -la config_files
java -jar "${1}" multi-dummy-plugin.jar "${2}"
