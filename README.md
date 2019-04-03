# Discord

This is the reference implementation of the **Discord** platform for **Manebot**, my multi-platform (where platform means chat platform) chatbot framework. You can use this plugin to get Manebot to interact with Discord. The integration is completely seamless; simply install the Discord plugin to Manebot, associate it with a bot user, and watch your existing plugins/features auto-magically work on the Discord platform!

The support for the Discord Bot API is excellently and efficiently provided through **JDA**.

## Manebot

Manebot is a really neat plugin-based Java chatbot framework that allows you to write one "bot", and host it across several platforms. This plugin provides the **Discord** "*Platform*" to Manebot, which allows Manebot to seamlessly interact with Discord and provide all of the features your Manebot instance is set up to provide on Discord.

## Installation

Manebot uses the **Maven** repository system to coordinate plugin and dependency installation. Because of this, you can easily install the Discord platform plugin without interacting with your filesystem at all.

```
plugin install discord
```

After you've installed Discord, you should configure its **token** property (that's how you authenticate with the Discord Bot API), then enable it:

```
plugin enable discord
```

... and that's it! Discord will automatically start with Manebot, and even re-install itself if you "accidentally" the associated JAR files. It's got your back.

**Uninstall**

```
plugin uninstall discord
```

You should restart Manebot too to make sure it's totally unplugged. You can clean up any no longer needed plugins it required with:

```
plugin autoremove
```

### Properties

| Property          	| Default 	| Required 	| Description                                                                                                                                                                                                                                        	|
|-------------------	|---------	|----------	|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------	|
| token             	| (none)  	| Yes      	| Discord needs a *bot* token to authenticate your bot. Generate a token here: https://discordapp.com/developers/applications/                                                                                                                       	|
| shardId           	| 0       	| No       	| If you want to use *sharding* (if your bot is super popular), you can set your shard ID with this property. If you don't know what sharding is, you probably don't need this.                                                                      	|
| totalShards       	| 1       	| No       	| If you want to use *sharding* (if your bot is super popular), you can set the total shard count with this property. If you don't know what sharding is, you probably don't need this.                                                              	|
| audio             	| true    	| No       	| This is a sort of "kill-switch" for the audio system. If you'd rather not have audio features (i.e. music bot), set this to "false". Keep in mind this plugin doesn't handle "music bot" stuff on its own, you'll need some other plugin for that. 	|
| autoReconnect     	| true    	| No       	| If you don't want this plugin to automatically reconnect to Discord when it loses connection, set this to "false".                                                                                                                                 	|
| poolSize          	| 5       	| No       	| This controls the "core pool size" for JDA, the Discord Bot API that this plugin uses. For more information on what this does, go check out their wiki.                                                                                            	|
| idle              	| false   	| No       	| This sets the "idle" property of JDA.                                                                                                                                                                                                              	|
| maxReconnectDelay 	| 900     	| No       	| Sets the maximum re-connection delay, in seconds. This only applies if "autoReconnect" hasn't been manually set to "false".                                                                                                                        	|
| compression       	| true    	| No       	| Set this to "false" if you don't want compression in the communications made to Discord.                                                                                                                                                           	|

### Dependencies

The **Discord** plugin requires the following plugins at least be *installed*. Don't worry, if you don't have them installed, Manebot will automatically install them for you.

* io.manebot.plugins:audio
* io.manebot.plugins:media

## Supported Features

This plugin supports the following essential Discord features:

* Discord user system
* Direct or private text channels
* Guild text channels
* Guild voice channels via the **audio** plugin's AudioChannel system
