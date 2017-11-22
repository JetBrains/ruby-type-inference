# coding: utf-8
lib = File.expand_path('../lib', __FILE__)
$LOAD_PATH.unshift(lib) unless $LOAD_PATH.include?(lib)
require 'arg_scanner/version'

Gem::Specification.new do |spec|
  spec.name = "arg_scanner"
  spec.version = ArgScanner::VERSION
  spec.authors = ["Nickolay Viuginov", "Valentin Fondaratov", "Vladimir Koshelev"]
  spec.email = ["viuginov.nickolay@gmail.com", "fondarat@gmail.com", "vkkoshelev@gmail.com"]

  spec.summary = %q{Program execution tracker to retrieve data types information}
  spec.homepage = "https://github.com/jetbrains/ruby-type-inference"
  spec.license = "MIT"

  # Prevent pushing this gem to RubyGems.org. To allow pushes either set the 'allowed_push_host'
  # to allow pushing to a single host or delete this section to allow pushing to any host.
  # if spec.respond_to?(:metadata)
  #   spec.metadata['allowed_push_host'] = "TODO: Set to 'http://mygemserver.com'"
  # else
  #   raise "RubyGems 2.0 or newer is required to protect against " \
  #     "public gem pushes."
  # end

  spec.files = `git ls-files -z`.split("\x0").reject do |f|
    f.match(%r{^(test|spec|features)/})
  end
  spec.bindir = "bin"
  spec.executables = spec.files.grep(%r{^bin/}) {|f| File.basename(f)}
  spec.require_paths = ["lib"]
  spec.extensions = ["ext/arg_scanner/extconf.rb"]

  spec.add_development_dependency "bundler", "~> 1.13"
  spec.add_development_dependency "rake", "~> 12.0"
  spec.add_development_dependency "rake-compiler"
  spec.add_development_dependency "debase-ruby_core_source", ">= 0.10.0"
end
