# ArgScanner

`arg_scanner` is a gem with the purpose to track all method calls and
deliver the following information:

* Method signature (arguments, their names and kinds) and declaration place
* The types of argument variables given to each method call done

This information can be used then to calculate and use type contracts
for the analysed methods.

## Installation


`arg_scanner` is meant to be used as a binary to run any other ruby executable
manually so including it in the `Gemfile` is not necessary.

The recommended way to install it is to install manually:

    $ gem install arg_scanner
    
If you want to compile the gem from sources, just run
    
    $ bundle install
    $ bundle exec rake install
    
If you have problems with native extension compilation, make sure you have
actual version of [ruby-core-source gem](https://github.com/os97673/debase-ruby_core_source). 

## Usage

`arg_scanner` provides binary `arg-scanner` which receives any number of
arguments and executes the given command in type tracking mode,
for example:

    $ arg-scanner bundle exec rake spec
    
The gem will need to send the obtained data though TCP socket on **port 7777**.
See [global readme](../README.md) for instructions on how to run server
to receive and process that data.

## Contributing

Bug reports and pull requests are welcome on GitHub at https://github.com/JetBrains/ruby-type-inference

## License

The gem is available as open source under the terms of the [MIT License](http://opensource.org/licenses/MIT).

