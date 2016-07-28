def bar(a=42)
  instance_variable_set(:@a, a)
  @a
end

class Qwerty
  def bar(a=42)
    instance_variable_set(:@a, a)
    @a
  end

  def foo
    bar
  end
end

qwe = Qwerty.new

# def qwe.baz
#   instance_variable_set(:@a, a)
#   @a
# end

# bar(42).        # Ruby:Call     -> Identifier    -> Instance method
# baz(42).        # Ruby:Call     -> Identifier    -> null
# bar.            # Identifier    -> Identifier    -> Instance method
# baz.            # Identifier    -> Identifier    -> null
# qwe.bar(42).    # Ruby:Call     -> Dot reference -> Instance method
# qwe.baz(42).    # Ruby:Call     -> Dot reference -> null
# qwe.bar.        # Dot reference -> Dot reference -> Instance method
# qwe.baz.
