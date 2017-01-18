# RailsConf17 Session Proposal

### Automated Type Contracts Generation for Ruby 

<div id="text">

Writing in Ruby is easy, fast and happy. 
However it is not so easy, fast and happy when it comes to finding bugs in large
codebase or trying to understand how the sacred Rails magic works.

One may use RDoc or YARD to annotate the code and provide info and
improved tooling such as code completion. But who will do this annotation and how much time
will it take?

Come to see the new approach for generating type contracts like:
 ```ruby
# (String|Symbol, Hash) -> ActiveSupport::HashWithIndifferentAccess
# (String|Symbol, T < Object) -> T
def []=(key, value)
```
Some C code, YARV hacking, minimized DFAs included. 
 
</div>

Body:
<script>document.print(document.body.innerHTML += document.getElementById("text").innerHTML.length)</script>

Header:
<script>document.print(document.body.innerHTML += document.getElementsByTagName("h3")[0].innerHTML.length)</script>