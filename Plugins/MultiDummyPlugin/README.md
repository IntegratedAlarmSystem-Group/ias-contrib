# MultiDummyPlugin

A variant of the DummyPlugin. This plugin publishes ias-values associated to multiple monitoring points.

## Compile locally with Ant
To Compile the Plugin, write `ant build` in the command line.  
To Run the Plugin, type `iasRun -l j` and the class name `org.eso.ias.plugin.MultiDummyPlugin`. Then, specify the name of the .json File you will use for the configuration (some files are already provided in the `config_files` folder). You can also add the Server Sink and Port Sink as optional arguments in the command line; if you choose not to the information will be taken from the config.json File.

## Compile locally with Gradle
To Compile the Plugin, write `gradle build` in the command line.  
To Run the Plugin, type `java -jar dist/multi-dummy-plugin.jar`. Then, specify the name of the .json File you will use for the configuration (some files are already provided in the `config_files` folder). You can also add the Server Sink and Port Sink as optional arguments in the command line; if you choose not to the information will be taken from the config.json File.

## Usage
Once you run the Plugin, you will be shown all available commands, for example:
```
 > value [double]
 ```
 In this case, you are able to change the value of the current ID by typing `value` and then the new value you want to change it to, for example:
 ```
 value 10.0
 >>*current ID* value updated to: 10.0
```
This applies to the other commands aswell.

There is an option to ask for help by typing `?`, this will print the default values and all available commands.

## Run with Docker
- Use the Dockerfile-build to build the Plugin with the provided `config_files` inside the docker container.
- Use the Dockerfile-mount to build the Plugin with an empty `config_files` folder inside the docker container. Then, when runninng mount a local folder with the configuration files you want to use. See `docker-compose-dummy.yml` in `integration-tools/docker/develop` for more details.
