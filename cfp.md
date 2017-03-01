# RailsConf17 Session Proposal

### Automated Type Contracts Generation for Ruby 

<div id="text">

Beauty and power of Ruby and Rails pays us back when it comes to finding bugs in large codebases. Static analysis is hindered by magic DSLs and patches.
We may annotate the code with YARD which also enables improved tooling such as code completion. Sadly, the benefits of this process rarely compensate for the effort.

In this session we’ll see a new approach to type annotations generation.
We'll learn how to obtain this data from runtime, to cope with DSLs and monkey patching, propose some tooling beyond YARD and create contracts like `(String, T) -> T`

YARV hacking and minimized DFAs included.

</div>

Body:
<script>document.print(document.body.innerHTML += document.getElementById("text").innerHTML.length)</script>

Header:
<script>document.print(document.body.innerHTML += document.getElementsByTagName("h3")[0].innerHTML.length)</script>

(793)

---------

Probably all of the ruby/rails developers have experienced time-consuming investigations to understand why the particular piece of code does not work as expected. The dynamic nature of Ruby allows for great possibilities which has a drawback — the codebase as a whole becomes entangled and investigations become more difficult compared to some other languages like Java.
As RDoc/YARD annotations may help, they have several drawbacks, too:
* the type system used for documenting attributes, parameters and return values is pretty decent however it is not clear how to connect some types with each other. For example, `def []=` for array usually returns the same type as the second arg taking any type so in YARD this will look like `@param value [Object]`, `@return [Object]` which is not really helpful.
* as documenting the code is a good intent, it takes much time and effort without any guarantee this work will help anybody in the nearest year.
* from some perspective, such documentation itself a kind of contradicts the purpose of Ruby — to be as natural and expressive as possible.

We tackle the problem like some people do manually: run or debug the programs to get valuable info about the code they're interested. As we automate this process, we can retrieve a plenty of information from all code covered by our runs.

The collected information not only might be used for YARD annotations generation but also could be stored in a database to provide additional "static" checks, code completion suggestions and more.

The talk may be useful for two things:
1. Learning technical difficulties we overcame and solutions we implemented to achieve the goal:
    * digging into VM internals to be able to collect the needed information without serious performance degradation,
    * further algorithms to process the raw information into human-readable type contracts,
    * infrastructure to store and use this data later.
2. Discovering real life use cases of this project and its usage on your own codebase. The tool will be ready to play with at the time of the conference.

So this session holds both educational purpose and proposes some new approach to ease rubist everyday work.