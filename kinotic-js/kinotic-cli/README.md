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
* [`kinotic create project NAME`](#kinotic-create-project-name)
* [`kinotic gen`](#kinotic-gen)
* [`kinotic generate`](#kinotic-generate)
* [`kinotic init`](#kinotic-init)
* [`kinotic initialize`](#kinotic-initialize)
* [`kinotic sync`](#kinotic-sync)
* [`kinotic synchronize`](#kinotic-synchronize)

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
<!-- commandsstop -->
