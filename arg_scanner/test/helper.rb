$LOAD_PATH.unshift(File.dirname(__dir__) + '/../lib')
require "test-unit"
require "arg_scanner"

class TestTypeTracker
  include Singleton

  attr_reader :last_args_info
  attr_reader :last_call_info

  def initialize
    @tp = TracePoint.new(:call, :return) do |tp|
      case tp.event
        when :call
          ArgScanner.handle_call(tp)

          @last_args_info = ArgScanner.get_args_info.split ';'
          @last_call_info = ArgScanner.get_call_info
        when :return
          ArgScanner.handle_return(tp)
      end
    end
  end

  def enable(*args, &b)
    @tp.enable *args, &b
  end

  def signatures
    Thread.current[:signatures] ||= Array.new
  end

end