$LOAD_PATH.unshift(File.dirname(__dir__) + '/../lib')
require "test-unit"
require "arg_scanner"

class TestTypeTracker
  include Singleton

  attr_reader :last_args_info
  attr_reader :last_call_info
  attr_reader :last_json

  def initialize
    @tp = TracePoint.new(:call, :return) do |tp|
      case tp.event
        when :call
          handle_call(tp)
        when :return
          handle_return(tp)
      end
    end
  end

  def enable(*args, &b)
    @tp.enable(*args, &b)
  end

  def signatures
    Thread.current[:signatures] ||= Array.new
  end

  private
  def handle_call(tp)
    #handle_call(VALUE self, VALUE lineno, VALUE method_name, VALUE path)
    signature = ArgScanner.handle_call(tp.lineno, tp.method_id, tp.path)
    signatures.push(signature)

    @last_args_info = ArgScanner.get_args_info.split ';'
      # @last_call_info = ArgScanner.get_call_info.split ';'
  end

  def handle_return(tp)
    sigi = signatures
    unless sigi.empty?
      signature = sigi.pop

      defined_class = tp.defined_class
      return if !defined_class || defined_class.singleton_class?

      receiver_name = defined_class.name ? defined_class : defined_class.ancestors.first
      return_type_name = tp.return_value.class

      return if !receiver_name || !receiver_name.to_s || receiver_name.to_s.length > 200

      json = ArgScanner.handle_return(signature, return_type_name) +
          "\"receiver_name\":\"#{receiver_name}\",\"return_type_name\":\"#{return_type_name}\","

      matches = tp.path.scan(/\w+-\d+(?:\.\d+)+/)
      gem_name, gem_version = matches[0] ? matches[0].split('-') : ['', '']
      json += '"gem_name":"' + gem_name.to_s + '","gem_version":"' + gem_version.to_s + '"}'
      @last_json = json
    end
  end
end