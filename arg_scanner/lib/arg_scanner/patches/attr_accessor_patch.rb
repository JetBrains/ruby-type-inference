# This attr_* methods own implementation is needed because TracePoint doesn't catch methods which were
# created by attr_accessor (or by attr_reader or by attr_writer). Because Ruby implements methods created by attr_*
# using some kind of magic which is not ruby method or even C function. So here I just implement attr_* methods as
# simply Ruby methods which can be easily caught by TracePoint and type providing for methods created via attr_*
# methods then works fine.
class Module
  def attr_reader(*funs)
    funs.each do |fun|
      class_eval { define_method(fun) { instance_variable_get(:"@#{fun}") } }
    end
  end

  def attr_writer(*funs)
    funs.each do |fun|
      class_eval { define_method(:"#{fun}=") { |x| instance_variable_set(:"@#{fun}", x) } }
    end
  end

  def attr_accessor(*funs)
    attr_reader(*funs)
    attr_writer(*funs)
  end
end
