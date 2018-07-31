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
          handle_call(tp)
        when :return
          handle_return(tp)
      end
    end
  end

  def enable(*args, &b)
    @tp.enable *args, &b
  end

  def signatures
    Thread.current[:signatures] ||= Array.new
  end

  private
  def handle_call(tp)
    if tp.defined_class && !tp.defined_class.singleton_class?
      signature = ArgScanner.handle_call(tp.lineno, tp.method_id.id2name, tp.path)
    else
      signature = nil
    end
    signatures.push(signature)

    @last_args_info = ArgScanner.get_args_info.split ';'
    @last_call_info = ArgScanner.get_call_info
  end

  def handle_return(tp)
    signature = signatures.pop
    if signature
      ArgScanner.handle_return(signature, tp.defined_class.name, tp.return_value.class.name)
    end
  end
end