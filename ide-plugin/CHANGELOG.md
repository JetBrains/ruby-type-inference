## 0.1.1 (15 Dec 2017)

* (#17) Fix "find usages" action for dynamic symbols which resolve to text-based
  definitions.

## 0.1 (29 Nov 2017)

Initial plugin version

* Collect State action

  Adds on_exit hook which dumps class/module includes structure and contained methods
  which can be used for resolution/completion later. 

* Collect Type action

  Enables call tracing (with a considerable slowdown) and dumps return types which
  can be used for better type inference.

* Symbol/Type provider to improve resolution and type inference based on the collected
  data.