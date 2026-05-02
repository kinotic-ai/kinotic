Kinotic CLI
=================

<!-- toc -->
* [Usage](#usage)
* [Commands](#commands)
<!-- tocstop -->
# Usage
<!-- usage -->
```sh-session
$ npm install -g @kinotic-ai/kinotic-cli
$ kinotic COMMAND
running command...
$ kinotic (--version)
@kinotic-ai/kinotic-cli/2.2.0 darwin-arm64 node-v22.13.1
$ kinotic --help [COMMAND]
USAGE
  $ kinotic COMMAND
...
```
<!-- usagestop -->
# Commands
<!-- commands -->
* [`kinotic autocomplete [SHELL]`](#kinotic-autocomplete-shell)
* [`kinotic create project NAME`](#kinotic-create-project-name)
* [`kinotic gen`](#kinotic-gen)
* [`kinotic generate`](#kinotic-generate)
* [`kinotic help [COMMAND]`](#kinotic-help-command)
* [`kinotic init`](#kinotic-init)
* [`kinotic initialize`](#kinotic-initialize)
* [`kinotic plugins`](#kinotic-plugins)
* [`kinotic plugins add PLUGIN`](#kinotic-plugins-add-plugin)
* [`kinotic plugins:inspect PLUGIN...`](#kinotic-pluginsinspect-plugin)
* [`kinotic plugins install PLUGIN`](#kinotic-plugins-install-plugin)
* [`kinotic plugins link PATH`](#kinotic-plugins-link-path)
* [`kinotic plugins remove [PLUGIN]`](#kinotic-plugins-remove-plugin)
* [`kinotic plugins reset`](#kinotic-plugins-reset)
* [`kinotic plugins uninstall [PLUGIN]`](#kinotic-plugins-uninstall-plugin)
* [`kinotic plugins unlink [PLUGIN]`](#kinotic-plugins-unlink-plugin)
* [`kinotic plugins update`](#kinotic-plugins-update)
* [`kinotic sync`](#kinotic-sync)
* [`kinotic synchronize`](#kinotic-synchronize)
* [`kinotic update [CHANNEL]`](#kinotic-update-channel)

## `kinotic autocomplete [SHELL]`

Display autocomplete installation instructions.

```
USAGE
  $ kinotic autocomplete [SHELL] [-r]

ARGUMENTS
  [SHELL]  (zsh|bash|powershell) Shell type

FLAGS
  -r, --refresh-cache  Refresh cache (ignores displaying instructions)

DESCRIPTION
  Display autocomplete installation instructions.

EXAMPLES
  $ kinotic autocomplete

  $ kinotic autocomplete bash

  $ kinotic autocomplete zsh

  $ kinotic autocomplete powershell

  $ kinotic autocomplete --refresh-cache
```

_See code: [@oclif/plugin-autocomplete](https://github.com/oclif/plugin-autocomplete/blob/v3.2.41/src/commands/autocomplete/index.ts)_

## `kinotic create project NAME`

Creates a Kinotic Project

```
USAGE
  $ kinotic create project NAME

ARGUMENTS
  NAME  The name for the project

DESCRIPTION
  Creates a Kinotic Project

EXAMPLES
  $ kinotic create project MyProjectName
```

_See code: [src/commands/create/project.ts](https://github.com/kinotic-ai/kinotic/blob/v2.2.0/src/commands/create/project.ts)_

## `kinotic gen`

This will generate all Repository classes.

```
USAGE
  $ kinotic gen [-v] [-f]

FLAGS
  -f, --force    Force full regeneration, ignoring incremental change detection
  -v, --verbose  Enable verbose logging

DESCRIPTION
  This will generate all Repository classes.

ALIASES
  $ kinotic gen

EXAMPLES
  $ kinotic generate

  $ kinotic gen

  $ kinotic gen -v

  $ kinotic gen --force
```

## `kinotic generate`

This will generate all Repository classes.

```
USAGE
  $ kinotic generate [-v] [-f]

FLAGS
  -f, --force    Force full regeneration, ignoring incremental change detection
  -v, --verbose  Enable verbose logging

DESCRIPTION
  This will generate all Repository classes.

ALIASES
  $ kinotic gen

EXAMPLES
  $ kinotic generate

  $ kinotic gen

  $ kinotic gen -v

  $ kinotic gen --force
```

_See code: [src/commands/generate.ts](https://github.com/kinotic-ai/kinotic/blob/v2.2.0/src/commands/generate.ts)_

## `kinotic help [COMMAND]`

Display help for kinotic.

```
USAGE
  $ kinotic help [COMMAND...] [-n]

ARGUMENTS
  [COMMAND...]  Command to show help for.

FLAGS
  -n, --nested-commands  Include all nested commands in the output.

DESCRIPTION
  Display help for kinotic.
```

_See code: [@oclif/plugin-help](https://github.com/oclif/plugin-help/blob/6.2.38/src/commands/help.ts)_

## `kinotic init`

This will initialize a new Kinotic Project for use with the Kinotic CLI.

```
USAGE
  $ kinotic init [-a <value>] [-e <value>] [-r <value>] [-m]

FLAGS
  -a, --application=<value>  The name of the application you want to use
  -e, --entities=<value>     Path to the directory containing the Entity definitions
  -m, --mirror               Mirror the entity folder structure under the repository path
  -r, --repository=<value>   Path to the directory to write generated Repository classes

DESCRIPTION
  This will initialize a new Kinotic Project for use with the Kinotic CLI.

ALIASES
  $ kinotic init

EXAMPLES
  $ kinotic initialize --application my.app --entities path/to/entities --repository path/to/repository

  $ kinotic init --application my.app --entities path/to/entities --repository path/to/repository

  $ kinotic init -a my.app -e path/to/entities -r path/to/repository

  $ kinotic init -a my.app -e path/to/entities -r path/to/repository --mirror
```

## `kinotic initialize`

This will initialize a new Kinotic Project for use with the Kinotic CLI.

```
USAGE
  $ kinotic initialize [-a <value>] [-e <value>] [-r <value>] [-m]

FLAGS
  -a, --application=<value>  The name of the application you want to use
  -e, --entities=<value>     Path to the directory containing the Entity definitions
  -m, --mirror               Mirror the entity folder structure under the repository path
  -r, --repository=<value>   Path to the directory to write generated Repository classes

DESCRIPTION
  This will initialize a new Kinotic Project for use with the Kinotic CLI.

ALIASES
  $ kinotic init

EXAMPLES
  $ kinotic initialize --application my.app --entities path/to/entities --repository path/to/repository

  $ kinotic init --application my.app --entities path/to/entities --repository path/to/repository

  $ kinotic init -a my.app -e path/to/entities -r path/to/repository

  $ kinotic init -a my.app -e path/to/entities -r path/to/repository --mirror
```

_See code: [src/commands/initialize.ts](https://github.com/kinotic-ai/kinotic/blob/v2.2.0/src/commands/initialize.ts)_

## `kinotic plugins`

List installed plugins.

```
USAGE
  $ kinotic plugins [--json] [--core]

FLAGS
  --core  Show core plugins.

GLOBAL FLAGS
  --json  Format output as json.

DESCRIPTION
  List installed plugins.

EXAMPLES
  $ kinotic plugins
```

_See code: [@oclif/plugin-plugins](https://github.com/oclif/plugin-plugins/blob/5.4.58/src/commands/plugins/index.ts)_

## `kinotic plugins add PLUGIN`

Installs a plugin into kinotic.

```
USAGE
  $ kinotic plugins add PLUGIN... [--json] [-f] [-h] [-s | -v]

ARGUMENTS
  PLUGIN...  Plugin to install.

FLAGS
  -f, --force    Force npm to fetch remote resources even if a local copy exists on disk.
  -h, --help     Show CLI help.
  -s, --silent   Silences npm output.
  -v, --verbose  Show verbose npm output.

GLOBAL FLAGS
  --json  Format output as json.

DESCRIPTION
  Installs a plugin into kinotic.

  Uses npm to install plugins.

  Installation of a user-installed plugin will override a core plugin.

  Use the KINOTIC_NPM_LOG_LEVEL environment variable to set the npm loglevel.
  Use the KINOTIC_NPM_REGISTRY environment variable to set the npm registry.

ALIASES
  $ kinotic plugins add

EXAMPLES
  Install a plugin from npm registry.

    $ kinotic plugins add myplugin

  Install a plugin from a github url.

    $ kinotic plugins add https://github.com/someuser/someplugin

  Install a plugin from a github slug.

    $ kinotic plugins add someuser/someplugin
```

## `kinotic plugins:inspect PLUGIN...`

Displays installation properties of a plugin.

```
USAGE
  $ kinotic plugins inspect PLUGIN...

ARGUMENTS
  PLUGIN...  [default: .] Plugin to inspect.

FLAGS
  -h, --help     Show CLI help.
  -v, --verbose

GLOBAL FLAGS
  --json  Format output as json.

DESCRIPTION
  Displays installation properties of a plugin.

EXAMPLES
  $ kinotic plugins inspect myplugin
```

_See code: [@oclif/plugin-plugins](https://github.com/oclif/plugin-plugins/blob/5.4.58/src/commands/plugins/inspect.ts)_

## `kinotic plugins install PLUGIN`

Installs a plugin into kinotic.

```
USAGE
  $ kinotic plugins install PLUGIN... [--json] [-f] [-h] [-s | -v]

ARGUMENTS
  PLUGIN...  Plugin to install.

FLAGS
  -f, --force    Force npm to fetch remote resources even if a local copy exists on disk.
  -h, --help     Show CLI help.
  -s, --silent   Silences npm output.
  -v, --verbose  Show verbose npm output.

GLOBAL FLAGS
  --json  Format output as json.

DESCRIPTION
  Installs a plugin into kinotic.

  Uses npm to install plugins.

  Installation of a user-installed plugin will override a core plugin.

  Use the KINOTIC_NPM_LOG_LEVEL environment variable to set the npm loglevel.
  Use the KINOTIC_NPM_REGISTRY environment variable to set the npm registry.

ALIASES
  $ kinotic plugins add

EXAMPLES
  Install a plugin from npm registry.

    $ kinotic plugins install myplugin

  Install a plugin from a github url.

    $ kinotic plugins install https://github.com/someuser/someplugin

  Install a plugin from a github slug.

    $ kinotic plugins install someuser/someplugin
```

_See code: [@oclif/plugin-plugins](https://github.com/oclif/plugin-plugins/blob/5.4.58/src/commands/plugins/install.ts)_

## `kinotic plugins link PATH`

Links a plugin into the CLI for development.

```
USAGE
  $ kinotic plugins link PATH [-h] [--install] [-v]

ARGUMENTS
  PATH  [default: .] path to plugin

FLAGS
  -h, --help          Show CLI help.
  -v, --verbose
      --[no-]install  Install dependencies after linking the plugin.

DESCRIPTION
  Links a plugin into the CLI for development.

  Installation of a linked plugin will override a user-installed or core plugin.

  e.g. If you have a user-installed or core plugin that has a 'hello' command, installing a linked plugin with a 'hello'
  command will override the user-installed or core plugin implementation. This is useful for development work.


EXAMPLES
  $ kinotic plugins link myplugin
```

_See code: [@oclif/plugin-plugins](https://github.com/oclif/plugin-plugins/blob/5.4.58/src/commands/plugins/link.ts)_

## `kinotic plugins remove [PLUGIN]`

Removes a plugin from the CLI.

```
USAGE
  $ kinotic plugins remove [PLUGIN...] [-h] [-v]

ARGUMENTS
  [PLUGIN...]  plugin to uninstall

FLAGS
  -h, --help     Show CLI help.
  -v, --verbose

DESCRIPTION
  Removes a plugin from the CLI.

ALIASES
  $ kinotic plugins unlink
  $ kinotic plugins remove

EXAMPLES
  $ kinotic plugins remove myplugin
```

## `kinotic plugins reset`

Remove all user-installed and linked plugins.

```
USAGE
  $ kinotic plugins reset [--hard] [--reinstall]

FLAGS
  --hard       Delete node_modules and package manager related files in addition to uninstalling plugins.
  --reinstall  Reinstall all plugins after uninstalling.
```

_See code: [@oclif/plugin-plugins](https://github.com/oclif/plugin-plugins/blob/5.4.58/src/commands/plugins/reset.ts)_

## `kinotic plugins uninstall [PLUGIN]`

Removes a plugin from the CLI.

```
USAGE
  $ kinotic plugins uninstall [PLUGIN...] [-h] [-v]

ARGUMENTS
  [PLUGIN...]  plugin to uninstall

FLAGS
  -h, --help     Show CLI help.
  -v, --verbose

DESCRIPTION
  Removes a plugin from the CLI.

ALIASES
  $ kinotic plugins unlink
  $ kinotic plugins remove

EXAMPLES
  $ kinotic plugins uninstall myplugin
```

_See code: [@oclif/plugin-plugins](https://github.com/oclif/plugin-plugins/blob/5.4.58/src/commands/plugins/uninstall.ts)_

## `kinotic plugins unlink [PLUGIN]`

Removes a plugin from the CLI.

```
USAGE
  $ kinotic plugins unlink [PLUGIN...] [-h] [-v]

ARGUMENTS
  [PLUGIN...]  plugin to uninstall

FLAGS
  -h, --help     Show CLI help.
  -v, --verbose

DESCRIPTION
  Removes a plugin from the CLI.

ALIASES
  $ kinotic plugins unlink
  $ kinotic plugins remove

EXAMPLES
  $ kinotic plugins unlink myplugin
```

## `kinotic plugins update`

Update installed plugins.

```
USAGE
  $ kinotic plugins update [-h] [-v]

FLAGS
  -h, --help     Show CLI help.
  -v, --verbose

DESCRIPTION
  Update installed plugins.
```

_See code: [@oclif/plugin-plugins](https://github.com/oclif/plugin-plugins/blob/5.4.58/src/commands/plugins/update.ts)_

## `kinotic sync`

Synchronize the local Entity definitions with the Kinotic Server

```
USAGE
  $ kinotic sync [-s <value>] [-p] [-v] [-f <value>] [--dryRun] [--force]

FLAGS
  -f, --authHeaderFile=<value>  JSON File containing authentication headers
  -p, --publish                 Publish each Entity after save/update
  -s, --server=<value>          The Kinotic server to connect to
  -v, --verbose                 Enable verbose logging
      --dryRun                  Dry run enables verbose logging and does not save any changes to the server
      --force                   Force full regeneration, ignoring incremental change detection

DESCRIPTION
  Synchronize the local Entity definitions with the Kinotic Server

ALIASES
  $ kinotic sync

EXAMPLES
  $ kinotic synchronize

  $ kinotic sync

  $ kinotic synchronize --server http://localhost:9090 --publish --verbose

  $ kinotic sync -p -v -s http://localhost:9090
```

## `kinotic synchronize`

Synchronize the local Entity definitions with the Kinotic Server

```
USAGE
  $ kinotic synchronize [-s <value>] [-p] [-v] [-f <value>] [--dryRun] [--force]

FLAGS
  -f, --authHeaderFile=<value>  JSON File containing authentication headers
  -p, --publish                 Publish each Entity after save/update
  -s, --server=<value>          The Kinotic server to connect to
  -v, --verbose                 Enable verbose logging
      --dryRun                  Dry run enables verbose logging and does not save any changes to the server
      --force                   Force full regeneration, ignoring incremental change detection

DESCRIPTION
  Synchronize the local Entity definitions with the Kinotic Server

ALIASES
  $ kinotic sync

EXAMPLES
  $ kinotic synchronize

  $ kinotic sync

  $ kinotic synchronize --server http://localhost:9090 --publish --verbose

  $ kinotic sync -p -v -s http://localhost:9090
```

_See code: [src/commands/synchronize.ts](https://github.com/kinotic-ai/kinotic/blob/v2.2.0/src/commands/synchronize.ts)_

## `kinotic update [CHANNEL]`

update the kinotic CLI

```
USAGE
  $ kinotic update [CHANNEL] [--force |  | [-a | -v <value> | -i]] [-b ]

FLAGS
  -a, --available        See available versions.
  -b, --verbose          Show more details about the available versions.
  -i, --interactive      Interactively select version to install. This is ignored if a channel is provided.
  -v, --version=<value>  Install a specific version.
      --force            Force a re-download of the requested version.

DESCRIPTION
  update the kinotic CLI

EXAMPLES
  Update to the stable channel:

    $ kinotic update stable

  Update to a specific version:

    $ kinotic update --version 1.0.0

  Interactively select version:

    $ kinotic update --interactive

  See available versions:

    $ kinotic update --available
```

_See code: [@oclif/plugin-update](https://github.com/oclif/plugin-update/blob/4.7.23/src/commands/update.ts)_
<!-- commandsstop -->
