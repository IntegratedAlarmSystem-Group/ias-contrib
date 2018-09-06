# MultiDummyPlugin

A variant of the DummyPlugin. This plugin publishes ias-values associated to multiple monitoring points.
 
To Compile the Plugin, write `ant build` in the command line.  
To Run the Plugin, type `iasRun -l j` and the class name `org.eso.ias.plugin.MultiDummyPlugin`. Then, specify the name of the .json File you will use for the configuration. You can also add the Server Sink and Port Sink as optional arguments in the command line; if you choose not to the information will be taken from the config.json File.

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
