Gem::Specification.new do |spec|
  spec.name = 'arg_scanner'
  spec.version = '0.1'
  spec.description = 'Some cool description here'
  spec.summary = 'Short description'
  spec.email = 'author@email.com'
  spec.homepage = ''
  spec.author = 'Author Name'
  spec.files = Dir['lib/**/*.rb'] + Dir['ext/**/*']
  spec.platform = Gem::Platform::RUBY
  spec.require_paths = [ 'lib', 'ext' ]
  spec.extensions = Dir['ext/arg_scanner/extconf.rb']
end
