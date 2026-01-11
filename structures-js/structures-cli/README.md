Structures CLI
=================

<!-- toc -->
* [Usage](#usage)
* [Commands](#commands)
<!-- tocstop -->
# Usage
<!-- usage -->
```sh-session
$ npm install -g @mindignited/structures-cli
$ structures COMMAND
running command...
$ structures (--version)
@mindignited/structures-cli/3.5.0-beta.10 darwin-arm64 node-v22.13.1
$ structures --help [COMMAND]
USAGE
  $ structures COMMAND
...
```
<!-- usagestop -->
# Commands
<!-- commands -->
* [`structures autocomplete [SHELL]`](#structures-autocomplete-shell)
* [`structures gen`](#structures-gen)
* [`structures generate`](#structures-generate)
* [`structures help [COMMAND]`](#structures-help-command)
* [`structures init`](#structures-init)
* [`structures initialize`](#structures-initialize)
* [`structures plugins`](#structures-plugins)
* [`structures plugins add PLUGIN`](#structures-plugins-add-plugin)
* [`structures plugins:inspect PLUGIN...`](#structures-pluginsinspect-plugin)
* [`structures plugins install PLUGIN`](#structures-plugins-install-plugin)
* [`structures plugins link PATH`](#structures-plugins-link-path)
* [`structures plugins remove [PLUGIN]`](#structures-plugins-remove-plugin)
* [`structures plugins reset`](#structures-plugins-reset)
* [`structures plugins uninstall [PLUGIN]`](#structures-plugins-uninstall-plugin)
* [`structures plugins unlink [PLUGIN]`](#structures-plugins-unlink-plugin)
* [`structures plugins update`](#structures-plugins-update)
* [`structures sync`](#structures-sync)
* [`structures synchronize`](#structures-synchronize)
* [`structures update [CHANNEL]`](#structures-update-channel)

## `structures autocomplete [SHELL]`

Display autocomplete installation instructions.

```
USAGE
  $ structures autocomplete [SHELL] [-r]

ARGUMENTS
  [SHELL]  (zsh|bash|powershell) Shell type

FLAGS
  -r, --refresh-cache  Refresh cache (ignores displaying instructions)

DESCRIPTION
  Display autocomplete installation instructions.

EXAMPLES
  $ structures autocomplete

  $ structures autocomplete bash

  $ structures autocomplete zsh

  $ structures autocomplete powershell

  $ structures autocomplete --refresh-cache
```

_See code: [@oclif/plugin-autocomplete](https://github.com/oclif/plugin-autocomplete/blob/v3.2.39/src/commands/autocomplete/index.ts)_

## `structures gen`

This will generate all Entity Service classes.

```
USAGE
  $ structures gen [-v]

FLAGS
  -v, --verbose  Enable verbose logging

DESCRIPTION
  This will generate all Entity Service classes.

ALIASES
  $ structures gen

EXAMPLES
  $ structures generate

  $ structures gen

  $ structures gen -v
```

## `structures generate`

This will generate all Entity Service classes.

```
USAGE
  $ structures generate [-v]

FLAGS
  -v, --verbose  Enable verbose logging

DESCRIPTION
  This will generate all Entity Service classes.

ALIASES
  $ structures gen

EXAMPLES
  $ structures generate

  $ structures gen

  $ structures gen -v
```

_See code: [src/commands/generate.ts](https://github.com/mindignited-Foundation/structures/blob/v3.5.0-beta.10/src/commands/generate.ts)_

## `structures help [COMMAND]`

Display help for structures.

```
USAGE
  $ structures help [COMMAND...] [-n]

ARGUMENTS
  [COMMAND...]  Command to show help for.

FLAGS
  -n, --nested-commands  Include all nested commands in the output.

DESCRIPTION
  Display help for structures.
```

_See code: [@oclif/plugin-help](https://github.com/oclif/plugin-help/blob/v6.0.21/src/commands/help.ts)_

## `structures init`

This will initialize a new Structures Project for use with the Structures CLI.

```
USAGE
  $ structures init [-a <value>] [-e <value>] [-g <value>]

FLAGS
  -a, --application=<value>  The name of the application you want to use
  -e, --entities=<value>     Path to the directory containing the Entity definitions
  -g, --generated=<value>    Path to the directory to write generated Services

DESCRIPTION
  This will initialize a new Structures Project for use with the Structures CLI.

ALIASES
  $ structures init

EXAMPLES
  $ structures initialize --application my.app --entities path/to/entities --generated path/to/services

  $ structures init --application my.app --entities path/to/entities --generated path/to/services

  $ structures init -a my.app -e path/to/entities -g path/to/services
```

## `structures initialize`

This will initialize a new Structures Project for use with the Structures CLI.

```
USAGE
  $ structures initialize [-a <value>] [-e <value>] [-g <value>]

FLAGS
  -a, --application=<value>  The name of the application you want to use
  -e, --entities=<value>     Path to the directory containing the Entity definitions
  -g, --generated=<value>    Path to the directory to write generated Services

DESCRIPTION
  This will initialize a new Structures Project for use with the Structures CLI.

ALIASES
  $ structures init

EXAMPLES
  $ structures initialize --application my.app --entities path/to/entities --generated path/to/services

  $ structures init --application my.app --entities path/to/entities --generated path/to/services

  $ structures init -a my.app -e path/to/entities -g path/to/services
```

_See code: [src/commands/initialize.ts](https://github.com/mindignited-Foundation/structures/blob/v3.5.0-beta.10/src/commands/initialize.ts)_

## `structures plugins`

List installed plugins.

```
USAGE
  $ structures plugins [--json] [--core]

FLAGS
  --core  Show core plugins.

GLOBAL FLAGS
  --json  Format output as json.

DESCRIPTION
  List installed plugins.

EXAMPLES
  $ structures plugins
```

_See code: [@oclif/plugin-plugins](https://github.com/oclif/plugin-plugins/blob/v5.4.54/src/commands/plugins/index.ts)_

## `structures plugins add PLUGIN`

Installs a plugin into structures.

```
USAGE
  $ structures plugins add PLUGIN... [--json] [-f] [-h] [-s | -v]

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
  Installs a plugin into structures.

  Uses npm to install plugins.

  Installation of a user-installed plugin will override a core plugin.

  Use the STRUCTURES_NPM_LOG_LEVEL environment variable to set the npm loglevel.
  Use the STRUCTURES_NPM_REGISTRY environment variable to set the npm registry.

ALIASES
  $ structures plugins add

EXAMPLES
  Install a plugin from npm registry.

    $ structures plugins add myplugin

  Install a plugin from a github url.

    $ structures plugins add https://github.com/someuser/someplugin

  Install a plugin from a github slug.

    $ structures plugins add someuser/someplugin
```

## `structures plugins:inspect PLUGIN...`

Displays installation properties of a plugin.

```
USAGE
  $ structures plugins inspect PLUGIN...

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
  $ structures plugins inspect myplugin
```

_See code: [@oclif/plugin-plugins](https://github.com/oclif/plugin-plugins/blob/v5.4.54/src/commands/plugins/inspect.ts)_

## `structures plugins install PLUGIN`

Installs a plugin into structures.

```
USAGE
  $ structures plugins install PLUGIN... [--json] [-f] [-h] [-s | -v]

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
  Installs a plugin into structures.

  Uses npm to install plugins.

  Installation of a user-installed plugin will override a core plugin.

  Use the STRUCTURES_NPM_LOG_LEVEL environment variable to set the npm loglevel.
  Use the STRUCTURES_NPM_REGISTRY environment variable to set the npm registry.

ALIASES
  $ structures plugins add

EXAMPLES
  Install a plugin from npm registry.

    $ structures plugins install myplugin

  Install a plugin from a github url.

    $ structures plugins install https://github.com/someuser/someplugin

  Install a plugin from a github slug.

    $ structures plugins install someuser/someplugin
```

_See code: [@oclif/plugin-plugins](https://github.com/oclif/plugin-plugins/blob/v5.4.54/src/commands/plugins/install.ts)_

## `structures plugins link PATH`

Links a plugin into the CLI for development.

```
USAGE
  $ structures plugins link PATH [-h] [--install] [-v]

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
  $ structures plugins link myplugin
```

_See code: [@oclif/plugin-plugins](https://github.com/oclif/plugin-plugins/blob/v5.4.54/src/commands/plugins/link.ts)_

## `structures plugins remove [PLUGIN]`

Removes a plugin from the CLI.

```
USAGE
  $ structures plugins remove [PLUGIN...] [-h] [-v]

ARGUMENTS
  [PLUGIN...]  plugin to uninstall

FLAGS
  -h, --help     Show CLI help.
  -v, --verbose

DESCRIPTION
  Removes a plugin from the CLI.

ALIASES
  $ structures plugins unlink
  $ structures plugins remove

EXAMPLES
  $ structures plugins remove myplugin
```

## `structures plugins reset`

Remove all user-installed and linked plugins.

```
USAGE
  $ structures plugins reset [--hard] [--reinstall]

FLAGS
  --hard       Delete node_modules and package manager related files in addition to uninstalling plugins.
  --reinstall  Reinstall all plugins after uninstalling.
```

_See code: [@oclif/plugin-plugins](https://github.com/oclif/plugin-plugins/blob/v5.4.54/src/commands/plugins/reset.ts)_

## `structures plugins uninstall [PLUGIN]`

Removes a plugin from the CLI.

```
USAGE
  $ structures plugins uninstall [PLUGIN...] [-h] [-v]

ARGUMENTS
  [PLUGIN...]  plugin to uninstall

FLAGS
  -h, --help     Show CLI help.
  -v, --verbose

DESCRIPTION
  Removes a plugin from the CLI.

ALIASES
  $ structures plugins unlink
  $ structures plugins remove

EXAMPLES
  $ structures plugins uninstall myplugin
```

_See code: [@oclif/plugin-plugins](https://github.com/oclif/plugin-plugins/blob/v5.4.54/src/commands/plugins/uninstall.ts)_

## `structures plugins unlink [PLUGIN]`

Removes a plugin from the CLI.

```
USAGE
  $ structures plugins unlink [PLUGIN...] [-h] [-v]

ARGUMENTS
  [PLUGIN...]  plugin to uninstall

FLAGS
  -h, --help     Show CLI help.
  -v, --verbose

DESCRIPTION
  Removes a plugin from the CLI.

ALIASES
  $ structures plugins unlink
  $ structures plugins remove

EXAMPLES
  $ structures plugins unlink myplugin
```

## `structures plugins update`

Update installed plugins.

```
USAGE
  $ structures plugins update [-h] [-v]

FLAGS
  -h, --help     Show CLI help.
  -v, --verbose

DESCRIPTION
  Update installed plugins.
```

_See code: [@oclif/plugin-plugins](https://github.com/oclif/plugin-plugins/blob/v5.4.54/src/commands/plugins/update.ts)_

## `structures sync`

Synchronize the local Entity definitions with the Structures Server

```
USAGE
  $ structures sync [-s <value>] [-p] [-v] [--dryRun]

FLAGS
  -p, --publish         Publish each Entity after save/update
  -s, --server=<value>  The structures server to connect to
  -v, --verbose         Enable verbose logging
      --dryRun          Dry run enables verbose logging and does not save any changes to the server

DESCRIPTION
  Synchronize the local Entity definitions with the Structures Server

ALIASES
  $ structures sync

EXAMPLES
  $ structures synchronize

  $ structures sync

  $ structures synchronize --server http://localhost:9090 --publish --verbose

  $ structures sync -p -v -s http://localhost:9090
```

## `structures synchronize`

Synchronize the local Entity definitions with the Structures Server

```
USAGE
  $ structures synchronize [-s <value>] [-p] [-v] [--dryRun]

FLAGS
  -p, --publish         Publish each Entity after save/update
  -s, --server=<value>  The structures server to connect to
  -v, --verbose         Enable verbose logging
      --dryRun          Dry run enables verbose logging and does not save any changes to the server

DESCRIPTION
  Synchronize the local Entity definitions with the Structures Server

ALIASES
  $ structures sync

EXAMPLES
  $ structures synchronize

  $ structures sync

  $ structures synchronize --server http://localhost:9090 --publish --verbose

  $ structures sync -p -v -s http://localhost:9090
```

_See code: [src/commands/synchronize.ts](https://github.com/mindignited-Foundation/structures/blob/v3.5.0-beta.10/src/commands/synchronize.ts)_

## `structures update [CHANNEL]`

update the structures CLI

```
USAGE
  $ structures update [CHANNEL] [--force |  | [-a | -v <value> | -i]] [-b ]

FLAGS
  -a, --available        See available versions.
  -b, --verbose          Show more details about the available versions.
  -i, --interactive      Interactively select version to install. This is ignored if a channel is provided.
  -v, --version=<value>  Install a specific version.
      --force            Force a re-download of the requested version.

DESCRIPTION
  update the structures CLI

EXAMPLES
  Update to the stable channel:

    $ structures update stable

  Update to a specific version:

    $ structures update --version 1.0.0

  Interactively select version:

    $ structures update --interactive

  See available versions:

    $ structures update --available
```

_See code: [@oclif/plugin-update](https://github.com/oclif/plugin-update/blob/v4.7.16/src/commands/update.ts)_
<!-- commandsstop -->
