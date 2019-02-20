# ArgScanner [![Gem Version](https://badge.fury.io/rb/arg_scanner.svg)](https://badge.fury.io/rb/arg_scanner)

`arg_scanner` is a gem with the purpose to track all method calls and
deliver the following information:

* Method signature (arguments, their names and kinds) and declaration place
* The types of argument variables given to each method call done

This information can be used then to calculate and use type contracts
for the analysed methods.

`arg_scanner` is meant to be used as a binary to run any other ruby executable
manually so including it in the `Gemfile` is not necessary.

## Installation

The recommended way to install it is to install manually:

```
gem install arg_scanner
```
**You will possibly need to install [native dependencies](#dependencies)**
    
## Building from sources

If you want to compile the gem from sources, just run

```    
bundle install
bundle exec rake install
```
    
If you have problems with native extension compilation, make sure you have
actual version of [ruby-core-source gem](https://github.com/os97673/debase-ruby_core_source) and 
have [native dependencies](#dependencies) installed. 

## Dependencies

##### [Glib](https://developer.gnome.org/glib/)

macOS: `brew install glib`   
Debian/Ubuntu: `sudo apt install libglib2.0-dev`  
Arch Linux: `sudo pacman -S glib2`  

## Usage

`arg_scanner` provides binary `arg-scanner` which receives any number of
arguments and executes the given command in type tracking mode,
for example:

```
arg-scanner --type-tracker --pipe-file-path=[pipe_file_path] bundle exec rake spec
```
`pipe_file_path` here is path to pipe file which is printed by server's stdout

## Contributing

Bug reports and pull requests are welcome on GitHub at https://github.com/JetBrains/ruby-type-inference

## License

The gem is available as open source under the terms of the [MIT License](http://opensource.org/licenses/MIT).

