Automated Type Contracts Generation 
===================================

`ruby-type-inference` project is a completely new approach to
tackle the problems Ruby dynamic nature by providing more reliable
symbol resolution and type inference.

## Architecture
 
The planned structure consists of the following parts:
* **Ruby Type Tracker** which collects the raw type data for all ruby calls in runtime.
  Currently consists of [type tracker script](ide-plugin/resources/type_tracker.rb) and
  [arg scanner gem] to intercept ruby calls and obtain lowlevel data from VM,
  respectively;

* [**Type contract producer**](contract-creator) server which listens for incoming raw data and transforms it
  to a compact format;

* Code analysis clients (a RubyMine/IJ+Ruby plugin [example](ide-plugin)) which use the contract data
  to provide features for the users such as code completion, better resolving, etc.

* (_todo_) Signature server which receives contracts anonymously from the users and provides
  a compiled contract collections for popular gems.

## Usage

#### Prerequisites

The [arg scanner gem] is required for collecting type information. It should be installed to the
target SDK manually and requires MRI Ruby at least 2.3.

**At the moment only Ruby 2.3 is supported**
   
In order to build plugin:
* Select the appropriate ruby SDK if rvm is used
* `cd arg_scanner`
* `bundle install`
* `bundle exec rake install`

#### Running type tracker

There are two possibilities to use the type tracker:
_(i)_ requiring it from Ruby code and _(ii)_ using IJ/RubyMine plugin.

##### Using in ruby code

1. In order to collect the data for the script needs a contract server to be up and running;
   it could be run by running
  
   ```sh
   ./gradlew contract-creator:runServer
   ```
   
   If you're using RubyMine plugin, there is no need to run server manually since it will
   be run as a plugin service.

1. Require [type tracker](ide-plugin/resources/type_tracker.rb) script when running your ruby process and it will
   start collecting information during that run.

1. Use the data collected by the contract server.

##### Using RubyMine plugin

The easiest way to run the plugin (and the most convenient for its development) is
running it with special gradle task against IJ Ultimate snapshot:
 
```
./gradlew ide-plugin:runIde
```

The task will compile the plugin, run IJ Ultimate with plugin "installed" in it.
There is no need in running anything manually in that case.

If you want to try it with existing RubyMine instance,
you should:

1. Build it via `./gradlew ide-plugin:buildPlugin`
2. Install plugin in the IDE
    * Navigate to `File | Settings | Plugins | Install plugin from disk...`
    * Locate plugin in `ide-plugin/build/distributions` and select.
    * Restart IDE.

Note that due to API changes the plugin may be incompatible with older RM instances.

## Contributions

Any kind of ideas, use cases, contributions and questions are very welcome
as the project is just incubating.
Please feel free to create issues for any sensible request.

[arg scanner gem]: arg_scanner