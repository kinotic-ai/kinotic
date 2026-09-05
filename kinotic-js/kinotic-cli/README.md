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
@kinotic-ai/kinotic-cli/5.2.0-beta.4 darwin-arm64 node-v22.13.1
$ kinotic --help [COMMAND]
USAGE
  $ kinotic COMMAND
...
```
<!-- usagestop -->
# Commands
<!-- commands -->
* [`kinotic gen`](#kinotic-gen)
* [`kinotic generate`](#kinotic-generate)
* [`kinotic init`](#kinotic-init)
* [`kinotic initialize`](#kinotic-initialize)
* [`kinotic login`](#kinotic-login)
* [`kinotic spawn lint`](#kinotic-spawn-lint)
* [`kinotic sync`](#kinotic-sync)
* [`kinotic synchronize`](#kinotic-synchronize)

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

_See code: [src/commands/generate.ts](https://github.com/kinotic-ai/kinotic/blob/develop/kinotic-js/kinotic-cli/src/commands/generate.ts)_

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

_See code: [src/commands/initialize.ts](https://github.com/kinotic-ai/kinotic/blob/develop/kinotic-js/kinotic-cli/src/commands/initialize.ts)_

## `kinotic login`

Log in to a Kinotic Server and store credentials for subsequent commands.

```
USAGE
  $ kinotic login [-s <value>]

FLAGS
  -s, --server=<value>  The Kinotic server to log in to

DESCRIPTION
  Log in to a Kinotic Server and store credentials for subsequent commands

EXAMPLES
  $ kinotic login

  $ kinotic login --server http://localhost:9090
```

_See code: [src/commands/login.ts](https://github.com/kinotic-ai/kinotic/blob/develop/kinotic-js/kinotic-cli/src/commands/login.ts)_

## `kinotic spawn lint`

Reports variables used in a spawn template that are not declared in its spawn.json.

```
USAGE
  $ kinotic spawn lint [DIR] [--fix]

ARGUMENTS
  DIR  [default: .] The spawn template directory

FLAGS
  --fix  Add stub propertySchema entries for undeclared variables

DESCRIPTION
  Reports variables used in a spawn template that are not declared in its spawn.json

EXAMPLES
  $ kinotic spawn lint

  $ kinotic spawn lint ./my-template --fix
```

_See code: [src/commands/spawn/lint.ts](https://github.com/kinotic-ai/kinotic/blob/develop/kinotic-js/kinotic-cli/src/commands/spawn/lint.ts)_

## `kinotic sync`

Synchronize the local Entity definitions with the Kinotic Server

```
USAGE
  $ kinotic sync [-s <value>] [-p] [-v] [--dryRun] [--force]

FLAGS
  -p, --publish         Publish each Entity after save/update
  -s, --server=<value>  The Kinotic server to connect to
  -v, --verbose         Enable verbose logging
      --dryRun          Dry run enables verbose logging and does not save any changes to the server
      --force           Force full regeneration, ignoring incremental change detection

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
  $ kinotic synchronize [-s <value>] [-p] [-v] [--dryRun] [--force]

FLAGS
  -p, --publish         Publish each Entity after save/update
  -s, --server=<value>  The Kinotic server to connect to
  -v, --verbose         Enable verbose logging
      --dryRun          Dry run enables verbose logging and does not save any changes to the server
      --force           Force full regeneration, ignoring incremental change detection

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

_See code: [src/commands/synchronize.ts](https://github.com/kinotic-ai/kinotic/blob/develop/kinotic-js/kinotic-cli/src/commands/synchronize.ts)_
<!-- commandsstop -->
